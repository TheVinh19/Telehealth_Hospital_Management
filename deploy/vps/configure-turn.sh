#!/usr/bin/env bash
# Run on Ubuntu VPS from the uploaded deploy/vps directory.
set -euo pipefail
cd -- "$(dirname -- "$0")"
umask 077
command -v python3 >/dev/null
command -v docker >/dev/null
[ -f .env ] || { echo 'Missing .env; keep the existing server .env.' >&2; exit 1; }

# Do not source .env: SMTP passwords and other values are data, never shell code.
# Prepare-only supports validation in an isolated test directory without Docker.
case "${1:-}" in
  '') prepare_only=false ;;
  --prepare-only) prepare_only=true ;;
  *) echo 'Usage: bash configure-turn.sh [--prepare-only]' >&2; exit 2 ;;
esac

python3 - <<'PY'
import ipaddress
import os
from pathlib import Path
import re
import subprocess
import tempfile

path = Path('.env')
text = path.read_text(encoding='utf-8-sig')
lines = text.splitlines()

def value(key):
    matches = [line.split('=', 1)[1].strip() for line in lines
               if re.match(r'^\s*' + re.escape(key) + r'\s*=', line)]
    if len(matches) > 1:
        raise SystemExit('Duplicate ' + key + ' in .env; resolve it before proceeding.')
    result = matches[0] if matches else ''
    if len(result) > 1 and result[0] == result[-1] and result[0] in "\"'":
        result = result[1:-1]
    return result

public_ip = value('PUBLIC_IP')
try:
    address = ipaddress.IPv4Address(public_ip)
except ValueError:
    raise SystemExit('PUBLIC_IP in .env must be the public IPv4 address of this VPS.')
if not address.is_global:
    raise SystemExit('PUBLIC_IP must be a routable public IPv4 address.')
relay_ip = value('TURN_RELAY_IP')
if not relay_ip:
    try:
        route = subprocess.check_output(['ip', '-4', 'route', 'get', '1.1.1.1'], text=True)
        source = re.search(r'\bsrc\s+(\d+\.\d+\.\d+\.\d+)', route)
        relay_ip = source.group(1) if source else public_ip
    except (OSError, subprocess.CalledProcessError):
        relay_ip = public_ip
try:
    ipaddress.IPv4Address(relay_ip)
except ValueError:
    raise SystemExit('TURN_RELAY_IP must be the IPv4 address assigned to the VPS network interface.')
secret = value('TURN_SECRET')
# setup.sh already generated this secret. Never replace it on repeat runs.
if not re.fullmatch(r'[A-Za-z0-9_+/=-]{32,256}', secret):
    raise SystemExit('TURN_SECRET must contain 32-256 safe ASCII characters; existing setup.sh uses 64 hex characters.')
profiles = [item.strip() for item in value('COMPOSE_PROFILES').split(',') if item.strip()]
if any(not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]*', item) for item in profiles):
    raise SystemExit('Unexpected COMPOSE_PROFILES format; use comma-separated profile names.')
if 'turn' not in profiles:
    profiles.append('turn')

