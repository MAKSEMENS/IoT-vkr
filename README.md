# IoT Monitoring

Распределённая асинхронная система обработки IoT-событий для мониторинга помещений. Приём событий от датчиков, вычисление текущего состояния по модели Event Sourcing, обнаружение аномалий по конфигурируемым правилам, REST API для чтения.

## Выпускная квалификационная работа бакалавра

| Поле               | Значение                                                              |
|--------------------|-----------------------------------------------------------------------|
| Тема               | Распределённая асинхронная система обработки IoT-событий для мониторинга и управления состоянием помещений |
| Студент            | Зиновьев Максим Дмитриевич                                            |
| Группа             | 2307                                                                  |
| Направление        | 09.03.01 — Информатика и вычислительная техника                       |
| Вуз / кафедра      | СПбГЭТУ «ЛЭТИ», кафедра ВТ, факультет КТИ                             |
| Руководитель       | Горячев А. В., к.т.н., доцент                                         |
| Год защиты         | 2026                                                                  |
| Задание            | [`ЗАДАНИЕ ( РАЗВЁРНУТАЯ ФОРМУЛИРОВКА).txt`](./ЗАДАНИЕ%20%28%20РАЗВЁРНУТАЯ%20ФОРМУЛИРОВКА%29.txt) |

## О проекте

В офисах, складах и производственных помещениях стоят датчики температуры, влажности, CO₂, задымлённости, освещённости, движения. Нужно собирать с них поток событий в реальном времени, держать актуальное состояние каждого помещения, реагировать на превышения порогов и отдавать данные наружу — в дашборды, мобильные приложения, системы оповещения. Готовые решения вроде AWS IoT Core или Azure IoT Hub привязаны к облаку конкретного вендора; SCADA-системы тяжело разворачиваются; многие из них работают синхронно и плохо переносят пики — например, одновременное срабатывание датчиков движения и задымления или выгрузку накопленных данных после восстановления связи.

Здесь собран рабочий прототип на открытом стеке (Kotlin, Kafka, PostgreSQL) с акцентом на свойства, которых синхронным архитектурам не хватает. HTTP-ingestion отделён от обработки и не блокируется на вычислении состояния. Полный лог событий лежит в Kafka и остаётся источником истины — текущее состояние помещения пересчитывается из лога (Event Sourcing), что даёт replay по требованию и устойчивость к падениям сервисов. Агрегация и детекция аномалий — независимые consumer-ы одного топика: новый алгоритм проверки правил можно выкатить, не трогая агрегацию. Партиционирование по `room_id` обеспечивает упорядоченную обработку внутри помещения и горизонтальное масштабирование между ними. Все компоненты упакованы в Docker Compose; поток нагрузки имитирует встроенный симулятор шлюза.

## Возможности

- Асинхронный приём событий через HTTP с публикацией в Kafka
- Event Sourcing: полный лог в Kafka, пересчёт состояния через сброс offset
- Идемпотентная обработка по `event_id`
- Партиционирование по `room_id`, упорядоченная обработка событий помещения
- Пороговый алертинг по конфигурации YAML, без перекомпиляции
- REST API: текущее состояние, история за диапазон, активные алерты
- Метрики Prometheus, health-чек на каждом сервисе
- Полный стенд в Docker Compose

## Архитектура

```
IoT-симулятор / шлюз
        │ HTTP POST /events
        ▼
┌──────────────────────────┐
│ event-ingestion-service  │   валидация, публикация в Kafka
└─────────────┬────────────┘
              │ sensor-raw-events  (key = room_id)
       ┌──────┴──────┐
       ▼             ▼
┌────────────┐  ┌────────────────┐
│   state-   │  │    anomaly-    │
│aggregation │  │   detection    │
└─────┬──────┘  └───────┬────────┘
      │ room_states     │ alerts
      └────────┬────────┘
               ▼ PostgreSQL
        ┌─────────────┐
        │query-service│  REST: /rooms, /rooms/{id}/history, /alerts
        └─────────────┘
```

Подробности — [`docs/architecture.md`](./docs/architecture.md). Архитектурные решения — [`docs/adr/`](./docs/adr/).

## Стек

Kotlin 1.9 / JVM 17 · Spring Boot 3.2 · Apache Kafka 3.6 (KRaft) · PostgreSQL 16 · Flyway · Maven · Docker Compose · Micrometer/Prometheus.

## Структура

```
vkr/
├── common/                      общие модели и константы топиков
├── event-ingestion-service/     POST /events → Kafka
├── state-aggregation-service/   Event Sourcing, агрегация состояния
├── anomaly-detection-service/   правила, алерты
├── query-service/               REST API на чтение
├── iot-simulator/               генератор событий
├── monitoring/                  Prometheus + provisioning Grafana
├── loadtest/                    k6-скрипты нагрузочного тестирования
├── docs/                        архитектура и ADR
├── docker-compose.yml           postgres, kafka, grafana, prometheus, сервисы
└── pom.xml                      родительский Maven POM
```

## Запуск в Docker

