import http from 'k6/http';
import { check, sleep } from 'k6';

// Phase 1 본 부하테스트 (지속 버전) — waiting-load.js와 동일한 시나리오지만 peak VU를
// 길게 유지한다. KubeHpaMaxedOut(15분 지속)과 우리가 만든 NowaitHighMemoryUsage/
// NowaitHighCpuUsage(5분 지속) 알림이 실제로 Slack까지 발동하는 걸 보기 위한 용도.
//
// ─────────────────────────────────────────────────────────────────────────────
// ⚠️ 환경별 부하 산정 — dev와 prod 캐파가 크게 다르다 (반드시 env로 조정)
//   dev : api HPA max 3, mem limit 1Gi, t3.large×2  → 100 VU·16m 면 HPA maxed/알림 발동
//   prod: api HPA max 10, mem limit 2Gi, m6i.large×3 → 100 VU론 순항(알림 안 뜸).
//         api 10개를 채우고 15분 유지하려면 수백 VU 필요 → 아래 env로 키워서 실행한다.
//   ※ 알림이 뜨려면 peak 도달 후 HOLD 가 최소 15분 이상이어야 KubeHpaMaxedOut(15m) 창을 채운다.
//   ※ CPU/Mem 80%-of-limit 알림은 HPA가 분산해서 띄우기 어렵다 — max 채우고도 넘치는 부하 필요.
//      현실적 1차 타깃은 KubeHpaMaxedOut. (대안: NowaitQueueBacklogHigh = 등록 burst)
//
// 조정 env:
//   VUS       peak VU            (기본 100 / prod 권장 600)
//   RAMP      peak까지 램프업     (기본 1m  / prod 권장 3m)
//   HOLD      peak 유지 시간       (기본 16m / prod 권장 22m — max 도달 후 15m 창 확보)
//   RAMPDOWN  하강 시간           (기본 1m)
//
// ─────────────────────────────────────────────────────────────────────────────
// ⚠️ PROD 안전 설계 (waiting-load.js 와 동일한 자체 완결형 구조)
//   - setup()이 "전용 테스트 점주 + 식당 + 오늘 웨이팅 세션"을 직접 만든다.
//     → 실제 운영 식당(예: ID 1)에 가짜 웨이팅을 꽂지 않는다. 실데이터/실점주 무영향.
//   - 모든 부하는 setup이 만든 전용 restaurantId 에만 들어간다.
//
//   정리(cleanup): teardown 이 전용 세션만 close 해 활성 대기/게이지를 0 으로 되돌린다.
//     → DB/Redis 일괄 초기화(reset)는 이 스크립트에서 하지 않는다(팀 공용 정리 스크립트 별도).
//
//   ※ 굳이 "기존 식당"에 부하를 주려면 RESTAURANT_ID 를 명시적으로 넘기면 식당/세션 생성을
//      건너뛰고 그 식당을 쓴다. 단 prod에서는 실데이터 오염 위험이 있으니 반드시 "테스트
//      전용 식당"의 ID여야 한다 (기본값으로 '1' 을 두지 않는다).
//   ※ prod를 api 10/worker 8 + Karpenter 노드까지 밀면 실비용+실사용자 영향 — off-hours +
//      팀 통지 + 테스트 중 backend 무배포 필수.
// ─────────────────────────────────────────────────────────────────────────────
//
// 사용 예 (dev — 기본값):
//   BASE_URL=https://dev.api... k6 run waiting-load-sustained.js
// 사용 예 (prod — HPA max 도달·알림 발동):
//   BASE_URL=https://api.nowait.singleuser.cloud VUS=600 RAMP=3m HOLD=22m k6 run waiting-load-sustained.js

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const OVERRIDE_RESTAURANT_ID = __ENV.RESTAURANT_ID || ''; // 비우면 setup이 전용 식당을 생성
const PASSWORD = 'k6-test-password-1234';
// 세션 1개의 최대 대기 인원(서버 상한 999). 초과분 등록은 409(정상 4xx)로 거절되지만 등록
// 경로(Redis Lua)와 폴링/인증 부하는 그대로 유지되므로 지속 부하·알림 발동엔 문제없다.
const MAX_WAITING_COUNT = Number(__ENV.MAX_WAITING_COUNT || 999);

