#!/usr/bin/env bash
set -euo pipefail

OPENREMOTE_URL="${OPENREMOTE_URL:-https://localhost:9443}"
OPENREMOTE_REALM="${OPENREMOTE_REALM:-master}"
OPENREMOTE_USER="${OPENREMOTE_USER:-admin}"
OPENREMOTE_PASSWORD="${OR_ADMIN_PASSWORD:-secret}"
DEMO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
STATE_DIR="$DEMO_DIR/.build"

mkdir -p "$STATE_DIR"

api() {
  local method="$1"
  local endpoint="$2"
  shift 2
  curl --fail-with-body --silent --show-error --insecure \
    --request "$method" \
    --header "Authorization: Bearer $ACCESS_TOKEN" \
    "$@" \
    "$OPENREMOTE_URL/api/$OPENREMOTE_REALM/$endpoint"
}

create_asset() {
  local asset_id="$1"
  local asset_name="$2"
  local region="$3"
  local channel="$4"
  local hardware_revision="$5"
  local latitude="$6"
  local longitude="$7"
  local response_file="$STATE_DIR/asset-$asset_id.json"
  local payload http_status

  payload="$(jq -n \
    --arg id "$asset_id" \
    --arg name "$asset_name" \
    --arg realm "$OPENREMOTE_REALM" \
    --arg region "$region" \
    --arg channel "$channel" \
    --arg hardwareRevision "$hardware_revision" \
    --argjson latitude "$latitude" \
    --argjson longitude "$longitude" \
    '{
      id: $id,
      name: $name,
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
          value: "OR-Fleet-Sensor-v2",
          meta: {firmwareMetadata: true}
        },
        region: {
          name: "region",
          type: "text",
          value: $region,
          meta: {firmwareMetadata: true}
        },
        releaseChannel: {
          name: "releaseChannel",
          type: "text",
          value: $channel,
          meta: {firmwareMetadata: true}
        },
        hardwareRevision: {
          name: "hardwareRevision",
          type: "text",
          value: $hardwareRevision,
          meta: {firmwareMetadata: true}
        },
        notes: {
          name: "notes",
          type: "text",
          value: "Part of the staged hawkBit fleet rollout demo"
        },
        location: {
          name: "location",
          type: "GEO_JSONPoint",
          value: {type: "Point", coordinates: [$longitude, $latitude]}
        }
      }
    }')"

  http_status="$(curl --silent --insecure --output "$response_file" --write-out '%{http_code}' \
    --request POST \
    --header "Authorization: Bearer $ACCESS_TOKEN" \
    --header 'Content-Type: application/json' \
    --data "$payload" \
    "$OPENREMOTE_URL/api/$OPENREMOTE_REALM/asset")"

  if [[ "$http_status" != "200" ]] && ! api GET "asset/$asset_id" >/dev/null 2>&1; then
    echo "OpenRemote asset creation failed for $asset_id (HTTP $http_status):" >&2
    cat "$response_file" >&2
    exit 1
  fi
}

ensure_module_type() {
  local key="$1"
  local name="$2"
  local description="$3"
  local module_type_id response

  module_type_id="$(api GET "firmware/softwaremoduletype?realm=$OPENREMOTE_REALM&limit=200" | \
    jq -r --arg key "$key" '.content[] | select(.key == $key) | .id' | head -1)"
  if [[ -z "$module_type_id" ]]; then
    response="$(api POST "firmware/softwaremoduletype?realm=$OPENREMOTE_REALM" \
      --header 'Content-Type: application/json' \
      --data "$(jq -nc --arg key "$key" --arg name "$name" --arg description "$description" \
        '[{key: $key, name: $name, description: $description, maxAssignments: 1}]')")"
    module_type_id="$(jq -r '.[0].id' <<<"$response")"
  fi
  printf '%s\n' "$module_type_id"
}

