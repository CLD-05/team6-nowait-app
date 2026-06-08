import client from "./client";

/* 식당 목록 (카테고리/검색어 옵션) */
export async function getRestaurants({ category, keyword } = {}) {
  const params = {};
  if (category) params.category = category;
  if (keyword) params.keyword = keyword;

  const { data } = await client.get("/api/restaurants", { params });
  return data;
}

/* 식당 상세 */
export async function getRestaurantDetail(id) {
  const { data } = await client.get(`/api/restaurants/${id}`);
  return data;
}

/* 식당 등록 (점주 토큰 필요) */
export async function registerRestaurant(payload) {
  const { data } = await client.post("/api/restaurants", payload);
  return data; // restaurantId
}

/* 점주: 식당 정보 수정 */
export async function updateRestaurant(restaurantId, payload) {
  const { data } = await client.put(`/api/restaurants/${restaurantId}`, payload);
  return data;
}