// 환경별 부하 프로파일 (dev 기본 / prod 는 env override)
const VUS = Number(__ENV.VUS || 100);
const RAMP = __ENV.RAMP || '1m';
const HOLD = __ENV.HOLD || '16m';
const RAMPDOWN = __ENV.RAMPDOWN || '1m';

http.setResponseCallback(http.expectedStatuses(200, 201, { min: 400, max: 499 }));

export const options = {
  scenarios: {
    waiting_load_sustained: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: RAMP, target: VUS },     // peak 까지 램프업
        { duration: HOLD, target: VUS },     // peak 유지 — 이 구간이 알림(HpaMaxed 15m) 창
        { duration: RAMPDOWN, target: 0 },   // 하강
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

// setup()은 전체 테스트에서 1회만 실행된다 — 전용 점주/식당/웨이팅 세션을 준비한다.
// RESTAURANT_ID 가 명시되면 식당/세션 프로비저닝을 건너뛰고 그 식당을 사용한다.
export function setup() {
  if (OVERRIDE_RESTAURANT_ID) {
    console.log(`[setup] RESTAURANT_ID=${OVERRIDE_RESTAURANT_ID} 지정됨 — 프로비저닝 생략. (전용 테스트 식당인지 반드시 확인할 것)`);
    return { restaurantId: OVERRIDE_RESTAURANT_ID, ownerToken: null, sessionId: null };
  }

  const suffix = Date.now();
  const ownerEmail = `k6-sustained-owner-${suffix}@example.com`;

  const signupRes = http.post(
    `${BASE_URL}/api/v1/auth/signup/owner`,
    JSON.stringify({ email: ownerEmail, password: PASSWORD, name: `k6 sustained owner ${suffix}` }),
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
      name: `k6 지속부하 식당 ${suffix}`,
      category: 'KOREAN',
      address: '서울특별시 k6지속부하구',
      phoneNumber: '02-0000-0000',
      description: 'k6 Phase1 지속 부하테스트용 전용 식당 (운영 데이터 아님)',
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

  const signupRes = http.post(
    `${BASE_URL}/api/v1/auth/signup`,
    JSON.stringify({ email, password: PASSWORD, name: `k6 load ${suffix}` }),
    { headers: jsonHeaders() }
  );
  check(signupRes, { '유저 가입 201': (r) => r.status === 201 });

  const loginRes = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ email, password: PASSWORD }),
    { headers: jsonHeaders() }
  );
  check(loginRes, { '유저 로그인 200': (r) => r.status === 200 });
  const token = loginRes.json('accessToken');
  if (!token) return;

  const partySize = 1 + Math.floor(Math.random() * 4);
  const registerRes = http.post(
    `${BASE_URL}/api/v1/restaurants/${data.restaurantId}/waitings`,
    JSON.stringify({ partySize }),
    { headers: jsonHeaders(token) }
  );
  check(registerRes, { '웨이팅 등록 201 또는 세션없음/중복 4xx': (r) => r.status === 201 || (r.status >= 400 && r.status < 500) });

  // 등록 성공 시에만 토큰 단위 상태조회 polling (/me 의 Redis KEYS 스캔 회피).
  const waitingToken = registerRes.status === 201 ? registerRes.json('waitingToken') : null;
  if (!waitingToken) return;

  for (let i = 0; i < 3; i++) {
    sleep(2);
    const statusRes = http.get(`${BASE_URL}/api/v1/waitings/${waitingToken}`, { headers: jsonHeaders(token) });
    check(statusRes, { '웨이팅 상태 조회 200': (r) => r.status === 200 });
  }
}

// teardown()은 전체 테스트 종료 후 1회 실행 — 전용 세션을 close 해 활성 대기/게이지를 0 으로
// 되돌린다. (DB/Redis 일괄 초기화는 이 스크립트에서 하지 않는다 — 팀 공용 정리 스크립트 별도.)
export function teardown(data) {
  if (!data || !data.sessionId || !data.ownerToken) {
    console.log('[teardown] 정리할 전용 세션 없음 (RESTAURANT_ID 지정 모드이거나 setup 미완료) — 스킵');
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
