import client from "./client";

/* 슬롯 조회: 특정 식당의 특정 날짜에 예약 가능한 시간대 목록 */
export async function getSlots(restaurantId, date) {
  const { data } = await client.get(
    `/api/v1/restaurants/${restaurantId}/slots`,
    { params: { date } }
  );
  return data;
}

/* 점주: 슬롯 생성 */
export async function createSlot(restaurantId, { slotDate, slotTime, totalCount }) {
  const { data } = await client.post(
    `/api/v1/owner/restaurants/${restaurantId}/slots`,
    { slotDate, slotTime, totalCount }
  );
  return data;
}

/* 점주: 슬롯 수정 (받을 수 있는 팀 수 변경) */
export async function updateSlot(slotId, { totalCount }) {
  const { data } = await client.patch(
    `/api/v1/owner/slots/${slotId}`,
    { totalCount }
  );
  return data;
}

/* 예약 생성 */
export async function createReservation({ restaurantId, slotId, headcount }) {
  const { data } = await client.post("/api/v1/reservations", {
    restaurantId,
    slotId,
    headcount,
  });
  return data;
}

/* 내 예약 목록 */
export async function getMyReservations() {
  const { data } = await client.get("/api/v1/reservations/me");
  return data;
}

/* 예약 상세 */
export async function getReservation(id) {
  const { data } = await client.get(`/api/v1/reservations/${id}`);
  return data;
}

/* 예약 취소 */
export async function cancelReservation(id) {
  const { data } = await client.patch(`/api/v1/reservations/${id}/cancel`);
  return data;
}
