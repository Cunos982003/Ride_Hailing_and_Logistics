-- Users table
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('CUSTOMER', 'DRIVER')),
    full_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Trips table
CREATE TABLE trips (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id BIGINT NOT NULL REFERENCES users(id),
    driver_id BIGINT REFERENCES users(id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'PICKING_UP', 'IN_TRIP', 'COMPLETED', 'CANCELLED')),
    pickup_lat NUMERIC(10, 7) NOT NULL,
    pickup_lng NUMERIC(10, 7) NOT NULL,
    dropoff_lat NUMERIC(10, 7) NOT NULL,
    dropoff_lng NUMERIC(10, 7) NOT NULL,
    distance_m INT NOT NULL DEFAULT 0,
    fare BIGINT NOT NULL DEFAULT 0,
    surge NUMERIC(3, 2) NOT NULL DEFAULT 1.00,
    idempotency_key VARCHAR(255) NOT NULL,
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (customer_id, idempotency_key)
);

-- Partial unique index: 1 driver chỉ có tối đa 1 chuyến đang hoạt động
CREATE UNIQUE INDEX trips_driver_active_unique
ON trips(driver_id)
WHERE status IN ('ACCEPTED', 'PICKING_UP', 'IN_TRIP');

-- Trip events table (audit log)
CREATE TABLE trip_events (
    id BIGSERIAL PRIMARY KEY,
    trip_id UUID NOT NULL REFERENCES trips(id),
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Wallets table
CREATE TABLE wallets (
    user_id BIGINT PRIMARY KEY REFERENCES users(id),
    balance BIGINT NOT NULL DEFAULT 0 CHECK (balance >= 0)
);

-- Ledger entries table (idempotent payment)
CREATE TABLE ledger_entries (
    id BIGSERIAL PRIMARY KEY,
    trip_id UUID NOT NULL REFERENCES trips(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    amount BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL CHECK (type IN ('CHARGE', 'REFUND', 'PAYOUT')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (trip_id, user_id, type)
);

-- Indexes for common queries
CREATE INDEX idx_trips_customer ON trips(customer_id);
CREATE INDEX idx_trips_driver ON trips(driver_id);
CREATE INDEX idx_trips_status ON trips(status);
CREATE INDEX idx_trip_events_trip ON trip_events(trip_id);
CREATE INDEX idx_ledger_entries_user ON ledger_entries(user_id);
CREATE INDEX idx_ledger_entries_trip ON ledger_entries(trip_id);