```bash
docker compose up -d --build
docker compose --profile simulator up -d iot-simulator
```

После старта:

| Компонент       | Адрес                                      |
|-----------------|--------------------------------------------|
| event-ingestion | http://localhost:8081                      |
| query-service   | http://localhost:8080                      |
| Grafana         | http://localhost:3000 (admin / admin)      |
| Prometheus      | http://localhost:9090                      |
| PostgreSQL      | localhost:5432, `iot_monitoring`, `vkr` / `vkr_secret` |
| Kafka           | localhost:9092                             |

## Запуск из IDE

```bash
docker compose up -d postgres kafka
docker compose up kafka-init
```

Запустить сервисы в порядке: `state-aggregation-service` → остальные → `iot-simulator`.
Все `application.yml` уже настроены на `localhost`.

## Сборка

```bash
./mvnw -B -ntp package -DskipTests
```

## REST API

Базовый URL: `http://localhost:8080`.

| Метод | Путь                                       | Описание                                   |
|-------|--------------------------------------------|--------------------------------------------|
| GET   | `/rooms`                                   | Состояние всех помещений                   |
| GET   | `/rooms/{id}`                              | Состояние одного помещения                 |
| GET   | `/rooms/{id}/history?from=&to=&limit=`     | История событий за диапазон (ISO-8601)     |
| GET   | `/alerts?roomId=&active=true&limit=`       | Алерты с фильтрами                         |

Приём событий — `event-ingestion-service` на 8081:

```bash
curl -X POST http://localhost:8081/events \
  -H 'Content-Type: application/json' \
  -d '{"roomId":"A101","sensorId":"A101-temperature","sensorType":"TEMPERATURE","value":24.7}'
```

## Replay

Полный пересчёт `room_states` из таблицы `sensor_events` (БД играет роль event log) выполняется по запросу:

```bash
curl -u admin:admin -X POST http://localhost:8082/admin/replay
# 202 Accepted
# {"success":true,"message":"Replay завершён: пересчитано N событий по K комнатам", ...}
```

`POST /admin/replay` под basic auth (`ADMIN_USER` / `ADMIN_PASSWORD` env-переменные, по умолчанию `admin`/`admin`). Сервис останавливает Kafka-листенеры на время операции, делает `TRUNCATE room_states`, упорядоченно перечитывает `sensor_events` и пересчитывает агрегаты. Метрики: `replay_invocations_total`, `replay_events_processed_total`, `replay_duration_seconds`, `replay_in_progress`.

## Отказоустойчивость и нагрузочные тесты

- DLQ + retry у consumer-ов: при 3 неудачных попытках сообщение уходит в `<topic>.DLT` (FixedBackOff 1с × 2 retry). Producer hardening: `enable.idempotence=true`, `acks=all`, `delivery.timeout.ms=120s`.
- Сценарии падений (PG/Kafka/state-aggregation/ingestion) — [`docs/resilience-tests.md`](./docs/resilience-tests.md).
- Нагрузочные тесты на k6 — [`loadtest/`](./loadtest/), результаты — [`docs/loadtest-results.md`](./docs/loadtest-results.md).

## Конфигурация правил

`anomaly-detection-service/src/main/resources/anomaly-rules.yml`:

```yaml
rules:
  - name: high_temperature
    sensor_type: temperature
    condition: GREATER_THAN
    threshold: 35.0
    description: "Temperature exceeds 35°C"
```

Поддерживаются `GREATER_THAN`, `LESS_THAN`. Применяется при перезапуске сервиса.

## Метрики и health

- `/actuator/health` — состояние сервиса
- `/actuator/prometheus` — метрики

Мониторинг разворачивается вместе со стендом: Prometheus скрейпит все четыре сервиса, Grafana поднимается с provisioned datasource-ами и тремя дашбордами (Service health, Pipeline throughput, Rooms & alerts). Конфигурация — [`monitoring/`](./monitoring/), решение — [`docs/adr/ADR-005-monitoring-grafana.md`](./docs/adr/ADR-005-monitoring-grafana.md).

## Статус по заданию

| Требование                                          | Статус            |
|-----------------------------------------------------|-------------------|
| Kotlin (JVM 17)                                     | готово            |
| Apache Kafka 3.x, at-least-once                     | готово            |
| Партиционирование по `room_id`                      | готово            |
| Event Sourcing + replay                             | готово            |
| Конфигурируемые правила в YAML                      | готово            |
| Хранение в PostgreSQL                               | готово            |
| REST API (rooms, history, alerts)                   | готово            |
| Контейнеризация Docker Compose                      | готово            |
| Юнит / интеграционные тесты (Testcontainers)        | готово            |
| DLQ + ретраи у consumer-ов, producer hardening      | готово            |
| Тестирование отказоустойчивости (DLQ-автотест + сценарии в docs) | готово            |
| Нагрузочное тестирование (k6-скрипты + шаблон)      | инфра готова, прогоны не сделаны |
| Мониторинг: Prometheus + provisioned Grafana-дашборды | готово          |
| Раздел БЖД (для пояснительной записки)              | вне кода          |
