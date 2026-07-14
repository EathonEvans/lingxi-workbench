#!/usr/bin/env bash
set -euo pipefail

curl --fail --silent --show-error "${LINGXI_APPLICATION_BASE_URL:-http://localhost:8080}/actuator/health"
curl --fail --silent --show-error "${LINGXI_ADMIN_BASE_URL:-http://localhost:8081}/actuator/health"
