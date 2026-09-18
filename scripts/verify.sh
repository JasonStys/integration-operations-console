#!/usr/bin/env bash
# File: verify.sh
# Purpose: Run every deterministic repository quality gate from a Unix-like shell.
# Functions: require_command validates prerequisites; main coordinates backend, web, and docs checks.
# Variables: ROOT_DIR is the immutable repository root resolved from this script's location.
set -euo pipefail

readonly ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Reports a clear prerequisite error before a nested build fails cryptically.
require_command() {
  local command_name="$1"
  command -v "${command_name}" >/dev/null 2>&1 || {
    echo "Required command is unavailable: ${command_name}" >&2
    exit 1
  }
}

# Executes repository checks in dependency order and stops on the first failure.
main() {
  require_command java
  require_command node
  require_command npm

  node "${ROOT_DIR}/scripts/validate-repository.mjs"
  (cd "${ROOT_DIR}/backend" && ./mvnw --batch-mode --no-transfer-progress verify)
  (cd "${ROOT_DIR}/frontend" && npm ci && npm run verify)
  node "${ROOT_DIR}/scripts/generate-code-index.mjs" --check
}

main "$@"
