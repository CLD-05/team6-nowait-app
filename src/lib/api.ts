// ============================================
// Nowait - api.ts
// 공통 fetch 함수 + API Base URL 환경변수 관리
// ============================================

export const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080/api/v1';

// JWT 토큰 가져오기
function getToken(): string | null {
  return localStorage.getItem('nowait_token');
}

// 공통 fetch 함수
export async function request<T>(
  path: string,
  options: RequestInit = {}
): Promise<T> {
  const token = getToken();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...(options.headers as Record<string, string> || {}),
  };

  const res = await fetch(`${API_BASE}${path}`, { ...options, headers });

  if (res.status === 401) {
    // 토큰 만료 시 로그인 페이지로
    localStorage.removeItem('nowait_token');
    localStorage.removeItem('nowait_user');
    window.location.href = '/auth';
    throw new Error('인증이 만료됐어요. 다시 로그인해주세요.');
  }

  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.message || '요청 처리 중 오류가 발생했습니다.');
  }

  if (res.status === 204) return null as T;
  return res.json();
}