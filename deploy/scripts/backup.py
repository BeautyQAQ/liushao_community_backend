#!/usr/bin/env python3
"""Create, verify and restore a joint database/upload backup on a local Docker host."""
import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
import fcntl
import gzip
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import signal
import subprocess
import sys
import tarfile
import tempfile
import time

DEPLOY = Path(__file__).resolve().parents[1]
ROOT = DEPLOY.parents[1]
SERVICES = ('mysql', 'redis', 'admin-api', 'community-api', 'admin-web', 'community-web')
APPS = SERVICES[2:]
DATABASES = ['liushao_community']
LEGACY_DATABASES = ['vueadmin', 'liushao_community']
PNG_NAME = re.compile(r'[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.png')
PROJECT_NAME = re.compile(r'[a-z0-9][a-z0-9_-]*')
BUNDLE_FILES = ('database.sql.gz', 'uploads.tar.gz', 'manifest.json')


class BackupError(Exception):
    pass


def digest(stream):
    result = hashlib.sha256()
    while chunk := stream.read(1024 * 1024):
        result.update(chunk)
    return result.hexdigest()


def file_digest(path):
    with path.open('rb') as stream:
        return digest(stream)


def archive_files(path):
    files = []
    seen = set()
    try:
        with tarfile.open(path, 'r:gz') as archive:
            for member in archive:
                if member.name in ('.', './') and member.isdir():
                    continue
                name = member.name.removeprefix('./')
                if not member.isfile() or not PNG_NAME.fullmatch(name) or name in seen:
                    raise BackupError('Upload archive contains an unsafe or duplicate entry')
                seen.add(name)
                with archive.extractfile(member) as stream:
                    files.append({'name': name, 'size': member.size, 'sha256': digest(stream)})
        # tar readers may stop before the gzip footer; read it to check CRC as well.
        with gzip.open(path, 'rb') as stream:
            while stream.read(1024 * 1024):
                pass
    except (OSError, EOFError, tarfile.TarError) as error:
        raise BackupError('Invalid upload archive') from error
    return sorted(files, key=lambda item: item['name'])


def verify_bundle(bundle):
    bundle = Path(bundle)
    try:
        if set(path.name for path in bundle.iterdir()) != {*BUNDLE_FILES, 'SHA256SUMS'}:
            raise BackupError('Backup must contain exactly its four declared files')
        for name in (*BUNDLE_FILES, 'SHA256SUMS'):
            path = bundle / name
            if path.is_symlink() or not path.is_file():
                raise BackupError('Backup is incomplete or contains linked files')
        entries = {}
        for line in (bundle / 'SHA256SUMS').read_text().splitlines():
            match = re.fullmatch(r'([a-f0-9]{64})  (database\.sql\.gz|uploads\.tar\.gz|manifest\.json)', line)
            if not match or match[2] in entries:
                raise BackupError('Invalid checksum manifest')
            entries[match[2]] = match[1]
        if set(entries) != set(BUNDLE_FILES):
            raise BackupError('Checksum manifest must cover the complete backup')
        for name, expected in entries.items():
            if file_digest(bundle / name) != expected:
                raise BackupError(f'Backup checksum mismatch: {name}')
        manifest = json.loads((bundle / 'manifest.json').read_text())
        expected_databases = {1: LEGACY_DATABASES, 2: DATABASES}.get(manifest.get('schemaVersion'))
        if (expected_databases is None or not isinstance(manifest.get('id'), str)
                or not manifest['id'] or manifest.get('databases') != expected_databases
                or not PROJECT_NAME.fullmatch(manifest.get('source', {}).get('project', ''))):
            raise BackupError('Unsupported backup format')
        images = manifest.get('images', {})
        if set(images) != set(SERVICES) or any(not re.fullmatch(r'sha256:[a-f0-9]{64}', value) for value in images.values()):
            raise BackupError('Backup must identify all six runtime images')
        with gzip.open(bundle / 'database.sql.gz', 'rb') as stream:
            if not stream.read(1):
                raise BackupError('Database dump is empty')
            while stream.read(1024 * 1024):
                pass
        files = archive_files(bundle / 'uploads.tar.gz')
        declared = manifest['uploads']['files']
        if sorted(declared, key=lambda item: item['name']) != files:
            raise BackupError('Upload archive differs from its file manifest')
        indexed = {item['name']: item for item in files}
        seen = set()
        for row in manifest['uploads']['databaseRows']:
            name, size = row['name'], row['size']
            if (name in seen or name not in indexed or indexed[name]['size'] != size
                    or not isinstance(size, int) or isinstance(size, bool) or size < 0):
                raise BackupError('Database upload reference is missing or has the wrong size')
            seen.add(name)
        return manifest
    except (OSError, EOFError, ValueError, KeyError, TypeError) as error:
        raise BackupError('Invalid or incomplete backup bundle') from error


