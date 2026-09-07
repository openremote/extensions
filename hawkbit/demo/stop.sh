#!/usr/bin/env bash
set -euo pipefail

DEMO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
docker compose --project-directory "$DEMO_DIR" -f "$DEMO_DIR/compose.yaml" down

echo "The demo containers are stopped. Their data volumes were preserved."
