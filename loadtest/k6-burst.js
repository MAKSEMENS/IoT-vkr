// Burst-нагрузка: до 5000 events/sec на 30 секунд.
// Цель — проверить как ведёт себя система в пике и как разгружается после.
//
// k6 run -e BASE_URL=http://localhost:8081 k6-burst.js

import http from 'k6/http';
import { check } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';

const errorRate = new Rate('ingest_error_rate');
const latency = new Trend('ingest_latency_ms', true);

export const options = {
  scenarios: {
    warmup: {
      executor: 'constant-arrival-rate',
      rate: 200,
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 50,
      maxVUs: 100,
      startTime: '0s',
      exec: 'send',
    },
    burst: {
      executor: 'constant-arrival-rate',
      rate: 5000,
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 500,
      maxVUs: 2000,
      startTime: '30s',
      exec: 'send',
    },
    cooldown: {
      executor: 'constant-arrival-rate',
      rate: 200,
      timeUnit: '1s',
      duration: '60s',
      preAllocatedVUs: 50,
      maxVUs: 100,
      startTime: '60s',
      exec: 'send',
    },
  },
  thresholds: {
    'ingest_error_rate{phase:warmup}': ['rate<0.01'],
    'ingest_error_rate{phase:cooldown}': ['rate<0.05'],
    http_req_failed: ['rate<0.30'],
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

export function send() {
  const res = http.post(`${BASE_URL}/events`, payload(), {
    headers: { 'Content-Type': 'application/json' },
    tags: { endpoint: 'ingest' },
  });
  const ok = check(res, {
    'status is 202': (r) => r.status === 202,
  });
  errorRate.add(!ok);
  latency.add(res.timings.duration);
}
