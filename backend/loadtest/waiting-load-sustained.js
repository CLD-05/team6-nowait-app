import http from 'k6/http';
import { check, sleep } from 'k6';

// Phase 1 본 부하테스트 (지속 버전) — waiting-load.js와 동일한 시나리오지만
// 100 VU를 16분간 유지한다. KubeHpaMaxedOut(15분 지속)과 우리가 만든
// NowaitHighMemoryUsage/NowaitHighCpuUsage(5분 지속) 알림이 실제로
// Slack까지 발동하는 걸 보기 위한 용도. 총 약 18분.

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const RESTAURANT_ID = __ENV.RESTAURANT_ID || '1';

http.setResponseCallback(http.expectedStatuses(200, 201, { min: 400, max: 499 }));

export const options = {
  scenarios: {
    waiting_load_sustained: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '1m', target: 100 },
        { duration: '16m', target: 100 },
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

  for (let i = 0; i < 3; i++) {
    sleep(2);
    const statusRes = http.get(`${BASE_URL}/api/v1/waitings/me`, { headers: jsonHeaders(token) });
    check(statusRes, { '웨이팅 상태 조회 200': (r) => r.status === 200 });
  }
}
