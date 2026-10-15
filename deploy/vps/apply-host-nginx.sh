#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "$0")"

site_path=$(readlink -f /etc/nginx/sites-enabled/api-gateway.conf)
if [ ! -f "$site_path" ]; then
  echo 'Expected /etc/nginx/sites-enabled/api-gateway.conf was not found.' >&2
  exit 1
fi
sudo grep -qE 'server_name[[:space:]]+thevinh19\.duckdns\.org[[:space:]]*;' "$site_path"
sudo nginx -t
if ! sudo openssl x509 -in /etc/letsencrypt/live/thevinh19.duckdns.org/fullchain.pem -noout -checkend 0; then
  echo 'The existing TLS certificate needs attention. No Nginx configuration was changed.' >&2
  exit 1
fi
docker compose config --quiet
curl --noproxy '*' --fail --silent --show-error --max-time 5 \
  --retry 10 --retry-all-errors --retry-delay 1 --retry-max-time 30 \
  --output /dev/null http://127.0.0.1:3000/

# Validate the uploaded inner proxy before recreating only its small web container.
docker compose run --rm --no-deps web nginx -t
docker compose up -d --no-deps --force-recreate web
# A new container can accept TCP before Nginx is ready, returning curl 52/56.
# Retry all GET failures briefly, not only refused connections/HTTP errors.
curl --noproxy '*' --fail --silent --show-error --max-time 5 \
  --retry 10 --retry-all-errors --retry-delay 1 --retry-max-time 30 \
  --output /dev/null http://127.0.0.1:3000/

sudo mkdir -p /var/backups/telehealth-nginx
backup_file=$(sudo mktemp /var/backups/telehealth-nginx/api-gateway.conf.XXXXXX)
sudo cp -p -- "$site_path" "$backup_file"

restore_previous_site() {
  local failure_status=$?
  trap - ERR
  echo 'Apply failed; restoring previous host Nginx configuration.' >&2
  sudo cp -p -- "$backup_file" "$site_path"
  if sudo nginx -t; then
    sudo systemctl reload nginx || true
  fi
  printf 'Backup retained: %s\n' "$backup_file" >&2
  exit "$failure_status"
}
trap restore_previous_site ERR
sudo cp -- host-nginx.conf "$site_path"
sudo nginx -t
sudo systemctl reload nginx

# Check the certificate hostname and the local HTTPS route without DNS/firewall.
curl --noproxy '*' --fail --silent --show-error --max-time 5 \
  --retry 10 --retry-all-errors --retry-delay 1 --retry-max-time 30 \
  --resolve thevinh19.duckdns.org:443:127.0.0.1 \
  --output /dev/null https://thevinh19.duckdns.org/
trap - ERR
printf 'HTTPS configured successfully. Backup retained: %s\n' "$backup_file"
echo 'Open https://thevinh19.duckdns.org in your browser and test login.'
echo 'Cross-network video still needs TURN configuration.'
