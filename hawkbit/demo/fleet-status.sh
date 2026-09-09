#!/usr/bin/env bash
set -euo pipefail

OPENREMOTE_URL="${OPENREMOTE_URL:-https://localhost:9443}"
OPENREMOTE_REALM="${OPENREMOTE_REALM:-master}"
OPENREMOTE_USER="${OPENREMOTE_USER:-admin}"
OPENREMOTE_PASSWORD="${OR_ADMIN_PASSWORD:-secret}"

ACCESS_TOKEN="$(curl --fail-with-body --silent --show-error --insecure \
  --request POST \
  --data-urlencode 'grant_type=password' \
  --data-urlencode 'client_id=openremote' \
  --data-urlencode "username=$OPENREMOTE_USER" \
  --data-urlencode "password=$OPENREMOTE_PASSWORD" \
  "$OPENREMOTE_URL/auth/realms/$OPENREMOTE_REALM/protocol/openid-connect/token" | jq -r '.access_token')"

api() {
  curl --fail-with-body --silent --show-error --insecure \
    --header "Authorization: Bearer $ACCESS_TOKEN" \
    "$OPENREMOTE_URL/api/$OPENREMOTE_REALM/$1"
}

echo "Fleet targets by cohort"
api "firmware/target?realm=$OPENREMOTE_REALM&limit=300" | jq '
  [.content[] | select(.controllerId | startswith("hbFleet"))]
  | group_by(.controllerId | capture("hbFleet(?<cohort>[A-Za-z]+)Device[0-9]+").cohort)
  | map({cohort: (.[0].controllerId | capture("hbFleet(?<cohort>[A-Za-z]+)Device[0-9]+").cohort), targets: length})'

echo "Software module types"
api "firmware/softwaremoduletype?realm=$OPENREMOTE_REALM&limit=200" | jq '
  [.content[] | select(.key == "os" or .key == "application" or (.key | startswith("fleet-")))
   | {id, key, name, maxAssignments}]'

echo "Software modules"
api "firmware/softwaremodule?realm=$OPENREMOTE_REALM&limit=300" | jq '
  [.content[] | select(.name | startswith("Fleet"))
   | {id, name, version, vendor, encrypted, locked}]'

echo "Distribution set types"
api "firmware/distributionsettype?realm=$OPENREMOTE_REALM&limit=200" | jq '
  [.content[] | select(.key | startswith("fleet-")) | {id, key, name}]'

echo "Distribution sets"
api "firmware/distributionset?realm=$OPENREMOTE_REALM&limit=300" | jq '
  [.content[] | select(.name | startswith("Fleet") or startswith("Athens") or startswith("Rotterdam"))
   | {id, name, version, complete}]'

echo "Target filters"
api "firmware/targetfilter?realm=$OPENREMOTE_REALM&limit=200" | jq '
  [.content[] | select(.name | startswith("Fleet -"))
   | {id, name, query, autoAssignDistributionSet, confirmationRequired}]'

echo "Future beta auto-assignment result"
api "firmware/target/hbFleetFutureDevice001/assignedDS?realm=$OPENREMOTE_REALM" | \
  jq '{target: "hbFleetFutureDevice001", distributionSet: {id, name, version}}'

echo "Rollouts"
ROLLOUTS="$(api "firmware/rollout?realm=$OPENREMOTE_REALM&limit=200")"
jq '[.content[] | select(.name | endswith("rollout"))
  | {id, name, status, totalTargets, totalGroups, totalTargetsPerStatus}]' <<<"$ROLLOUTS"

jq -r '.content[] | select(.name | endswith("rollout")) | [.id, .name] | @tsv' <<<"$ROLLOUTS" | \
  while IFS=$'\t' read -r rollout_id rollout_name; do
    echo "Deployment groups: $rollout_name"
    api "firmware/rollout/$rollout_id/deploygroups?realm=$OPENREMOTE_REALM&limit=100" | jq '
      [.content[] | {id, name, status, targetPercentage, totalTargets, totalTargetsPerStatus}]'
  done
