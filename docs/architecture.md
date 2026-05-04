# Архитектура системы

## Обзор

Распределённая асинхронная система обработки IoT-событий для мониторинга помещений.
Четыре микросервиса взаимодействуют через Apache Kafka; состояние сохраняется в PostgreSQL.

```
IoT-симулятор / шлюз
        │ HTTP POST /events
        ▼
┌─────────────────────────┐
│  event-ingestion-service │  валидирует схему, публикует в Kafka
└────────────┬────────────┘
             │ sensor-raw-events (партиционирование по room_id)
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

## Топики Kafka

| Топик                    | Producer                              | Consumers                                  | Ключ      |
|--------------------------|---------------------------------------|--------------------------------------------|-----------|
| sensor-raw-events        | ingestion                             | state-aggregation, anomaly-detection       | room_id   |
| sensor-raw-events.DLT    | DeadLetterPublishingRecoverer (state) | (вручную)                                  | room_id   |
| room-state-events        | state-aggregation                     | будущие потребители, Grafana streaming     | room_id   |
| room-state-events.DLT    | DeadLetterPublishingRecoverer         | (вручную)                                  | room_id   |
| alert-events             | anomaly-detection                     | будущий сервис уведомлений                 | room_id   |

Партиционирование по `room_id` гарантирует упорядоченную обработку событий внутри одного помещения.
Producer-ы используют `acks=all` и идемпотентный режим (`enable.idempotence=true`, `delivery.timeout.ms=120000`), что обеспечивает at-least-once доставку без дубликатов от ретраев.

Consumer-ы `state-aggregation` и `anomaly-detection` используют `DefaultErrorHandler` с `FixedBackOff(1000ms, 2 retries)`. После 3 неудачных попыток обработки сообщение попадает в `<topic>.DLT` той же партиции и листенер продолжает обработку — poison message не блокирует партицию.

## Схема PostgreSQL

Схема принадлежит `state-aggregation-service` и накатывается через Flyway при старте
(`state-aggregation-service/src/main/resources/db/migration/V1__init_schema.sql`).
Остальные сервисы используют ту же БД; `query-service` — только на чтение.

Таблицы:
- `room_states (room_id PK, temperature, humidity, co2, smoke, motion, light, updated_at, version)` — последнее агрегированное состояние помещения; `version` — счётчик оптимистичной блокировки JPA.
- `sensor_events (id PK, event_id UNIQUE, room_id, sensor_id, sensor_type, value, recorded_at)` — полный лог событий; `event_id` обеспечивает идемпотентность вставок; индекс `(room_id, recorded_at)`.
- `alerts (id PK, alert_id UNIQUE, room_id, sensor_type, rule_name, severity, message, triggering_value, triggered_at, resolved_at)` — нарушения порогов; индекс `(room_id, resolved_at)`.

## Зоны ответственности сервисов

### event-ingestion-service
- Единственная точка входа для данных датчиков
- Валидирует JSON-схему: обязательные поля, enum `sensor_type`, наличие `value`, парсинг `timestamp` (если поле отсутствует — сервер подставляет `Instant.now()`)
- Валидные события публикует в `sensor-raw-events`; невалидные — отказ HTTP 400
- Stateless, масштабируется горизонтально

### state-aggregation-service
- Kafka consumer group `state-aggregation`
- Идемпотентность: `existsByEventId` перед вставкой в `sensor_events`
- Применяет Event Sourcing: дописывает событие в лог `sensor_events`, обновляет `room_states` (последнее показание по каждому `sensor_type`)
- Публикует свежий `RoomState` в `room-state-events`
- Замыкание Event Sourcing: `POST /admin/replay` (basic auth, порт 8082) останавливает Kafka-листенеры, делает `TRUNCATE room_states`, упорядоченно перечитывает таблицу `sensor_events` и пересчитывает `room_states`. Источник истины при replay — БД, не Kafka, потому что идемпотентность по `event_id` пропустила бы повторно полученные из Kafka события. Метрики: `replay_invocations_total`, `replay_events_processed_total`, `replay_duration_seconds`, `replay_in_progress`.
- Партиционирование гарантирует одного писателя на `room_id` — конфликтов записи нет

### anomaly-detection-service
- Kafka consumer group `anomaly-detection`, читает `sensor-raw-events` независимо от state-aggregation
- При старте загружает пороговые правила из `anomaly-detection-service/src/main/resources/anomaly-rules.yml`
- На каждое событие прогоняет совпадающие по типу датчика правила; при срабатывании вставляет запись в `alerts` и публикует в `alert-events`
- Stateless, масштабируется горизонтально

### query-service
- Stateless REST API, читает только из PostgreSQL
- `GET /rooms` — текущее состояние всех помещений
- `GET /rooms/{id}` — состояние одного помещения
- `GET /rooms/{id}/history?from=&to=&limit=` — история событий за диапазон (ISO-8601)
- `GET /alerts?roomId=&active=true&limit=` — список алертов с опциональной фильтрацией

### iot-simulator
- Самостоятельное Kotlin/Spring Boot приложение, в продакшене не разворачивается
- Генерирует настраиваемый поток правдоподобных событий датчиков
- Отправляет POST в event-ingestion-service по HTTP

## Замечания о масштабируемости

- Все сервисы stateless, кроме state-aggregation (его «состояние» — назначение партиций по `room_id`).
- Replay реализован как пересчёт `room_states` из БД (см. `state-aggregation-service`). Возможно полное восстановление при потере таблицы.
- Consumer groups позволяют независимо масштабировать state-aggregation и anomaly-detection.
- `query-service` и `event-ingestion` — чисто stateless и масштабируются репликами за балансировщиком.

## Отказоустойчивость

- Producer-ы: `acks=all`, `enable.idempotence=true`, `retries=Integer.MAX_VALUE`, `delivery.timeout.ms=120000`.
- Consumer-ы: `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` + `FixedBackOff(1000ms, 2 retries)` → `<topic>.DLT`.
- Идемпотентный consumer на стороне state-aggregation: `UNIQUE(event_id)` в `sensor_events`.
- Сценарии отказов и ручные проверки описаны в `docs/resilience-tests.md`.
- Автотест DLQ: `state-aggregation-service` → `DlqIntegrationTest`.
