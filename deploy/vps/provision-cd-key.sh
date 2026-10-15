#!/usr/bin/env bash
# Add a dedicated GitHub Actions public key while preserving existing SSH access.
set -euo pipefail
[[ $# == 1 && -f $1 ]] || { echo 'Usage: bash provision-cd-key.sh PUBLIC_KEY_FILE' >&2; exit 2; }
public_key=$(tr -d '\r' < "$1")
[[ $public_key =~ ^ssh-ed25519[[:space:]][A-Za-z0-9+/=]+[[:space:]]telehealth-github-actions$ ]] || {
  echo 'Expected a dedicated Ed25519 GitHub Actions public key.' >&2
  exit 1
}
umask 077
mkdir -p "$HOME/.ssh"
chmod 700 "$HOME/.ssh"
touch "$HOME/.ssh/authorized_keys"
chmod 600 "$HOME/.ssh/authorized_keys"
if ! grep -Fqx -- "restrict $public_key" "$HOME/.ssh/authorized_keys"; then
  printf 'restrict %s\n' "$public_key" >> "$HOME/.ssh/authorized_keys"
fi
echo 'Dedicated CD public key installed; existing SSH keys preserved.'
