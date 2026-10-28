#!/usr/bin/env python3
"""Exercise joint database/upload backup and restore in disposable local Docker projects."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import signal
import socket
import struct
import subprocess
import sys
import time
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import Request, urlopen
import zlib

DEPLOY = Path(__file__).resolve().parents[1]
ROOT = DEPLOY.parents[1]
BACKUP = DEPLOY / "scripts/backup.py"
APPLICATIONS = ("authority_management_backend", "authority_management_frontend",
                "liushao_community_backend", "liushao_community_frontend")
SECRET_KEYS = ("MYSQL_ROOT_PASSWORD", "ADMIN_DB_PASSWORD", "COMMUNITY_DB_PASSWORD", "REDIS_PASSWORD",
               "ADMIN_JWT_SECRET", "COMMUNITY_JWT_SECRET", "COMMUNITY_ADMIN_KEY")


def isolated_environment():
    return {key: value for key, value in os.environ.items()
            if not re.match(r"^(DB_|REDIS_|SMTP_|JWT_|COMMUNITY_|ADMIN_|MYSQL_|BOOTSTRAP_|COMPOSE_|DEPLOY_|E2E_)", key)
            and key not in ("BIND_ADDRESS", "SPRING_PROFILES_ACTIVE", "DOCKER_CONTEXT")}


def free_ports(count):
    sockets = []
    try:
        for _ in range(count):
            connection = socket.socket()
            connection.bind(("127.0.0.1", 0))
            sockets.append(connection)
        return [connection.getsockname()[1] for connection in sockets]
    finally:
        for connection in sockets:
            connection.close()


def read_environment(path):
    return dict(line.split("=", 1) for line in path.read_text().splitlines()
                if line and not line.startswith("#"))


def png(red, green, blue):
    def chunk(name, payload):
        return struct.pack("!I", len(payload)) + name + payload + struct.pack("!I", zlib.crc32(name + payload))
    pixels = b"".join(b"\x00" + bytes((red, green, blue, 255)) * 2 for _ in range(2))
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack("!2I5B", 2, 2, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(pixels)) + chunk(b"IEND", b""))


def request(base, path, *, method="GET", data=None, token=None, expected=200, form=False,
            content_type=None, raw=False):
    if not re.fullmatch(r"http://127\.0\.0\.1:\d+", base) or not path.startswith("/api/"):
        raise RuntimeError("HTTP requests must use this drill's generated loopback endpoints")
    headers = {}
    if token:
        headers["Authorization"] = token if token.startswith("Bearer ") else "Bearer " + token
    if data is not None and not isinstance(data, bytes):
        data = urlencode(data).encode() if form else json.dumps(data, ensure_ascii=False).encode()
        headers["Content-Type"] = "application/x-www-form-urlencoded" if form else "application/json"
    if content_type:
        headers["Content-Type"] = content_type
    deadline = time.monotonic() + 70
    while True:
        try:
            with urlopen(Request(base + path, data=data, headers=headers, method=method), timeout=20) as response:
                status, body = response.status, response.read()
        except HTTPError as error:
            status, body = error.code, error.read()
        # A rejected rate-limited mutation is safe to retry; never repeat a transport failure.
        if status != 429 or expected == 429 or time.monotonic() >= deadline:
            break
        time.sleep(2)
    if status != expected:
        raise RuntimeError(f"{method} {path.split('?')[0]} returned HTTP {status}, expected {expected}")
    if raw:
        return body
    result = json.loads(body)
    if expected == 200 and result.get("code") != 200:
        raise RuntimeError(f"{method} {path.split('?')[0]} rejected the operation")
    return result.get("data")


def upload(base, token, image):
    boundary = "drill-" + secrets.token_hex(12)
    data = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="drill.png"\r\n'
            'Content-Type: image/png\r\n\r\n').encode() + image + f"\r\n--{boundary}--\r\n".encode()
    result = request(base, "/api/uploads/image", method="POST", data=data, token=token,
                     content_type="multipart/form-data; boundary=" + boundary)
    if not re.fullmatch(r"/uploads/[a-f0-9-]+\.png", result.get("url", "")):
        raise RuntimeError("Unexpected uploaded image URL")
    return result["url"]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--npm-registry", help="Optional trusted npm registry used only during image builds")
    parser.add_argument("--maven-settings", type=Path, help="Optional private Maven build settings")
    parser.add_argument("--delete-backups", action="store_true", help="Remove this drill's private backup bundles after verification")
    parser.add_argument("--keep-environment", action="store_true", help="Keep only this drill's two projects for debugging")
    args = parser.parse_args()
    os.umask(0o077)
    for binary in ("docker",):
        if not shutil.which(binary):
            parser.error(f"{binary} is required")
    if not BACKUP.is_file():
        parser.error("scripts/backup.py is required")
    if args.maven_settings and not args.maven_settings.is_file():
        parser.error("Maven settings file does not exist")
    environment = isolated_environment()
    endpoint = environment.get("DOCKER_HOST")
    if not endpoint:
        try:
            context = json.loads(subprocess.check_output(["docker", "context", "inspect"], env=environment,
                                                        text=True, stderr=subprocess.PIPE))
            endpoint = context[0]["Endpoints"]["docker"]["Host"]
        except (subprocess.CalledProcessError, KeyError, ValueError):
            parser.error("Cannot inspect the local Docker context")
    if not endpoint.startswith("unix://"):
        parser.error("This drill requires a local Docker Unix socket")
    environment["DOCKER_HOST"] = endpoint
    suffix = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ").lower() + "-" + secrets.token_hex(4)
    artifacts = DEPLOY / ".artifacts/backup-restore" / suffix
    private = artifacts / "private"
    private.mkdir(parents=True, mode=0o700)
    source_env, target_env = private / "source.env", private / "restored.env"
    source_project, target_project = ("liushao-backup-drill-" + suffix + part for part in ("-source", "-restored"))
    source_community_port, source_admin_port, target_community_port, target_admin_port = free_ports(4)
    source_base, source_admin = (f"http://127.0.0.1:{port}" for port in (source_community_port, source_admin_port))
    target_base, target_admin = (f"http://127.0.0.1:{port}" for port in (target_community_port, target_admin_port))
    logfile = artifacts / "runner.log"
    checks = []
    backup_invocations = []
    passed = False
    cleanup_verified = False
    started = time.monotonic()
    started_at = datetime.now(timezone.utc).isoformat()
    bundle = None
    commands = {}

    def terminate(_signal, _frame):
        raise KeyboardInterrupt("Termination requested")
    signal.signal(signal.SIGTERM, terminate)

    def run(command, label=None, timeout=1200, capture=False, expected=0):
        if label:
            print(label, flush=True)
        if len(command) >= 3 and command[1] == str(BACKUP):
            backup_invocations.append({"action": command[2],
                                       "sourceSha256": hashlib.sha256(BACKUP.read_bytes()).hexdigest()})
        with logfile.open("ab") as log:
            process = subprocess.Popen(command, cwd=ROOT, env=environment, stdout=subprocess.PIPE if capture else log,
                                       stderr=log, start_new_session=True)
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
        if expected == "failure":
            if process.returncode == 0:
                raise RuntimeError(f"{label or 'Negative check'} unexpectedly succeeded")
        elif process.returncode != expected:
            raise RuntimeError(f"{label or 'Command'} failed (exit {process.returncode}); see private runner.log")
        return output.decode("utf-8") if capture else None

    def compose(path, project):
        command = ["docker", "compose", "--project-directory", str(DEPLOY), "--env-file", str(path),
                   "-p", project, "-f", str(DEPLOY / "compose.yaml")]
        override = path.with_name(path.name + ".images.yaml")
        if override.is_file():
            command += ["-f", str(override)]
        return command

    def sql(command, query):
        return run(command + ["exec", "-T", "mysql", "sh", "-c",
                              'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql --default-character-set=utf8mb4 '
                              '--batch --skip-column-names -uroot -e "$1"', "drill-query", query],
                   capture=True, timeout=30).strip()

    def histories(command):
        return {app: sql(command, f"SELECT installed_rank,COALESCE(version,''),description,type,script,"
                         f"COALESCE(checksum,''),success FROM liushao_community.{table} ORDER BY installed_rank")
                for app, table in (("admin", "flyway_admin_schema_history"),
                                   ("community", "flyway_schema_history"))}

    def upload_rows(command):
        return sql(command, "SELECT filename,user_id,size_bytes FROM liushao_community.community_upload ORDER BY filename")

    def admin_login(base, command, username, password, expected=200):
        captcha = request(base, "/api/captcha")
        token = captcha.get("token", "")
        if not re.fullmatch(r"[a-f0-9-]{36}", token):
            raise RuntimeError("Unexpected CAPTCHA key")
        key = "liushao:admin:captcha:" + token
        code = run(command + ["exec", "-T", "redis", "sh", "-c",
                              'export REDISCLI_AUTH="$REDIS_PASSWORD"; exec redis-cli --raw -n 12 GET "$1"',
                              "drill-captcha", key], capture=True, timeout=20).strip()
        if len(code) != 5:
            raise RuntimeError("Real Redis CAPTCHA was not found")
        result = request(base, "/api/login", method="POST", form=True, expected=expected,
                         data={"username": username, "password": password, "token": token, "code": code})
        return result["token"] if expected == 200 else None

    def record(name):
        checks.append({"name": name, "status": "passed"})
        print("PASS: " + name, flush=True)

    def smoke(community, admin):
        run([sys.executable, str(DEPLOY / "scripts/smoke-deploy.py"), "--community", community,
             "--admin", admin], "Check application readiness and public/authentication boundaries", timeout=120)

    def ensure_no_project(project):
        for command in (["docker", "ps", "-aq", "--filter", f"label=com.docker.compose.project={project}"],
                        ["docker", "volume", "ls", "-q", "--filter", f"label=com.docker.compose.project={project}"]):
            if run(command, capture=True, timeout=30).strip():
                raise RuntimeError("A disposable project's resources remain")

    try:
        run([sys.executable, str(DEPLOY / "scripts/init-deploy.py"), "--output", str(source_env),
             "--project", source_project, "--community-port", str(source_community_port),
             "--admin-port", str(source_admin_port)], "Create independent source credentials", timeout=15)
        source = compose(source_env, source_project)
        commands[source_project] = source
        build = source[:]
        if args.maven_settings:
            environment["MAVEN_SETTINGS_FILE"] = str(args.maven_settings.resolve())
            build += ["-f", str(DEPLOY / "compose.maven.yaml")]
        build += ["--progress", "plain", "build"]
        if args.npm_registry:
            build += ["--build-arg", "NPM_REGISTRY=" + args.npm_registry]
        run(build, "Build all four current production applications")
        run(source + ["up", "-d", "--no-build", "--wait", "--wait-timeout", "600"],
            "Start a completely fresh source database, Redis, upload volume and four applications", timeout=660)
        smoke(source_base, source_admin)
        config = read_environment(source_env)
        print("Seed real users, two distinct uploaded PNGs, Markdown, comments and private messages", flush=True)
        users = []
        for index in range(2):
            username = f"drill{index}_" + secrets.token_hex(5)
            password = "Drill_" + secrets.token_hex(16) + "!"
            registered = request(source_base, "/api/ums/user/register", method="POST",
                                 data={"name": username, "pass": password, "checkPass": password,
                                       "email": username + "@example.test"})
            session = request(source_base, "/api/ums/user/login", method="POST",
                              data={"username": username, "password": password, "rememberMe": False})
            users.append({"username": username, "password": password, "token": session["token"], "id": registered["id"]})
        author, reader = users
        avatar_bytes, post_bytes = png(38, 128, 196), png(228, 90, 32)
        avatar_url = upload(source_base, author["token"], avatar_bytes)
        post_url = upload(source_base, author["token"], post_bytes)
        # ImageIO deliberately decodes and re-encodes every upload. Compare the
        # persisted source bytes with restored bytes, rather than the input encoder.
        avatar_bytes = request(source_base, "/api" + avatar_url, raw=True)
        post_bytes = request(source_base, "/api" + post_url, raw=True)
        if (not all(image.startswith(b"\x89PNG\r\n\x1a\n") for image in (avatar_bytes, post_bytes))
                or hashlib.sha256(avatar_bytes).digest() == hashlib.sha256(post_bytes).digest()):
            raise RuntimeError("Source images must be two distinct stored PNGs")
        request(source_base, "/api/ums/user/avatar", method="POST", token=author["token"], data={"url": avatar_url})
        title = "联合备份恢复演练 🌊 " + secrets.token_hex(4)
        content = "# 中文与 emoji 数据完整性 🌊🧪\n\n数据库与图片必须一起恢复。\n\n![帖子图片](" + post_url + ")\n"
        post = request(source_base, "/api/post/add", method="POST", token=author["token"],
                       data={"title": title, "content": content, "tags": ["恢复演练"]})
        comment_text = "恢复后仍存在的评论 🧩"
        request(source_base, f"/api/post/{post['id']}/comments", method="POST", token=reader["token"], data={"content": comment_text})
        request(source_base, f"/api/post/{post['id']}/like", method="POST", token=reader["token"])
        message_text = "联合快照内的私信 🔐 中文"
        request(source_base, "/api/messages", method="POST", token=reader["token"],
                data={"username": author["username"], "content": message_text})
        old_admin_token = admin_login(source_admin, source, config["ADMIN_USERNAME"], config["ADMIN_PASSWORD"])
        request(source_admin, "/api/sys/userInfo", token=old_admin_token)
        # Enabled SQL events must remain dormant during restore. Keeping the
        # source scheduler OFF also makes this probe deterministic without
        # modifying the production Compose configuration.
        sql(source, "SET GLOBAL event_scheduler=OFF; "
                    "CREATE TABLE liushao_community.backup_restore_event_probe "
                    "(id BIGINT AUTO_INCREMENT PRIMARY KEY,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP); "
                    "CREATE EVENT liushao_community.backup_restore_event_writer ON SCHEDULE EVERY 1 SECOND "
                    "STARTS CURRENT_TIMESTAMP + INTERVAL 1 SECOND ENABLE DO "
                    "INSERT INTO liushao_community.backup_restore_event_probe () VALUES ()")

        def dormant_event(command):
            state = sql(command, "SELECT @@GLOBAL.event_scheduler; "
                                 "SELECT STATUS FROM information_schema.EVENTS WHERE EVENT_SCHEMA='liushao_community' "
                                 "AND EVENT_NAME='backup_restore_event_writer'; "
                                 "SELECT COUNT(*) FROM liushao_community.backup_restore_event_probe")
            if state.splitlines() != ["OFF", "ENABLED", "0"]:
                raise RuntimeError("Enabled SQL event must be present but remain dormant with scheduler OFF")
        dormant_event(source)
        before_histories, before_uploads = histories(source), upload_rows(source)
        if len(before_uploads.splitlines()) != 2:
            raise RuntimeError("The seed must have exactly two upload database rows")
        stored_sizes = {line.split("\t")[0]: int(line.split("\t")[2]) for line in before_uploads.splitlines()}
        for url, image in ((avatar_url, avatar_bytes), (post_url, post_bytes)):
            if stored_sizes.get(url.rsplit("/", 1)[1]) != len(image):
                raise RuntimeError("Upload database byte lengths do not match persisted images")
        record("real_source_data_and_images")

        backups = private / "bundles"
        run([sys.executable, str(BACKUP), "create", str(backups), "--env-file", str(source_env), "--stop-apps"],
            "Create one coordinated database/upload snapshot while applications are stopped", timeout=480)
        bundles = [path for path in backups.iterdir() if path.is_dir() and (path / "manifest.json").is_file()]
        if len(bundles) != 1:
            raise RuntimeError("Backup did not create exactly one completed bundle")
        bundle = bundles[0]
        run([sys.executable, str(BACKUP), "verify", str(bundle)], "Verify the complete backup bundle", timeout=120)
        smoke(source_base, source_admin)
        request(source_base, "/api/ums/user/info", token=author["token"])
        request(source_admin, "/api/sys/userInfo", token=old_admin_token)
        record("coordinated_snapshot_and_source_resumed")

        print("Verify corrupted bundles fail before any restore environment or resources exist", flush=True)
        corrupt = private / "corrupted-bundle"
        shutil.copytree(bundle, corrupt)
        with (corrupt / "uploads.tar.gz").open("ab") as stream:
            stream.write(b"corruption-check")
        run([sys.executable, str(BACKUP), "verify", str(corrupt)], "Reject corrupt checksum", expected="failure", timeout=60)
        corrupt_env = private / "corrupt.env"
        run([sys.executable, str(BACKUP), "restore", str(corrupt), "--output-env", str(corrupt_env),
             "--project", target_project, "--community-port", str(target_community_port),
             "--admin-port", str(target_admin_port)], "Reject corrupt restore before provisioning", expected="failure", timeout=90)
        if corrupt_env.exists() or corrupt_env.with_name(corrupt_env.name + ".images.yaml").exists():
            raise RuntimeError("Corrupt restore created credentials or an image override")
        ensure_no_project(target_project)
        shutil.rmtree(corrupt)
        record("corrupt_bundle_rejected_before_provisioning")

        run([sys.executable, str(BACKUP), "restore", str(bundle), "--output-env", str(target_env),
             "--project", target_project, "--community-port", str(target_community_port),
             "--admin-port", str(target_admin_port)], "Restore into a new project with fresh credentials and no Redis sessions", timeout=900)
        restored = compose(target_env, target_project)
        commands[target_project] = restored
        if not target_env.with_name(target_env.name + ".images.yaml").is_file():
            raise RuntimeError("Restore did not retain its pinned image override")
        new_config = read_environment(target_env)
        if new_config.get("BOOTSTRAP_ADMIN") != "false":
            raise RuntimeError("Restore must disable administrator bootstrap")
        if any(new_config.get(key) == config.get(key) or not new_config.get(key) for key in SECRET_KEYS):
            raise RuntimeError("Restore did not rotate every infrastructure and signing credential")
        smoke(target_base, target_admin)
        if histories(restored) != before_histories:
            raise RuntimeError("Restored Flyway migration histories differ")
        if upload_rows(restored) != before_uploads:
            raise RuntimeError("Restored upload metadata differs")
        dormant_event(restored)
        record("fresh_credentials_and_both_flyway_histories")

        print("Compare restored Unicode records, image URLs, SHA256 bytes, ownership and revoked sessions", flush=True)
        request(target_admin, "/api/sys/userInfo", token=old_admin_token, expected=401)
        for user in users:
            request(target_base, "/api/ums/user/info", token=user["token"], expected=401)
            user["restored_token"] = request(target_base, "/api/ums/user/login", method="POST",
                    data={"username": user["username"], "password": user["password"], "rememberMe": False})["token"]
        admin_token = admin_login(target_admin, restored, config["ADMIN_USERNAME"], config["ADMIN_PASSWORD"])
        request(target_admin, "/api/sys/userInfo", token=admin_token)
        if new_config.get("ADMIN_PASSWORD") and new_config["ADMIN_PASSWORD"] != config["ADMIN_PASSWORD"]:
            admin_login(target_admin, restored, config["ADMIN_USERNAME"], new_config["ADMIN_PASSWORD"], expected=401)
        recovered = request(target_base, f"/api/post/{post['id']}")
        if recovered.get("title") != title or recovered.get("content") != content:
            raise RuntimeError("Unicode Markdown did not survive restore exactly")
        profile = request(target_base, "/api/ums/user/info", token=author["restored_token"])
        if profile.get("avatar") != avatar_url:
            raise RuntimeError("Avatar URL changed during restore")
        image_hashes = {}
        for kind, url, original in (("avatar", avatar_url, avatar_bytes), ("post", post_url, post_bytes)):
            downloaded = request(target_base, "/api" + url, raw=True)
            if hashlib.sha256(downloaded).digest() != hashlib.sha256(original).digest():
                raise RuntimeError("Restored image content changed")
            image_hashes[kind] = hashlib.sha256(downloaded).hexdigest()
        comments = request(target_base, f"/api/post/{post['id']}/comments")
        if comment_text not in json.dumps(comments, ensure_ascii=False):
            raise RuntimeError("The restored comment is missing")
        conversation = request(target_base, "/api/messages?" + urlencode({"username": reader["username"]}),
                               token=author["restored_token"])
        if message_text not in json.dumps(conversation, ensure_ascii=False):
            raise RuntimeError("The restored private message is missing")
        request(target_base, "/api/ums/user/avatar", method="POST", token=reader["restored_token"],
                data={"url": avatar_url}, expected=403)
        record("unicode_images_social_data_ownership_and_session_revocation")

        identity = run(restored + ["exec", "-T", "community-api", "id", "-u"], capture=True, timeout=20).strip()
        if identity != "10001":
            raise RuntimeError("Restored community application must run as UID 10001")
        extra_image = png(10, 190, 77)
        extra_url = upload(target_base, author["restored_token"], extra_image)
        if not request(target_base, "/api" + extra_url, raw=True).startswith(b"\x89PNG\r\n\x1a\n"):
            raise RuntimeError("The restored application's upload volume is not writable")
        if len(upload_rows(restored).splitlines()) != 3:
            raise RuntimeError("The post-restore upload was not recorded")
        for app_user, password_var, table in (("admin_app", "ADMIN_DB_PASSWORD", "flyway_admin_schema_history"),
                                              ("community_app", "COMMUNITY_DB_PASSWORD", "flyway_schema_history")):
            query = f"SELECT COUNT(*) FROM liushao_community.{table}"
            run(restored + ["exec", "-T", "mysql", "sh", "-c",
                            f'export MYSQL_PWD="${password_var}"; exec mysql --protocol=TCP -h127.0.0.1 -u{app_user} -e "$1"',
                            "drill-shared-query", query], capture=True, timeout=30)
            run(restored + ["exec", "-T", "mysql", "sh", "-c",
                            f'export MYSQL_PWD="${password_var}"; exec mysql --protocol=TCP -h127.0.0.1 -u{app_user} -e "$1"',
                            "drill-denied-query", "SELECT COUNT(*) FROM mysql.user"], expected="failure", timeout=30)
        record("uid_10001_upload_write_shared_database_and_restricted_accounts")
        dormant_event(restored)
        record("enabled_database_event_restored_but_never_executed")

        existing_env = private / "must-not-create.env"
        run([sys.executable, str(BACKUP), "restore", str(bundle), "--output-env", str(existing_env),
             "--project", target_project, "--community-port", str(target_community_port),
             "--admin-port", str(target_admin_port)], "Reject restoring over an existing project", expected="failure", timeout=90)
        if existing_env.exists() or existing_env.with_name(existing_env.name + ".images.yaml").exists():
            raise RuntimeError("Existing-project rejection created credentials")
        request(target_base, "/api/ums/user/info", token=author["restored_token"])
        record("existing_target_rejected_without_modification")

        print("Inject one missing source image and verify failed backup recovery", flush=True)
        missing_output = private / "missing-image-attempt"
        image_path = "/app/data/uploads/" + post_url.rsplit("/", 1)[1]
        held_path = "/tmp/backup-drill-held-" + secrets.token_hex(8) + ".png"
        run(source + ["exec", "-T", "community-api", "mv", image_path, held_path], timeout=20)
        try:
            run([sys.executable, str(BACKUP), "create", str(missing_output), "--env-file", str(source_env), "--stop-apps"],
                "Reject database/upload mismatch and resume source services", expected="failure", timeout=480)
            if missing_output.exists() and any(missing_output.iterdir()):
                raise RuntimeError("A failed backup published a complete or partial bundle")
            smoke(source_base, source_admin)
            request(source_base, "/api/ums/user/info", token=author["token"])
        finally:
            run(source + ["exec", "-T", "community-api", "mv", held_path, image_path],
                "Undo only this drill's injected missing image", timeout=30)
        if request(source_base, "/api" + post_url, raw=True) != post_bytes:
            raise RuntimeError("The source image was not recovered after fault injection")
        record("missing_upload_rejected_without_bundle_and_source_resumed")
        (artifacts / "image-hashes.json").write_text(json.dumps(image_hashes, indent=2) + "\n")
        passed = True
    except (RuntimeError, subprocess.TimeoutExpired, OSError, ValueError, KeyError, KeyboardInterrupt) as error:
        print(f"FAIL: {error}; private log: {logfile}", file=sys.stderr, flush=True)
        with (artifacts / "services.log").open("ab") as log:
            for project, command in commands.items():
                for extra in (["ps", "-a"], ["logs", "--no-color", "--tail", "80"]):
                    try:
                        subprocess.run(command + extra, env=environment, stdout=log, stderr=subprocess.STDOUT, timeout=30)
                    except (OSError, subprocess.TimeoutExpired):
                        pass
    finally:
        for interrupted in (signal.SIGTERM, signal.SIGINT):
            signal.signal(interrupted, signal.SIG_IGN)
        if args.keep_environment:
            print(f"Kept this drill's private environments at {private}", flush=True)
        else:
            try:
                # Restore may fail after it has started resources; inspect its generated env even then.
                for path, project in ((target_env, target_project), (source_env, source_project)):
                    if path.is_file():
                        command = compose(path, project)
                        cleanup = command + ["down", "--volumes", "--remove-orphans"]
                        # The target reuses source image IDs. Remove only the
                        # source project's generated application tags after both
                        # projects have released their containers.
                        if project == source_project:
                            cleanup += ["--rmi", "local"]
                        run(cleanup,
                            "Remove only disposable project " + project, timeout=180)
                    ensure_no_project(project)
                    if path.exists():
                        path.unlink()
                    override = path.with_name(path.name + ".images.yaml")
                    if override.exists():
                        override.unlink()
                cleanup_verified = True
                if args.delete_backups and private.exists():
                    shutil.rmtree(private)
                elif private.exists() and not any(private.iterdir()):
                    private.rmdir()
            except (RuntimeError, OSError, subprocess.TimeoutExpired) as error:
                passed = False
                print(f"Cleanup failed; generated private environments retained: {error}", file=sys.stderr)
        revisions = {}
        for name in APPLICATIONS:
            try:
                revisions[name] = {"commit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT / name,
                                                                     text=True, stderr=subprocess.DEVNULL).strip(),
                                   "dirty": bool(subprocess.check_output(["git", "status", "--porcelain"], cwd=ROOT / name,
                                                                          text=True, stderr=subprocess.DEVNULL).strip())}
            except (OSError, subprocess.CalledProcessError):
                revisions[name] = {"commit": None, "dirty": None}
        summary = {"startedAt": started_at, "durationSeconds": round(time.monotonic() - started, 2),
                   "passed": passed, "cleanupVerified": cleanup_verified, "checks": checks,
                   "sourceRevisions": revisions,
                   "sourceHashesAtCompletion": {path.name: hashlib.sha256(path.read_bytes()).hexdigest()
                                                for path in (Path(__file__), BACKUP) if path.is_file()},
                   "backupInvocations": backup_invocations,
                   "backupRetained": bool(bundle and bundle.is_dir()),
                   "scope": "Two disposable local Docker projects; no production credentials or services used"}
        (artifacts / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n")
        print(f"Private drill artifacts: {artifacts}", flush=True)
    if passed:
        print("PASS: joint database and uploaded-image backup/restore drill completed", flush=True)
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
