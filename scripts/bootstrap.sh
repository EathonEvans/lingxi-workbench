#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
./mvnw -q -DskipTests dependency:go-offline