config = f'''# Generated on the VPS; never commit or upload this credentials file.
# Existing v4 realtime-service returns a fixed username/password, NOT REST HMAC.
listening-port=3478
listening-ip={relay_ip}
relay-ip={relay_ip}
external-ip={public_ip}/{relay_ip}
min-port=49152
max-port=49200
relay-threads=1
cpus=1
fingerprint
lt-cred-mech
realm=thevinh19.duckdns.org
user=telehealth:{secret}
user-quota=12
total-quota=12
# Bytes/second per allocation and across the server; sized for a small demo.
max-bps=500000
bps-capacity=3000000
stale-nonce=600
max-allocate-lifetime=3600
cli=0
no-tls
# DTLS is disabled by default in this pinned Coturn version.
# TCP client connections still relay UDP media (the WebRTC transport).
no-tcp-relay
no-multicast-peers
no-rfc5780
# Legacy STUN compatibility is disabled by default in this Coturn version.
response-origin-only-with-rfc5780
no-software-attribute
# Loopback access is denied by Coturn by default; additionally deny private,
# link-local, CGNAT and reserved peer addresses reachable only from the VPS.
denied-peer-ip=0.0.0.0-0.255.255.255
denied-peer-ip=10.0.0.0-10.255.255.255
denied-peer-ip=100.64.0.0-100.127.255.255
denied-peer-ip=127.0.0.0-127.255.255.255
denied-peer-ip=169.254.0.0-169.254.255.255
denied-peer-ip=172.16.0.0-172.31.255.255
denied-peer-ip=192.168.0.0-192.168.255.255
denied-peer-ip=224.0.0.0-255.255.255.255
# Do not list :: as a denied peer: Coturn interprets the unspecified address as
# a wildcard and can reject IPv4 peers too. Loopback is already denied above.
denied-peer-ip=fc00::-fdff:ffff:ffff:ffff:ffff:ffff:ffff:ffff
denied-peer-ip=fe80::-febf:ffff:ffff:ffff:ffff:ffff:ffff:ffff
log-file=stdout
simple-log
pidfile=/tmp/turnserver.pid
userdb=/var/lib/coturn/turndb
'''

def write_private(target, contents):
    fd, temp = tempfile.mkstemp(prefix=target.name + '.tmp.', dir='.')
    try:
        with os.fdopen(fd, 'w', encoding='utf-8', newline='\n') as output:
            output.write(contents)
        os.chmod(temp, 0o600)
        os.replace(temp, target)
    finally:
        if os.path.exists(temp):
            os.unlink(temp)

write_private(Path('.env.turnserver.conf'), config)
settings = {
    'COMPOSE_PROFILES': ','.join(profiles),
    'TURN_UID': str(os.getuid()) if hasattr(os, 'getuid') else '1000',
    'TURN_GID': str(os.getgid()) if hasattr(os, 'getgid') else '1000',
}
updated = []
replaced = set()
for line in lines:
    match = re.match(r'^\s*(COMPOSE_PROFILES|TURN_UID|TURN_GID)\s*=', line)
    if match:
        key = match.group(1)
        if key not in replaced:
            updated.append(key + '=' + settings[key])
            replaced.add(key)
    else:
        updated.append(line)
for key, setting in settings.items():
    if key not in replaced:
        updated.append(key + '=' + setting)
new_text = '\n'.join(updated) + '\n'
if new_text != text:
    backup = Path('.env.before-turn')
    if not backup.exists():
        write_private(backup, text)
    write_private(path, new_text)
else:
    os.chmod(path, 0o600)
print('TURN configuration prepared; existing credentials kept; profile turn enabled.')
PY

if "$prepare_only"; then exit 0; fi
docker compose config --quiet
enabled_services=$(docker compose config --services)
grep -qx coturn <<< "$enabled_services" || {
  echo 'Upload the current compose.yml containing the coturn service, then rerun.' >&2
  exit 1
}
docker compose --parallel 1 pull coturn
docker compose up -d --no-deps --force-recreate coturn
# Probe authenticated allocations and relay payloads, with bounded startup wait.
for attempt in 1 2 3 4 5; do
  if python3 turn-probe.py --env-file .env --transport both; then
    echo 'Local authenticated TURN allocation and relay tests passed.'
    echo 'An external-network test is still required; a local pass cannot prove provider firewall access.'
    echo 'Provider/host firewall must allow inbound 3478 UDP+TCP and 49152:49200 UDP.'
    echo 'No firewall was enabled or changed. Keep the existing SSH access rules.'
    echo 'Video is not using TURN TLS; port 5349 is not required by this configuration.'
    exit 0
  fi
  if [ "$attempt" -lt 5 ]; then sleep 2; fi
done
echo 'TURN probe failed. Check docker compose logs --tail=60 coturn and firewall rules.' >&2
exit 1
