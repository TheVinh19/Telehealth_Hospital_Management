#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "$0")"
if [ -e .env ]; then
  echo '.env already exists; kept existing credentials.'
  exit 0
fi
command -v openssl >/dev/null
umask 077
# An atomic temporary file prevents partially generated credentials being reused.
temporary_env=$(mktemp .env.tmp.XXXXXX)
trap 'rm -f -- "$temporary_env"' EXIT
{
  echo 'IMAGE_PREFIX=ghcr.io/thevinh19/telehealth_hospital_management'
  echo 'IMAGE_TAG=sha-0ce0c79'
  echo 'PUBLIC_IP=103.77.172.232'
  echo 'COMPOSE_PROFILES=mailpit'
  for key in POSTGRES_PASSWORD IDENTITY_DB_PASSWORD APPOINTMENT_DB_PASSWORD CLINICAL_DB_PASSWORD BILLING_DB_PASSWORD REALTIME_DB_PASSWORD NOTIFICATION_DB_PASSWORD RABBITMQ_PASSWORD JWT_SECRET INTERNAL_SERVICE_TOKEN TURN_SECRET; do
    printf '%s=%s\n' "$key" "$(openssl rand -hex 32)"
  done
  # Exactly 32 ASCII bytes; compatible with the existing AES key parser.
  for key in CLINICAL_ENCRYPTION_KEY REALTIME_ENCRYPTION_KEY; do
    printf '%s=%s\n' "$key" "$(openssl rand -hex 16)"
  done
} > "$temporary_env"
mv -- "$temporary_env" .env
echo '.env created. Keep it private and back it up; do not regenerate after data exists.'