ensure_module() {
  local type_key="$1"
  local name="$2"
  local version="$3"
  local description="$4"
  local artifact_name="$5"
  local artifact_content="$6"
  local module_id response artifact_id artifact_file

  module_id="$(api GET "firmware/softwaremodule?realm=$OPENREMOTE_REALM&limit=300" | \
    jq -r --arg name "$name" --arg version "$version" \
      '.content[] | select(.name == $name and .version == $version) | .id' | head -1)"
  if [[ -z "$module_id" ]]; then
    response="$(api POST "firmware/softwaremodule?realm=$OPENREMOTE_REALM" \
      --header 'Content-Type: application/json' \
      --data "$(jq -nc --arg type "$type_key" --arg name "$name" --arg version "$version" \
        --arg description "$description" \
        '[{type: $type, name: $name, version: $version, description: $description, vendor: "OpenRemote demo"}]')")"
    module_id="$(jq -r '.[0].id' <<<"$response")"
  fi

  artifact_id="$(api GET "firmware/softwaremodule/$module_id/artifacts?realm=$OPENREMOTE_REALM" | \
    jq -r --arg filename "$artifact_name" '.[] | select(.providedFilename == $filename) | .id' | head -1)"
  if [[ -z "$artifact_id" ]]; then
    artifact_file="$STATE_DIR/$artifact_name"
    printf '%s\n' "$artifact_content" > "$artifact_file"
    api POST "firmware/softwaremodule/$module_id/artifacts?realm=$OPENREMOTE_REALM&filename=$artifact_name" \
      --form "file=@$artifact_file;type=application/octet-stream" >/dev/null
  fi

  printf '%s\n' "$module_id"
}

ensure_distribution_type() {
  local key="$1"
  local name="$2"
  local description="$3"
  local mandatory_ids_json="$4"
  local optional_ids_json="$5"
  local distribution_type_id response payload

  distribution_type_id="$(api GET "firmware/distributionsettype?realm=$OPENREMOTE_REALM&limit=200" | \
    jq -r --arg key "$key" '.content[] | select(.key == $key) | .id' | head -1)"
  if [[ -z "$distribution_type_id" ]]; then
    payload="$(jq -nc \
      --arg key "$key" \
      --arg name "$name" \
      --arg description "$description" \
      --argjson mandatory "$mandatory_ids_json" \
      --argjson optional "$optional_ids_json" \
      '[{key: $key, name: $name, description: $description,
         mandatorymodules: ($mandatory | map({id: .})),
         optionalmodules: ($optional | map({id: .}))}]')"
    response="$(api POST "firmware/distributionsettype?realm=$OPENREMOTE_REALM" \
      --header 'Content-Type: application/json' \
      --data "$payload")"
    distribution_type_id="$(jq -r '.[0].id' <<<"$response")"
  fi
  printf '%s\n' "$distribution_type_id"
}

ensure_distribution_set() {
  local type_key="$1"
  local name="$2"
  local version="$3"
  local description="$4"
  local module_ids_json="$5"
  local distribution_id response payload

  distribution_id="$(api GET "firmware/distributionset?realm=$OPENREMOTE_REALM&limit=300" | \
    jq -r --arg name "$name" --arg version "$version" \
      '.content[] | select(.name == $name and .version == $version) | .id' | head -1)"
  if [[ -z "$distribution_id" ]]; then
    payload="$(jq -nc \
      --arg type "$type_key" \
      --arg name "$name" \
      --arg version "$version" \
      --arg description "$description" \
      --argjson moduleIds "$module_ids_json" \
      '[{type: $type, name: $name, version: $version, description: $description,
         modules: ($moduleIds | map({id: .}))}]')"
    response="$(api POST "firmware/distributionset?realm=$OPENREMOTE_REALM" \
      --header 'Content-Type: application/json' \
      --data "$payload")"
    distribution_id="$(jq -r '.[0].id' <<<"$response")"
  fi
  printf '%s\n' "$distribution_id"
}

ensure_filter() {
  local name="$1"
  local query="$2"
  local filter_id response

  filter_id="$(api GET "firmware/targetfilter?realm=$OPENREMOTE_REALM&limit=200" | \
    jq -r --arg name "$name" '.content[] | select(.name == $name) | .id' | head -1)"
  if [[ -z "$filter_id" ]]; then
    response="$(api POST "firmware/targetfilter?realm=$OPENREMOTE_REALM" \
      --header 'Content-Type: application/json' \
      --data "$(jq -nc --arg name "$name" --arg query "$query" '{name: $name, query: $query}')")"
    filter_id="$(jq -r '.id' <<<"$response")"
  fi
  printf '%s\n' "$filter_id"
}

