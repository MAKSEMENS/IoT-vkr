# Состояние ADR-004

Снимок состояния на 2026-05-04 после второго заезда.

## Состояние блоков

| Блок | Статус | Что сделано |
|------|--------|-------------|
| 1. Тестирование | ✅ Готов | 17 юнит + 11 интеграционных тестов на Testcontainers (ingestion, aggregation, anomaly, replay, DLQ). |
| 2. Replay /admin/replay | ✅ Готов | Endpoint, basic auth, переписанная логика «recompute из БД». 3 интеграционных теста зелёные. |
| 3. DLQ + producer hardening + сценарии отказов | ✅ Готов | DLQ-конфиги в обоих consumer'ах, `*.DLT` топики в kafka-init, `DlqIntegrationTest` (3 retry → DLT), `docs/resilience-tests.md` со сценариями compose-stop. |
| 4. Нагрузочное тестирование | ⚠️ Инфраструктура готова | `loadtest/k6-constant.js`, `loadtest/k6-burst.js`, `loadtest/README.md`, шаблон `docs/loadtest-results.md`. **Прогоны не выполнены** — это требует поднятого compose-стенда и желательно отдельной машины для k6, чтобы клиент не конкурировал с серверами за CPU. |

## Что было закоммичено

- `d7d93ff` — юнит-тесты RuleEngine, RuleCondition, AnomalyRulesConfig, RoomStateAggregator (17 кейсов).
- `0cf7a87` — интеграционные тесты на Testcontainers ingestion / aggregation / anomaly (8 кейсов).
- `783eede` — Block 2 + 3 код (admin, security, DLQ-конфиг, producer hardening, docker-compose tweaks). Прим.: первая версия replay-логики была сломана, второй заезд её починил.
- (после второго заезда) — `ReplayIntegrationTest` рабочий, `DlqIntegrationTest` рабочий, k6-скрипты, обновлены `docs/architecture.md`, `docs/adr/ADR-004-implementation-roadmap.md`, README.

## Открытые задачи (что осталось сделать руками)

1. **Прогнать k6** на готовом стенде: 200 / 500 / 1000 rps × 5 мин и burst 5000 × 30 сек. Заполнить `docs/loadtest-results.md`. Это требует времени и стенда — оставлено пользователю.
2. **Прогнать ручные сценарии отказов** из `docs/resilience-tests.md` и зафиксировать наблюдаемое поведение. Опционально — добавить скриншот или короткий лог-кат.
3. По желанию — собрать Grafana-дашборд по метрикам `replay_*`, `spring_kafka_listener_seconds`, `kafka_producer_record_send_total` (см. ADR-005 и `loadtest/README.md`).
4. По желанию — продублировать `DlqIntegrationTest` для `anomaly-detection-service` (логика DLQ та же, поэтому покрытия в одном модуле может быть достаточно для ВКР).

## Известный мелкий мусор

- В корне репозитория есть `target-replay-test.log` и `target-dlq-test.log` от прогонов в фоне. Пользователь явно отказался удалять их.
