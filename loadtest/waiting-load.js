import http from 'k6/http';
import { check, sleep } from 'k6';

// Phase 1 본 부하테스트 — 웨이팅 등록/상태조회 중심.
// 목표: 과도한 트래픽을 만드는 게 아니라, KEDA(worker: Redis pending 큐 트리거)와
// 모니터링/알림 파이프라인이 실제 트래픽 증가에 어떻게 반응하는지 관찰하는 것.
// 최대 100 VU, 약 9분.

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const RESTAURANT_ID = __ENV.RESTAURANT_ID || '1';

http.setResponseCallback(http.expectedStatuses(200, 201, { min: 400, max: 499 }));

export const options = {
  scenarios: {
    waiting_load: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '1m', target: 50 },
        { duration: '3m', target: 50 },
        { duration: '1m', target: 100 },
        { duration: '3m', target: 100 },
        { duration: '1m', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<2000'],
  },
};

function jsonHeaders(token) {
  const h = { 'Content-Type': 'application/json' };
  if (token) h['Authorization'] = `Bearer ${token}`;
  return h;
}

export default function () {
  const suffix = `${Date.now()}-${__VU}-${__ITER}`;
  const email = `k6-load-${suffix}@example.com`;
  const password = 'k6-test-password-1234';

  const signupRes = http.post(
    `${BASE_URL}/api/v1/auth/signup`,
    JSON.stringify({ email, password, name: `k6 load ${suffix}` }),
    { headers: jsonHeaders() }
  );
  check(signupRes, { '유저 가입 201': (r) => r.status === 201 });

  const loginRes = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ email, password }),
    { headers: jsonHeaders() }
  );
  check(loginRes, { '유저 로그인 200': (r) => r.status === 200 });
  const token = loginRes.json('accessToken');

  const partySize = 1 + Math.floor(Math.random() * 4);
  const registerRes = http.post(
    `${BASE_URL}/api/v1/restaurants/${RESTAURANT_ID}/waitings`,
    JSON.stringify({ partySize }),
    { headers: jsonHeaders(token) }
  );
  check(registerRes, { '웨이팅 등록 201 또는 세션없음/중복 4xx': (r) => r.status === 201 || (r.status >= 400 && r.status < 500) });

  // WaitingStatusPage의 실제 폴링 패턴(짧은 간격 반복 조회)을 모사
  for (let i = 0; i < 3; i++) {
    sleep(2);
    const statusRes = http.get(`${BASE_URL}/api/v1/waitings/me`, { headers: jsonHeaders(token) });
    check(statusRes, { '웨이팅 상태 조회 200': (r) => r.status === 200 });
  }
}