ensure_rollout() {
  local name="$1"
  local description="$2"
  local query="$3"
  local distribution_id="$4"
  local groups="$5"
  local success_threshold="$6"
  local error_threshold="$7"
  local rollout_id response payload

  rollout_id="$(api GET "firmware/rollout?realm=$OPENREMOTE_REALM&limit=200" | \
    jq -r --arg name "$name" '.content[] | select(.name == $name) | .id' | head -1)"
  if [[ -z "$rollout_id" ]]; then
    payload="$(jq -nc \
      --arg name "$name" \
      --arg description "$description" \
      --arg query "$query" \
      --argjson distributionSetId "$distribution_id" \
      --argjson amountGroups "$groups" \
      --arg successThreshold "$success_threshold" \
      --arg errorThreshold "$error_threshold" \
      '{name: $name, description: $description, targetFilterQuery: $query,
        distributionSetId: $distributionSetId, amountGroups: $amountGroups,
        type: "forced", confirmationRequired: false,
        successCondition: {condition: "THRESHOLD", expression: $successThreshold},
        successAction: {action: "NEXTGROUP", expression: ""},
        errorCondition: {condition: "THRESHOLD", expression: $errorThreshold},
        errorAction: {action: "PAUSE", expression: ""}}')"
    response="$(api POST "firmware/rollout?realm=$OPENREMOTE_REALM" \
      --header 'Content-Type: application/json' \
      --data "$payload")"
    rollout_id="$(jq -r '.id' <<<"$response")"
  fi
  printf '%s\n' "$rollout_id"
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

echo "Creating ten OpenRemote fleet assets across three cohorts..."
for index in 001 002 003 004; do
  create_asset "hbFleetAthensDevice$index" "Athens fleet sensor $index" "athens" "stable" "rev-b" 37.9838 23.7275
done
for index in 00001 00002 00003 00004; do
  create_asset "hbFleetRdamDevice$index" "Rotterdam fleet sensor $index" "rotterdam" "stable" "rev-b" 51.9244 4.4777
done
for index in 001 002; do
  create_asset "hbFleetCanaryDevice$index" "Canary fleet sensor $index" "athens" "canary" "rev-c" 37.9838 23.7275
done

echo "Waiting for all assets to synchronize to hawkBit targets..."
for _ in {1..90}; do
  target_count="$(api GET "firmware/target?realm=$OPENREMOTE_REALM&limit=300" | \
    jq '[.content[] | select(
      (.controllerId | startswith("hbFleetAthensDevice")) or
      (.controllerId | startswith("hbFleetRdamDevice")) or
      (.controllerId | startswith("hbFleetCanaryDevice")))] | length')"
  if [[ "$target_count" == "10" ]]; then
    break
  fi
  sleep 2
done
if [[ "$target_count" != "10" ]]; then
  echo "Only $target_count of 10 fleet targets synchronized within three minutes." >&2
  exit 1
fi

echo "Creating software module types, modules, and artifacts..."
OS_TYPE_ID="$(api GET "firmware/softwaremoduletype?realm=$OPENREMOTE_REALM&limit=200" | jq -r '.content[] | select(.key == "os") | .id' | head -1)"
APP_TYPE_ID="$(api GET "firmware/softwaremoduletype?realm=$OPENREMOTE_REALM&limit=200" | jq -r '.content[] | select(.key == "application") | .id' | head -1)"
CONFIG_TYPE_ID="$(ensure_module_type 'fleet-config' 'Fleet configuration' 'Site and release-channel configuration')"
BOOTLOADER_TYPE_ID="$(ensure_module_type 'fleet-bootloader' 'Fleet bootloader' 'Low-level boot and recovery firmware')"

OS_MODULE_ID="$(ensure_module 'os' 'Fleet sensor OS' '3.2.0' 'Stable operating system image' \
  'fleet-sensor-os-3.2.0.bin' 'Demo OS image: version=3.2.0')"
APP_STABLE_ID="$(ensure_module 'application' 'Fleet telemetry agent' '2.0.0' 'Stable telemetry application' \
  'fleet-telemetry-2.0.0.bin' 'Demo telemetry application: version=2.0.0 channel=stable')"
APP_HOTFIX_ID="$(ensure_module 'application' 'Fleet telemetry agent' '2.0.1' 'Stable-channel telemetry hotfix' \
  'fleet-telemetry-2.0.1.bin' 'Demo telemetry application: version=2.0.1 channel=stable')"
APP_CANARY_ID="$(ensure_module 'application' 'Fleet telemetry agent' '2.1.0-rc1' 'Canary telemetry release candidate' \
  'fleet-telemetry-2.1.0-rc1.bin' 'Demo telemetry application: version=2.1.0-rc1 channel=canary')"
ATHENS_CONFIG_ID="$(ensure_module 'fleet-config' 'Fleet site configuration' '2026.09-athens' 'Athens site configuration' \
  'fleet-config-athens-2026.09.json' '{"site":"athens","pollIntervalSeconds":30}')"
ROTTERDAM_CONFIG_ID="$(ensure_module 'fleet-config' 'Fleet site configuration' '2026.09-rotterdam' 'Rotterdam site configuration' \
  'fleet-config-rotterdam-2026.09.json' '{"site":"rotterdam","pollIntervalSeconds":45}')"
