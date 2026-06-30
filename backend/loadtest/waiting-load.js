import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';

// Phase 1 — Baseline / Load (정상 부하 기준선)
// 목적: "평상시 예상 트래픽"에서의 기준 수치 확보.
//   이 단계의 p50/p95/p99/에러율을 "골든 베이스라인"으로 박제하고,
//   이후 모든 변경(코드/스케일/인프라)의 효과는 이 수치와의 차이로 판단한다.
//
// 패턴: 50 VU → 100 VU 램프업, plateau 유지, 램프다운.
// 통과 기준: http_req_failed < 5%, p95 < 2000ms
//
// ─────────────────────────────────────────────────────────────────────────────
// ⚠️ PROD 안전 설계 (이 스크립트는 운영 환경에서 돌려도 안전하도록 자체 완결형이다)
//   - setup()이 "전용 테스트 점주 + 식당 + 오늘 웨이팅 세션"을 직접 만든다.
//     → 실제 운영 식당(예: ID 1)에 가짜 웨이팅을 꽂지 않는다. 실데이터/실점주 무영향.
//   - 모든 부하는 setup이 만든 전용 restaurantId 에만 들어간다.
//
//   정리(cleanup) 전략 — TEST_SECRET_TOKEN 유무로 갈린다:
//   (A) TEST_SECRET_TOKEN 설정 시: setup 시작과 teardown 양쪽에서 /api/global/test/reset 호출.
//       → 웨이팅 등 비보호 테이블 TRUNCATE + Redis 'waiting:*' / 'waitingPolicy:*' 키 삭제.
//       ※ 단, 서버 보호 목록(users / restaurant / restaurant_hours / restaurant_owners /
//          slots / favorite / flyway_schema_history)은 reset 해도 보존된다 — "전체 wipe"가
//          아니라 "웨이팅·큐 중심 초기화"다. orphan 유저/식당 row 는 남는다(게이지/큐엔 무해).
//       ※ 이 토큰은 운영에 기본 미설정 → 미설정 환경에선 reset 이 403 으로 거부되어 (B)로 폴백.
//   (B) 토큰 미설정 시: teardown 이 전용 세션만 close 해 'nowait_waiting_active_count' 게이지를
//       0 으로 되돌리고 Redis 활성세션/카운터를 정리한다(전체 DB는 건드리지 않음).
//
//   ※ 굳이 "기존 식당"에 부하를 주려면 RESTAURANT_ID 를 명시적으로 넘기면 setup의
//      식당/세션 생성을 건너뛰고 그 식당을 쓴다. 단 이 경우 prod에서는 실데이터 오염
//      위험이 있으니 반드시 "테스트 전용 식당"의 ID여야 한다 (기본값으로 '1' 을 두지 않는다).
// ─────────────────────────────────────────────────────────────────────────────
//
// 사용 예 (권장 — 자체 프로비저닝 + 전후 reset):
//   BASE_URL=https://api.nowait.singleuser.cloud TEST_SECRET_TOKEN=<토큰> k6 run waiting-load.js
//
// 사용 예 (reset 없이 전용 세션 close 정리만):
//   BASE_URL=https://api.nowait.singleuser.cloud k6 run waiting-load.js
//
// 사용 예 (이미 만들어 둔 전용 식당 지정):
//   BASE_URL=https://api.nowait.singleuser.cloud RESTAURANT_ID=<전용식당ID> k6 run waiting-load.js
//
// 관찰 포인트: 안정 상태에서의 p95/p99, HikariCP 사용량, Redis hit rate, HPA replica 수

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const OVERRIDE_RESTAURANT_ID = __ENV.RESTAURANT_ID || ''; // 비우면 setup이 전용 식당을 생성
const THINK = Number(__ENV.THINK || 1);
const PASSWORD = 'k6-test-password-1234';
// 설정 시 setup/teardown 에서 DB+Redis(웨이팅·큐 중심) 초기화를 수행한다. 미설정이면 reset 생략.
const TEST_TOKEN = __ENV.TEST_SECRET_TOKEN || '';

// 세션 1개의 최대 대기 인원(서버 상한 999). 부하가 이 수를 넘기면 이후 등록은
// WAITING_COUNT_EXCEEDED(409)로 거절되는데, 이는 정상 비즈니스 결과(4xx)이며
// 등록 경로(Redis Lua)는 그대로 실행되므로 부하 측정에는 문제없다.
const MAX_WAITING_COUNT = Number(__ENV.MAX_WAITING_COUNT || 999);

