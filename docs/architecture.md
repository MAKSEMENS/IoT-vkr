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

| Topic               | Producer           | Consumers                                | Key       |
|---------------------|--------------------|------------------------------------------|-----------|
| sensor-raw-events   | ingestion          | state-aggregation, anomaly-detection     | room_id   |
| room-state-events   | state-aggregation  | (future consumers, Grafana streaming)    | room_id   |
| alert-events        | anomaly-detection  | (future notification service)            | room_id   |

Partition key `room_id` guarantees ordered per-room processing.
Producers use `acks=all` and idempotent mode for at-least-once delivery without duplicates from retries.

## PostgreSQL Schema

Schema is owned by `state-aggregation-service` and applied via Flyway on startup
(`state-aggregation-service/src/main/resources/db/migration/V1__init_schema.sql`).
Other services share the same database; `query-service` reads only.

Tables:
- `room_states (room_id PK, temperature, humidity, co2, smoke, motion, light, updated_at, version)` — latest aggregated state per room; `version` is JPA optimistic-lock counter.
- `sensor_events (id PK, event_id UNIQUE, room_id, sensor_id, sensor_type, value, recorded_at)` — full event log; `event_id` makes inserts idempotent; index on `(room_id, recorded_at)`.
- `alerts (id PK, alert_id UNIQUE, room_id, sensor_type, rule_name, severity, message, triggering_value, triggered_at, resolved_at)` — threshold violations; index on `(room_id, resolved_at)`.

## Service Responsibilities

### event-ingestion-service
- Single entry point for sensor data
- Validates JSON schema: required fields, sensor_type enum, value present, timestamp parseable (server fills `Instant.now()` if absent)
- Publishes valid events to `sensor-raw-events`; rejects invalid with 400
- Stateless, horizontally scalable

### state-aggregation-service
- Kafka consumer group `state-aggregation`
- Idempotency: `existsByEventId` before insert into `sensor_events`
- Applies Event Sourcing: appends to `sensor_events` log, upserts `room_states` (latest reading per sensor_type)
- Publishes updated `RoomState` to `room-state-events`
- Replay: full state can be rebuilt by truncating `room_states` and resetting consumer offset to earliest
- Partition assignment ensures single writer per `room_id` — no write conflicts

### anomaly-detection-service
- Kafka consumer group `anomaly-detection`, reads `sensor-raw-events` independently of state-aggregation
- Loads threshold rules from `anomaly-detection-service/src/main/resources/anomaly-rules.yml` at startup
- For each event evaluates all matching rules; on match inserts into `alerts` and publishes to `alert-events`
- Stateless, horizontally scalable

### query-service
- Stateless REST API, reads only from PostgreSQL
- `GET /rooms` — current state of all rooms
- `GET /rooms/{id}` — current state of one room
- `GET /rooms/{id}/history?from=&to=&limit=` — sensor event history for a time range (ISO-8601 timestamps)
- `GET /alerts?roomId=&active=true&limit=` — alerts list with optional filtering

### iot-simulator
- Standalone Kotlin/Spring Boot app, not deployed in production
- Generates a configurable stream of realistic sensor events
- POSTs them to event-ingestion-service over HTTP

## Scalability Notes

- All services are stateless except state-aggregation (stateful via partition assignment to `room_id`).
- Replay: reset `state-aggregation` consumer offset to earliest → it rebuilds `room_states` from the full Kafka log.
- Consumer groups allow independent horizontal scaling of state-aggregation and anomaly-detection.
- `query-service` and `event-ingestion` are pure stateless and scale by replicas behind a load balancer.
