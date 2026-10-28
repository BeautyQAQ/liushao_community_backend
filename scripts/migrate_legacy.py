#!/usr/bin/env python3
"""Convert the historical community SQL into the current shared schema using the packaged JDBC driver."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--env-file', type=Path, default=ROOT / '.env.local')
    parser.add_argument('--source', type=Path, default=ROOT / 'liushao_community.sql')
    parser.add_argument('--source-offset', default='+08:00', help='UTC offset of historical DATETIME values')
    parser.add_argument('--report', type=Path, default=ROOT / '.artifacts/legacy-community-migration/import.json')
    args = parser.parse_args()
    jar = ROOT / 'target/liushao_community_backend-0.0.1-SNAPSHOT.jar'
    if not jar.is_file():
        parser.error('Build the current backend first: ./mvnw -DskipTests package')
    java = Path(os.environ.get('JAVA_HOME', '/opt/java/jdk21')) / 'bin/java'
    os.umask(0o077)
    args.report.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='liushao-legacy-jdbc-') as directory:
        with zipfile.ZipFile(jar) as archive:
            names = [name for name in archive.namelist() if name.startswith('BOOT-INF/lib/mysql-connector-j-') and name.endswith('.jar')]
            if len(names) != 1:
                parser.error('The executable backend JAR must contain exactly one MySQL driver')
            driver = Path(directory) / 'mysql-driver.jar'
            driver.write_bytes(archive.read(names[0]))
        process = subprocess.run([
            str(java), '-cp', str(driver), str(ROOT / 'scripts/MigrateLegacyCommunity.java'),
            str(args.env_file.resolve()), str(args.source.resolve()),
            str(ROOT / 'scripts/import-legacy-community.sql'), args.source_offset,
        ], cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if process.returncode:
        # Driver diagnostics may include data; keep them in a private local log.
        log = args.report.with_suffix('.error.log')
        log.write_text(process.stderr)
        raise SystemExit(f'Migration failed; private diagnostics: {log}')
    report = {
        'completedAt': datetime.now(timezone.utc).isoformat(),
        'source': str(args.source.resolve()),
        'sourceSha256': hashlib.sha256(args.source.read_bytes()).hexdigest(),
        'sourceOffset': args.source_offset,
        'sourceCounts': {}, 'inserted': {}, 'totals': {}, 'rejected': {}, 'repaired': {}, 'idMappings': [],
    }
    sections = {'SOURCE': 'sourceCounts', 'INSERTED': 'inserted', 'TOTAL': 'totals',
                'REJECTED': 'rejected', 'REPAIRED': 'repaired'}
    for line in process.stdout.splitlines():
        fields = line.split('\t')
        if fields[0] in sections and len(fields) == 3:
            report[sections[fields[0]]][fields[1]] = int(fields[2])
        elif fields[0] == 'MAP' and len(fields) == 4:
            report['idMappings'].append({'resource': fields[1], 'legacyId': fields[2], 'currentId': int(fields[3])})
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({key: report[key] for key in ('sourceCounts', 'inserted', 'totals', 'rejected', 'repaired')}, ensure_ascii=False, indent=2))
    print(f'Migration complete; ID mappings and result: {args.report}')


if __name__ == '__main__':
    main()
