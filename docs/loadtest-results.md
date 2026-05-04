# Результаты нагрузочного тестирования

Шаблон для фиксации результатов прогонов k6-скриптов из `loadtest/`. Заполняется по факту прогонов на тестовой машине.

## Стенд

- Хост: <CPU/RAM/OS>
- Docker compose: `docker compose up -d kafka kafka-init postgres event-ingestion state-aggregation anomaly-detection query-service`
- Версия проекта: <git rev-parse HEAD>
- Дата прогонов: <YYYY-MM-DD>

## Ключевые конфигурационные параметры

| Параметр | Значение |
|---|---|
| Kafka topic `sensor-raw-events` partitions | 4 |
| Producer `acks` | all |
| Producer `enable.idempotence` | true |
| Producer `delivery.timeout.ms` | 120000 |
| Consumer `state-aggregation` concurrency | по умолчанию (1 thread/partition) |
| `DefaultErrorHandler` retry policy | FixedBackOff(1000ms, 2 retries) |

## Сценарий 1. Постоянная нагрузка 200 rps × 5 минут

```bash
docker run --rm -i --network host -e BASE_URL=http://localhost:8081 \
  -e RPS=200 -e DURATION=5m grafana/k6 run - < loadtest/k6-constant.js
```

| Метрика | Значение |
|---|---|
| Общее число запросов | <N> |
| Успешных (`status=202`) | <N> |
| `ingest_error_rate` | <%> |
| `ingest_latency_ms` p50 / p95 / p99 | <ms> / <ms> / <ms> |
| Consumer lag в конце прогона | <records> |
| Кол-во записей в `sensor_events` после прогона | <N> |
| Кол-во в `<topic>.DLT` | <N> |

Комментарий: <узкие места, что было замечено в логах>

## Сценарий 2. Постоянная нагрузка 500 rps × 5 минут

| Метрика | Значение |
|---|---|
| Общее число запросов | <N> |
| `ingest_error_rate` | <%> |
| `ingest_latency_ms` p50 / p95 / p99 | <ms> / <ms> / <ms> |
| Consumer lag | <records> |

## Сценарий 3. Постоянная нагрузка 1000 rps × 5 минут

| Метрика | Значение |
|---|---|
| Общее число запросов | <N> |
| `ingest_error_rate` | <%> |
| `ingest_latency_ms` p50 / p95 / p99 | <ms> / <ms> / <ms> |
| Consumer lag | <records> |

## Сценарий 4. Burst 5000 rps × 30 секунд

```bash
docker run --rm -i --network host -e BASE_URL=http://localhost:8081 \
  grafana/k6 run - < loadtest/k6-burst.js
```

| Фаза | Длительность | RPS | Error rate | p95 latency |
|---|---|---|---|---|
| warmup | 30s | 200 | <%> | <ms> |
| burst | 30s | 5000 | <%> | <ms> |
| cooldown | 60s | 200 | <%> | <ms> |

Время выхода lag-а в ноль после burst: <sec>.

## Выводы

- <Какой максимальный устойчивый RPS на стенде>
- <Что становится узким местом: ingestion, Kafka producer, БД, ...>
- <Какие настройки следует поднять, если потребуется больше throughput>

## Артефакты

- `loadtest/results/<date>-200rps.json` — `k6 --out json=...`
- скриншоты Grafana (`http://localhost:3000`)
