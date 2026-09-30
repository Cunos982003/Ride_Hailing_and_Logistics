#!/bin/bash
set -e
set -u

echo "Enabling PostGIS extension on location_db"
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname=location_db <<-EOSQL
    CREATE EXTENSION IF NOT EXISTS postgis;
    CREATE EXTENSION IF NOT EXISTS postgis_topology;
EOSQL

echo "PostGIS extension enabled"
