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

Schema is owned by `state-aggregation-service` and applied via Flyway on its startup
(`state-aggregation-service/src/main/resources/db/migration/V1__init_schema.sql`).
Other services share the same database read-only.

Tables:
- `room_states (room_id PK, temperature, humidity, co2, smoke, motion, illuminance, updated_at)` — latest aggregated state per room (upserted)
- `sensor_events (id, room_id, sensor_type, value, recorded_at)` — full event log for history; index on `(room_id, recorded_at)`
- `alerts (id, room_id, sensor_type, value, rule_name, triggered_at, resolved_at)` — threshold violations; index on `(room_id, resolved_at)`

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
