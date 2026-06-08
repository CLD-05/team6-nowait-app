import client from "./client";

/* 식당의 현재 운영 중인 웨이팅 세션 조회 (없으면 404) */
export async function getWaitingSession(restaurantId) {
  const { data } = await client.get(
    `/api/restaurants/${restaurantId}/waiting-session`
  );
  return data;
}

/* 손님: 웨이팅 등록 */
export async function registerWaiting(restaurantId, partySize) {
  const { data } = await client.post(
    `/api/restaurants/${restaurantId}/waitings`,
    { partySize }
  );
  return data;
}

/* 손님: 내 웨이팅 조회 (없으면 404) */
export async function getMyWaiting() {
  const { data } = await client.get("/api/waitings/me");
  return data;
}

/* 손님: 내 웨이팅 취소 */
export async function cancelMyWaiting(waitingId) {
  const { data } = await client.patch(`/api/waitings/${waitingId}/cancel`);
  return data;
}

/* ───── 점주용 ───── */

/* 점주: 세션 오픈 */
export async function openSession(restaurantId, payload) {
  const { data } = await client.post(
    `/api/owners/restaurants/${restaurantId}/waiting-sessions`,
    payload
  );
  return data;
}

/* 점주: 세션 일시정지 */
export async function pauseSession(sessionId) {
  const { data } = await client.patch(
    `/api/owners/waiting-sessions/${sessionId}/pause`
  );
  return data;
}

/* 점주: 세션 재개 */
export async function resumeSession(sessionId) {
  const { data } = await client.patch(
    `/api/owners/waiting-sessions/${sessionId}/resume`
  );
  return data;
}

/* 점주: 세션 마감 */
export async function closeSession(sessionId) {
  const { data } = await client.patch(
    `/api/owners/waiting-sessions/${sessionId}/close`
  );
  return data;
}

/* 점주: 식당의 웨이팅 목록 */
export async function getOwnerWaitings(restaurantId) {
  const { data } = await client.get(
    `/api/owners/restaurants/${restaurantId}/waitings`
  );
  return data;
}

/* 점주: 호출 */
export async function callWaiting(waitingId) {
  const { data } = await client.patch(
    `/api/owners/waiting/${waitingId}/call`
  );
  return data;
}

/* 점주: 취소 처리 */
export async function cancelByOwner(waitingId) {
  const { data } = await client.patch(
    `/api/owners/waiting/${waitingId}/cancelled`
  );
  return data;
}

/* 점주: 입장 처리 */
export async function enterWaiting(waitingId) {
  const { data } = await client.patch(
    `/api/owners/waiting/${waitingId}/enter`
  );
  return data;
}