http.setResponseCallback(http.expectedStatuses(200, 201, { min: 400, max: 499 }));

const regDur = new Trend('waiting_register_duration', true);
const statusDur = new Trend('waiting_status_duration', true);
const reg5xx = new Counter('waiting_register_5xx');

export const options = {
  scenarios: {
    baseline: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '1m', target: 50 },    // 워밍업
        { duration: '3m', target: 50 },    // 50 VU plateau
        { duration: '1m', target: 100 },   // 정상 피크 진입
        { duration: '5m', target: 100 },   // 100 VU plateau — 이 구간이 베이스라인 수치
        { duration: '1m', target: 0 },     // 램프다운
      ],
      gracefulStop: '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    'http_req_duration{name:waiting_register}': ['p(95)<2000'],
    'http_req_duration{name:waiting_status}': ['p(95)<1500'],
  },
};

function jsonHeaders(token) {
  const h = { 'Content-Type': 'application/json' };
  if (token) h['Authorization'] = `Bearer ${token}`;
  return h;
}

// DB(비보호 테이블) + Redis(waiting:* / waitingPolicy:*) 초기화. TEST_SECRET_TOKEN 필요.
// 토큰 미설정/오류 환경에선 서버가 403 으로 거부하므로 안전하게 no-op 가 된다.
function resetData(phase) {
  if (!TEST_TOKEN) return;
  const res = http.post(`${BASE_URL}/api/global/test/reset`, null, {
    headers: { 'X-Test-Token': TEST_TOKEN },
  });
  check(res, { [`[${phase}] reset 200 또는 403(토큰 미설정)`]: (r) => r.status === 200 || r.status === 403 });
  console.log(`[${phase}] reset 결과: ${res.status} ${res.status === 200 ? '(DB+Redis 초기화 완료)' : '(거부/미설정 — 스킵)'}`);
}

// setup()은 전체 테스트에서 1회만 실행된다 — 전용 점주/식당/웨이팅 세션을 준비한다.
// RESTAURANT_ID 가 명시되면 식당/세션 프로비저닝을 건너뛰고 그 식당을 사용한다.
export function setup() {
  // 클린 베이스라인 — 직전 테스트 잔여 웨이팅/큐 데이터를 비우고 시작 (토큰 있을 때만).
  resetData('setup');

  if (OVERRIDE_RESTAURANT_ID) {
    console.log(`[setup] RESTAURANT_ID=${OVERRIDE_RESTAURANT_ID} 지정됨 — 프로비저닝 생략. (전용 테스트 식당인지 반드시 확인할 것)`);
    return { restaurantId: OVERRIDE_RESTAURANT_ID, ownerToken: null, sessionId: null };
  }

  const suffix = Date.now();
  const ownerEmail = `k6-load-owner-${suffix}@example.com`;

  const signupRes = http.post(
    `${BASE_URL}/api/v1/auth/signup/owner`,
    JSON.stringify({ email: ownerEmail, password: PASSWORD, name: `k6 load owner ${suffix}` }),
    { headers: jsonHeaders() }
  );
  check(signupRes, { '[setup] 점주 가입 201': (r) => r.status === 201 });

  const loginRes = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ email: ownerEmail, password: PASSWORD }),
    { headers: jsonHeaders() }
  );
  check(loginRes, { '[setup] 점주 로그인 200': (r) => r.status === 200 });
  const ownerToken = loginRes.json('accessToken');
  if (!ownerToken) {
    throw new Error('[setup] 점주 토큰 발급 실패 — 부하 테스트 중단');
  }

  const registerRes = http.post(
    `${BASE_URL}/api/v1/restaurants`,
    JSON.stringify({
      name: `k6 부하테스트 식당 ${suffix}`,
      category: 'KOREAN',
      address: '서울특별시 k6부하테스트구',
      phoneNumber: '02-0000-0000',
      description: 'k6 Phase1 부하테스트용 전용 식당 (운영 데이터 아님)',
      mainMenuName: '테스트메뉴',
      parkingAvailable: 'N',
      wifiAvailable: 'N',
      multilingualMenuAvailable: 'N',
    }),
    { headers: jsonHeaders(ownerToken) }
  );
  check(registerRes, { '[setup] 식당 등록 201': (r) => r.status === 201 });
  const restaurantId = registerRes.status === 201 ? registerRes.json() : null;
  if (!restaurantId) {
    throw new Error('[setup] 전용 식당 생성 실패 — 부하 테스트 중단');
  }

  // 기본 상태 CLOSED → OPEN 으로 전환해야 공개/웨이팅에 노출된다.
  const openRes = http.patch(
    `${BASE_URL}/api/v1/restaurants/${restaurantId}/status`,
    JSON.stringify({ status: 'OPEN' }),
    { headers: jsonHeaders(ownerToken) }
  );
  check(openRes, { '[setup] 식당 OPEN 전환 200': (r) => r.status === 200 });

  // 오늘 웨이팅 세션 오픈 — 이게 있어야 등록이 201 이 되고 Redis 큐가 움직인다.
  const sessionRes = http.post(
    `${BASE_URL}/api/v1/owners/restaurants/${restaurantId}/waiting-sessions`,
    JSON.stringify({ maxWaitingCount: MAX_WAITING_COUNT }),
    { headers: jsonHeaders(ownerToken) }
  );
  check(sessionRes, { '[setup] 웨이팅 세션 오픈 201': (r) => r.status === 201 });
  const sessionId = sessionRes.status === 201 ? sessionRes.json('sessionId') : null;

  console.log(`[setup] 전용 식당=${restaurantId}, 세션=${sessionId}, maxWaitingCount=${MAX_WAITING_COUNT}`);
  return { restaurantId, ownerToken, sessionId };
}

