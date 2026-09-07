#!/usr/bin/env bash
set -euo pipefail

OPENREMOTE_URL="${OPENREMOTE_URL:-https://localhost:9443}"
OPENREMOTE_REALM="${OPENREMOTE_REALM:-master}"
OPENREMOTE_USER="${OPENREMOTE_USER:-admin}"
OPENREMOTE_PASSWORD="${OR_ADMIN_PASSWORD:-secret}"
DEVICE_ID="${DEVICE_ID:-hawkbitDemoDevice00001}"
FIRMWARE_VERSION="${FIRMWARE_VERSION:-1.0.0}"
DEMO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
STATE_DIR="$DEMO_DIR/.build"

mkdir -p "$STATE_DIR"

api() {
  local method="$1"
  local path="$2"
  shift 2
  curl --fail-with-body --silent --show-error --insecure \
    --request "$method" \
    --header "Authorization: Bearer $ACCESS_TOKEN" \
    "$@" \
    "$OPENREMOTE_URL/api/$OPENREMOTE_REALM/$path"
}

echo "Signing in to OpenRemote..."
ACCESS_TOKEN="$(curl --fail-with-body --silent --show-error --insecure \
  --request POST \
  --data-urlencode 'grant_type=password' \
  --data-urlencode 'client_id=openremote' \
  --data-urlencode "username=$OPENREMOTE_USER" \
  --data-urlencode "password=$OPENREMOTE_PASSWORD" \
  "$OPENREMOTE_URL/auth/realms/$OPENREMOTE_REALM/protocol/openid-connect/token" | jq -r '.access_token')"

if [[ -z "$ACCESS_TOKEN" || "$ACCESS_TOKEN" == "null" ]]; then
  echo "OpenRemote did not return an access token." >&2
  exit 1
fi

echo "Creating the OpenRemote demo device..."
ASSET_PAYLOAD="$(jq -n \
  --arg id "$DEVICE_ID" \
  --arg realm "$OPENREMOTE_REALM" \
  '{
    id: $id,
    name: "OTA Demo Sensor",
    type: "ThingAsset",
    realm: $realm,
    attributes: {
      firmwareTargetInfo: {
        name: "firmwareTargetInfo",
        type: "text",
        meta: {firmwareTarget: true}
      },
      deviceModel: {
        name: "deviceModel",
        type: "text",
        value: "OR-Demo-Sensor-v1",
        meta: {firmwareMetadata: true}
      },
      region: {
        name: "region",
        type: "text",
        value: "lab-athens",
        meta: {firmwareMetadata: true}
      },
      notes: {
        name: "notes",
        type: "text",
        value: "Simulated over-the-air update target"
      },
      location: {
        name: "location",
        type: "GEO_JSONPoint",
        value: {type: "Point", coordinates: [23.7275, 37.9838]}
      }
    }
  }')"

ASSET_HTTP_STATUS="$(curl --silent --insecure --output "$STATE_DIR/asset-response.json" --write-out '%{http_code}' \
  --request POST \
  --header "Authorization: Bearer $ACCESS_TOKEN" \
  --header 'Content-Type: application/json' \
  --data "$ASSET_PAYLOAD" \
  "$OPENREMOTE_URL/api/$OPENREMOTE_REALM/asset")"

if [[ "$ASSET_HTTP_STATUS" != "200" ]] && ! api GET "asset/$DEVICE_ID" >/dev/null 2>&1; then
  echo "OpenRemote asset creation failed (HTTP $ASSET_HTTP_STATUS):" >&2
  cat "$STATE_DIR/asset-response.json" >&2
  exit 1
fi

echo "Waiting for the extension to synchronize the asset to hawkBit..."
TARGET_JSON=""
for _ in {1..60}; do
  if TARGET_JSON="$(api GET "firmware/target/$DEVICE_ID?realm=$OPENREMOTE_REALM" 2>/dev/null)"; then
    break
  fi
  sleep 2
done

if [[ -z "$TARGET_JSON" ]]; then
  echo "The hawkBit target was not created within two minutes." >&2
  exit 1
fi

SECURITY_TOKEN="$(jq -r '.securityToken' <<<"$TARGET_JSON")"
if [[ -z "$SECURITY_TOKEN" || "$SECURITY_TOKEN" == "null" ]]; then
  echo "The hawkBit target has no device security token." >&2
  exit 1
fi

MODULE_TYPE_KEY="demo-app"
DS_TYPE_KEY="demo-device"

echo "Creating firmware catalog entries through the OpenRemote extension API..."
MODULE_TYPE_ID="$(api GET "firmware/softwaremoduletype?realm=$OPENREMOTE_REALM&limit=100" | \
  jq -r --arg key "$MODULE_TYPE_KEY" '.content[] | select(.key == $key) | .id' | head -1)"
