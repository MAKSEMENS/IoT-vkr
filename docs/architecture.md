# System Architecture

## Overview

Distributed asynchronous IoT event processing system for room monitoring.
Four microservices communicate via Apache Kafka; state is persisted in PostgreSQL.

```
IoT Simulator / Gateway
        │ HTTP POST /events
        ▼
┌─────────────────────────┐
│  event-ingestion-service │  validates schema, publishes to Kafka
└────────────┬────────────┘
             │ sensor-raw-events (partitioned by room_id)
      ┌──────┴──────┐
      ▼             ▼
┌──────────┐  ┌───────────┐
│  state-  │  │ anomaly-  │
│aggregation│  │ detection │
│ -service │  │ -service  │
└────┬─────┘  └─────┬─────┘
     │ room_states  │ alerts
     └──────┬───────┘
            ▼ PostgreSQL
     ┌─────────────┐
     │query-service│  GET /rooms, /rooms/{id}/history, /alerts
     └─────────────┘
```

## Kafka Topics

| Topic               | Producer           | Consumers                                | Partitions       |
|---------------------|--------------------|------------------------------------------|------------------|
| sensor-raw-events   | ingestion          | state-aggregation, anomaly-detection     | by room_id       |
| room-state-events   | state-aggregation  | (future consumers, Grafana streaming)    | by room_id       |
| alert-events        | anomaly-detection  | (future notification service)            | by room_id       |

Partitioning key: `room_id` — guarantees ordered processing per room.

## PostgreSQL Schema

```sql
-- room_states: latest aggregated state per room (upsert on each event)
CREATE TABLE room_states (
    room_id       VARCHAR(64) PRIMARY KEY,
    temperature   DOUBLE PRECISION,
    humidity      DOUBLE PRECISION,
    co2           DOUBLE PRECISION,
    smoke         DOUBLE PRECISION,
    motion        BOOLEAN,
    illuminance   DOUBLE PRECISION,
    updated_at    TIMESTAMPTZ NOT NULL
);

-- sensor_events: full event log for history queries and replay
CREATE TABLE sensor_events (
    id          BIGSERIAL PRIMARY KEY,
    room_id     VARCHAR(64) NOT NULL,
    sensor_type VARCHAR(32) NOT NULL,
    value       DOUBLE PRECISION NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_sensor_events_room_time ON sensor_events (room_id, recorded_at);

-- alerts: threshold violations
CREATE TABLE alerts (
    id          BIGSERIAL PRIMARY KEY,
    room_id     VARCHAR(64) NOT NULL,
    sensor_type VARCHAR(32) NOT NULL,
    value       DOUBLE PRECISION NOT NULL,
    rule_name   VARCHAR(128) NOT NULL,
    triggered_at TIMESTAMPTZ NOT NULL,
    resolved_at  TIMESTAMPTZ
);
CREATE INDEX idx_alerts_room_active ON alerts (room_id, resolved_at);
```

## Service Responsibilities

### event-ingestion-service
- Single entry point for sensor data
- Validates JSON schema: required fields, sensor_type enum, value ranges, timestamp format
- Publishes valid events to `sensor-raw-events`; rejects invalid with 400
- Horizontally scalable behind a load balancer

### state-aggregation-service
- Kafka consumer group `state-aggregation`
- Applies Event Sourcing: rebuilds RoomState by replaying events from offset 0
- Upserts `room_states` and appends to `sensor_events` (history log)
- Publishes updated state to `room-state-events`
- Partition assignment ensures single writer per room_id, no write conflicts

### anomaly-detection-service
- Kafka consumer group `anomaly-detection`, reads `sensor-raw-events` independently
- Loads threshold rules from `config/anomaly-rules.yml` at startup
- Inserts alert to `alerts` and publishes to `alert-events` on rule match
- Horizontally scalable

### query-service
- Stateless REST API, reads only from PostgreSQL
- `GET /rooms` — current state of all rooms
- `GET /rooms/{id}/history?from=&to=` — sensor event history for a time range
- `GET /alerts?active=true` — active (unresolved) alerts

### iot-simulator
- Standalone Kotlin app, not deployed in production
- Generates configurable stream of realistic sensor events
- Sends HTTP POST to event-ingestion-service

## Scalability Notes

- All services are stateless except state-aggregation (stateful via Kafka partition assignment)
- Replay: offset reset to earliest on state-aggregation replays full event history
- Consumer groups allow independent scaling of state-aggregation and anomaly-detection
