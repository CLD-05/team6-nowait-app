import client from "./client";

/* 일반 회원가입 */
export async function signUp({ email, password, name }) {
  const { data } = await client.post("/api/users/signup", { email, password, name });
  return data;
}

/* 점주 회원가입 */
export async function signUpOwner({ email, password, name, restaurantInfo }) {
  const { data } = await client.post("/api/users/signup/owner", {
    email,
    password,
    name,
    /* 백엔드 OwnerSignUpRequest는 필드명이 'restaurant' */
    restaurant: restaurantInfo,
  });
  return data;
}

/* 로그인 */
export async function login({ email, password }) {
  const { data } = await client.post("/api/users/login", { email, password });
  return data;
}

/* 내 정보 */
export async function getMe() {
  const { data } = await client.get("/api/users/me");
  return data;
}

/* 내 정보 수정 (현재 이름만 변경 가능) */
export async function updateMe({ name }) {
  const { data } = await client.patch("/api/users/me", { name });
  return data;
}
