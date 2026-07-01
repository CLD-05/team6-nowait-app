import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';

// 예약(Reservation) 부하테스트 — 웨이팅 waiting-load.js 와 동일한 자체 완결형 구조.
//
// 흐름: 예약 생성(POST /reservations) → 토큰 단위 상태조회(GET /reservations/{token})
//       → 취소(PATCH /reservations/{token}/cancel)
//
// ─────────────────────────────────────────────────────────────────────────────
// ⚠️ PROD 안전 설계 (운영에서 돌려도 안전하도록 전용 점주/식당/슬롯만 사용)
//   - setup()이 "전용 테스트 점주 + 식당 + 영업시간 + 슬롯"을 직접 만든다.
//     → 실제 운영 식당/슬롯에 가짜 예약을 꽂지 않는다.
//   - 모든 부하는 setup 이 만든 전용 restaurantId/slotId 에만 들어간다.
//   - TEST_SECRET_TOKEN 설정 시 setup/teardown 에서 /api/global/test/reset 로 예약·큐 정리.
//
// 사용 예:
//   BASE_URL=https://api.nowait.singleuser.cloud k6 run reservation-load.js
//   BASE_URL=... TEST_SECRET_TOKEN=<토큰> VUS=200 k6 run reservation-load.js
// ─────────────────────────────────────────────────────────────────────────────

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PASSWORD = 'k6-test-password-1234';
const TEST_TOKEN = __ENV.TEST_SECRET_TOKEN || '';
const THINK = Number(__ENV.THINK || 1);
const DWELL_POLLS = Number(__ENV.DWELL_POLLS || 3);

// 슬롯 캐파/시간. 예약은 슬롯 정원을 차감하므로 totalCount 를 크게 잡아 정원 고갈을 피한다.
// 취소가 정원을 되돌리지만, 지속 부하 여유를 위해 기본값을 넉넉히 둔다.
const SLOT_TOTAL_COUNT = Number(__ENV.SLOT_TOTAL_COUNT || 99999);
const NUM_SLOTS = Number(__ENV.NUM_SLOTS || 5);
const SLOT_TIME = __ENV.SLOT_TIME || '18:00:00';
const OPEN_TIME = __ENV.OPEN_TIME || '09:00:00';
const CLOSE_TIME = __ENV.CLOSE_TIME || '23:59:00';
const MAX_HEADCOUNT = Number(__ENV.MAX_HEADCOUNT || 100);

// 부하 프로파일 (dev 기본 / prod 는 env override)
const VUS = Number(__ENV.VUS || 100);
const RAMP = __ENV.RAMP || '1m';
const HOLD = __ENV.HOLD || '5m';
const RAMPDOWN = __ENV.RAMPDOWN || '1m';

// 미래 날짜(기본: 내일) — 과거 시각 슬롯 예약 거절을 피한다. SLOT_DATE 로 고정 가능.
const SLOT_DATE = __ENV.SLOT_DATE || new Date(Date.now() + 86400000).toISOString().slice(0, 10);

// 200/201/4xx 는 실패로 보지 않음(4xx 는 비즈니스 거절일 수 있음). 5xx 만 서버 장애.
http.setResponseCallback(http.expectedStatuses(200, 201, { min: 400, max: 499 }));

const createDur = new Trend('reservation_create_duration', true);
const statusDur = new Trend('reservation_status_duration', true);
const cancelDur = new Trend('reservation_cancel_duration', true);

// 생성 status 분리
const create201 = new Counter('reservation_create_201');
const create4xx = new Counter('reservation_create_4xx');
const create5xx = new Counter('reservation_create_5xx');
const create400 = new Counter('reservation_create_400');
const create403 = new Counter('reservation_create_403');
const create404 = new Counter('reservation_create_404');
const create409 = new Counter('reservation_create_409');
const createTokenMissing = new Counter('reservation_create_token_missing');

// 상태조회 status 분리
const status200 = new Counter('reservation_status_200');
const status4xx = new Counter('reservation_status_4xx');
const status5xx = new Counter('reservation_status_5xx');

// 취소 status 분리
const cancelCalled = new Counter('reservation_cancel_called');
const cancel200 = new Counter('reservation_cancel_200');
const cancel4xx = new Counter('reservation_cancel_4xx');
const cancel5xx = new Counter('reservation_cancel_5xx');

const CREATE_BY_CODE = { 400: create400, 403: create403, 404: create404, 409: create409 };

function record(status, c2xx, agg4xx, c5xx, byCode) {
  if (status >= 200 && status < 300) c2xx.add(1);
  else if (status >= 400 && status < 500) {
    agg4xx.add(1);
    if (byCode) { const c = byCode[status]; if (c) c.add(1); }
  } else if (status >= 500) c5xx.add(1);
}