CANARY_CONFIG_ID="$(ensure_module 'fleet-config' 'Fleet site configuration' '2026.09-canary' 'Canary feature configuration' \
  'fleet-config-canary-2026.09.json' '{"site":"athens","experimentalFeatures":true}')"
BOOTLOADER_MODULE_ID="$(ensure_module 'fleet-bootloader' 'Fleet bootloader' '1.4.0' 'Recovery-capable bootloader' \
  'fleet-bootloader-1.4.0.bin' 'Demo bootloader image: version=1.4.0')"

echo "Creating distribution set types and composed releases..."
FULL_TYPE_ID="$(ensure_distribution_type 'fleet-full' 'Fleet full release' \
  'OS and application are mandatory; site configuration is optional' \
  "[$OS_TYPE_ID,$APP_TYPE_ID]" "[$CONFIG_TYPE_ID]")"
APP_ONLY_TYPE_ID="$(ensure_distribution_type 'fleet-app-only' 'Fleet application update' \
  'Application hotfix with optional configuration' \
  "[$APP_TYPE_ID]" "[$CONFIG_TYPE_ID]")"
MAINTENANCE_TYPE_ID="$(ensure_distribution_type 'fleet-maintenance' 'Fleet maintenance release' \
  'Bootloader or recovery maintenance payload' \
  "[$BOOTLOADER_TYPE_ID]" '[]')"

ATHENS_DS_ID="$(ensure_distribution_set 'fleet-full' 'Athens stable fleet release' '2.0.0' \
  'Stable OS, telemetry agent, and Athens configuration' \
  "[$OS_MODULE_ID,$APP_STABLE_ID,$ATHENS_CONFIG_ID]")"
ROTTERDAM_DS_ID="$(ensure_distribution_set 'fleet-full' 'Rotterdam stable fleet release' '2.0.0' \
  'Stable OS, telemetry agent, and Rotterdam configuration' \
  "[$OS_MODULE_ID,$APP_STABLE_ID,$ROTTERDAM_CONFIG_ID]")"
HOTFIX_DS_ID="$(ensure_distribution_set 'fleet-app-only' 'Fleet telemetry hotfix' '2.0.1' \
  'Application-only patch for the stable channel' \
  "[$APP_HOTFIX_ID]")"
CANARY_DS_ID="$(ensure_distribution_set 'fleet-full' 'Fleet canary release' '2.1.0-rc1' \
  'Release candidate for the canary cohort' \
  "[$OS_MODULE_ID,$APP_CANARY_ID,$CANARY_CONFIG_ID]")"
MAINTENANCE_DS_ID="$(ensure_distribution_set 'fleet-maintenance' 'Fleet bootloader maintenance' '1.4.0' \
  'Recovery bootloader maintenance release' \
  "[$BOOTLOADER_MODULE_ID]")"

echo "Creating reusable target filters..."
ALL_FILTER_ID="$(ensure_filter 'Fleet - all demo sensors' 'controllerId==hbFleet*')"
ATHENS_FILTER_ID="$(ensure_filter 'Fleet - Athens stable' 'controllerId==hbFleetAthensDevice*')"
ROTTERDAM_FILTER_ID="$(ensure_filter 'Fleet - Rotterdam stable' 'controllerId==hbFleetRdamDevice*')"
CANARY_FILTER_ID="$(ensure_filter 'Fleet - canary cohort' 'controllerId==hbFleetCanaryDevice*')"
FUTURE_FILTER_ID="$(ensure_filter 'Fleet - future beta devices' 'controllerId==hbFleetFuture*')"

AUTO_ASSIGN_RESPONSE="$(api GET "firmware/targetfilter/$FUTURE_FILTER_ID/autoAssignDS?realm=$OPENREMOTE_REALM" 2>/dev/null || true)"
AUTO_ASSIGN_ID=""
if [[ -n "$AUTO_ASSIGN_RESPONSE" ]]; then
  AUTO_ASSIGN_ID="$(jq -r '.id // empty' <<<"$AUTO_ASSIGN_RESPONSE")"
fi
if [[ -z "$AUTO_ASSIGN_ID" ]]; then
  api POST "firmware/targetfilter/$FUTURE_FILTER_ID/autoAssignDS?realm=$OPENREMOTE_REALM" \
    --header 'Content-Type: application/json' \
    --data "$(jq -nc --argjson id "$CANARY_DS_ID" \
      '{id: $id, type: "forced", confirmationRequired: false}')" >/dev/null
fi

