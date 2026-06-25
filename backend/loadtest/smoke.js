import http from 'k6/http';
import { check, sleep } from 'k6';

// Phase 0 스모크 테스트 — 큰 트래픽이 목적이 아니라, 다음을 확인하는 용도다:
//   1) 모니터링 파이프라인(Prometheus/Grafana)에 실제 지표가 찍히는지
//   2) KEDA가 신호를 받아 실제로 반응하는지 (api=CPU/메모리, worker=Redis pending 큐)
//   3) Alertmanager/Slack 알림 경로가 살아있는지
// 본 부하테스트(대용량 시나리오)는 별도 스크립트에서 다룬다.

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TEST_TOKEN = __ENV.TEST_SECRET_TOKEN || '';

// 정원초과(409)/웨이팅 세션 없음(4xx)은 이 스모크 테스트가 의도적으로 유발하는
// 정상 비즈니스 응답이다 — k6 기본값(2xx/3xx 외 전부 실패)대로 두면 http_req_failed가
// 가짜로 치솟으므로, 실제 장애 신호(5xx/네트워크 에러)만 실패로 집계하도록 범위를 넓힌다.
http.setResponseCallback(http.expectedStatuses(200, 201, { min: 400, max: 499 }));

export const options = {
  scenarios: {
    smoke: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '20s', target: 5 },
        { duration: '1m', target: 10 },
        { duration: '20s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<1500'],
  },
};

function jsonHeaders(token) {
  const h = { 'Content-Type': 'application/json' };
  if (token) h['Authorization'] = `Bearer ${token}`;
  return h;
}

// setup()은 전체 테스트에서 1회만 실행된다 — 점주/식당/슬롯을 준비한다.
export function setup() {
  if (TEST_TOKEN) {
    const resetRes = http.post(`${BASE_URL}/api/global/test/reset`, null, {
      headers: { 'X-Test-Token': TEST_TOKEN },
    });
    check(resetRes, { 'reset ok or forbidden(토큰 미설정)': (r) => r.status === 200 || r.status === 403 });
  }

  const suffix = Date.now();
  const ownerEmail = `k6-owner-${suffix}@example.com`;
  const password = 'k6-test-password-1234';

  // /auth/signup/owner는 현재 User(role=OWNER) 생성만 하고 식당 생성은 TODO로
  // 비어있다(AuthService.signUpAsOwner 참고). 식당은 별도로 POST /restaurants를
  // 호출해서 만들어야 한다.
  const signupRes = http.post(
    `${BASE_URL}/api/v1/auth/signup/owner`,
    JSON.stringify({ email: ownerEmail, password, name: `k6 owner ${suffix}` }),
    { headers: jsonHeaders() }
  );
  check(signupRes, { '점주 가입 201': (r) => r.status === 201 });

  const loginRes = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ email: ownerEmail, password }),
    { headers: jsonHeaders() }
  );
  check(loginRes, { '점주 로그인 200': (r) => r.status === 200 });
  const ownerToken = loginRes.json('accessToken');

  const registerRes = http.post(
    `${BASE_URL}/api/v1/restaurants`,
    JSON.stringify({
      name: `k6 식당 ${suffix}`,
      category: 'KOREAN',
      address: '서울특별시 k6테스트구',
      phoneNumber: '02-0000-0000',
      description: 'k6 스모크 테스트용 식당',
      mainMenuName: '테스트메뉴',
      parkingAvailable: 'N',
      wifiAvailable: 'N',
      multilingualMenuAvailable: 'N',
      // restaurantHours를 생략하면 RestaurantService가 11:00~22:00 기본값으로 채워준다.
    }),
    { headers: jsonHeaders(ownerToken) }
  );
  check(registerRes, { '식당 등록 201': (r) => r.status === 201 });
  const restaurantId = registerRes.status === 201 ? registerRes.json() : null;

  if (restaurantId) {
    // 기본 상태가 CLOSED라 공개 목록/예약/웨이팅에 노출되지 않는다 — OPEN으로 전환.
    const openRes = http.patch(
      `${BASE_URL}/api/v1/restaurants/${restaurantId}/status`,
      JSON.stringify({ status: 'OPEN' }),
      { headers: jsonHeaders(ownerToken) }
    );
    check(openRes, { '식당 OPEN 전환 200': (r) => r.status === 200 });
  }

  const slotIds = [];
  if (restaurantId) {
    const today = new Date().toISOString().slice(0, 10);
    for (const time of ['12:00:00', '18:00:00', '19:00:00']) {
      const slotRes = http.post(
        `${BASE_URL}/api/v1/owner/restaurants/${restaurantId}/slots`,
        JSON.stringify({ slotDate: today, slotTime: time, totalCount: 50, minHeadcount: 1, maxHeadcount: 8 }),
        { headers: jsonHeaders(ownerToken) }
      );
      if (slotRes.status === 201) {
        const body = slotRes.json();
        const slotId = body.id ?? body.slotId;
        if (slotId) slotIds.push(slotId);
      }
    }
  }

  return { restaurantId, slotIds };
}

export default function (data) {
  const suffix = `${Date.now()}-${__VU}-${__ITER}`;
  const email = `k6-user-${suffix}@example.com`;
  const password = 'k6-test-password-1234';

  const signupRes = http.post(
    `${BASE_URL}/api/v1/auth/signup`,
    JSON.stringify({ email, password, name: `k6 user ${suffix}` }),
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

  const listRes = http.get(`${BASE_URL}/api/v1/restaurants`);
  check(listRes, { '식당 목록 조회 200': (r) => r.status === 200 });

  if (data.restaurantId && data.slotIds && data.slotIds.length > 0) {
    const slotId = data.slotIds[Math.floor(Math.random() * data.slotIds.length)];
    const reservationRes = http.post(
      `${BASE_URL}/api/v1/reservations`,
      JSON.stringify({ restaurantId: data.restaurantId, slotId, headcount: 2 }),
      { headers: jsonHeaders(token) }
    );
    check(reservationRes, { '예약 생성 201 또는 정원초과 409': (r) => r.status === 201 || r.status === 409 });

    const waitingRes = http.post(
      `${BASE_URL}/api/v1/restaurants/${data.restaurantId}/waitings`,
      JSON.stringify({ partySize: 2 }),
      { headers: jsonHeaders(token) }
    );
    // 오늘 웨이팅 세션이 아직 없을 수 있어(자정/부팅 시에만 생성) 404도 정상 범주로 둔다.
    check(waitingRes, { '웨이팅 등록 201 또는 세션없음 4xx': (r) => r.status === 201 || (r.status >= 400 && r.status < 500) });
  }

  sleep(1);
}
