#!/usr/bin/env bash
set -euo pipefail

HAWKBIT_URL="${HAWKBIT_URL:-http://localhost:8083/hawkbit}"
HAWKBIT_TENANT="${HAWKBIT_TENANT:-DEFAULT}"
OPENREMOTE_URL="${OPENREMOTE_URL:-https://localhost:9443}"
OPENREMOTE_REALM="${OPENREMOTE_REALM:-master}"
OPENREMOTE_USER="${OPENREMOTE_USER:-admin}"
OPENREMOTE_PASSWORD="${OR_ADMIN_PASSWORD:-secret}"
DEMO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
STATE_FILE="$DEMO_DIR/.build/demo-state.json"
DOWNLOAD_DIR="$DEMO_DIR/.build/downloaded"

if [[ -z "${DEVICE_ID:-}" && ! -f "$STATE_FILE" ]]; then
  echo "Run ./hawkbit/demo/prepare-demo.sh first." >&2
  exit 1
fi

if [[ -z "${DEVICE_ID:-}" ]]; then
  DEVICE_ID="$(jq -r '.deviceId' "$STATE_FILE")"
  SECURITY_TOKEN="$(jq -r '.securityToken' "$STATE_FILE")"
else
  ACCESS_TOKEN="$(curl --fail-with-body --silent --show-error --insecure \
    --request POST \
    --data-urlencode 'grant_type=password' \
    --data-urlencode 'client_id=openremote' \
    --data-urlencode "username=$OPENREMOTE_USER" \
    --data-urlencode "password=$OPENREMOTE_PASSWORD" \
    "$OPENREMOTE_URL/auth/realms/$OPENREMOTE_REALM/protocol/openid-connect/token" | jq -r '.access_token')"
  SECURITY_TOKEN="$(curl --fail-with-body --silent --show-error --insecure \
    --header "Authorization: Bearer $ACCESS_TOKEN" \
    "$OPENREMOTE_URL/api/$OPENREMOTE_REALM/firmware/target/$DEVICE_ID?realm=$OPENREMOTE_REALM" | \
    jq -r '.securityToken')"
fi

if [[ -z "$SECURITY_TOKEN" || "$SECURITY_TOKEN" == "null" ]]; then
  echo "Could not obtain the hawkBit security token for $DEVICE_ID." >&2
  exit 1
fi

AUTH_HEADER="Authorization: TargetToken $SECURITY_TOKEN"
CONTROLLER_URL="$HAWKBIT_URL/$HAWKBIT_TENANT/controller/v1/$DEVICE_ID"

DOWNLOAD_DIR="$DOWNLOAD_DIR/$DEVICE_ID"
mkdir -p "$DOWNLOAD_DIR"

echo "1/4 Device polls hawkBit for work..."
CONTROLLER_RESPONSE="$(curl --fail-with-body --silent --show-error --header "$AUTH_HEADER" "$CONTROLLER_URL")"
DEPLOYMENT_URL="$(jq -r '._links.deploymentBase.href // empty' <<<"$CONTROLLER_RESPONSE")"

if [[ -z "$DEPLOYMENT_URL" ]]; then
  echo "No deployment is waiting for this device."
  jq . <<<"$CONTROLLER_RESPONSE"
  exit 0
fi

echo "2/4 Device reads the deployment instructions..."
DEPLOYMENT_RESPONSE="$(curl --fail-with-body --silent --show-error --header "$AUTH_HEADER" "$DEPLOYMENT_URL")"
ACTION_ID="$(jq -r '.id' <<<"$DEPLOYMENT_RESPONSE")"
FEEDBACK_URL="${DEPLOYMENT_URL%%\?*}/feedback"

echo "3/4 Device downloads and verifies every artifact..."
jq -c '.deployment.chunks[].artifacts[]' <<<"$DEPLOYMENT_RESPONSE" | while IFS= read -r artifact; do
  filename="$(jq -r '.filename' <<<"$artifact")"
  download_url="$(jq -r '._links."download-http".href // ._links.download.href' <<<"$artifact")"
  expected_sha1="$(jq -r '.hashes.sha1' <<<"$artifact")"
  output_file="$DOWNLOAD_DIR/$filename"

  curl --fail-with-body --silent --show-error --location --header "$AUTH_HEADER" \
    --output "$output_file" "$download_url"

  actual_sha1="$(shasum -a 1 "$output_file" | awk '{print $1}')"
  if [[ "$actual_sha1" != "$expected_sha1" ]]; then
    echo "Checksum mismatch for $filename" >&2
    exit 1
  fi
  echo "  Downloaded $filename (SHA-1 verified)"
done

echo "4/4 Device reports a successful installation..."
curl --fail-with-body --silent --show-error \
  --request POST \
  --header "$AUTH_HEADER" \
  --header 'Content-Type: application/json' \
  --data "{\"id\":$ACTION_ID,\"status\":{\"execution\":\"closed\",\"result\":{\"finished\":\"success\"},\"details\":[\"Downloaded and installed by the OpenRemote demo device\"]}}" \
  "$FEEDBACK_URL" > /dev/null

cat <<EOF

Update complete. The simulated device downloaded and verified the artifact,
then hawkBit recorded installation success for action $ACTION_ID.
EOF
