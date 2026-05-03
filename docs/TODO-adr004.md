# Незавершённое по ADR-004

Снимок состояния на 2026-05-04. Что закоммичено, что осталось, какие проблемы найдены — чтобы продолжить с того же места.

## Состояние блоков

| Блок | Статус | Что сделано |
|------|--------|-------------|
| 1. Тестирование | ✅ Готов | Юнит и интеграционные тесты на Testcontainers закоммичены и проходят. |
| 2. Replay /admin/replay | ⚠️ В работе, требует доработки | Endpoint, ReplayService, basic auth, тест написаны. **Логика replay переработана**, но не перезапускалась — см. «Проблемы». |
| 3. DLQ + producer-устойчивость | ⚠️ В работе | Producer-настройки (`enable.idempotence`, `retries`, `delivery.timeout.ms`) добавлены. `KafkaConsumerConfig` с `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` (FixedBackOff 3 попытки → `<topic>.DLT`) добавлен в state-aggregation и anomaly-detection. **Не проверено в рантайме**, тестов нет. |
| 4. Нагрузочное тестирование | ⛔ Не начато | Нужен каталог `loadtest/` с k6-скриптами и `docs/loadtest-results.md`. |

## Что было сделано в этой сессии (закоммичено или ожидает коммита)

### Закоммичено
- `d7d93ff` — юнит-тесты RuleEngine, RuleCondition, AnomalyRulesConfig, RoomStateAggregator (17 кейсов).
- `0cf7a87` — интеграционные тесты на Testcontainers: ingestion (POST → Kafka), aggregation (Kafka → DB, идемпотентность), anomaly (превышение → alert). 8 кейсов, все зелёные.

### Ожидает коммита (в working tree)
- `state-aggregation-service/src/main/kotlin/ru/leti/vkr/aggregation/admin/`
  - `ReplayService.kt` — пересчёт `room_states` из таблицы `sensor_events` (event log = БД, не Kafka). Метрики `replay_invocations_total`, `replay_events_processed_total`, `replay_duration_seconds`, `replay_in_progress`.
  - `AdminController.kt` — `POST /admin/replay`.
  - `AdminSecurityConfig.kt` — basic auth на `/admin/**`, остальное permitAll.
- `state-aggregation-service/src/test/.../ReplayIntegrationTest.kt` — два кейса: replay восстанавливает состояние; неверные креденшелы → 401.
- `state-aggregation-service/src/main/kotlin/ru/leti/vkr/aggregation/domain/SensorEventRepository.kt` — добавлен `streamAllOrderedByTime()` для пересчёта.
- `state-aggregation-service/pom.xml` — `spring-boot-starter-security`.
- `state-aggregation-service/src/main/resources/application.yml` — блок `spring.security.user`, producer hardening.
- `event-ingestion-service/src/main/resources/application.yml` — producer hardening.
- `anomaly-detection-service/src/main/resources/application.yml` — producer hardening.
- `state-aggregation-service/src/main/kotlin/ru/leti/vkr/aggregation/kafka/KafkaConsumerConfig.kt` — DLQ + ретраи.
- `anomaly-detection-service/src/main/kotlin/ru/leti/vkr/anomaly/kafka/KafkaConsumerConfig.kt` — DLQ + ретраи.
- `docker-compose.yml` — порт 8082 для state-aggregation, env `ADMIN_USER`/`ADMIN_PASSWORD`.

## Проблемы и риски на текущий момент

### Блок 2 (replay)

1. **Реалистичность replay-логики, ещё не проверена в тесте.** Изначально replay делал «truncate `room_states` + reset offset Kafka до earliest». Это конфликтует с идемпотентностью: `RoomStateAggregator.apply()` пропускает событие если `event_id` уже в `sensor_events`, поэтому при reset offset Kafka состояние НЕ пересчитывается. Тест поймал ошибку (`expected 22.0 but null`).
   - **Решение, уже в коде:** replay теперь не сбрасывает offset Kafka, а заново считывает `sensor_events` упорядоченно по `recorded_at` и пересчитывает `room_states`. Listener останавливается, чтобы избежать гонки, и запускается обратно.
   - **Что нужно сделать:** прогнать `ReplayIntegrationTest` целиком после фикса. Compile выпадет если что-то синтаксически кривое — у меня в `recomputeFromEventLog()` использован `transactionTemplate.execute { ... }!!` с возвратом `Pair<Long, Long>`, проверить что Kotlin не ругается на nullable.

2. **`RestTemplate` и 401-challenge.** В тесте `без креденшелов admin replay отдаёт 401` падал с `java.net.HttpRetryException: cannot retry due to server authentication, in streaming mode`. Это streaming POST + WWW-Authenticate challenge.
   - **Решение, уже в коде:** тест переименован в `с неверными креденшелами admin replay отдаёт 401` и шлёт `withBasicAuth("nobody", "nope")`. Сервер ответит сразу 401 без challenge-ретрая.
   - **Альтернатива (если остался shaky):** заменить TestRestTemplate на `WebClient`/`HttpClient` напрямую.

