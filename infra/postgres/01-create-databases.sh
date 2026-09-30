#!/usr/bin/env bash
set -euo pipefail

# Entrypoint runs this only on a new volume. Passwords are local-development inputs.
for service in user location dispatch payment; do
  password_var="${service^^}_DB_PASSWORD"
  password="${!password_var:?Missing service database password}"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
    --set=db_name="$service" --set=db_user="${service}_app" --set=db_password="$password" <<'SQL'
CREATE ROLE :"db_user" LOGIN PASSWORD :'db_password' NOSUPERUSER NOCREATEDB NOCREATEROLE;
CREATE DATABASE :"db_name" OWNER :"db_user" TEMPLATE template0;
REVOKE ALL ON DATABASE :"db_name" FROM PUBLIC;
SQL
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$service" <<'SQL'
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
SQL
done

# PostGIS needs elevated privileges; install as the bootstrap user, not the application.
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname location <<'SQL'
CREATE EXTENSION IF NOT EXISTS postgis;
SQL
