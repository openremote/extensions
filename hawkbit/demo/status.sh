#!/usr/bin/env bash
set -euo pipefail

OPENREMOTE_URL="${OPENREMOTE_URL:-https://localhost:9443}"
OPENREMOTE_REALM="${OPENREMOTE_REALM:-master}"
OPENREMOTE_USER="${OPENREMOTE_USER:-admin}"
OPENREMOTE_PASSWORD="${OR_ADMIN_PASSWORD:-secret}"
DEVICE_ID="${DEVICE_ID:-hawkbitDemoDevice00001}"

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

echo "Firmware Management service"
api "service?realm=$OPENREMOTE_REALM" | \
  jq '.[] | select(.serviceId == "hawkbit-ui") | {label, status, homepageUrl}'

echo "Target"
api "firmware/target/$DEVICE_ID?realm=$OPENREMOTE_REALM" | \
  jq '{controllerId, updateStatus, lastControllerRequestAt}'

echo "Metadata synchronized from OpenRemote"
api "firmware/target/$DEVICE_ID/metadata?realm=$OPENREMOTE_REALM" | jq '.content'

echo "Assigned firmware"
ASSIGNED="$(api "firmware/target/$DEVICE_ID/assignedDS?realm=$OPENREMOTE_REALM")"
if [[ -n "$ASSIGNED" ]]; then
  jq '{id, name, version, complete}' <<<"$ASSIGNED"
else
  echo "None"
fi

echo "Installed firmware"
INSTALLED="$(api "firmware/target/$DEVICE_ID/installedDS?realm=$OPENREMOTE_REALM")"
if [[ -n "$INSTALLED" ]]; then
  jq '{id, name, version, complete}' <<<"$INSTALLED"
else
  echo "None"
fi

echo "Actions"
api "firmware/target/$DEVICE_ID/actions?realm=$OPENREMOTE_REALM&limit=20" | \
  jq '[.content[]? | {id, status, type, active, forceType}]'
