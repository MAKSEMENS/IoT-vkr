-- Room state: latest aggregated sensor readings per room
CREATE TABLE room_states (
    room_id       VARCHAR(64)      PRIMARY KEY,
    temperature   DOUBLE PRECISION,
    humidity      DOUBLE PRECISION,
    co2           DOUBLE PRECISION,
    smoke         DOUBLE PRECISION,
    motion        BOOLEAN,
    illuminance   DOUBLE PRECISION,
    updated_at    TIMESTAMPTZ      NOT NULL
);

-- Full sensor event log for history queries and Event Sourcing replay
CREATE TABLE sensor_events (
    id          BIGSERIAL        PRIMARY KEY,
    room_id     VARCHAR(64)      NOT NULL,
    sensor_type VARCHAR(32)      NOT NULL,
    value       DOUBLE PRECISION NOT NULL,
    recorded_at TIMESTAMPTZ      NOT NULL
);

CREATE INDEX idx_sensor_events_room_time ON sensor_events (room_id, recorded_at);

-- Threshold violation alerts
CREATE TABLE alerts (
    id           BIGSERIAL        PRIMARY KEY,
    room_id      VARCHAR(64)      NOT NULL,
    sensor_type  VARCHAR(32)      NOT NULL,
    value        DOUBLE PRECISION NOT NULL,
    rule_name    VARCHAR(128)     NOT NULL,
    triggered_at TIMESTAMPTZ      NOT NULL,
    resolved_at  TIMESTAMPTZ
);

CREATE INDEX idx_alerts_room_active ON alerts (room_id, resolved_at);
