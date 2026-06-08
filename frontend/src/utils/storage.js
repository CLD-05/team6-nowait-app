/* 토큰/유저 정보 로컬 저장 유틸 */

const TOKEN_KEY = "nowait_token";
const USER_KEY = "nowait_user";
const RESTAURANT_KEY = "nowait_restaurant_id";

export const tokenStorage = {
  get() {
    return localStorage.getItem(TOKEN_KEY);
  },
  set(token) {
    localStorage.setItem(TOKEN_KEY, token);
  },
  clear() {
    localStorage.removeItem(TOKEN_KEY);
  },
};

export const userStorage = {
  get() {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? JSON.parse(raw) : null;
  },
  set(user) {
    localStorage.setItem(USER_KEY, JSON.stringify(user));
  },
  clear() {
    localStorage.removeItem(USER_KEY);
  },
};

/* 점주가 운영 중인 식당 ID 저장 (점주 회원가입 시 받은 값) */
export const restaurantIdStorage = {
  get() {
    const raw = localStorage.getItem(RESTAURANT_KEY);
    return raw ? Number(raw) : null;
  },
  set(id) {
    localStorage.setItem(RESTAURANT_KEY, String(id));
  },
  clear() {
    localStorage.removeItem(RESTAURANT_KEY);
  },
};

export function logout() {
  tokenStorage.clear();
  userStorage.clear();
  restaurantIdStorage.clear();
}