export const options = {
  setupTimeout: '300s',
  scenarios: {
    reservation_load: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: RAMP, target: VUS },
        { duration: HOLD, target: VUS },
        { duration: RAMPDOWN, target: 0 },
      ],
      gracefulStop: '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    'http_req_duration{name:reservation_create}': ['p(95)<3000'],
    'http_req_duration{name:reservation_status}': ['p(95)<2000'],
    'http_req_duration{name:reservation_cancel}': ['p(95)<3000'],
    // 정상 시나리오가 수행됐는지 보장 (false positive 방지)
    reservation_create_201: ['count>0'],
    reservation_cancel_called: ['count>0'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

function jsonHeaders(token) {
  const h = { 'Content-Type': 'application/json' };
  if (token) h.Authorization = `Bearer ${token}`;
  return h;
}

function safeJson(res, path) {
  try { return res.json(path); } catch (e) { return null; }
}

function extractId(res, paths) {
  try {
    const body = res.json();
    if (typeof body === 'number' || typeof body === 'string') return String(body);
  } catch (e) { /* ignore */ }
  for (const p of paths) {
    const v = safeJson(res, p);
    if (v !== null && v !== undefined && v !== '') return String(v);
  }
  return null;
}

function resetData(phase) {
  if (!TEST_TOKEN) return;
  const res = http.post(`${BASE_URL}/api/global/test/reset`, null, {
    headers: { 'X-Test-Token': TEST_TOKEN },
  });
  check(res, { [`[${phase}] reset 200/403`]: (r) => r.status === 200 || r.status === 403 });
  console.log(`[${phase}] reset: ${res.status}`);
}

// 7일 전부 OPEN_TIME~CLOSE_TIME 영업, 휴무 아님. 없으면 예약이 영업시간 외로 거절될 수 있다.
function registerHours(restaurantId, ownerToken) {
  const days = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'];
  const body = days.map((day) => ({
    dayOfWeek: day, openTime: OPEN_TIME, closeTime: CLOSE_TIME, isRegularHoliday: 'N',
  }));
  const res = http.post(
    `${BASE_URL}/api/v1/owners/restaurants/${restaurantId}/hours`,
    JSON.stringify(body),
    { headers: jsonHeaders(ownerToken) }
  );
  check(res, { '[setup] 영업시간 등록 200/201': (r) => r.status === 200 || r.status === 201 });
}

export function setup() {
  resetData('setup');

  const suffix = Date.now();
  const ownerEmail = `k6-resv-owner-${suffix}@example.com`;

  const signupRes = http.post(
    `${BASE_URL}/api/v1/auth/signup/owner`,
    JSON.stringify({ email: ownerEmail, password: PASSWORD, name: `k6 resv owner ${suffix}` }),
    { headers: jsonHeaders() }
  );
  check(signupRes, { '[setup] 점주 가입 201': (r) => r.status === 201 });

  const loginRes = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ email: ownerEmail, password: PASSWORD }),
    { headers: jsonHeaders() }
  );
  const ownerToken = safeJson(loginRes, 'accessToken');
  if (!ownerToken) throw new Error(`[setup] 점주 토큰 실패 status=${loginRes.status} body=${loginRes.body}`);

  const restaurantRes = http.post(
    `${BASE_URL}/api/v1/restaurants`,
    JSON.stringify({
      name: `k6 예약부하 식당 ${suffix}`, category: 'KOREAN',
      address: '서울특별시 k6예약부하구', phoneNumber: '02-0000-0000',
      description: 'k6 예약 부하테스트 전용 식당 (운영 데이터 아님)',
      mainMenuName: '테스트메뉴', parkingAvailable: 'N', wifiAvailable: 'N', multilingualMenuAvailable: 'N',
    }),
    { headers: jsonHeaders(ownerToken) }
  );
  const restaurantId = restaurantRes.status === 201 ? extractId(restaurantRes, ['id', 'restaurantId']) : null;
  if (!restaurantId) throw new Error(`[setup] 식당 생성 실패 status=${restaurantRes.status} body=${restaurantRes.body}`);

  const openRes = http.patch(
    `${BASE_URL}/api/v1/restaurants/${restaurantId}/status`,
    JSON.stringify({ status: 'OPEN' }),
    { headers: jsonHeaders(ownerToken) }
  );
  check(openRes, { '[setup] 식당 OPEN': (r) => r.status === 200 || r.status === 204 });

  registerHours(restaurantId, ownerToken);

  // 슬롯 여러 개 생성 — 부하를 분산하고 정원 고갈을 늦춘다.
  const slotIds = [];
  for (let i = 0; i < NUM_SLOTS; i++) {
    const slotRes = http.post(
      `${BASE_URL}/api/v1/owner/restaurants/${restaurantId}/slots`,
      JSON.stringify({
        slotDate: SLOT_DATE,
        slotTime: SLOT_TIME,
        totalCount: SLOT_TOTAL_COUNT,
        minHeadcount: 1,
        maxHeadcount: MAX_HEADCOUNT,
      }),
      { headers: jsonHeaders(ownerToken) }
    );
    const slotId = slotRes.status === 201 ? extractId(slotRes, ['slotId', 'id', 'data.slotId', 'data.id']) : null;
    if (slotId) slotIds.push(slotId);
    else console.log(`[setup] 슬롯 생성 실패(${i}) status=${slotRes.status} body=${slotRes.body}`);
  }
  if (slotIds.length === 0) {
    throw new Error('[setup] 슬롯 생성 실패 — 예약 부하 불가. 슬롯 API/날짜/영업시간 확인 필요.');
  }

  console.log(`[setup] 전용 식당=${restaurantId}, 슬롯=${slotIds.length}개(${SLOT_DATE} ${SLOT_TIME}), totalCount=${SLOT_TOTAL_COUNT}`);
  return { restaurantId, slotIds, ownerToken };
}

