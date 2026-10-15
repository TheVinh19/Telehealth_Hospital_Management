#!/usr/bin/env bash
# Invoked from a separate incoming directory by GitHub Actions; no sudo required.
set -euo pipefail
umask 077

die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
[[ $# == 4 ]] || die 'Usage: bash deploy-release.sh DEPLOY_DIR IMAGE_TAG IMAGE_PREFIX RELEASE_SEQUENCE'
deploy_dir=$1
new_tag=$2
new_prefix=$3
sequence=$4
[[ $deploy_dir =~ ^/(home|opt|srv)/[a-zA-Z0-9_./-]+$ && $deploy_dir != *..* ]] || die 'Unsafe deployment path'
[[ $new_tag =~ ^sha-[a-f0-9]{7,40}$ ]] || die 'An immutable SHA image tag is required'
[[ $new_prefix =~ ^ghcr\.io/[a-z0-9_.-]+/[a-z0-9_.-]+$ ]] || die 'Invalid image prefix'
[[ $sequence =~ ^[0-9]+$ ]] || die 'Invalid release sequence'
source_dir=$(cd -- "$(dirname -- "$0")" && pwd -P)
cd -- "$deploy_dir"
deploy_dir=$(pwd -P)
[[ $source_dir != "$deploy_dir" ]] || die 'Run the script from a distinct incoming directory'
[[ -f .env && -f compose.yml ]] || die 'Existing deployment and .env are required; run initial setup first'
for command in docker curl flock awk; do command -v "$command" >/dev/null || die "Missing $command"; done
# GitHub serializes deploy jobs; this also prevents simultaneous manual deployments.
exec 9>.deploy.lock
flock -n 9 || die 'Another deployment is running'
previous_sequence=0
if [[ -f .release-sequence ]]; then
  read -r previous_sequence < .release-sequence
  [[ $previous_sequence =~ ^[0-9]+$ ]] || die 'Invalid previous release sequence'
fi
if (( 10#$sequence < 10#$previous_sequence )); then
  echo 'Skipping superseded deployment; a newer workflow run is already installed.'
  exit 0
fi

apps=(identity-service realtime-service notification-service appointment-service clinical-service billing-service api-gateway web)
compose=(docker compose --project-name telehealth-vps --project-directory "$deploy_dir" --env-file "$deploy_dir/.env" -f "$deploy_dir/compose.yml")
dc() { "${compose[@]}" "$@"; }
container_id() { dc ps --all --quiet "$1"; }
# The rollback baseline is the running image, not a possibly stale .env default.
old_image=$(docker inspect --format '{{.Config.Image}}' "$(container_id identity-service)")
[[ $old_image == *-identity-service:sha-* ]] || die 'Existing identity image is not a versioned release'
old_prefix=${old_image%-identity-service:*}
old_tag=${old_image##*:}
for service in "${apps[@]}"; do
  cid=$(container_id "$service")
  [[ -n $cid ]] || die "Missing rollback baseline for $service; finish initial deployment first"
  current_image=$(docker inspect --format '{{.Config.Image}}' "$cid")
  [[ $current_image == "$old_prefix-$service:$old_tag" ]] || die "Mixed existing image versions at $service; reconcile before deploying"
done

# Pull every new application image BEFORE changing any configuration or container.
for service in "${apps[@]}"; do
  docker pull "$new_prefix-$service:$new_tag"
done
# Validate the candidate using server credentials without printing rendered secrets.
IMAGE_TAG=$new_tag IMAGE_PREFIX=$new_prefix docker compose \
  --project-name telehealth-vps --project-directory "$deploy_dir" --env-file "$deploy_dir/.env" \
  -f "$source_dir/compose.yml" config --quiet
[[ -f $source_dir/nginx.conf && -f $source_dir/init-multiple-dbs.sh ]] || die 'Incomplete configuration bundle'

release_dir="$deploy_dir/.releases/$(date -u +%Y%m%dT%H%M%SZ)-$sequence-$$"
mkdir -p "$release_dir/previous"
config_files=()
while IFS= read -r -d '' file; do
  name=${file##*/}
  case "$name" in
    *.sh|compose*.yml|nginx.conf|host-nginx.conf|turn-probe.py|README.md) config_files+=("$name") ;;
  esac
done < <(find "$source_dir" -maxdepth 1 -type f -print0)
for file in "${config_files[@]}"; do
  if [[ -e $file ]]; then cp -p -- "$file" "$release_dir/previous/$file"; fi
done
cp -p -- .env "$release_dir/previous/.env"
printf 'IMAGE_PREFIX=%s\nIMAGE_TAG=%s\n' "$old_prefix" "$old_tag" > "$release_dir/previous-images"

env_value() {
  awk -F= -v key="$1" '$1 == key {value=substr($0,index($0,"=")+1); sub(/\r$/, "", value); gsub(/^\047|\047$/, "", value); gsub(/^"|"$/, "", value); print value; exit}' .env
}
has_profile() { [[ ,$(env_value COMPOSE_PROFILES), == *,"$1",* ]]; }
wait_infra() {
  local service=$1 cid health count
  for ((count=0; count<60; count++)); do
    cid=$(container_id "$service")
    health=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$cid")
    [[ $health == healthy ]] && return 0
    [[ $health == exited || $health == dead || $health == unhealthy ]] && return 1
    sleep 3
  done
  echo "Timeout waiting for $service health" >&2
  return 1
}
wait_app() {
  local service=$1 port=$2 cid state count response startup_logs
  cid=$(container_id "$service")
  [[ -n $cid ]] || return 1
  for ((count=0; count<80; count++)); do
    state=$(docker inspect --format '{{.State.Status}} {{.State.OOMKilled}} {{.RestartCount}}' "$cid")
    # A running process alone is insufficient; require completed Spring startup and HTTP.
    if [[ $state != 'running false 0' ]]; then
      echo "$service is not stable ($state)" >&2
      return 1
    fi
    startup_logs=$(docker logs "$cid" 2>&1) || return 1
    if grep -qE 'Started [[:alnum:]_.$]+ in ' <<< "$startup_logs"; then
      response=$(docker exec "$cid" sh -c "wget -S -T 5 -O /dev/null http://127.0.0.1:$port/ 2>&1" || true)
      if [[ $response =~ HTTP/[0-9.]+\ [2-5][0-9][0-9] ]]; then return 0; fi
    fi
    sleep 3
  done
  echo "Timeout waiting for $service startup and HTTP response" >&2
  return 1
}
wait_web() {
  local path
  # Verify static web AND a public gateway -> appointment -> database request.
  for path in / /api/v1/specialties; do
    curl --noproxy '*' --fail --silent --show-error --retry 10 --retry-all-errors \
      --retry-delay 2 --retry-max-time 60 --max-time 10 \
      --output /dev/null "http://127.0.0.1:3000$path" || return 1
  done
}
start_apps() {
  local service port
  for service in "${apps[@]}"; do
    echo "Starting $service ($IMAGE_TAG)"
    dc up -d --no-deps --pull never "$service" || return 1
    case "$service" in
      identity-service) port=8081 ;; realtime-service) port=8085 ;;
      notification-service) port=8086 ;; appointment-service) port=8082 ;;
      clinical-service) port=8083 ;; billing-service) port=8084 ;;
      api-gateway) port=8080 ;; web) wait_web || return 1; continue ;;
    esac
    wait_app "$service" "$port" || return 1
  done
}
rollback() {
  local file
  echo 'Release failed. Restoring previous configuration and application images.' >&2
  echo 'Database migrations are NOT reversed. New migrations must remain backward compatible.' >&2
  for file in "${config_files[@]}"; do
    if [[ -f $release_dir/previous/$file ]]; then
      cp -p -- "$release_dir/previous/$file" "$deploy_dir/$file" || return 1
    else
      rm -f -- "$deploy_dir/$file" || return 1
    fi
  done
  cp -p -- "$release_dir/previous/.env" .env || return 1
  export IMAGE_TAG=$old_tag IMAGE_PREFIX=$old_prefix
  start_apps
}
on_failure() {
  local code=$1
  trap - ERR INT TERM HUP
  # Do not print service logs or .env: SMTP credentials can appear in exception logs.
  if rollback; then
    echo "Previous release restored. Diagnostic snapshot: $release_dir" >&2
  else
    echo "ROLLBACK FAILED. Inspect docker compose ps and $release_dir on the VPS." >&2
  fi
  exit "$code"
}
trap 'on_failure $?' ERR
trap 'on_failure 130' INT
trap 'on_failure 143' TERM
trap 'on_failure 129' HUP

