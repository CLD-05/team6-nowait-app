// ============================================
// Nowait - API URL 관리 + 공통 request 함수
// ============================================
//
// VITE_API_BASE_URL에는 API origin만 넣는다.
//
// 예:
// local: http://localhost:8080
// dev:   http://k8s-nowaitdev-xxxx.ap-northeast-2.elb.amazonaws.com
// prod:  https://api.nowait.com
//
// 주의:
// VITE_API_BASE_URL에 /api/v1을 붙이지 않는다.

const API_ORIGIN = (
  import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
).replace(/\/$/, '');

export const API_BASE = `${API_ORIGIN}/api/v1`;
export const IMAGE_BASE = API_ORIGIN;

function getToken(): string | null {
  return localStorage.getItem('nowait_token');
}

function normalizePath(path: string): string {
  return path.startsWith('/') ? path : `/${path}`;
}

export async function request<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const token = getToken();

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...((options.headers as Record<string, string>) || {}),
  };

  const res = await fetch(`${API_BASE}${normalizePath(path)}`, {
    ...options,
    headers,
  });

  if (res.status === 401) {
    localStorage.removeItem('nowait_token');
    localStorage.removeItem('nowait_user');
    window.location.href = '/auth';
    throw new Error('인증이 만료됐어요. 다시 로그인해주세요.');
  }

  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.message || '요청 처리 중 오류가 발생했습니다.');
  }

  if (res.status === 204) {
    return null as T;
  }

  const contentType = res.headers.get('content-type') || '';
  if (contentType.includes('application/json')) {
    return res.json();
  }

  return res.text() as unknown as T;
}