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

export function resolveImageUrl(imageUrl?: string | null, fallback = '/favicon.svg'): string {
  if (!imageUrl) return fallback;

  if (/^https?:\/\//.test(imageUrl)) {
    return imageUrl;
  }

  if (imageUrl.startsWith('//')) {
    return `https:${imageUrl}`;
  }

  return `${IMAGE_BASE}/${imageUrl.replace(/^\/+/, '')}`;
}

function getToken(): string | null {
  return localStorage.getItem('nowait_token');
}

function normalizePath(path: string): string {
  return path.startsWith('/') ? path : `/${path}`;
}

/** 필드별 검증 에러. 백엔드 ErrorResponse.errors와 동일한 모양이다. */
export type ApiFieldError = { field?: string; value?: string; reason?: string };

/** status를 들고 있는 API 에러. 호출부에서 404 등 특정 상태코드를 구분해야 할 때 사용한다. */
export class ApiError extends Error {
  status: number;
  errors?: ApiFieldError[];

  constructor(status: number, message: string, errors?: ApiFieldError[]) {
    super(message);
    this.status = status;
    this.errors = errors;
  }
}

type RequestOptions = RequestInit & {
  /** 로그인처럼 401이 "세션 만료"가 아니라 정상적인 실패 응답인 호출에서 전역 리다이렉트를 막는다. */
  skipAuthRedirect?: boolean;
};

export async function request<T>(
  path: string,
  options: RequestOptions = {},
): Promise<T> {
  const { skipAuthRedirect, ...fetchOptions } = options;
  const token = getToken();

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...((fetchOptions.headers as Record<string, string>) || {}),
  };

  const res = await fetch(`${API_BASE}${normalizePath(path)}`, {
    ...fetchOptions,
    headers,
  });

  if (res.status === 401 && !skipAuthRedirect) {
    localStorage.removeItem('nowait_token');
    localStorage.removeItem('nowait_user');
    window.location.href = '/auth';
    throw new ApiError(401, '인증이 만료됐어요. 다시 로그인해주세요.');
  }

  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new ApiError(res.status, err.message || '요청 처리 중 오류가 발생했습니다.', err.errors);
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