for file in "${config_files[@]}"; do cp -- "$source_dir/$file" "$deploy_dir/$file"; done
export IMAGE_TAG=$new_tag IMAGE_PREFIX=$new_prefix
dc config --quiet
# Reuse existing infrastructure and persistent volumes; never upgrade/reset them here.
dc up -d --no-recreate postgres redis rabbitmq
for service in postgres redis rabbitmq; do wait_infra "$service"; done
if [[ $(env_value SPRING_MAIL_HOST) == mailpit || -z $(env_value SPRING_MAIL_HOST) ]] || has_profile mailpit; then
  dc up -d --no-recreate mailpit
fi
if has_profile turn; then
  dc up -d --no-recreate coturn
  [[ $(docker inspect --format '{{.State.Running}}' "$(container_id coturn)") == true ]]
fi
start_apps

# Only commit the new image settings after all services and the API probe pass.
temporary_env=$(mktemp "$deploy_dir/.env.release.XXXXXX")
awk '$0 !~ /^(IMAGE_TAG|IMAGE_PREFIX)=/' .env > "$temporary_env"
printf '\nIMAGE_PREFIX=%s\nIMAGE_TAG=%s\n' "$new_prefix" "$new_tag" >> "$temporary_env"
chmod 600 "$temporary_env"
mv -- "$temporary_env" .env
printf '%s\n' "$sequence" > .release-sequence
trap - ERR INT TERM HUP
echo "Deployment successful: $new_tag. Previous release retained at $release_dir"
echo 'Persistent database/report volumes, server secrets and host Nginx were preserved.'
