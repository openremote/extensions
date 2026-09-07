#!/usr/bin/env bash
set -euo pipefail

DEMO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_DIR="$(cd "$DEMO_DIR/../.." && pwd)"

mkdir -p "$DEMO_DIR/.build"

echo "Building the hawkBit extension..."
"$REPOSITORY_DIR/gradlew" -p "$REPOSITORY_DIR" :hawkbit:jar

EXTENSION_JAR="$(find "$REPOSITORY_DIR/hawkbit/build/libs" -maxdepth 1 -type f -name 'openremote-hawkbit-extension-*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' -print -quit)"
if [[ -z "$EXTENSION_JAR" ]]; then
  echo "Could not find the built hawkBit extension JAR." >&2
  exit 1
fi

cp "$EXTENSION_JAR" "$DEMO_DIR/.build/openremote-hawkbit-extension.jar"

echo "Starting OpenRemote and hawkBit..."
docker compose --project-directory "$DEMO_DIR" -f "$DEMO_DIR/compose.yaml" \
  up -d --wait --wait-timeout 600 \
  proxy postgresql keycloak manager hawkbitdb hawkbit

HAWKBIT_UI_SERVICE_SECRET="$("$DEMO_DIR/bootstrap-hawkbit-ui.sh")"
export HAWKBIT_UI_SERVICE_SECRET

echo "Starting the Firmware Management UI..."
docker compose --project-directory "$DEMO_DIR" -f "$DEMO_DIR/compose.yaml" \
  up -d --build --wait --wait-timeout 300 hawkbit-ui

cat <<'EOF'

The demo stack is ready:
  OpenRemote: https://localhost:9443/manager/
  Firmware Management: http://localhost:8004/services/hawkbit/ui/master
  hawkBit API explorer: http://localhost:8083/hawkbit/

Credentials for this local demo:
  OpenRemote: admin / secret
  hawkBit:    hawkbit / hawkbit

Next, run:
  ./hawkbit/demo/prepare-demo.sh
EOF
