// Постоянная нагрузка на ingestion: 200/500/1000 events/sec, 5 минут.
// Запуск: см. loadtest/README.md
//
// k6 run --vus 50 --duration 5m -e RPS=200 -e BASE_URL=http://localhost:8081 k6-constant.js

import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const RPS = Number(__ENV.RPS || 200);
const DURATION = __ENV.DURATION || '5m';
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';

const errorRate = new Rate('ingest_error_rate');
const ingested = new Counter('ingest_success_total');
const latency = new Trend('ingest_latency_ms', true);

export const options = {
  scenarios: {
    constant_rps: {
      executor: 'constant-arrival-rate',
      rate: RPS,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: Math.max(50, Math.ceil(RPS / 4)),
      maxVUs: Math.max(200, Math.ceil(RPS)),
    },
  },
  thresholds: {
    ingest_error_rate: ['rate<0.01'],
    ingest_latency_ms: ['p(95)<500', 'p(99)<1500'],
    http_req_failed: ['rate<0.01'],
  },
};

const SENSOR_TYPES = ['TEMPERATURE', 'HUMIDITY', 'CO2', 'SMOKE', 'MOTION', 'LIGHT'];
const ROOMS = 100;

function payload() {
  const room = `ROOM-${Math.floor(Math.random() * ROOMS)}`;
  const type = SENSOR_TYPES[Math.floor(Math.random() * SENSOR_TYPES.length)];
  return JSON.stringify({
    roomId: room,
    sensorId: `${room}-${type.toLowerCase()}`,
    sensorType: type,
    value: Math.random() * 100,
  });
}

export default function () {
  const res = http.post(`${BASE_URL}/events`, payload(), {
    headers: { 'Content-Type': 'application/json' },
    tags: { endpoint: 'ingest' },
  });
  const ok = check(res, {
    'status is 202': (r) => r.status === 202,
  });
  errorRate.add(!ok);
  if (ok) ingested.add(1);
  latency.add(res.timings.duration);
}