echo "Creating a future beta target to exercise the auto-assignment filter..."
create_asset 'hbFleetFutureDevice001' 'Future beta fleet sensor 001' 'athens' 'beta' 'rev-c' 37.9838 23.7275
FUTURE_ASSIGNED_ID=""
for _ in {1..60}; do
  FUTURE_ASSIGNED_RESPONSE="$(api GET \
    "firmware/target/hbFleetFutureDevice001/assignedDS?realm=$OPENREMOTE_REALM" 2>/dev/null || true)"
  if [[ -n "$FUTURE_ASSIGNED_RESPONSE" ]]; then
    FUTURE_ASSIGNED_ID="$(jq -r '.id // empty' <<<"$FUTURE_ASSIGNED_RESPONSE")"
  fi
  if [[ "$FUTURE_ASSIGNED_ID" == "$CANARY_DS_ID" ]]; then
    break
  fi
  sleep 1
done
if [[ "$FUTURE_ASSIGNED_ID" != "$CANARY_DS_ID" ]]; then
  echo "The future beta target was not auto-assigned distribution set $CANARY_DS_ID." >&2
  exit 1
fi
target_count=11

echo "Creating rollouts with different deployment-group policies..."
ATHENS_ROLLOUT_ID="$(ensure_rollout 'Athens staged stable rollout' \
  'Four targets in two groups; advance at 75% success and pause at 25% errors' \
  'controllerId==hbFleetAthensDevice*' "$ATHENS_DS_ID" 2 75 25)"
ROTTERDAM_ROLLOUT_ID="$(ensure_rollout 'Rotterdam cautious hotfix rollout' \
  'Four targets in four one-device groups; every group must succeed' \
  'controllerId==hbFleetRdamDevice*' "$HOTFIX_DS_ID" 4 100 1)"
CANARY_ROLLOUT_ID="$(ensure_rollout 'Canary release-candidate rollout' \
  'Two canary targets in two groups; started automatically for the live demo' \
  'controllerId==hbFleetCanaryDevice*' "$CANARY_DS_ID" 2 100 50)"

echo "Waiting for the canary rollout to become ready..."
for _ in {1..60}; do
  canary_status="$(api GET "firmware/rollout/$CANARY_ROLLOUT_ID?realm=$OPENREMOTE_REALM" | jq -r '.status')"
  if [[ "$canary_status" != "creating" ]]; then
    break
  fi
  sleep 1
done
if [[ "$canary_status" == "ready" ]]; then
  api POST "firmware/rollout/$CANARY_ROLLOUT_ID/start?realm=$OPENREMOTE_REALM" \
    --header 'Content-Type: application/json' --data '{}' >/dev/null
fi
sleep 1
canary_status="$(api GET "firmware/rollout/$CANARY_ROLLOUT_ID?realm=$OPENREMOTE_REALM" | jq -r '.status')"

jq -n \
  --argjson targetCount "$target_count" \
  --argjson softwareModuleTypes "[$OS_TYPE_ID,$APP_TYPE_ID,$CONFIG_TYPE_ID,$BOOTLOADER_TYPE_ID]" \
  --argjson distributionSetTypes "[$FULL_TYPE_ID,$APP_ONLY_TYPE_ID,$MAINTENANCE_TYPE_ID]" \
  --argjson distributionSets "[$ATHENS_DS_ID,$ROTTERDAM_DS_ID,$HOTFIX_DS_ID,$CANARY_DS_ID,$MAINTENANCE_DS_ID]" \
  --argjson targetFilters "[$ALL_FILTER_ID,$ATHENS_FILTER_ID,$ROTTERDAM_FILTER_ID,$CANARY_FILTER_ID,$FUTURE_FILTER_ID]" \
  --argjson rollouts "[$ATHENS_ROLLOUT_ID,$ROTTERDAM_ROLLOUT_ID,$CANARY_ROLLOUT_ID]" \
  '{targetCount: $targetCount, softwareModuleTypes: $softwareModuleTypes,
    distributionSetTypes: $distributionSetTypes, distributionSets: $distributionSets,
    targetFilters: $targetFilters, rollouts: $rollouts}' > "$STATE_DIR/fleet-state.json"

cat <<EOF

Fleet demo data is ready.
  Targets:                11 across stable, canary, and future-beta cohorts
  Software module types:  4 used by the fleet catalog
  Distribution set types: 3
  Distribution sets:      5
  Target filters:         5 (including one auto-assignment policy)
  Rollouts:               3 with 2, 4, and 2 deployment groups

The canary rollout is $canary_status; the Athens and Rotterdam rollouts remain
ready so you can start them from Firmware Management:
  http://localhost:8004/services/hawkbit/ui/$OPENREMOTE_REALM/rollouts

Inspect everything with:
  ./hawkbit/demo/fleet-status.sh
EOF