export default function (data) {
  if (!data || !data.restaurantId) return; // setup 실패 시 안전하게 종료

  const suffix = `${Date.now()}-${__VU}-${__ITER}`;
  const email = `k6-load-${suffix}@example.com`;

  http.post(
    `${BASE_URL}/api/v1/auth/signup`,
    JSON.stringify({ email, password: PASSWORD, name: `k6 load ${suffix}` }),
    { headers: jsonHeaders(), tags: { name: 'signup' } }
  );

  const loginRes = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ email, password: PASSWORD }),
    { headers: jsonHeaders(), tags: { name: 'login' } }
  );
  const token = loginRes.json('accessToken');
  if (!token) return;

  const partySize = 1 + Math.floor(Math.random() * 4);
  const regRes = http.post(
    `${BASE_URL}/api/v1/restaurants/${data.restaurantId}/waitings`,
    JSON.stringify({ partySize }),
    { headers: jsonHeaders(token), tags: { name: 'waiting_register' } }
  );
  regDur.add(regRes.timings.duration);
  if (regRes.status >= 500) reg5xx.add(1);
  check(regRes, {
    '등록 201 또는 4xx': (r) => r.status === 201 || (r.status >= 400 && r.status < 500),
  });

  // 상태조회는 등록 성공(201) 후 받은 waitingToken 으로만 수행한다.
  // /me(전체 목록 + Redis KEYS 스캔) 대신 토큰 단위 조회 → DB·KEYS 부하 없음.
  const waitingToken = regRes.status === 201 ? regRes.json('waitingToken') : null;
  if (!waitingToken) return;

  for (let i = 0; i < 3; i++) {
    sleep(THINK);
    const sRes = http.get(`${BASE_URL}/api/v1/waitings/${waitingToken}`, {
      headers: jsonHeaders(token),
      tags: { name: 'waiting_status' },
    });
    statusDur.add(sRes.timings.duration);
    check(sRes, { '상태조회 200': (r) => r.status === 200 });
  }
}

// teardown()은 전체 테스트 종료 후 1회 실행 — 테스트가 만든 데이터를 정리한다.
//   (A) TEST_SECRET_TOKEN 있으면: reset 으로 웨이팅 데이터 + Redis 큐/세션 키를 일괄 초기화.
//   (B) 토큰 없으면: 전용 세션만 close 해 활성 대기/게이지를 0 으로 되돌린다.
export function teardown(data) {
  if (TEST_TOKEN) {
    resetData('teardown');
    return;
  }
  if (!data || !data.sessionId || !data.ownerToken) {
    console.log('[teardown] 토큰 없음 + 정리할 전용 세션 없음 (RESTAURANT_ID 지정 모드이거나 setup 미완료) — 스킵');
    return;
  }
  const closeRes = http.patch(
    `${BASE_URL}/api/v1/owners/waiting-sessions/${data.sessionId}/close`,
    null,
    { headers: jsonHeaders(data.ownerToken) }
  );
  check(closeRes, { '[teardown] 전용 세션 마감 200': (r) => r.status === 200 });
  console.log(`[teardown] 전용 세션 ${data.sessionId} 마감 결과: ${closeRes.status}`);
}
