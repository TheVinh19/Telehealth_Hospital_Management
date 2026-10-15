#!/bin/sh
# Never fall back to Coturn's default (potentially unauthenticated) settings.
set -eu
config=/etc/coturn/turnserver.conf
if [ ! -r "$config" ] || [ ! -s "$config" ]; then
  echo 'Private TURN config is missing/unreadable; run bash configure-turn.sh as the deployment user.' >&2
  exit 1
fi
if ! grep -qx 'lt-cred-mech' "$config" ||
   ! grep -qE '^user=telehealth:[A-Za-z0-9_+/=-]{32,}$' "$config" ||
   ! grep -qx 'min-port=49152' "$config" ||
   ! grep -qx 'max-port=49200' "$config"; then
  echo 'TURN authentication or relay range configuration is invalid; refusing to start.' >&2
  exit 1
fi
exec /usr/bin/turnserver -c "$config"
