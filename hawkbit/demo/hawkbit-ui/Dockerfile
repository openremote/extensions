ARG HAWKBIT_UI_VERSION=develop
FROM openremote/service-hawkbit-ui:${HAWKBIT_UI_VERSION}

# The upstream service uses one pair of URLs for both its server-side service
# registration and its browser application. Compose needs internal URLs for
# the former and host-accessible URLs for the latter.
COPY --chown=node:node hawkbit-ui/server.js /app/server/dist/server.js
