#!/bin/bash
set -e
set -u

function create_user_and_database() {
    local database=$1
    local user=$2
    local password=$3
    echo "Creating user '$user' and database '$database'..."
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
        CREATE USER $user WITH PASSWORD '$password';
        CREATE DATABASE $database OWNER $user;
        GRANT ALL PRIVILEGES ON DATABASE $database TO $user;
EOSQL
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$database" <<-EOSQL
        ALTER SCHEMA public OWNER TO $user;
        GRANT ALL ON SCHEMA public TO $user;
EOSQL
}

if [ -n "${POSTGRES_MULTIPLE_DATABASES:-}" ]; then
    echo "Multiple database creation requested: $POSTGRES_MULTIPLE_DATABASES"
    for db_entry in $(echo $POSTGRES_MULTIPLE_DATABASES | tr ',' ' '); do
        db=$(echo $db_entry | cut -d: -f1)
        usr=$(echo $db_entry | cut -d: -f2)
        pwd=$(echo $db_entry | cut -d: -f3)
        create_user_and_database $db $usr $pwd
    done
    echo "Multiple databases created successfully!"
fi
