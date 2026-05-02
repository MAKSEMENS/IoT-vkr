# ADR-003: Service Decomposition

**Status:** Accepted  
**Date:** 2026-05-03

## Decision

Four services: event-ingestion, state-aggregation, anomaly-detection, query-service.

## Rationale

Each service has a single, independent scaling axis:

| Service | Scaling trigger | Isolation benefit |
|---|---|---|
| event-ingestion | Inbound HTTP request rate | Can scale without touching consumers |
| state-aggregation | Number of rooms / event rate | Stateful; partition-bound scaling |
| anomaly-detection | Rule evaluation throughput | Can change rules/logic independently |
| query-service | Read request rate | Stateless; scales freely |

Splitting ingestion from aggregation means the HTTP entry point never blocks on state computation. Splitting anomaly detection from aggregation lets both consume the same topic independently — a new detection algorithm can be deployed without touching state logic.

## Alternatives Rejected

**Monolith:** Eliminates independent scaling and couples unrelated change cycles (HTTP validation changes force redeployment of aggregation logic).

**Ingestion + Aggregation merged:** Tempting for simplicity, but violates single-responsibility: the ingestion path must remain low-latency while aggregation may be compute-heavy.
