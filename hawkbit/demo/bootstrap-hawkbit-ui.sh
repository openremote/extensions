#!/usr/bin/env bash
set -euo pipefail

OPENREMOTE_URL="${OPENREMOTE_URL:-https://localhost:9443}"
OPENREMOTE_REALM="${OPENREMOTE_REALM:-master}"
OPENREMOTE_USER="${OPENREMOTE_USER:-admin}"
OPENREMOTE_PASSWORD="${OR_ADMIN_PASSWORD:-secret}"
SERVICE_USER="${HAWKBIT_UI_SERVICE_USER:-hawkbitui}"
DEMO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SECRET_FILE="$DEMO_DIR/.build/hawkbit-ui-secret"

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

service_secret_works() {
  local secret="$1"
  curl --fail --silent --insecure \
    --request POST \
    --data-urlencode 'grant_type=client_credentials' \
    --data-urlencode "client_id=$SERVICE_USER" \
    --data-urlencode "client_secret=$secret" \
    "$OPENREMOTE_URL/auth/realms/$OPENREMOTE_REALM/protocol/openid-connect/token" >/dev/null
}

mkdir -p "$DEMO_DIR/.build"
umask 077

echo "Preparing the Firmware Management service account..." >&2
ACCESS_TOKEN="$(curl --fail-with-body --silent --show-error --insecure \
  --request POST \
  --data-urlencode 'grant_type=password' \
  --data-urlencode 'client_id=openremote' \
  --data-urlencode "username=$OPENREMOTE_USER" \
  --data-urlencode "password=$OPENREMOTE_PASSWORD" \
  "$OPENREMOTE_URL/auth/realms/$OPENREMOTE_REALM/protocol/openid-connect/token" | jq -r '.access_token')"

USERS="$(api POST 'user/query' \
  --header 'Content-Type: application/json' \
  --data '{"serviceUsers":true}')"
USER_ID="$(jq -r --arg username "$SERVICE_USER" '.[] | select(.username == $username) | .id' <<<"$USERS" | head -1)"

if [[ -z "$USER_ID" ]]; then
  USER_RESPONSE="$(api POST "user/$OPENREMOTE_REALM/users" \
    --header 'Content-Type: application/json' \
    --data "{\"serviceAccount\":true,\"username\":\"$SERVICE_USER\",\"enabled\":true}")"
  USER_ID="$(jq -r '.id' <<<"$USER_RESPONSE")"
fi

if [[ -z "$USER_ID" || "$USER_ID" == "null" ]]; then
  echo "Could not create or find the '$SERVICE_USER' service account." >&2
  exit 1
fi

api PUT "user/$OPENREMOTE_REALM/userRoles/$USER_ID/openremote" \
  --header 'Content-Type: application/json' \
  --data '["write:services"]' >/dev/null

SERVICE_SECRET=""
if [[ -f "$SECRET_FILE" ]]; then
  SERVICE_SECRET="$(<"$SECRET_FILE")"
fi

if [[ -z "$SERVICE_SECRET" ]] || ! service_secret_works "$SERVICE_SECRET"; then
  SECRET_RESPONSE="$(api GET "user/$OPENREMOTE_REALM/reset-secret/$USER_ID")"
  if SERVICE_SECRET="$(jq -er 'if type == "string" then . else empty end' <<<"$SECRET_RESPONSE" 2>/dev/null)"; then
    :
  else
    SERVICE_SECRET="$SECRET_RESPONSE"
  fi
  if [[ -z "$SERVICE_SECRET" || "$SERVICE_SECRET" == "null" ]]; then
    echo "OpenRemote did not return a service-account secret." >&2
    exit 1
  fi
  printf '%s' "$SERVICE_SECRET" > "$SECRET_FILE"
fi

printf '%s\n' "$SERVICE_SECRET"
