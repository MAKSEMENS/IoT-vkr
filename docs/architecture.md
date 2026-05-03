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

| Топик               | Producer           | Consumers                                  | Ключ      |
|---------------------|--------------------|--------------------------------------------|-----------|
| sensor-raw-events   | ingestion          | state-aggregation, anomaly-detection       | room_id   |
| room-state-events   | state-aggregation  | будущие потребители, Grafana streaming     | room_id   |
| alert-events        | anomaly-detection  | будущий сервис уведомлений                 | room_id   |

Партиционирование по `room_id` гарантирует упорядоченную обработку событий внутри одного помещения.
Producer-ы используют `acks=all` и идемпотентный режим, что обеспечивает at-least-once доставку без дубликатов от ретраев.

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
- Replay: полное состояние можно пересчитать, опустошив `room_states` и сбросив offset consumer-а на `earliest`
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
- Replay: сброс offset consumer-а `state-aggregation` на `earliest` → пересчёт `room_states` из полного лога Kafka.
- Consumer groups позволяют независимо масштабировать state-aggregation и anomaly-detection.
- `query-service` и `event-ingestion` — чисто stateless и масштабируются репликами за балансировщиком.
