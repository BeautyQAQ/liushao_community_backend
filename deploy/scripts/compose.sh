#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEPLOY_ENV_FILE="${DEPLOY_ENV_FILE:-$ROOT_DIR/.env}"
if [[ ! -f "$DEPLOY_ENV_FILE" ]]; then
  printf 'Missing deployment environment: %s. Run python3 %s/scripts/init-deploy.py first.\n' "$DEPLOY_ENV_FILE" "$ROOT_DIR" >&2
  exit 1
fi
exec docker compose --project-directory "$ROOT_DIR" --env-file "$DEPLOY_ENV_FILE" --file "$ROOT_DIR/compose.yaml" "$@"
