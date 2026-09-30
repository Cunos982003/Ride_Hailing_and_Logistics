-- Location Service Schema
-- This migration creates the initial schema for location tracking

-- Driver locations table (real-time location updates)
CREATE TABLE driver_locations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id UUID NOT NULL,
    location GEOGRAPHY(POINT, 4326) NOT NULL,
    heading DECIMAL(5, 2) CHECK (heading >= 0 AND heading < 360),
    speed DECIMAL(6, 2) CHECK (speed >= 0),
    accuracy DECIMAL(6, 2),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ONLINE', 'ON_TRIP', 'OFFLINE')),
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for geospatial queries
CREATE INDEX idx_driver_locations_driver_id ON driver_locations(driver_id);
CREATE INDEX idx_driver_locations_status ON driver_locations(status);
CREATE INDEX idx_driver_locations_timestamp ON driver_locations(timestamp DESC);
CREATE INDEX idx_driver_locations_location ON driver_locations USING GIST(location);

-- Composite index for nearby driver queries
CREATE INDEX idx_driver_locations_status_location ON driver_locations(status)
    WHERE status IN ('ONLINE', 'ON_TRIP');

-- Trip routes table (store trip paths)
CREATE TABLE trip_routes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id UUID NOT NULL,
    driver_id UUID NOT NULL,
    route_points GEOGRAPHY(LINESTRING, 4326),
    pickup_location GEOGRAPHY(POINT, 4326) NOT NULL,
    dropoff_location GEOGRAPHY(POINT, 4326) NOT NULL,
    distance_meters DECIMAL(10, 2),
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_trip_routes_trip_id ON trip_routes(trip_id);
CREATE INDEX idx_trip_routes_driver_id ON trip_routes(driver_id);
CREATE INDEX idx_trip_routes_started_at ON trip_routes(started_at DESC);
CREATE INDEX idx_trip_routes_pickup_location ON trip_routes USING GIST(pickup_location);
CREATE INDEX idx_trip_routes_dropoff_location ON trip_routes USING GIST(dropoff_location);

-- Location history table (for analytics)
CREATE TABLE location_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id UUID NOT NULL,
    location GEOGRAPHY(POINT, 4326) NOT NULL,
    status VARCHAR(20) NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
) PARTITION BY RANGE (timestamp);

-- Create partitions for the current and next month
CREATE TABLE location_history_default PARTITION OF location_history DEFAULT;

-- Index for partitioned table
CREATE INDEX idx_location_history_driver_timestamp
    ON location_history(driver_id, timestamp DESC);
CREATE INDEX idx_location_history_location
    ON location_history USING GIST(location);

-- Geofences table (for zones, surge areas, etc.)
CREATE TABLE geofences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL CHECK (type IN ('CITY', 'ZONE', 'SURGE_AREA', 'RESTRICTED')),
    boundary GEOGRAPHY(POLYGON, 4326) NOT NULL,
    properties JSONB,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_geofences_type ON geofences(type);
CREATE INDEX idx_geofences_active ON geofences(active);
CREATE INDEX idx_geofences_boundary ON geofences USING GIST(boundary);

-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Triggers for updated_at
CREATE TRIGGER update_trip_routes_updated_at BEFORE UPDATE ON trip_routes
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_geofences_updated_at BEFORE UPDATE ON geofences
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Helper function to find nearby drivers
CREATE OR REPLACE FUNCTION find_nearby_drivers(
    p_location GEOGRAPHY,
    p_radius_meters DECIMAL,
    p_limit INTEGER DEFAULT 10
)
RETURNS TABLE (
    driver_id UUID,
    distance_meters DECIMAL,
    location GEOGRAPHY,
    heading DECIMAL,
    status VARCHAR
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        dl.driver_id,
        ST_Distance(dl.location, p_location)::DECIMAL AS distance_meters,
        dl.location,
        dl.heading,
        dl.status
    FROM driver_locations dl
    WHERE dl.status IN ('ONLINE', 'ON_TRIP')
      AND ST_DWithin(dl.location, p_location, p_radius_meters)
      AND dl.timestamp > CURRENT_TIMESTAMP - INTERVAL '5 minutes'
    ORDER BY dl.location <-> p_location
    LIMIT p_limit;
END;
$$ LANGUAGE plpgsql STABLE;