export default function (data) {
  if (!data || !data.restaurantId || !data.slotIds || data.slotIds.length === 0) {
    sleep(THINK);
    return;
  }

  const suffix = `${Date.now()}-${__VU}-${__ITER}`;
  const email = `k6-resv-${suffix}@example.com`;

  http.post(
    `${BASE_URL}/api/v1/auth/signup`,
    JSON.stringify({ email, password: PASSWORD, name: `k6 resv ${suffix}` }),
    { headers: jsonHeaders() }
  );
  const loginRes = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ email, password: PASSWORD }),
    { headers: jsonHeaders() }
  );
  const token = safeJson(loginRes, 'accessToken');
  if (!token) return;

  const slotId = data.slotIds[__VU % data.slotIds.length];
  const headcount = 1 + Math.floor(Math.random() * 4);

  // 1. 예약 생성
  const createRes = http.post(
    `${BASE_URL}/api/v1/reservations`,
    JSON.stringify({ restaurantId: Number(data.restaurantId), slotId: Number(slotId), headcount }),
    { headers: jsonHeaders(token), tags: { name: 'reservation_create' } }
  );
  createDur.add(createRes.timings.duration);
  record(createRes.status, create201, create4xx, create5xx, CREATE_BY_CODE);
  check(createRes, { '예약 생성 201': (r) => r.status === 201 });

  const reservationToken = createRes.status === 201
    ? extractId(createRes, ['reservationToken', 'token', 'data.reservationToken'])
    : null;

  if (createRes.status === 201 && !reservationToken) {
    createTokenMissing.add(1);
    sleep(THINK);
    return;
  }
  // 생성 실패 시 상태조회/취소 생략 (4xx/5xx 가 결과를 오염시키지 않게)
  if (!reservationToken) { sleep(THINK); return; }

  // 2. 토큰 단위 상태조회 polling
  for (let i = 0; i < DWELL_POLLS; i++) {
    sleep(THINK);
    const sRes = http.get(`${BASE_URL}/api/v1/reservations/${reservationToken}`, {
      headers: jsonHeaders(token), tags: { name: 'reservation_status' },
    });
    statusDur.add(sRes.timings.duration);
    record(sRes.status, status200, status4xx, status5xx, null);
    check(sRes, { '예약 상태조회 200': (r) => r.status === 200 });
  }

  // 3. 예약 취소
  cancelCalled.add(1);
  const cancelRes = http.patch(
    `${BASE_URL}/api/v1/reservations/${reservationToken}/cancel`,
    null,
    { headers: jsonHeaders(token), tags: { name: 'reservation_cancel' } }
  );
  cancelDur.add(cancelRes.timings.duration);
  record(cancelRes.status, cancel200, cancel4xx, cancel5xx, null);
  check(cancelRes, { '예약 취소 200': (r) => r.status === 200 });
}

export function teardown(data) {
  if (TEST_TOKEN) {
    resetData('teardown');
    return;
  }
  // 토큰 미설정 시: 예약·큐 정리를 위한 전용 reset 이 없으므로 잔여 테스트 데이터가 남을 수 있다.
  // (전용 테스트 식당/슬롯에만 쌓이므로 운영 데이터엔 무영향. 필요 시 팀 공용 정리 스크립트 사용)
  console.log('[teardown] TEST_SECRET_TOKEN 미설정 — 예약 잔여 데이터 정리 생략(전용 식당에만 누적).');
}
