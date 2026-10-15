#!/bin/bash
set -euo pipefail
# setup.sh generates hexadecimal passwords, so delimiters/SQL quotes cannot occur.
IFS=',' read -ra entries <<< "$POSTGRES_MULTIPLE_DATABASES"
for entry in "${entries[@]}"; do
  IFS=':' read -r database username password <<< "$entry"
  echo "Creating database $database"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" \
    --set=db_name="$database" --set=db_user="$username" --set=db_password="$password" <<'SQL'
CREATE USER :"db_user" WITH PASSWORD :'db_password';
CREATE DATABASE :"db_name" OWNER :"db_user";
GRANT ALL PRIVILEGES ON DATABASE :"db_name" TO :"db_user";
SQL
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$database" \
    --set=db_user="$username" <<'SQL'
ALTER SCHEMA public OWNER TO :"db_user";
GRANT ALL ON SCHEMA public TO :"db_user";
SQL
done
