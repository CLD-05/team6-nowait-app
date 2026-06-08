import axios from "axios";
import { tokenStorage, logout } from "../utils/storage";

/* 백엔드 베이스 URL */
const BASE_URL = "http://localhost:8080";

const client = axios.create({
  baseURL: BASE_URL,
  headers: { "Content-Type": "application/json" },
});

/* 요청 인터셉터: 토큰 있으면 자동으로 Authorization 헤더 첨부 */
client.interceptors.request.use((config) => {
  const token = tokenStorage.get();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

/* 응답 인터셉터: 401이면 토큰 만료 → 자동 로그아웃 */
client.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      logout();
      if (window.location.pathname !== "/login") {
        window.location.href = "/login";
      }
    }
    return Promise.reject(err);
  }
);

export default client;
