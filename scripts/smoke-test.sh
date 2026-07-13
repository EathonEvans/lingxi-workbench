#!/usr/bin/env bash
set -euo pipefail

curl --fail --silent --show-error "${LINGXI_BASE_URL:-http://localhost:8080}/actuator/health"