3. **`KafkaConsumerConfig` сначала компилировался криво.** `DeadLetterPublishingRecoverer` ожидает `BiFunction<ConsumerRecord, Exception, TopicPartition>`, я возвращал `ProducerRecord`. **Исправлено в обоих сервисах**, но Maven `test-compile` после фикса я запустить не успел — пользователь прервал. Перед коммитом обязательно `./mvnw -am compile` в state-aggregation и anomaly-detection.

4. **`@Transactional` self-invocation.** Изначальный `truncateRoomStates()` был `@Transactional`, но вызывался из `replay()` в том же бине → AOP-обёртка не срабатывала, падало `TransactionRequiredException`.
   - **Решение, уже в коде:** перешёл на `TransactionTemplate.executeWithoutResult { ... }`. Хорошее решение — не зависит от self-invocation.

### Блок 3 (DLQ + ретраи)

1. **DLQ-топики `<topic>.DLT` не создаются автоматически.** В Spring Kafka `DeadLetterPublishingRecoverer` пишет в `sensor-raw-events.DLT` и `room-state-events.DLT`. Если auto-create отключён в Kafka — нужно завести их в `kafka-init` сервисе compose-файла.
   - **Что сделать:** добавить в `docker-compose.yml` → `kafka-init.command` создание `sensor-raw-events.DLT` и других `*.DLT`.

2. **`commonErrorHandler` подхватится автоматически или нет — не проверено.** Spring Boot 3.2 авто-конфиг должен подхватить `@Bean DefaultErrorHandler` и применить к `ConcurrentKafkaListenerContainerFactory`. Если не подхватит — нужно явно переопределять `kafkaListenerContainerFactory` через `ConcurrentKafkaListenerContainerFactoryConfigurer`.
   - **Как проверить:** в логах при отказной обработке должно быть `Retry attempt N for topic=...` (мой `setRetryListeners`).

3. **Нет тестов на DLQ-сценарий.** Нужен интеграционный тест: отправить событие, которое падает (например, через инжекцию исключения в `RoomStateAggregator` или подсунув кривой JSON), убедиться что:
   - Было ровно 3 retry в логах.
   - После 3-й ошибки сообщение появилось в `sensor-raw-events.DLT`.
   - Listener продолжил обрабатывать следующее сообщение (не залип).

4. **Нет сценариев на падение PG/Kafka.** В ADR-004 явно записано: «остановка PostgreSQL во время записи, остановка Kafka, kill state-aggregation в середине обработки, рестарт ingestion». Нужно сделать `docs/resilience-tests.md` с пошаговым проигрыванием через `docker compose stop <service>` и описанием поведения.

### Блок 4 (нагрузка)

1. **Не начато.** Нужно:
   - `loadtest/k6-constant.js` — постоянная нагрузка 200/500/1000 events/sec, 5 минут.
   - `loadtest/k6-burst.js` — burst 5000 events/sec, 30 секунд.
   - `loadtest/README.md` — как запустить (`docker run -i --rm grafana/k6 run - < k6-constant.js` или native).
   - `docs/loadtest-results.md` — пустой шаблон для результатов (p50/p95/p99, потери, lag).

### Документация (Блок 5 в моих заметках, не из ADR)

1. **README актуализация.** Добавить:
   - Раздел про `POST /admin/replay` (curl-пример с basic auth).
   - В таблицу «Статус по заданию»: «Юнит/интеграционные тесты — готово», «Отказоустойчивость DLQ — готово», «Нагрузочное тестирование — в работе».
2. **`docs/architecture.md`:** упомянуть DLQ-топики, admin-endpoint, basic auth.
3. **ADR-004 переоформить:** добавить раздел «Что фактически реализовано» с галочками по блокам.

## Известный мелкий мусор

- В корне могут остаться `target-replay-test.log`, `target-aggregation-test.log`, `target-anomaly-test.log` от прогонов в фоне. Их нужно удалить или добавить в `.gitignore` (`target-*.log`). На момент написания этого файла они либо уже удалены, либо лежат не закоммиченные.

## Минимальный план «сразу после возврата»

1. `./mvnw -pl state-aggregation-service,anomaly-detection-service -am test-compile` — убедиться, что фиксы `KafkaConsumerConfig` и `ReplayService` компилируются.
2. Прогнать `ReplayIntegrationTest` (~70 сек).
3. Если всё зелёное — закоммитить блоки 2+3 одним или двумя коммитами.
4. Добавить `*.DLT` топики в `kafka-init` compose.
5. Написать DLQ-тест.
6. Каталог `loadtest/` с k6.
7. Обновить README, architecture.md, ADR-004.
