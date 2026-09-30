-- Dispatch Service Schema
-- This migration creates the initial schema for ride dispatching

-- Ride requests table
CREATE TABLE ride_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rider_id UUID NOT NULL,
    pickup_location POINT NOT NULL,
    pickup_address VARCHAR(500) NOT NULL,
    dropoff_location POINT NOT NULL,
    dropoff_address VARCHAR(500) NOT NULL,
    vehicle_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'MATCHING', 'MATCHED', 'CANCELLED', 'EXPIRED')),
    estimated_fare DECIMAL(12, 2),
    estimated_distance_meters INTEGER,
    estimated_duration_seconds INTEGER,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_ride_requests_rider_id ON ride_requests(rider_id);
CREATE INDEX idx_ride_requests_status ON ride_requests(status);
CREATE INDEX idx_ride_requests_requested_at ON ride_requests(requested_at DESC);
CREATE INDEX idx_ride_requests_expires_at ON ride_requests(expires_at)
    WHERE status IN ('PENDING', 'MATCHING');

-- Trips table
CREATE TABLE trips (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ride_request_id UUID NOT NULL UNIQUE REFERENCES ride_requests(id),
    rider_id UUID NOT NULL,
    driver_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRIVER_ASSIGNED'
        CHECK (status IN ('DRIVER_ASSIGNED', 'DRIVER_EN_ROUTE', 'DRIVER_ARRIVED',
                          'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    pickup_location POINT NOT NULL,
    pickup_address VARCHAR(500) NOT NULL,
    dropoff_location POINT NOT NULL,
    dropoff_address VARCHAR(500) NOT NULL,
    vehicle_type VARCHAR(50) NOT NULL,
    estimated_fare DECIMAL(12, 2),
    actual_fare DECIMAL(12, 2),
    estimated_distance_meters INTEGER,
    actual_distance_meters INTEGER,
    estimated_duration_seconds INTEGER,
    actual_duration_seconds INTEGER,
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    driver_arrived_at TIMESTAMP WITH TIME ZONE,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    cancelled_at TIMESTAMP WITH TIME ZONE,
    cancellation_reason VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_trips_rider_id ON trips(rider_id);
CREATE INDEX idx_trips_driver_id ON trips(driver_id);
CREATE INDEX idx_trips_status ON trips(status);
CREATE INDEX idx_trips_created_at ON trips(created_at DESC);
CREATE INDEX idx_trips_ride_request_id ON trips(ride_request_id);

-- Driver assignments table (tracks matching attempts)
CREATE TABLE driver_assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ride_request_id UUID NOT NULL REFERENCES ride_requests(id),
    driver_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'EXPIRED')),
    offered_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    responded_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(ride_request_id, driver_id)
);

CREATE INDEX idx_driver_assignments_ride_request ON driver_assignments(ride_request_id);
CREATE INDEX idx_driver_assignments_driver_id ON driver_assignments(driver_id);
CREATE INDEX idx_driver_assignments_status ON driver_assignments(status);
CREATE INDEX idx_driver_assignments_expires_at ON driver_assignments(expires_at)
    WHERE status = 'PENDING';

-- Trip events table (audit trail)
CREATE TABLE trip_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id UUID NOT NULL REFERENCES trips(id),
    event_type VARCHAR(50) NOT NULL,
    actor_id UUID,
    actor_type VARCHAR(20),
    details JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_trip_events_trip_id ON trip_events(trip_id);
CREATE INDEX idx_trip_events_type ON trip_events(event_type);
CREATE INDEX idx_trip_events_created_at ON trip_events(created_at DESC);

-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Triggers for updated_at
CREATE TRIGGER update_ride_requests_updated_at BEFORE UPDATE ON ride_requests
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_trips_updated_at BEFORE UPDATE ON trips
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