def clean_environment():
    environment = {key: value for key, value in os.environ.items()
                   if not re.match(r'^(DB_|REDIS_|SMTP_|JWT_|COMMUNITY_|ADMIN_|MYSQL_|BOOTSTRAP_|COMPOSE_|DEPLOY_|E2E_)', key)
                   and key not in ('BIND_ADDRESS', 'SPRING_PROFILES_ACTIVE', 'DOCKER_CONTEXT')}
    endpoint = environment.get('DOCKER_HOST')
    if not endpoint:
        context = json.loads(command(['docker', 'context', 'inspect'], env=environment))
        endpoint = context[0]['Endpoints']['docker']['Host']
    if not endpoint.startswith('unix://'):
        raise BackupError('Only a local Docker Unix socket is supported')
    environment['DOCKER_HOST'] = endpoint
    return environment


def command(args, *, env=None, stdin=None, stdout=None, timeout=900):
    process = subprocess.Popen(args, env=env, stdin=stdin,
                               stdout=stdout if stdout is not None else subprocess.PIPE,
                               stderr=subprocess.PIPE, start_new_session=True)
    try:
        output, _ = process.communicate(timeout=timeout)
    except BaseException:
        try:
            os.killpg(process.pid, signal.SIGTERM)
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid, signal.SIGKILL)
            process.wait()
        except ProcessLookupError:
            pass
        raise
    if process.returncode:
        raise BackupError(f'Operation failed (exit {process.returncode}): {args[0]} {args[1]}')
    return output.decode() if output is not None else ''


def pipeline(producer_args, consumer_args, *, environment, stdout=None, timeout=3600):
    """Bounded streaming pipeline; never buffer SQL or expose its error output."""
    processes = []
    deadline = time.monotonic() + timeout
    with tempfile.TemporaryFile() as errors:
        try:
            producer = subprocess.Popen(producer_args, env=environment, stdout=subprocess.PIPE,
                                        stderr=errors, start_new_session=True)
            processes.append(producer)
            consumer = subprocess.Popen(consumer_args, env=environment, stdin=producer.stdout,
                                        stdout=stdout if stdout is not None else subprocess.DEVNULL,
                                        stderr=errors, start_new_session=True)
            processes.append(consumer)
            producer.stdout.close()
            for process in processes:
                if process.wait(timeout=max(1, deadline - time.monotonic())):
                    raise BackupError('Database compression/export/import pipeline failed')
        finally:
            for process in processes:
                if process.poll() is None:
                    try:
                        os.killpg(process.pid, signal.SIGTERM)
                        process.wait(timeout=10)
                    except subprocess.TimeoutExpired:
                        os.killpg(process.pid, signal.SIGKILL)
                        process.wait()
                    except ProcessLookupError:
                        pass


class Stack:
    def __init__(self, env_file, environment=None, override=None):
        self.env_file = Path(env_file).resolve()
        if not self.env_file.is_file():
            raise BackupError('Deployment environment file does not exist')
        self.environment = environment or clean_environment()
        self.args = ['docker', 'compose', '--project-directory', str(DEPLOY), '--env-file', str(self.env_file),
                     '-f', str(DEPLOY / 'compose.yaml')]
        if override:
            self.args += ['-f', str(override)]
        # Capture only; never print the expanded configuration, which contains credentials.
        self.config = json.loads(self.run('config', '--format', 'json'))
        self.project = self.config['name']
        if not PROJECT_NAME.fullmatch(self.project):
            raise BackupError('Invalid Compose project identity')
        self.args += ['-p', self.project]

    def run(self, *args, **kwargs):
        return command(self.args + list(args), env=self.environment, **kwargs)

    def container(self, service):
        ids = self.run('ps', '-aq', service).split()
        if len(ids) != 1:
            raise BackupError(f'Expected exactly one {service} container')
        data = json.loads(command(['docker', 'inspect', ids[0]], env=self.environment))[0]
        if data['Config']['Labels'].get('com.docker.compose.project') != self.project:
            raise BackupError('Container project identity mismatch')
        return data

    def sql(self, query):
        return self.run('exec', '-T', 'mysql', 'sh', '-c',
                        'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql --user=root --batch --raw --skip-column-names -e "$1"',
                        'backup-query', query)

    def wait_healthy(self, services, timeout=300):
        deadline = time.monotonic() + timeout
        while True:
            if all(self.container(name)['State'].get('Health', {}).get('Status') == 'healthy' for name in services):
                return
            if time.monotonic() >= deadline:
                raise BackupError('Applications did not become healthy after maintenance')
            time.sleep(2)