if [[ -z "$MODULE_TYPE_ID" ]]; then
  MODULE_TYPE_RESPONSE="$(api POST "firmware/softwaremoduletype?realm=$OPENREMOTE_REALM" \
    --header 'Content-Type: application/json' \
    --data "[{\"key\":\"$MODULE_TYPE_KEY\",\"name\":\"Demo application\",\"description\":\"Application firmware for the OpenRemote hawkBit demo\",\"maxAssignments\":1}]")"
  MODULE_TYPE_ID="$(jq -r '.[0].id' <<<"$MODULE_TYPE_RESPONSE")"
fi

MODULE_ID="$(api GET "firmware/softwaremodule?realm=$OPENREMOTE_REALM&limit=100" | \
  jq -r --arg version "$FIRMWARE_VERSION" '.content[] | select(.name == "Demo sensor application" and .version == $version) | .id' | head -1)"
if [[ -z "$MODULE_ID" ]]; then
  MODULE_RESPONSE="$(api POST "firmware/softwaremodule?realm=$OPENREMOTE_REALM" \
    --header 'Content-Type: application/json' \
    --data "[{\"type\":\"$MODULE_TYPE_KEY\",\"name\":\"Demo sensor application\",\"version\":\"$FIRMWARE_VERSION\",\"description\":\"A harmless text artifact used to demonstrate OTA firmware delivery\",\"vendor\":\"OpenRemote demo\"}]")"
  MODULE_ID="$(jq -r '.[0].id' <<<"$MODULE_RESPONSE")"
fi

FIRMWARE_FILE="$STATE_DIR/demo-firmware-$FIRMWARE_VERSION.txt"
printf 'OpenRemote hawkBit demo firmware\nversion=%s\ndevice=%s\n' "$FIRMWARE_VERSION" "$DEVICE_ID" > "$FIRMWARE_FILE"

ARTIFACT_NAME="$(basename "$FIRMWARE_FILE")"
ARTIFACT_ID="$(api GET "firmware/softwaremodule/$MODULE_ID/artifacts?realm=$OPENREMOTE_REALM" | \
  jq -r --arg filename "$ARTIFACT_NAME" '.[] | select(.providedFilename == $filename) | .id' | head -1)"
if [[ -z "$ARTIFACT_ID" ]]; then
  api POST "firmware/softwaremodule/$MODULE_ID/artifacts?realm=$OPENREMOTE_REALM&filename=$ARTIFACT_NAME" \
    --form "file=@$FIRMWARE_FILE;type=application/octet-stream" > "$STATE_DIR/artifact-response.json"
fi

DS_TYPE_ID="$(api GET "firmware/distributionsettype?realm=$OPENREMOTE_REALM&limit=100" | \
  jq -r --arg key "$DS_TYPE_KEY" '.content[] | select(.key == $key) | .id' | head -1)"
if [[ -z "$DS_TYPE_ID" ]]; then
  DS_TYPE_RESPONSE="$(api POST "firmware/distributionsettype?realm=$OPENREMOTE_REALM" \
    --header 'Content-Type: application/json' \
    --data "[{\"key\":\"$DS_TYPE_KEY\",\"name\":\"Demo sensor\",\"description\":\"A demo device with one application module\",\"mandatorymodules\":[{\"id\":$MODULE_TYPE_ID}],\"optionalmodules\":[]}]")"
  DS_TYPE_ID="$(jq -r '.[0].id' <<<"$DS_TYPE_RESPONSE")"
fi

DS_ID="$(api GET "firmware/distributionset?realm=$OPENREMOTE_REALM&limit=100" | \
  jq -r --arg version "$FIRMWARE_VERSION" '.content[] | select(.name == "Demo firmware" and .version == $version) | .id' | head -1)"
if [[ -z "$DS_ID" ]]; then
  DS_RESPONSE="$(api POST "firmware/distributionset?realm=$OPENREMOTE_REALM" \
    --header 'Content-Type: application/json' \
    --data "[{\"name\":\"Demo firmware\",\"version\":\"$FIRMWARE_VERSION\",\"description\":\"Firmware assignment created through OpenRemote\",\"type\":\"$DS_TYPE_KEY\",\"modules\":[{\"id\":$MODULE_ID}]}]")"
  DS_ID="$(jq -r '.[0].id' <<<"$DS_RESPONSE")"
fi

ASSIGNED_DS_ID="$(api GET "firmware/target/$DEVICE_ID/assignedDS?realm=$OPENREMOTE_REALM" | jq -r '.id // empty')"
if [[ "$ASSIGNED_DS_ID" != "$DS_ID" ]]; then
  api POST "firmware/distributionset/$DS_ID/assign?realm=$OPENREMOTE_REALM" \
    --header 'Content-Type: application/json' \
    --data "[{\"id\":\"$DEVICE_ID\",\"type\":\"forced\",\"confirmationRequired\":false}]" > "$STATE_DIR/assignment-response.json"
fi

jq -n \
  --arg deviceId "$DEVICE_ID" \
  --arg securityToken "$SECURITY_TOKEN" \
  --arg firmwareVersion "$FIRMWARE_VERSION" \
  --argjson moduleTypeId "$MODULE_TYPE_ID" \
  --argjson moduleId "$MODULE_ID" \
  --argjson distributionSetTypeId "$DS_TYPE_ID" \
  --argjson distributionSetId "$DS_ID" \
  '{deviceId: $deviceId, securityToken: $securityToken, firmwareVersion: $firmwareVersion, moduleTypeId: $moduleTypeId, moduleId: $moduleId, distributionSetTypeId: $distributionSetTypeId, distributionSetId: $distributionSetId}' \
  > "$STATE_DIR/demo-state.json"

cat <<EOF

Demo data is ready.
  Device:           $DEVICE_ID
  Firmware version: $FIRMWARE_VERSION
  Distribution set: $DS_ID

Inspect it in Firmware Management:
  http://localhost:8004/services/hawkbit/ui/$OPENREMOTE_REALM

The asset was synchronized to hawkBit, firmware was uploaded through OpenRemote,
and the update was assigned. To act as the physical device, run:
  ./hawkbit/demo/simulate-device.sh
EOF
