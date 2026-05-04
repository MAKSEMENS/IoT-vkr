# Нагрузочные тесты (k6)

Скрипты в каталоге проверяют пропускную способность и устойчивость пайплайна `event-ingestion → Kafka → state-aggregation → PostgreSQL` под нагрузкой.

## Что есть

| Файл | Сценарий | Длительность |
|---|---|---|
| `k6-constant.js` | Постоянная нагрузка `RPS` events/sec | `DURATION` (по умолчанию 5 минут) |
| `k6-burst.js` | warmup 200 rps → burst 5000 rps → cooldown 200 rps | 2 минуты |

Целевая ingestion-точка: `POST {BASE_URL}/events`, формат — `SensorEventRequest` (см. `event-ingestion-service`).

## Запуск

### Через docker (рекомендуется)

```bash
# 1. поднять стек
docker compose up -d kafka kafka-init postgres event-ingestion state-aggregation anomaly-detection query-service

# 2. константная нагрузка 200 rps на 5 минут
docker run --rm -i --network host \
  -e BASE_URL=http://localhost:8081 -e RPS=200 -e DURATION=5m \
  grafana/k6 run - < loadtest/k6-constant.js

# 3. burst 5000 rps
docker run --rm -i --network host \
  -e BASE_URL=http://localhost:8081 \
  grafana/k6 run - < loadtest/k6-burst.js
```

### Native k6

```bash
# Установка k6: https://grafana.com/docs/k6/latest/set-up/install-k6/

BASE_URL=http://localhost:8081 RPS=500 DURATION=5m k6 run loadtest/k6-constant.js
BASE_URL=http://localhost:8081 k6 run loadtest/k6-burst.js
```

## Что проверяется

- `ingest_error_rate < 1%` (под штатной нагрузкой) — ingestion не теряет события.
- `ingest_latency_ms.p95 < 500ms`, `.p99 < 1500ms` — приемлемая задержка POST.
- `http_req_failed < 1%` — TCP/HTTP-уровень.

## Что смотреть на стороне сервера во время прогона

```bash
# lag по консьюмеру
docker compose exec kafka kafka-consumer-groups \
  --bootstrap-server kafka:29092 --describe --group state-aggregation

# метрики Spring Boot Actuator
curl http://localhost:8081/actuator/prometheus | grep -E 'http_server_requests_seconds_count|kafka_producer_record_send_total'
curl http://localhost:8082/actuator/prometheus | grep -E 'spring_kafka_listener_seconds|replay_'

# рост строк в БД
docker compose exec postgres psql -U vkr -d iot_monitoring -c \
  "SELECT count(*) FROM sensor_events;"
```

## Куда писать результаты

Результаты прогонов оформлять в [docs/loadtest-results.md](../docs/loadtest-results.md) — файл-шаблон с разделами под каждый сценарий и таблицами p50/p95/p99/lag.