@contextmanager
def project_lock(project):
    directory = DEPLOY / '.artifacts/backup-locks'
    directory.mkdir(parents=True, exist_ok=True, mode=0o700)
    with (directory / (project + '.lock')).open('a') as stream:
        try:
            fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError as error:
            raise BackupError('Another backup or restore operation is using this project') from error
        try:
            yield
        finally:
            fcntl.flock(stream, fcntl.LOCK_UN)


def upload_volume(container):
    matches = [mount for mount in container['Mounts'] if mount['Destination'] == '/app/data/uploads']
    if len(matches) != 1 or matches[0]['Type'] != 'volume':
        raise BackupError('Expected a named community uploads volume')
    return matches[0]['Name']


def helper(stack, image, volume, action, *, stdin=None, stdout=None, readonly=False):
    name = f'{stack.project}-backup-helper-{secrets.token_hex(4)}'
    mount = f'type=volume,source={volume},target=/data' + (',readonly' if readonly else '')
    args = ['docker', 'run', '--rm', '--name', name, '--network', 'none', '--read-only',
            '--security-opt', 'no-new-privileges', '--mount', mount, '--user', '0:0',
            '--entrypoint', 'sh', '-i', image, '-c', action]
    try:
        return command(args, env=stack.environment, stdin=stdin, stdout=stdout, timeout=3600)
    finally:
        # Killing the attached client alone can leave a daemon-side helper running.
        subprocess.run(['docker', 'rm', '-f', name], env=stack.environment,
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=30)


def revisions():
    result = {}
    for name in ('authority_management_backend', 'authority_management_frontend',
                 'liushao_community_backend', 'liushao_community_frontend'):
        try:
            result[name] = {'commit': command(['git', '-C', str(ROOT / name), 'rev-parse', 'HEAD']).strip(),
                            'dirty': bool(command(['git', '-C', str(ROOT / name), 'status', '--porcelain']).strip())}
        except BackupError:
            result[name] = {'commit': None, 'dirty': None}
    return result


def create_backup(args):
    if not shutil.which('gzip'):
        raise BackupError('gzip is required for streaming database backups')
    stack = Stack(args.env_file)
    with project_lock(stack.project):
        return create_locked(args, stack)


