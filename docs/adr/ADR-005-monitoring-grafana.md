# ADR-005: Внедрение мониторинга на Grafana

**Статус:** Принято
**Дата:** 2026-05-03

## Контекст

В задании Grafana заявлена как готовый инструмент визуализации, без обязательной разработки дашбордов. На стенде она уже поднимается через Docker Compose, но без datasource и без панелей — ценности от неё пока ноль. Для защиты полезно показать живую картинку, а в пояснительную записку положить скриншоты под раздел тестирования. Этот ADR описывает, как довести мониторинг до рабочего состояния, не выходя за scope ВКР.

## Решение

Двухконтурный мониторинг.

- **Технический контур** (Prometheus). Каждый Spring-сервис уже отдаёт `/actuator/prometheus`. Поднимаем рядом контейнер `prom/prometheus`, который скрейпит четыре сервиса, и подключаем его как datasource в Grafana. Этот контур видит JVM, HTTP-метрики, состояние Kafka-клиентов.
- **Бизнес-контур** (PostgreSQL). Grafana читает напрямую из БД через JDBC datasource: таблицы `room_states`, `sensor_events`, `alerts`. Этот контур видит «что происходит в помещениях», не зависит от метрик и сохраняется при рестарте сервисов.

Оба datasource — provisioned: YAML-файлы кладутся в репо и монтируются в контейнер Grafana, чтобы не настраивать вручную после каждого `docker compose down -v`.

### Что добавляется в репозиторий

```
monitoring/
├── prometheus/
│   └── prometheus.yml             scrape config: 4 сервиса
└── grafana/
    └── provisioning/
        ├── datasources/
        │   └── datasources.yml    Prometheus + PostgreSQL
        └── dashboards/
            ├── dashboards.yml     loader
            ├── service-health.json
            ├── pipeline.json
            └── rooms.json
```

Изменения в `docker-compose.yml`:
- сервис `prometheus` (image `prom/prometheus:v2.51.0`, маунт конфига, порт 9090)
- сервис `grafana` получает маунт `./monitoring/grafana/provisioning` и переменную `GF_PATHS_PROVISIONING`
- метрики ingestion/aggregation/anomaly/query становятся видны контейнеру Prometheus по DNS-имени контейнера (`event-ingestion:8080/actuator/prometheus` и т.д.)

### Дашборды

Минимально достаточный набор — три штуки.

1. **Service health.** На каждый из четырёх сервисов: uptime, JVM heap, GC pauses, HTTP 5xx rate, Kafka consumer lag (через `kafka_consumer_fetch_manager_records_lag_max`). Источник — Prometheus.
2. **Pipeline throughput.** Events/sec на входе (rate POST `/events`), events/sec в `sensor-raw-events`, lag по партициям, alerts/sec. Видна end-to-end пропускная способность и точки бутылочного горла.
3. **Rooms & alerts.** Бизнес-картина из PostgreSQL: текущее состояние комнат (последние значения `room_states`), временной ряд по типам датчиков из `sensor_events` за последний час, лента активных алертов (`resolved_at IS NULL`).

### Алертинг внутри Grafana

В этом скоупе не делаем. Источник истины для алертов — таблица `alerts` (её наполняет наш `anomaly-detection-service`), Grafana только показывает. Дублировать пороги в правилах Grafana бессмысленно — это размывает источник истины и противоречит задаче «правила в YAML без перекомпиляции».

## Что вне scope

- HA-мониторинг, federated Prometheus, долгосрочное хранилище (Thanos/Mimir).
- Distributed tracing (Tempo/Jaeger) — отдельная большая тема, не нужна для защиты.
- Логи в Loki — `docker compose logs` достаточно для демо.
- Auth/SSO в Grafana — оставляем дефолтный admin/admin, стенд локальный.

## Последствия

- Появляется ещё один контейнер (Prometheus) и три файла-дашборда — операционная сложность стенда растёт незначительно.
- Все сервисы должны выставлять `/actuator/prometheus` (уже выставляют).
- В пояснительную записку идут скриншоты Service health и Rooms & alerts — конкретный визуальный артефакт результата работы.
- Если в блоке 4 ADR-004 (нагрузочное тестирование) метрики снимаются через Prometheus — этот мониторинг становится прямой инфраструктурой для самих тестов.
