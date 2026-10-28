#!/usr/bin/env python3
"""Create a private Compose environment without reading development credentials."""
import argparse
import os
from pathlib import Path
import secrets


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parents[1] / ".env")
    parser.add_argument("--project", default="liushao")
    parser.add_argument("--community-port", type=int, default=8088)
    parser.add_argument("--admin-port", type=int, default=8089)
    args = parser.parse_args()
    import re
    if not re.fullmatch(r"[a-z0-9][a-z0-9_-]*", args.project):
        parser.error("project must contain lowercase letters, digits, underscores or hyphens")
    if not all(1 <= p <= 65535 for p in (args.community_port, args.admin_port)) or args.community_port == args.admin_port:
        parser.error("ports must be distinct and between 1 and 65535")
    values = {
        "COMPOSE_PROJECT_NAME": args.project,
        "BIND_ADDRESS": "127.0.0.1",
        "COMMUNITY_HTTP_PORT": args.community_port,
        "ADMIN_HTTP_PORT": args.admin_port,
        "COMMUNITY_ORIGIN": f"http://localhost:{args.community_port}",
        **{key: secrets.token_hex(32) for key in (
            "MYSQL_ROOT_PASSWORD", "ADMIN_DB_PASSWORD", "COMMUNITY_DB_PASSWORD", "REDIS_PASSWORD")},
        "ADMIN_JWT_SECRET": secrets.token_hex(64),
        "COMMUNITY_JWT_SECRET": secrets.token_hex(64),
        "COMMUNITY_ADMIN_KEY": secrets.token_hex(32),
        "BOOTSTRAP_ADMIN": "true",
        "ADMIN_USERNAME": "admin",
        "ADMIN_PASSWORD": secrets.token_hex(16),
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    try:
        fd = os.open(args.output, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    except FileExistsError:
        parser.error(f"{args.output} already exists; kept existing credentials")
    with os.fdopen(fd, "w") as stream:
        stream.write("# Private deployment credentials; generated independently of .env.local.\n")
        stream.writelines(f"{key}={value}\n" for key, value in values.items())
    print(f"Created {args.output} (0600). ADMIN_PASSWORD is stored in this file.")
    print("After the first successful start, set BOOTSTRAP_ADMIN=false in this file.")


if __name__ == "__main__":
    main()
