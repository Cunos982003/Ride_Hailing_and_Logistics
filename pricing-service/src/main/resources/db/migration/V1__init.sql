-- Pricing Service Schema
-- This migration creates the initial schema for fare calculation and pricing

-- Pricing rules table
CREATE TABLE pricing_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    city VARCHAR(100) NOT NULL DEFAULT 'Hanoi',
    vehicle_type VARCHAR(50) NOT NULL,
    base_fare DECIMAL(12, 2) NOT NULL,
    per_km_rate DECIMAL(12, 2) NOT NULL,
    per_minute_rate DECIMAL(12, 2) NOT NULL,
    minimum_fare DECIMAL(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    effective_from TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    effective_to TIMESTAMP WITH TIME ZONE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE(city, vehicle_type, effective_from)
);

CREATE INDEX idx_pricing_rules_city_vehicle ON pricing_rules(city, vehicle_type);
CREATE INDEX idx_pricing_rules_active ON pricing_rules(active);
CREATE INDEX idx_pricing_rules_effective ON pricing_rules(effective_from, effective_to);

-- Surge pricing zones table
CREATE TABLE surge_zones (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    city VARCHAR(100) NOT NULL,
    center_latitude DECIMAL(10, 8) NOT NULL,
    center_longitude DECIMAL(11, 8) NOT NULL,
    radius_meters INTEGER NOT NULL,
    multiplier DECIMAL(4, 2) NOT NULL DEFAULT 1.0 CHECK (multiplier >= 1.0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    effective_from TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    effective_to TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_surge_zones_city ON surge_zones(city);
CREATE INDEX idx_surge_zones_active ON surge_zones(active);
CREATE INDEX idx_surge_zones_effective ON surge_zones(effective_from, effective_to);

-- Fare quotes table (historical pricing quotes)
CREATE TABLE fare_quotes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rider_id UUID NOT NULL,
    pickup_latitude DECIMAL(10, 8) NOT NULL,
    pickup_longitude DECIMAL(11, 8) NOT NULL,
    dropoff_latitude DECIMAL(10, 8) NOT NULL,
    dropoff_longitude DECIMAL(11, 8) NOT NULL,
    vehicle_type VARCHAR(50) NOT NULL,
    estimated_distance_meters INTEGER NOT NULL,
    estimated_duration_seconds INTEGER NOT NULL,
    base_fare DECIMAL(12, 2) NOT NULL,
    distance_fare DECIMAL(12, 2) NOT NULL,
    time_fare DECIMAL(12, 2) NOT NULL,
    surge_multiplier DECIMAL(4, 2) NOT NULL DEFAULT 1.0,
    total_fare DECIMAL(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    valid_until TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_fare_quotes_rider_id ON fare_quotes(rider_id);
CREATE INDEX idx_fare_quotes_created_at ON fare_quotes(created_at DESC);
CREATE INDEX idx_fare_quotes_valid_until ON fare_quotes(valid_until);

-- Trip fares table (final calculated fares)
CREATE TABLE trip_fares (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id UUID NOT NULL UNIQUE,
    fare_quote_id UUID REFERENCES fare_quotes(id),
    base_fare DECIMAL(12, 2) NOT NULL,
    distance_fare DECIMAL(12, 2) NOT NULL,
    time_fare DECIMAL(12, 2) NOT NULL,
    surge_amount DECIMAL(12, 2) NOT NULL DEFAULT 0,
    discount_amount DECIMAL(12, 2) NOT NULL DEFAULT 0,
    total_fare DECIMAL(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    actual_distance_meters INTEGER,
    actual_duration_seconds INTEGER,
    calculated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_trip_fares_trip_id ON trip_fares(trip_id);
CREATE INDEX idx_trip_fares_quote_id ON trip_fares(fare_quote_id);

-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Triggers for updated_at
CREATE TRIGGER update_pricing_rules_updated_at BEFORE UPDATE ON pricing_rules
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_surge_zones_updated_at BEFORE UPDATE ON surge_zones
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
