#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
export JAVA_HOME="${JAVA_HOME:-/opt/java/jdk21}"
export PATH="$JAVA_HOME/bin:$PATH"
for project in authority_management_frontend liushao_community_frontend; do
  (cd "$ROOT_DIR/$project" && npm ci --include=dev && npm run build)
done
for project in authority_management_backend liushao_community_backend; do
  (cd "$ROOT_DIR/$project" && ./mvnw --batch-mode --no-transfer-progress verify)
done
