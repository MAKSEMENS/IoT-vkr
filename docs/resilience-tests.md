# Сценарии отказоустойчивости

Документ описывает ручные сценарии проверки устойчивости системы к сбоям инфраструктуры и сервисов. Все сценарии воспроизводятся через `docker compose`.

## Подготовка

```bash
docker compose up -d kafka kafka-init postgres event-ingestion state-aggregation anomaly-detection query-service
docker compose ps  # все healthy
```

Симулятор нагрузки запускается отдельно профилем:

```bash
docker compose --profile simulator up -d iot-simulator
```

## Автотесты по DLQ

Шаблонный сценарий «падение обработчика → 3 попытки → DLT» покрыт `DlqIntegrationTest` в `state-aggregation-service`. Тест эмулирует исключение в обработчике через `@MockBean RoomStateAggregator` и проверяет:

1. Listener вызывает `apply` ровно 3 раза (1 initial + 2 retry, `FixedBackOff(1000ms, 2)`).
2. После 3-й ошибки сообщение появляется в `sensor-raw-events.DLT`.
3. Listener не залипает: следующее сообщение обрабатывается штатно.

Запуск: `./mvnw -pl state-aggregation-service -am -Dtest=DlqIntegrationTest test`.

## Сценарий 1. Остановка PostgreSQL во время записи

**Цель:** проверить, что state-aggregation корректно переживает падение БД.

**Шаги:**

1. Запустить симулятор: `docker compose --profile simulator up -d iot-simulator`.
2. Через ~10 секунд: `docker compose stop postgres`.
3. Подождать 30 секунд: в логах `state-aggregation` будут `CannotCreateTransactionException`. Listener будет ретраить, но события скапливаются в Kafka — не теряются.
4. `docker compose start postgres` — Postgres поднимается за ~5 сек.
5. Через ~30 секунд: в логах `state-aggregation` пойдёт штатная обработка. Lag консьюмера сокращается.

**Ожидаемое поведение:**

- Сообщения не теряются (хранятся в Kafka).
- После восстановления PG все события дообрабатываются.
- В `sensor-raw-events.DLT` могут попасть сообщения, по которым 3 retry упали в одном окне даунтайма — это ожидаемо и видно в метрике `spring_kafka_listener_seconds_count{result="failure"}`.

**Команды проверки:**

```bash
# lag по топику до и после
docker compose exec kafka kafka-consumer-groups \
  --bootstrap-server kafka:29092 --describe --group state-aggregation
# что попало в DLT
docker compose exec kafka kafka-console-consumer \
  --bootstrap-server kafka:29092 --topic sensor-raw-events.DLT --from-beginning --max-messages 5
```

## Сценарий 2. Остановка Kafka во время отправки

**Цель:** проверить producer-идемпотентность и отсутствие потерь у `event-ingestion`.

**Шаги:**

1. `curl -X POST http://localhost:8081/events -H 'Content-Type: application/json' -d ...` — несколько штатных запросов.
2. `docker compose stop kafka`.
3. `curl ... 5 раз` — клиент получает 503 (или зависание до `delivery.timeout.ms = 120s`).
4. `docker compose start kafka`.
5. Producer внутри `delivery.timeout.ms` дожимает в Kafka. Клиенту, который послал запрос ДО таймаута, вернётся 200.

**Ожидаемое поведение:**

- При `enable.idempotence=true` повторная отправка ретраи не создаёт дублей по `event_id`.
- `state-aggregation` идемпотентен по `UNIQUE(event_id)` — повторно полученное событие не пересчитает `room_states`.

## Сценарий 3. Kill state-aggregation в середине обработки

**Цель:** проверить, что обработка возобновляется ровно с того offset, на котором остановилась.

**Шаги:**

1. Запустить симулятор на 200 events/sec.
2. `docker compose kill state-aggregation` — SIGKILL без graceful shutdown.
3. Через 5 секунд: `docker compose up -d state-aggregation`.
4. Дождаться, пока `consumer-group lag` сойдёт к нулю.

**Ожидаемое поведение:**

- Lag в момент рестарта = X сообщений.
- После старта lag за ~30 сек сходит к нулю.
- В БД `room_states.version` инкрементируется для всех роумов, события не дублируются (UNIQUE constraint срабатывает при попытке повторной обработки уже сохранённых событий).

## Сценарий 4. Остановка ingestion и его рестарт

**Цель:** проверить, что REST-клиенты деградируют корректно, а после рестарта всё восстанавливается.

**Шаги:**

1. `docker compose stop event-ingestion`.
2. `curl ...` — connection refused.
3. `docker compose start event-ingestion`. Старт ~10 сек.
4. `curl ...` — снова 200.

**Ожидаемое поведение:** простой только в течение downtime, без потерь данных (клиент получает явную ошибку и должен ретраить сам).

## Что покрыто автотестами и что только ручным сценарием

| Сценарий | Автотест | Ручной (compose) |
|---|---|---|
| DLQ после 3 retry | ✅ `DlqIntegrationTest` | — |
| Идемпотентность по event_id | ✅ `AggregationIntegrationTest` | — |
| Падение PG | — | Сценарий 1 |
| Падение Kafka | — | Сценарий 2 |
| Kill state-aggregation | — | Сценарий 3 |
| Рестарт ingestion | — | Сценарий 4 |

## Связанные конфигурационные параметры

- `spring.kafka.producer.acks=all`, `enable.idempotence=true`, `retries=2147483647`, `delivery.timeout.ms=120000` — не теряем при кратком даунтайме брокера.
- `spring.kafka.producer.max.in.flight.requests.per.connection=5` — совместимо с idempotence.
- `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` + `FixedBackOff(1000ms, 2 retry)` — конечный гарант что bad message не блокирует партицию.
- БД: `UNIQUE(event_id)` на `sensor_events` — идемпотентный consumer.
