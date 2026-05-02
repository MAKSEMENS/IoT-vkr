# ADR-001: Technology Stack

**Status:** Accepted  
**Date:** 2026-05-03

## Decision

Kotlin (JVM 17) + Spring Boot 3.x + Apache Kafka 3.x + PostgreSQL + Docker Compose.

## Rationale

**Kotlin:** Concise JVM language with null-safety and coroutine support; natural fit for Spring Boot ecosystem; stated in the assignment.

**Apache Kafka:** Persistent, replayable event log with ordered delivery per partition. Enables Event Sourcing replay, at-least-once guarantee, and independent consumer group scaling — not achievable with a simple message queue (RabbitMQ) or synchronous REST calls.

**PostgreSQL:** Well-known relational DBMS. Sufficient for aggregated state and alert storage; supports range queries needed for history API.

**Spring Boot 3.x:** Mature framework with first-class Kafka (`spring-kafka`), JPA, and REST support. Reduces boilerplate while remaining transparent.

**Docker Compose:** Reproducible local environment for all infrastructure components. Simpler than Kubernetes for a single-node dev/demo setup.

## Alternatives Rejected

| Alternative | Reason rejected |
|---|---|
| Java | More verbose, no null-safety; Kotlin is a direct upgrade with same ecosystem |
| RabbitMQ | No built-in log retention, no replay capability |
| MongoDB | No benefit over PostgreSQL for this schema; adds operational complexity |
| Kubernetes | Over-engineered for a single-node academic prototype |
