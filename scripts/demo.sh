#!/usr/bin/env bash
# File: demo.sh
# Purpose: Exercise account linking, idempotent submission, scheduling, and audit over HTTP.
# Functions: request sends JSON requests; main performs the deterministic happy-path demonstration.
# Variables: BASE_URL can override the default local web URL; role headers are demo authorization only.
set -euo pipefail

readonly BASE_URL="${BASE_URL:-http://localhost:8081}"

# Sends one JSON request with the explicit operator role used by this local simulation.
request() {
  local method="$1"
  local path="$2"
  local body="${3:-}"
  local idempotency_key="${4:-}"
  local arguments=(-fsS -X "${method}" -H "Accept: application/json" -H "X-Demo-Role: operator")
  [[ -z "${body}" ]] || arguments+=(-H "Content-Type: application/json" --data "${body}")
  [[ -z "${idempotency_key}" ]] || arguments+=(-H "Idempotency-Key: ${idempotency_key}")
  curl "${arguments[@]}" "${BASE_URL}${path}"
}

# Creates data, submits the same key twice, runs the scheduler, and prints the dashboard.
main() {
  echo "Linking a deterministic fictional account"
  local account_response
  account_response="$(request POST /api/v1/accounts '{"connectorId":"atlas-ads","displayName":"CLI Demo","externalReference":"cli-demo"}')"
  local account_id
  account_id="$(node -e 'process.stdin.on("data",d=>console.log(JSON.parse(d).resource.id))' <<<"${account_response}")"

  local job_body
  job_body="{\"accountId\":\"${account_id}\",\"failurePlan\":\"NONE\",\"maxAttempts\":3}"
  local first_job
  first_job="$(request POST /api/v1/jobs "${job_body}" cli-demo-idempotency-key)"
  echo "${first_job}"
  local job_id
  job_id="$(node -e 'process.stdin.on("data",d=>console.log(JSON.parse(d).resource.id))' <<<"${first_job}")"
  echo
  request POST /api/v1/jobs "${job_body}" cli-demo-idempotency-key
  echo
  request POST /api/v1/jobs/run-next
  echo
  request POST /api/v1/jobs/run-next
  echo
  request GET "/api/v1/jobs/${job_id}/audit"
  echo
  request GET /api/v1/dashboard
  echo
}

main "$@"
