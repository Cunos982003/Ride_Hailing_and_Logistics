#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    -- Create databases
    CREATE DATABASE user_db;
    CREATE DATABASE location_db;
    CREATE DATABASE dispatch_db;
    CREATE DATABASE pricing_db;
    CREATE DATABASE payment_db;

    -- Grant privileges
    GRANT ALL PRIVILEGES ON DATABASE user_db TO $POSTGRES_USER;
    GRANT ALL PRIVILEGES ON DATABASE location_db TO $POSTGRES_USER;
    GRANT ALL PRIVILEGES ON DATABASE dispatch_db TO $POSTGRES_USER;
    GRANT ALL PRIVILEGES ON DATABASE pricing_db TO $POSTGRES_USER;
    GRANT ALL PRIVILEGES ON DATABASE payment_db TO $POSTGRES_USER;
EOSQL

# Enable PostGIS extension for location_db
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "location_db" <<-EOSQL
    CREATE EXTENSION IF NOT EXISTS postgis;
    CREATE EXTENSION IF NOT EXISTS postgis_topology;
EOSQL

# Enable PostGIS extension for dispatch_db (for geospatial queries)
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "dispatch_db" <<-EOSQL
    CREATE EXTENSION IF NOT EXISTS postgis;
EOSQL

echo "All databases created and PostGIS extensions enabled successfully."