def create_locked(args, stack):
    containers = {name: stack.container(name) for name in SERVICES}
    if not all(containers[name]['State'].get('Health', {}).get('Status') == 'healthy' for name in ('mysql', 'redis')):
        raise BackupError('MySQL and Redis must be healthy')
    running = [name for name in APPS if containers[name]['State']['Running']]
    if running and not args.stop_apps:
        raise BackupError('Apps are running; use --stop-apps for a maintenance window')
    scheduler = stack.sql('SELECT @@GLOBAL.event_scheduler').strip()
    enabled_events = int(stack.sql("SELECT COUNT(*) FROM information_schema.EVENTS WHERE STATUS='ENABLED'").strip())
    if scheduler not in ('OFF', 'DISABLED') and enabled_events:
        raise BackupError('Disable MySQL event_scheduler before a consistent backup')
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True, mode=0o700)
    identifier = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ') + '-' + secrets.token_hex(4)
    partial = output / ('.liushao-' + identifier + '.partial')
    destination = output / ('liushao-' + identifier)
    partial.mkdir(mode=0o700)
    published = False
    try:
        if running:
            print('Stop applications and drain in-flight writes', flush=True)
            stack.run('stop', *running, timeout=180)
        if any(stack.container(name)['State']['Running'] for name in APPS):
            raise BackupError('All application writers must be stopped')
        rows = []
        for line in stack.sql('SELECT filename,size_bytes FROM liushao_community.community_upload ORDER BY filename').splitlines():
            name, size = line.split('\t')
            if not PNG_NAME.fullmatch(name):
                raise BackupError('Unexpected upload filename in database')
            rows.append({'name': name, 'size': int(size)})
        print('Export the shared database and the matching uploads volume', flush=True)
        # Stream SQL directly into gzip, with a deadline covering both processes.
        dump = stack.args + ['exec', '-T', 'mysql', 'sh', '-c',
            'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysqldump --user=root --default-character-set=utf8mb4 '
            '--single-transaction --quick --routines --events --triggers --set-gtid-purged=OFF --no-tablespaces '
            '--databases liushao_community']
        with (partial / 'database.sql.gz').open('wb') as output_file:
            pipeline(dump, ['gzip', '-c'], environment=stack.environment, stdout=output_file)
        with (partial / 'uploads.tar.gz').open('wb') as stream:
            helper(stack, containers['community-api']['Image'], upload_volume(containers['community-api']),
                   'exec tar -czf - -C /data .', stdout=stream, readonly=True)
        files = archive_files(partial / 'uploads.tar.gz')
        manifest = {'schemaVersion': 2, 'id': identifier, 'createdAt': datetime.now(timezone.utc).isoformat(),
                    'source': {'project': stack.project, 'applications': revisions()}, 'databases': DATABASES,
                    'images': {name: data['Image'] for name, data in containers.items()},
                    'mysql': {'eventScheduler': scheduler},
                    'consistency': 'Both APIs and web services stopped; no external writers or DDL permitted',
                    'uploads': {'files': files, 'databaseRows': rows}}
        (partial / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')
        (partial / 'SHA256SUMS').write_text(''.join(f'{file_digest(partial / name)}  {name}\n' for name in BUNDLE_FILES))
        verify_bundle(partial)
        partial.rename(destination)
        published = True
    finally:
        # Finish recovery even if deletion fails or a second cancellation arrives.
        previous = {sig: signal.signal(sig, signal.SIG_IGN) for sig in (signal.SIGINT, signal.SIGTERM)}
        try:
            try:
                if not published:
                    shutil.rmtree(partial)
            finally:
                if running:
                    print('Restart only the applications that were running before maintenance', flush=True)
                    stack.run('start', *running, timeout=180)
                    stack.wait_healthy(running)
        finally:
            for sig, handler in previous.items():
                signal.signal(sig, handler)
    print(f'Backup complete: {destination}', flush=True)
    return destination


def require_empty_project(project, environment):
    for resource in ('container', 'volume', 'network'):
        args = ['docker', resource, 'ls', '-q', '--filter', 'label=com.docker.compose.project=' + project]
        if resource == 'container':
            args.insert(3, '-a')
        if command(args, env=environment).strip():
            raise BackupError('Restore requires a completely new project with no existing resources')
    # Also reject conventional volume names whose Compose labels were removed.
    for name in ('mysql-data', 'redis-data', 'community-uploads'):
        existing = command(['docker', 'volume', 'ls', '--format', '{{.Name}}'], env=environment).splitlines()
        if f'{project}_{name}' in existing:
            raise BackupError('Restore target volume already exists')


def restore_backup(args):
    if not shutil.which('gzip'):
        raise BackupError('gzip is required for streaming database restores')
    bundle = args.bundle.resolve()
    print('Verify the entire backup before creating target resources', flush=True)
    manifest = verify_bundle(bundle)
    if manifest['schemaVersion'] != 2:
        raise BackupError('Legacy two-database backups require the matching pre-consolidation deployment; '
                          'restore there, migrate vueadmin into liushao_community, then create a new backup')
    if not PROJECT_NAME.fullmatch(args.project) or args.project == manifest['source']['project']:
        raise BackupError('Restore project must be valid and different from the source')
    if not all(1 <= port <= 65535 for port in (args.community_port, args.admin_port)) or args.community_port == args.admin_port:
        raise BackupError('Restore ports must be distinct and valid')
    environment = clean_environment()
    with project_lock(args.project):
        return restore_locked(args, bundle, manifest, environment)


def restore_locked(args, bundle, manifest, environment):
    require_empty_project(args.project, environment)
    for image in set(manifest['images'].values()):
        command(['docker', 'image', 'inspect', image], env=environment)
    env_file = args.output_env.resolve()
    override = env_file.with_name(env_file.name + '.images.yaml')
    if env_file.exists() or override.exists():
        raise BackupError('Restore output environment and image override must not already exist')
    command([sys.executable, str(DEPLOY / 'scripts/init-deploy.py'), '--output', str(env_file),
             '--project', args.project, '--community-port', str(args.community_port), '--admin-port', str(args.admin_port)])
    env_file.write_text(env_file.read_text().replace('BOOTSTRAP_ADMIN=true\n', 'BOOTSTRAP_ADMIN=false\n'))
    pinned = {name: {'image': image, 'pull_policy': 'never'} for name, image in manifest['images'].items()}
    # Restored ENABLED events must not execute against a partially imported DB.
    # Keep this off across container recreation until an operator reviews events.
    pinned['mysql']['command'] = ['--character-set-server=utf8mb4', '--collation-server=utf8mb4_unicode_ci',
                                  '--event-scheduler=OFF']
    override.write_text(json.dumps({'services': pinned}, indent=2) + '\n')
    stack = Stack(env_file, environment, override)
    try:
        print('Initialize fresh databases, Redis and upload storage', flush=True)
        stack.run('up', '-d', '--no-build', '--wait', '--wait-timeout', '600', 'mysql', 'redis', timeout=660)
        # Create stopped application containers/volumes without starting any writer.
        stack.run('create', '--no-build', 'community-api', timeout=60)
        container = stack.container('community-api')
        with (bundle / 'uploads.tar.gz').open('rb') as stream:
            helper(stack, manifest['images']['community-api'], upload_volume(container),
                   'set -eu; test -z "$(ls -A /data)"; tar -xzf - --no-same-owner --no-same-permissions -C /data; '
                   'chown -R 10001:10001 /data; find /data -type d -exec chmod 700 {} +; find /data -type f -exec chmod 600 {} +', stdin=stream)
        actual_hashes = helper(stack, manifest['images']['community-api'], upload_volume(container),
                               'for path in /data/*.png; do [ -f "$path" ] || continue; sha256sum "$path"; done', readonly=True)
        actual_hashes = {name: checksum for checksum, name in
                         (line.split('  /data/', 1) for line in actual_hashes.splitlines())}
        if actual_hashes != {item['name']: item['sha256'] for item in manifest['uploads']['files']}:
            raise BackupError('Restored upload files differ from the backup')
        pipeline(['gzip', '-dc', str(bundle / 'database.sql.gz')],
                 stack.args + ['exec', '-T', 'mysql', 'sh', '-c',
                               'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql --user=root'],
                 environment=environment)
        actual_rows = []
        for line in stack.sql('SELECT filename,size_bytes FROM liushao_community.community_upload ORDER BY filename').splitlines():
            name, size = line.split('\t')
            actual_rows.append({'name': name, 'size': int(size)})
        if actual_rows != sorted(manifest['uploads']['databaseRows'], key=lambda row: row['name']):
            raise BackupError('Restored database upload references differ from the backup')
        print('Start the restored applications and check production endpoints', flush=True)
        stack.run('up', '-d', '--no-build', '--wait', '--wait-timeout', '600', timeout=660)
        command([sys.executable, str(DEPLOY / 'scripts/smoke-deploy.py'),
                 '--community', f'http://127.0.0.1:{args.community_port}', '--admin', f'http://127.0.0.1:{args.admin_port}'], timeout=120)
    except BaseException:
        stack.run('stop', *APPS, timeout=180)
        raise
    print(f'Restore complete: {args.project}; private environment: {env_file}; image override: {override}', flush=True)


def main():
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='action', required=True)
    create = sub.add_parser('create', help='Joint backup; --stop-apps acknowledges a brief maintenance window')
    create.add_argument('output', nargs='?', type=Path, default=DEPLOY / 'backups')
    create.add_argument('--env-file', type=Path, default=Path(os.environ.get('DEPLOY_ENV_FILE', DEPLOY / '.env')))
    create.add_argument('--stop-apps', action='store_true')
    verify = sub.add_parser('verify', help='Verify checksums, compressed streams and upload references without Docker')
    verify.add_argument('bundle', type=Path)
    restore = sub.add_parser('restore', help='Restore into a completely new local project using recorded images')
    restore.add_argument('bundle', type=Path)
    restore.add_argument('--output-env', type=Path, required=True)
    restore.add_argument('--project', required=True)
    restore.add_argument('--community-port', type=int, default=9088)
    restore.add_argument('--admin-port', type=int, default=9089)
    args = parser.parse_args()
    def cancelled(_signal, _frame):
        raise KeyboardInterrupt()
    signal.signal(signal.SIGTERM, cancelled)
    try:
        if args.action == 'create':
            create_backup(args)
        elif args.action == 'verify':
            verify_bundle(args.bundle)
            print('PASS: complete database/upload backup verified')
        else:
            restore_backup(args)
    except (BackupError, OSError, subprocess.TimeoutExpired, KeyboardInterrupt) as error:
        print(f'FAIL: {error or "Cancelled; attempted application recovery"}', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
