# ADR-002: Event Sourcing for Room State

**Status:** Accepted  
**Date:** 2026-05-03

## Decision

Room state is not stored as a mutable record updated in-place. Instead, the full event log in Kafka (`sensor-raw-events`) is the source of truth. The current state is derived by applying events sequentially.

## Rationale

1. **Replay:** Resetting consumer offset to `earliest` rebuilds any historical state without a separate backup mechanism. Required by the assignment.
2. **Fault recovery:** If `state-aggregation-service` crashes, it resumes from last committed offset; no state is lost.
3. **Auditability:** The complete history of every sensor reading is preserved in Kafka (configurable retention) and in the `sensor_events` table.
4. **No write conflicts:** Partition assignment by `room_id` guarantees a single consumer processes each room's events in order.

## Trade-offs

- Cold start (full replay from offset 0) takes time proportional to log size. Mitigated by Kafka log compaction and periodic snapshots to `room_states`.
- State aggregation logic must be deterministic and idempotent.

## Implementation

`state-aggregation-service` maintains an in-memory `Map<roomId, RoomState>` updated per event, then upserts to `room_states`. On restart it reads the last committed offset and continues from there (not a full replay in normal operation; full replay is an explicit admin operation).
