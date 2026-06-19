import { useState, useEffect, useRef, useCallback } from 'react';
import { useParams, useNavigate, useSearchParams } from 'react-router-dom';
import { API_BASE, ApiError, request, resolveImageUrl } from '../lib/api';

const CAT_LABEL: Record<string, string> = {
  KOREAN: '한식', JAPANESE: '일식', CHINESE: '중식', WESTERN: '양식', ASIAN: '아시안',
};

type RestaurantDetail = {
  id: number;
  name: string;
  category: string;
  address: string;
  phoneNumber?: string;
  description?: string;
  imageUrl?: string;
  mainMenuName?: string;
  parkingAvailable: string;
  wifiAvailable: string;
  multilingualMenuAvailable: string;
  status?: string;
  reservationAvailable?: string;
  waitingAvailable?: string;
  openTime?: string;
  closeTime?: string;
  closedDays?: string;
};

type RestaurantHour = {
  dayOfWeek: 'MON' | 'TUE' | 'WED' | 'THU' | 'FRI' | 'SAT' | 'SUN';
  openTime: string;
  closeTime: string;
  isRegularHoliday: 'Y' | 'N';
};

const DAY_LABEL: Record<RestaurantHour['dayOfWeek'], string> = {
  MON: '월요일',
  TUE: '화요일',
  WED: '수요일',
  THU: '목요일',
  FRI: '금요일',
  SAT: '토요일',
  SUN: '일요일',
};

const JS_DAY_TO_KEY: RestaurantHour['dayOfWeek'][] = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT'];

type Slot = {
  slotId: number;
  slotDate?: string;
  slotTime: string;
  totalCount: number;
  remainCount: number;
  available?: boolean;
  minHeadcount?: number;
  maxHeadcount?: number;
  _isPast?: boolean;
};

type WaitingSession = {
  sessionId?: number;
  status: string;
  currentCount: number;
  maxWaitingCount: number;
};

type Review = {
  reviewId: number;
  userId?: number;
  userName: string;
  restaurantId?: number;
  reservationId?: number;
  rating: number;
  content: string;
  visitedAt?: string;
  createdAt: string;
};

type FavoriteSummary = {
  favoriteId: number;
  restaurantId: number;
  restaurantName?: string;
};


// ─── 유틸 ──────────────────────────────────────────────────────

/**
 * 오늘 날짜를 "YYYY-MM-DD" 형태로 반환.
 * toISOString()은 UTC 기준이라 KST 00:00~08:59 사이에는 하루 전 날짜를 반환해
 * 백엔드(KST 기준 LocalDate.now)와 어긋난다. 로컬 캘린더 날짜를 그대로 사용한다.
 */
function getTodayStr() {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

/**
 * 슬롯 시간이 현재 시각보다 이전이면서 오늘인 경우 비활성화 처리.
 * 내일 이후 날짜는 모두 활성.
 */
function isPastSlot(slotDate: string, slotTime: string): boolean {
  const today = getTodayStr();
  if (slotDate !== today) return false; // 오늘이 아니면 해당 없음

  const now = new Date();
  const [h, m] = slotTime.split(':').map(Number);
  const slotMinutes = h * 60 + m;
  const nowMinutes = now.getHours() * 60 + now.getMinutes();
  return slotMinutes <= nowMinutes; // 현재 시각 이하는 마감
}

// ─── 컴포넌트 ─────────────────────────────────────────────────
const SLOT_POLL_MS = 30_000;    // 슬롯 polling 주기
const WAITING_POLL_MS = 30_000; // 웨이팅 polling 주기

export default function RestaurantPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const reservationToken = searchParams.get('reservationToken');

  const [tab, setTab] = useState<'reserve' | 'waiting' | 'review'>(
    searchParams.get('tab') === 'review' ? 'review' : 'reserve'
  );
  const [restaurant, setRestaurant] = useState<RestaurantDetail | null>(null);
  const [restaurantHours, setRestaurantHours] = useState<RestaurantHour[]>([]);
  const [loading, setLoading] = useState(true);
  const [isFavorite, setIsFavorite] = useState(false);

  // 예약
  const [selectedDate, setSelectedDate] = useState(getTodayStr); // ← 오늘 날짜 초기값
  const [slots, setSlots] = useState<Slot[]>([]);
  const [slotsUpdatedAt, setSlotsUpdatedAt] = useState<Date | null>(null); // 마지막 갱신 시각
  const [slotsError, setSlotsError] = useState(false); // 진짜 빈 슬롯 vs 조회 실패 구분
  const [selectedSlot, setSelectedSlot] = useState<Slot | null>(null);
  const [headcount, setHeadcount] = useState(2);
  const [reserveLoading, setReserveLoading] = useState(false);

  const headcountMin = selectedSlot?.minHeadcount ?? 1;
  const headcountMax = selectedSlot?.maxHeadcount ?? 8;

  /* 슬롯 선택 시, 그 슬롯의 인원 범위 밖이면 인원을 범위 안으로 즉시 맞춰준다. */
  function selectSlot(slot: Slot) {
    setSelectedSlot(slot);
    const min = slot.minHeadcount ?? 1;
    const max = slot.maxHeadcount ?? 8;
    setHeadcount(h => Math.min(max, Math.max(min, h)));
  }

  // 웨이팅
  const [waitingSession, setWaitingSession] = useState<WaitingSession | null>(null);
  const [waitingUpdatedAt, setWaitingUpdatedAt] = useState<Date | null>(null);
  const [waitingSessionError, setWaitingSessionError] = useState(false); // 세션 없음(404) vs 조회 실패 구분
  const [partySize, setPartySize] = useState(2);
  const [waitingLoading, setWaitingLoading] = useState(false);

  // 리뷰
  const [reviews, setReviews] = useState<Review[]>([]);
  const [reviewRating, setReviewRating] = useState(5);
  const [reviewContent, setReviewContent] = useState('');
  const [reviewLoading, setReviewLoading] = useState(false);

  // polling 타이머 ref (cleanup 용)
  const slotTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const waitingTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // SSE ref (개인 알림용)
  const sseRef = useRef<EventSource | null>(null);

  // ── fetch 함수들 ────────────────────────────────────────────

  const fetchRestaurant = useCallback(async () => {
    setLoading(true);
    try {
      const [detail, hours] = await Promise.all([
        request<RestaurantDetail>(`/restaurants/${id}`),
        request<RestaurantHour[]>(`/restaurants/${id}/hours`).catch(() => []),
      ]);
      setRestaurant(detail);
      setRestaurantHours(hours);
    } finally {
      setLoading(false);
    }
  }, [id]);

  /**
   * 슬롯 조회 + 지난 시간 슬롯 비활성화 적용
   * polling 에서도 동일하게 호출
   */
  const fetchSlots = useCallback(async (date: string) => {
    try {
      const data = await request<{ slots?: Slot[] } | Slot[]>(`/restaurants/${id}/slots?date=${date}`);
      // 백엔드 응답: { restaurantId, date, slots: [...] }
      const raw = Array.isArray(data) ? data : (data.slots || []);

      // 지난 시간 슬롯을 remainCount=0 으로 표시 (서버 값 보존 + UI 비활성화)
      const processed = raw
        .filter(slot => slot.slotTime.slice(3, 5) === '00')
        .map(slot => ({
          ...slot,
          _isPast: isPastSlot(slot.slotDate ?? date, slot.slotTime),
        }));

      setSlots(processed);
      setSlotsError(false);
      setSlotsUpdatedAt(new Date());

      // 선택된 슬롯이 이미 마감/과거가 됐으면 선택 해제
      setSelectedSlot(prev => {
        if (!prev) return prev;
        const refreshed = processed.find(s => s.slotId === prev.slotId);
        if (!refreshed || refreshed.remainCount === 0 || refreshed._isPast) return null;
        return refreshed; // 최신 remainCount 반영
      });
    } catch {
      // 네트워크 오류 / 5xx 등 — "슬롯 없음"이 아니라 "조회 실패"로 구분해서 보여줘야 한다.
      setSlots([]);
      setSlotsError(true);
    }
  }, [id]);

  /** 웨이팅 세션 조회 */
  const fetchWaitingSession = useCallback(async () => {
    try {
      const data = await request<WaitingSession>(`/restaurants/${id}/waiting-session`);
      setWaitingSession(data);
      setWaitingSessionError(false);
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) {
        // 오늘 오픈된 세션이 없는 정상적인 상태 — 에러가 아니다.
        setWaitingSession(null);
        setWaitingSessionError(false);
      } else {
        // 네트워크 오류 등 — "세션 없음"과 구분해서 보여줘야 한다.
        setWaitingSession(null);
        setWaitingSessionError(true);
      }
    } finally {
      setWaitingUpdatedAt(new Date());
    }
  }, [id]);

  const fetchReviews = useCallback(async () => {
    try {
      const data = await request<Review[]>(`/restaurants/${id}/reviews`);
      setReviews(data);
    } catch { setReviews([]); }
  }, [id]);

  const fetchFavoriteStatus = useCallback(async () => {
    const token = localStorage.getItem('nowait_token');
    if (!token) return;
    try {
      const favorites = await request<FavoriteSummary[]>('/users/me/favorites');
      setIsFavorite(favorites.some(f => String(f.restaurantId) === id));
    } catch { /* 로그인 안 된 경우 등 무시 */ }
  }, [id]);

  // ── SSE 개인 알림 연결 ───────────────────────────────────────
  // 로그인한 경우에만 연결. "waiting_called" 이벤트 수신 시 웨이팅 즉시 갱신.
  const connectSse = useCallback(async () => {
    const token = localStorage.getItem('nowait_token');
    if (!token) return;

    try {
      // 1) 단발 티켓 발급
      const { ticket } = await request<{ ticket: string }>('/notifications/stream/ticket', { method: 'POST' });

      // 2) SSE 연결 — EventSource는 fetch 래퍼를 못 쓰므로 API_BASE를 직접 사용한다.
      const es = new EventSource(`${API_BASE}/notifications/stream?ticket=${ticket}`);
      sseRef.current = es;

      // 웨이팅 호출 이벤트 → 즉시 fetchWaitingSession
      es.addEventListener('waiting_called', () => fetchWaitingSession());
      es.addEventListener('waiting_cancelled', () => fetchWaitingSession());

      es.onerror = () => {
        es.close();
        sseRef.current = null;
      };
    } catch { /* SSE 실패해도 polling 으로 커버되므로 무시 */ }
  }, [fetchWaitingSession]);

  // ── polling 설정 ─────────────────────────────────────────────

  /** 슬롯 polling 시작. 날짜 바뀌면 기존 타이머 교체. */
  const startSlotPolling = useCallback((date: string) => {
    if (slotTimerRef.current) clearInterval(slotTimerRef.current);
    slotTimerRef.current = setInterval(() => fetchSlots(date), SLOT_POLL_MS);
  }, [fetchSlots]);

  /** 웨이팅 polling 시작 */
  const startWaitingPolling = useCallback(() => {
    if (waitingTimerRef.current) clearInterval(waitingTimerRef.current);
    waitingTimerRef.current = setInterval(fetchWaitingSession, WAITING_POLL_MS);
  }, [fetchWaitingSession]);

  // ── 초기 마운트 ──────────────────────────────────────────────
  useEffect(() => {
  // 데이터 요청의 setState는 비동기 응답 이후 실행된다.
  // eslint-disable-next-line react-hooks/set-state-in-effect
  void fetchRestaurant();
  void fetchWaitingSession();
  void fetchReviews();
  void fetchFavoriteStatus();
  startWaitingPolling();
  void connectSse();

  return () => {
    if (waitingTimerRef.current) clearInterval(waitingTimerRef.current);
    if (slotTimerRef.current) clearInterval(slotTimerRef.current);
    sseRef.current?.close();
  };
}, [connectSse, fetchFavoriteStatus, fetchRestaurant, fetchReviews, fetchWaitingSession, startWaitingPolling]);

  // ── 날짜 변경 시 슬롯 즉시 조회 + polling 재설정 ─────────────
  useEffect(() => {
  if (!selectedDate) return;
  // 날짜가 바뀌면 이전 선택을 즉시 초기화한다.
  // eslint-disable-next-line react-hooks/set-state-in-effect
  setSelectedSlot(null);
  void fetchSlots(selectedDate);
  startSlotPolling(selectedDate);

  return () => {
    if (slotTimerRef.current) clearInterval(slotTimerRef.current);
  };
}, [fetchSlots, selectedDate, startSlotPolling]);

  // ── 액션 핸들러 ─────────────────────────────────────────────

  async function handleReserve() {
    if (!selectedSlot) { alert('슬롯을 선택해주세요.'); return; }
    const token = localStorage.getItem('nowait_token');
    if (!token) { navigate('/auth'); return; }
    setReserveLoading(true);
    try {
      await request('/reservations', {
        method: 'POST',
        body: JSON.stringify({
          restaurantId: Number(id),
          slotId: selectedSlot.slotId, // ← slotId 사용
          headcount,
        }),
      });
      navigate('/mypage');
    } catch (err) {
      alert(err instanceof Error ? err.message : '예약에 실패했습니다.');
      // 실패 후 슬롯 즉시 재조회 (다른 사람이 예약해서 마감됐을 수 있음)
      void fetchSlots(selectedDate);
    } finally {
      setReserveLoading(false);
    }
  }

  async function handleWaiting() {
    const token = localStorage.getItem('nowait_token');
    if (!token) { navigate('/auth'); return; }
    setWaitingLoading(true);
    try {
      await request(`/restaurants/${id}/waitings`, {
        method: 'POST',
        body: JSON.stringify({ partySize }),
      });
      // 등록 직후 웨이팅 세션 즉시 갱신
      await fetchWaitingSession();
      navigate('/mypage');
    } catch (err) {
      alert(err instanceof Error ? err.message : '웨이팅 등록에 실패했습니다.');
    } finally {
      setWaitingLoading(false);
    }
  }

  async function handleReview() {
    if (!reviewContent.trim()) { alert('리뷰 내용을 입력해주세요.'); return; }
    const token = localStorage.getItem('nowait_token');
    if (!token) { navigate('/auth'); return; }
    setReviewLoading(true);
    try {
      await request(`/reservations/${reservationToken}/reviews`, {
        method: 'POST',
        body: JSON.stringify({ rating: reviewRating, content: reviewContent.trim() }),
      });
      setReviewContent('');
      setReviewRating(5);
      await fetchReviews();
      alert('리뷰가 등록됐어요!');
    } catch (err) {
      alert(err instanceof Error ? err.message : '리뷰 등록에 실패했습니다.');
    } finally {
      setReviewLoading(false);
    }
  }

  async function toggleFavorite() {
    const token = localStorage.getItem('nowait_token');
    if (!token) { navigate('/auth'); return; }
    try {
      await request(`/restaurants/${id}/favorite`, { method: 'POST' });
      setIsFavorite(prev => !prev);
    } catch {
      alert('즐겨찾기 처리에 실패했습니다.');
    }
  }

  // ── 렌더링 헬퍼 ─────────────────────────────────────────────

  /** polling으로 마지막 갱신된 시각을 표시 */
  function formatUpdatedAt(date: Date | null) {
    if (!date) return '';
    return `${date.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', second: '2-digit' })} 갱신`;
  }

  /** 슬롯 버튼이 선택 불가인지 판단 */
  function isSlotDisabled(slot: Slot) {
    return slot.remainCount === 0 || slot._isPast;
  }

  const todayHour = restaurantHours.find(hour => hour.dayOfWeek === JS_DAY_TO_KEY[new Date().getDay()]);
  const operatingHoursText = todayHour
    ? todayHour.isRegularHoliday === 'Y'
      ? '오늘 정기 휴무'
      : `${todayHour.openTime.slice(0, 5)} ~ ${todayHour.closeTime.slice(0, 5)}`
    : restaurant?.openTime && restaurant?.closeTime
      ? `${restaurant.openTime} ~ ${restaurant.closeTime}`
      : '매장 문의';

  /*
   * 웨이팅은 "지금 줄을 서는" 개념이라 슬롯과 달리 현재 시각이 오늘 영업시간 안인지로
   * 가능 여부가 결정된다 (백엔드 WaitingService.register도 동일하게 현재 시각 기준으로
   * 검증함). todayHour가 없으면(영업시간 미설정) 막지 않고 서버 응답에 맡긴다.
   */
  const isOutsideOperatingHoursNow = (() => {
    if (!todayHour) return false;
    if (todayHour.isRegularHoliday === 'Y') return true;
    const toMinutes = (t: string) => {
      const [h, m] = t.split(':').map(Number);
      return h * 60 + m;
    };
    const nowMinutes = new Date().getHours() * 60 + new Date().getMinutes();
    return nowMinutes < toMinutes(todayHour.openTime) || nowMinutes > toMinutes(todayHour.closeTime);
  })();
  const regularHolidayText = restaurantHours
    .filter(hour => hour.isRegularHoliday === 'Y')
    .map(hour => DAY_LABEL[hour.dayOfWeek])
    .join(', ');

  // ── 로딩 / 에러 ─────────────────────────────────────────────
  if (loading) return (
    <div style={{ minHeight: '100vh', background: 'var(--cream)' }}>
      <div className="spinner" />
    </div>
  );
  if (!restaurant) return (
    <div style={{ minHeight: '100vh', background: 'var(--cream)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <p>식당을 찾을 수 없어요</p>
    </div>
  );

  const averageRating = reviews.length === 0
    ? 0
    : reviews.reduce((sum, review) => sum + review.rating, 0) / reviews.length;

  // ── 메인 렌더 ────────────────────────────────────────────────
  return (
    <div style={{ minHeight: '100vh', background: 'var(--cream)' }}>

      {/* 이미지 헤더 */}
      <div style={{
        position: 'relative', width: 'calc(100% - 40px)', maxWidth: 760, height: 240,
        margin: '20px auto 0', overflow: 'hidden', background: '#f0e3d7',
        border: '2.5px solid var(--ink)', borderRadius: 20,
        boxShadow: '5px 5px 0 var(--ink)'
      }}>
        <img src={resolveImageUrl(restaurant.imageUrl)} alt={restaurant.name}
          style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
        <div style={{ position: 'absolute', inset: 0, background: 'linear-gradient(to top, rgba(35,26,20,0.7) 0%, transparent 50%)' }} />

        <button onClick={() => navigate(-1)} style={{
          position: 'absolute', top: 16, left: 16, width: 40, height: 40,
          borderRadius: '50%', background: '#fff', border: '2.5px solid var(--ink)',
          cursor: 'pointer', fontWeight: 900, fontSize: '1rem', boxShadow: '3px 3px 0 var(--ink)'
        }}>←</button>

        <button onClick={() => void toggleFavorite()} style={{
          position: 'absolute', top: 16, right: 16, width: 40, height: 40,
          borderRadius: '50%', background: isFavorite ? 'var(--tomato)' : '#fff',
          border: '2.5px solid var(--ink)', cursor: 'pointer', fontSize: '1.1rem',
          boxShadow: '3px 3px 0 var(--ink)', transition: 'all 0.15s'
        }}>{isFavorite ? '❤️' : '🤍'}</button>

        <div style={{ position: 'absolute', bottom: 20, left: 24, color: '#fff' }}>
          <span style={{
            background: 'var(--tomato)', padding: '4px 12px', borderRadius: 999,
            fontSize: '0.78rem', fontWeight: 800, border: '2px solid var(--ink)',
            marginBottom: 8, display: 'inline-block'
          }}>{CAT_LABEL[restaurant.category]}</span>
          <h1 style={{ fontSize: '1.8rem', fontWeight: 900, marginTop: 6, letterSpacing: '-1px' }}>
            {restaurant.name}
          </h1>
          <div style={{ fontSize: '0.88rem', marginTop: 4, opacity: 0.9 }}>
            ⭐ {averageRating.toFixed(1)} · 리뷰 {reviews.length}개
          </div>
        </div>
      </div>

      <div style={{ maxWidth: 800, margin: '0 auto', padding: '0 20px 60px' }}>

        {/* 식당 정보 카드 */}
        <div style={{
          background: '#fff', border: '2.5px solid var(--ink)', borderRadius: 20,
          boxShadow: '5px 5px 0 var(--ink)', padding: 24, margin: '20px 0'
        }}>
          <p style={{ color: 'var(--muted)', fontSize: '0.92rem', lineHeight: 1.7, marginBottom: 16 }}>
            {restaurant.description}
          </p>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))', gap: 12 }}>
            {[
              { icon: '🕐', label: '영업시간', value: operatingHoursText },
              { icon: '📍', label: '주소', value: restaurant.address },
              { icon: '📞', label: '전화', value: restaurant.phoneNumber },
              { icon: '🚫', label: '휴무', value: regularHolidayText ? `매주 ${regularHolidayText}` : restaurant.closedDays || '연중무휴' },
            ].map(item => (
              <div key={item.label} style={{ display: 'flex', gap: 8, alignItems: 'flex-start' }}>
                <span>{item.icon}</span>
                <div>
                  <div style={{ fontSize: '0.75rem', color: 'var(--muted)', marginBottom: 2, fontWeight: 700 }}>{item.label}</div>
                  <div style={{ fontSize: '0.88rem', fontWeight: 700 }}>{item.value}</div>
                </div>
              </div>
            ))}
          </div>
          <div style={{ display: 'flex', gap: 8, marginTop: 16, flexWrap: 'wrap' }}>
            {restaurant.parkingAvailable === 'Y' && <span style={{ fontSize: '0.78rem', fontWeight: 800, padding: '4px 12px', borderRadius: 999, background: 'var(--amber)', border: '2px solid var(--ink)' }}>🅿️ 주차가능</span>}
            {restaurant.wifiAvailable === 'Y' && <span style={{ fontSize: '0.78rem', fontWeight: 800, padding: '4px 12px', borderRadius: 999, background: '#EFF6FF', border: '2px solid var(--ink)', color: '#1D4ED8' }}>📶 와이파이</span>}
            {restaurant.multilingualMenuAvailable === 'Y' && <span style={{ fontSize: '0.78rem', fontWeight: 800, padding: '4px 12px', borderRadius: 999, background: 'var(--tomato-light)', border: '2px solid var(--ink)', color: 'var(--tomato)' }}>🌐 다국어메뉴</span>}
          </div>
        </div>

        {/* 탭 */}
        <div style={{
          display: 'flex', marginBottom: 20, border: '2.5px solid var(--ink)',
          borderRadius: 14, overflow: 'hidden', boxShadow: '4px 4px 0 var(--ink)'
        }}>
          {([
            { key: 'reserve', label: '📋 예약하기' },
            { key: 'waiting', label: '⏳ 웨이팅' },
            { key: 'review', label: `⭐ 리뷰 (${reviews.length})` },
          ] as { key: 'reserve' | 'waiting' | 'review'; label: string }[]).map((t, i) => (
            <button key={t.key} onClick={() => setTab(t.key)} style={{
              flex: 1, padding: 16, border: 'none', cursor: 'pointer',
              fontWeight: 800, fontSize: '0.92rem', fontFamily: 'inherit',
              borderRight: i < 2 ? '2px solid var(--ink)' : 'none',
              background: tab === t.key ? 'var(--ink)' : '#fff',
              color: tab === t.key ? '#fff' : 'var(--muted)',
              transition: 'all 0.15s'
            }}>{t.label}</button>
          ))}
        </div>

        {/* ── 예약 탭 ─────────────────────────────────────────── */}
        {tab === 'reserve' && (
          <div style={{
            background: '#fff', border: '2.5px solid var(--ink)',
            borderRadius: 20, boxShadow: '5px 5px 0 var(--ink)', padding: 24
          }}>

            {/* 날짜 선택 */}
            <div style={{ marginBottom: 24 }}>
              <div style={{ fontSize: '0.92rem', fontWeight: 900, marginBottom: 10 }}>📅 날짜 선택</div>
              <input type="date" value={selectedDate}
                min={getTodayStr()}
                onChange={e => setSelectedDate(e.target.value)}
                style={{
                  padding: '10px 16px', border: '2.5px solid var(--ink)', borderRadius: 10,
                  fontSize: '0.92rem', outline: 'none', fontFamily: 'inherit',
                  boxShadow: '3px 3px 0 var(--ink)'
                }} />
            </div>

            {/* 슬롯 목록 */}
            <div style={{ marginBottom: 24 }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 10 }}>
                <div style={{ fontSize: '0.92rem', fontWeight: 900 }}>🕐 시간 선택</div>
                {/* 마지막 갱신 시각 표시 */}
                {slotsUpdatedAt && (
                  <div style={{ fontSize: '0.72rem', color: 'var(--muted)', fontWeight: 700 }}>
                    🔄 {formatUpdatedAt(slotsUpdatedAt)}
                  </div>
                )}
              </div>

              {slots.length === 0 ? (
                <div style={{
                  textAlign: 'center', padding: '32px 0', color: 'var(--muted)',
                  fontSize: '0.9rem', fontWeight: 700
                }}>
                  {slotsError ? (
                    <>
                      ⚠️ 슬롯 정보를 불러오지 못했어요
                      <div style={{ marginTop: 10 }}>
                        <button onClick={() => void fetchSlots(selectedDate)} style={{
                          padding: '8px 16px', border: '2px solid var(--ink)', borderRadius: 10,
                          background: '#fff', fontWeight: 800, fontSize: '0.82rem', cursor: 'pointer'
                        }}>다시 시도</button>
                      </div>
                    </>
                  ) : '📭 해당 날짜에 예약 가능한 슬롯이 없어요'}
                </div>
              ) : (
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(90px, 1fr))', gap: 8 }}>
                  {slots.map(slot => {
                    const disabled = isSlotDisabled(slot);
                    const selected = selectedSlot?.slotId === slot.slotId;
                    return (
                      <button
                        key={slot.slotId}
                        disabled={disabled}
                        onClick={() => selectSlot(slot)}
                        style={{
                          padding: '12px 8px', borderRadius: 10, textAlign: 'center',
                          border: `2.5px solid ${selected ? 'var(--tomato)' : 'var(--ink)'}`,
                          background: selected ? 'var(--tomato)' : disabled ? '#f5f5f5' : '#fff',
                          color: selected ? '#fff' : disabled ? '#ccc' : 'var(--ink)',
                          cursor: disabled ? 'not-allowed' : 'pointer',
                          fontWeight: 800, fontSize: '0.88rem',
                          boxShadow: disabled ? 'none' : '2px 2px 0 var(--ink)',
                          transition: 'all 0.15s',
                          position: 'relative',
                        }}>
                        {/* 지난 시간 슬롯에 사선 표시 */}
                        {slot._isPast && (
                          <div style={{
                            position: 'absolute', inset: 0, borderRadius: 8,
                            background: 'repeating-linear-gradient(-45deg, transparent, transparent 4px, rgba(0,0,0,0.06) 4px, rgba(0,0,0,0.06) 8px)',
                            pointerEvents: 'none',
                          }} />
                        )}
                        <div>{slot.slotTime.slice(0, 5)}</div>
                        <div style={{ fontSize: '0.72rem', marginTop: 2 }}>
                          {slot._isPast ? '지난시간' : slot.remainCount === 0 ? '마감' : `잔여 ${slot.remainCount}`}
                        </div>
                        {!slot._isPast && slot.remainCount !== 0 && slot.minHeadcount != null && slot.maxHeadcount != null && (
                          <div style={{ fontSize: '0.66rem', marginTop: 1, opacity: 0.8 }}>
                            {slot.minHeadcount}~{slot.maxHeadcount}명
                          </div>
                        )}
                      </button>
                    );
                  })}
                </div>
              )}
            </div>

            {/* 인원 선택 */}
            <div style={{ marginBottom: 24 }}>
              <div style={{ fontSize: '0.92rem', fontWeight: 900, marginBottom: 10 }}>👥 인원 선택</div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
                <button
                  onClick={() => setHeadcount(h => Math.max(headcountMin, h - 1))}
                  disabled={headcount <= headcountMin}
                  style={{
                    width: 40, height: 40, borderRadius: '50%', border: '2.5px solid var(--ink)',
                    background: headcount <= headcountMin ? '#f5f5f5' : '#fff',
                    color: headcount <= headcountMin ? '#bbb' : 'var(--ink)',
                    fontSize: '1.2rem', cursor: headcount <= headcountMin ? 'not-allowed' : 'pointer', fontWeight: 900,
                    boxShadow: headcount <= headcountMin ? 'none' : '2px 2px 0 var(--ink)'
                  }}>−</button>
                <span style={{ fontSize: '1.2rem', fontWeight: 900, minWidth: 40, textAlign: 'center' }}>
                  {headcount}명
                </span>
                <button
                  onClick={() => setHeadcount(h => Math.min(headcountMax, h + 1))}
                  disabled={headcount >= headcountMax}
                  style={{
                    width: 40, height: 40, borderRadius: '50%', border: '2.5px solid var(--ink)',
                    background: headcount >= headcountMax ? '#f5f5f5' : '#fff',
                    color: headcount >= headcountMax ? '#bbb' : 'var(--ink)',
                    fontSize: '1.2rem', cursor: headcount >= headcountMax ? 'not-allowed' : 'pointer', fontWeight: 900,
                    boxShadow: headcount >= headcountMax ? 'none' : '2px 2px 0 var(--ink)'
                  }}>+</button>
              </div>
              {selectedSlot && (
                <div style={{ fontSize: '0.78rem', color: 'var(--muted)', fontWeight: 700, marginTop: 8 }}>
                  이 시간대는 {headcountMin}~{headcountMax}명까지 예약 가능해요.
                </div>
              )}
            </div>

            <button onClick={handleReserve} disabled={reserveLoading || !selectedSlot} style={{
              width: '100%', padding: 16,
              background: !selectedSlot ? '#f5f5f5' : 'var(--tomato)',
              color: !selectedSlot ? '#999' : '#fff',
              border: `2.5px solid ${!selectedSlot ? '#ddd' : 'var(--ink)'}`,
              borderRadius: 14, fontSize: '1rem', fontWeight: 900,
              cursor: !selectedSlot ? 'not-allowed' : 'pointer',
              boxShadow: !selectedSlot ? 'none' : '4px 4px 0 var(--ink)',
              fontFamily: 'inherit'
            }}>
              {reserveLoading
                ? '처리 중...'
                : selectedSlot
                  ? `${selectedSlot.slotTime.slice(0, 5)} · ${headcount}명 예약하기`
                  : '시간을 선택해주세요'}
            </button>
          </div>
        )}

        {/* ── 웨이팅 탭 ───────────────────────────────────────── */}
        {tab === 'waiting' && (
          <div style={{
            background: '#fff', border: '2.5px solid var(--ink)',
            borderRadius: 20, boxShadow: '5px 5px 0 var(--ink)', padding: 24
          }}>
            {waitingSession ? (
              <>
                <div style={{
                  textAlign: 'center', padding: 24, background: 'var(--cream)',
                  borderRadius: 14, border: '2.5px solid var(--ink)',
                  marginBottom: 24, boxShadow: '3px 3px 0 var(--ink)'
                }}>
                  <div style={{ fontSize: '0.88rem', color: 'var(--muted)', fontWeight: 700, marginBottom: 8 }}>
                    현재 대기 팀 수
                  </div>
                  <div style={{ fontSize: '3.5rem', fontWeight: 900, color: 'var(--tomato)', lineHeight: 1 }}>
                    {waitingSession.currentCount}
                  </div>
                  <div style={{ fontSize: '0.88rem', color: 'var(--muted)', marginTop: 8, fontWeight: 700 }}>
                    팀 대기 중
                  </div>
                  <div style={{ marginTop: 12 }}>
                    <span style={{
                      fontSize: '0.78rem', fontWeight: 800, padding: '4px 12px', borderRadius: 999,
                      border: '2px solid var(--ink)',
                      background: waitingSession.status === 'OPEN' ? 'var(--mint)' : 'var(--tomato-light)',
                      color: waitingSession.status === 'OPEN' ? '#fff' : 'var(--tomato)'
                    }}>
                      {waitingSession.status === 'OPEN'
                        ? '● 웨이팅 운영중'
                        : waitingSession.status === 'PAUSED'
                          ? '⏸ 일시정지'
                          : '■ 마감'}
                    </span>
                  </div>
                  {/* 마지막 갱신 시각 */}
                  {waitingUpdatedAt && (
                    <div style={{ fontSize: '0.7rem', color: 'var(--muted)', marginTop: 8 }}>
                      🔄 {formatUpdatedAt(waitingUpdatedAt)}
                    </div>
                  )}
                </div>

                {waitingSession.status === 'OPEN' && isOutsideOperatingHoursNow && (
                  <div style={{
                    textAlign: 'center', padding: '16px', background: 'var(--tomato-light)',
                    border: '2px solid var(--tomato)', borderRadius: 14, fontWeight: 800,
                    color: 'var(--tomato)', fontSize: '0.88rem'
                  }}>
                    지금은 영업시간이 아니라 웨이팅을 등록할 수 없어요.
                    <br />오늘 영업시간: {operatingHoursText}
                  </div>
                )}

                {waitingSession.status === 'OPEN' && !isOutsideOperatingHoursNow && (
                  <>
                    <div style={{ marginBottom: 24 }}>
                      <div style={{ fontSize: '0.92rem', fontWeight: 900, marginBottom: 10 }}>👥 인원 선택</div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
                        <button onClick={() => setPartySize(p => Math.max(1, p - 1))} style={{
                          width: 40, height: 40, borderRadius: '50%', border: '2.5px solid var(--ink)',
                          background: '#fff', fontSize: '1.2rem', cursor: 'pointer', fontWeight: 900,
                          boxShadow: '2px 2px 0 var(--ink)'
                        }}>−</button>
                        <span style={{ fontSize: '1.2rem', fontWeight: 900, minWidth: 40, textAlign: 'center' }}>
                          {partySize}명
                        </span>
                        <button onClick={() => setPartySize(p => Math.min(8, p + 1))} style={{
                          width: 40, height: 40, borderRadius: '50%', border: '2.5px solid var(--ink)',
                          background: '#fff', fontSize: '1.2rem', cursor: 'pointer', fontWeight: 900,
                          boxShadow: '2px 2px 0 var(--ink)'
                        }}>+</button>
                      </div>
                    </div>
                    <button onClick={handleWaiting} disabled={waitingLoading} style={{
                      width: '100%', padding: 16, background: 'var(--tomato)', color: '#fff',
                      border: '2.5px solid var(--ink)', borderRadius: 14, fontSize: '1rem',
                      fontWeight: 900, cursor: 'pointer', boxShadow: '4px 4px 0 var(--ink)',
                      fontFamily: 'inherit'
                    }}>
                      {waitingLoading ? '처리 중...' : `${partySize}명으로 웨이팅 등록하기`}
                    </button>
                  </>
                )}
              </>
            ) : (
              <div className="empty-state">
                <div className="icon">{waitingSessionError ? '⚠️' : '😅'}</div>
                <p>{waitingSessionError ? '웨이팅 정보를 불러오지 못했어요' : '운영 중인 웨이팅 세션이 없어요'}</p>
                {waitingSessionError && (
                  <button onClick={() => void fetchWaitingSession()} style={{
                    marginTop: 10, padding: '8px 16px', border: '2px solid var(--ink)', borderRadius: 10,
                    background: '#fff', fontWeight: 800, fontSize: '0.82rem', cursor: 'pointer'
                  }}>다시 시도</button>
                )}
              </div>
            )}
          </div>
        )}

        {/* ── 리뷰 탭 ─────────────────────────────────────────── */}
        {tab === 'review' && (
          <div>
            <div style={{
              background: '#fff', border: '2.5px solid var(--ink)', borderRadius: 20,
              boxShadow: '5px 5px 0 var(--ink)', padding: 24, marginBottom: 20
            }}>
              <div style={{ fontSize: '1rem', fontWeight: 900, marginBottom: 16 }}>✍️ 리뷰 작성하기</div>
              {!reservationToken && (
                <div style={{
                  padding: '16px 18px', background: 'var(--cream)', borderRadius: 12,
                  border: '2px solid var(--line)', color: 'var(--muted)',
                  fontSize: '0.88rem', fontWeight: 700, lineHeight: 1.6,
                }}>
                  방문 완료된 예약이 있어야 리뷰를 작성할 수 있어요.
                  <br />
                  마이페이지 → 예약 내역 → 방문 완료 예약의 <strong>리뷰 작성</strong> 버튼을 이용해주세요.
                </div>
              )}
              {reservationToken && (
                <>
                  <div style={{ marginBottom: 16 }}>
                    <div style={{ fontSize: '0.88rem', fontWeight: 800, marginBottom: 8 }}>평점</div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                      {[1, 2, 3, 4, 5].map(star => {
                        const selected = star <= reviewRating;
                        return (
                          <button
                            key={star}
                            type="button"
                            aria-label={`${star}점`}
                            onClick={() => setReviewRating(star)}
                            style={{
                              width: 42, height: 42, padding: 0, display: 'grid', placeItems: 'center',
                              fontSize: '1.85rem', lineHeight: 1, background: 'transparent', border: 'none',
                              cursor: 'pointer', opacity: selected ? 1 : 0.35,
                              filter: selected
                                ? 'drop-shadow(1px 0 0 var(--ink)) drop-shadow(-1px 0 0 var(--ink)) drop-shadow(0 1px 0 var(--ink)) drop-shadow(0 -1px 0 var(--ink))'
                                : 'grayscale(1)',
                              transform: selected ? 'scale(1.06)' : 'scale(0.94)',
                              transition: 'opacity 0.15s, filter 0.15s, transform 0.15s',
                            }}
                          >
                            ⭐
                          </button>
                        );
                      })}
                      <span style={{ fontSize: '0.88rem', fontWeight: 900, marginLeft: 4 }}>
                        {reviewRating}점
                      </span>
                    </div>
                  </div>
                  <textarea value={reviewContent} onChange={e => setReviewContent(e.target.value)}
                    placeholder="방문 경험을 자유롭게 남겨주세요 (최소 10자)"
                    rows={4}
                    style={{
                      width: '100%', padding: '12px 16px', border: '2.5px solid var(--ink)',
                      borderRadius: 12, fontSize: '0.92rem', fontFamily: 'inherit', resize: 'none',
                      outline: 'none', background: '#fff', color: 'var(--ink)',
                      boxShadow: '3px 3px 0 var(--ink)'
                    }} />
                  <button onClick={handleReview} disabled={reviewLoading} style={{
                    marginTop: 12, width: '100%', padding: 14, background: 'var(--ink)',
                    color: '#fff', border: '2.5px solid var(--ink)', borderRadius: 12,
                    fontSize: '0.95rem', fontWeight: 900, cursor: 'pointer', fontFamily: 'inherit',
                    boxShadow: '3px 3px 0 var(--tomato)'
                  }}>{reviewLoading ? '등록 중...' : '리뷰 등록하기'}</button>
                </>
              )}
            </div>

            <div style={{
              background: '#fff', border: '2.5px solid var(--ink)', borderRadius: 20,
              boxShadow: '5px 5px 0 var(--ink)', padding: '20px 24px', marginBottom: 16,
              display: 'flex', alignItems: 'center', gap: 20
            }}>
              <div style={{ textAlign: 'center' }}>
                <div style={{ fontSize: '3rem', fontWeight: 900, lineHeight: 1, color: 'var(--tomato)' }}>
                  {averageRating.toFixed(1)}
                </div>
                <div style={{ fontSize: '1rem', marginTop: 4 }}>{'⭐'.repeat(Math.round(averageRating))}</div>
              </div>
              <div style={{ flex: 1 }}>
                <div style={{ fontSize: '0.88rem', fontWeight: 800, color: 'var(--muted)' }}>
                  총 {reviews.length}개 리뷰
                </div>
                <div style={{ marginTop: 8 }}>
                  {[5, 4, 3, 2, 1].map(star => (
                    <div key={star} style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
                      <span style={{ fontSize: '0.78rem', fontWeight: 800, width: 16 }}>{star}</span>
                      <div style={{
                        flex: 1, height: 8, background: 'var(--line)', borderRadius: 999,
                        border: '1.5px solid var(--ink)', overflow: 'hidden'
                      }}>
                        <div style={{
                          height: '100%', background: 'var(--amber)',
                          width: `${star === 5 ? 70 : star === 4 ? 20 : 10}%`
                        }} />
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>

            {reviews.length === 0 ? (
              <div className="empty-state">
                <div className="icon">⭐</div>
                <p>아직 리뷰가 없어요. 첫 리뷰를 남겨주세요!</p>
              </div>
            ) : reviews.map(r => (
              <div key={r.reviewId} style={{
                background: '#fff', border: '2.5px solid var(--ink)',
                borderRadius: 16, boxShadow: '4px 4px 0 var(--ink)', padding: '18px 20px', marginBottom: 12
              }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                    <div style={{
                      width: 36, height: 36, borderRadius: '50%', background: 'var(--tomato-light)',
                      border: '2px solid var(--ink)', display: 'flex', alignItems: 'center',
                      justifyContent: 'center', fontSize: '1rem'
                    }}>👤</div>
                    <div>
                      <div style={{ fontWeight: 900, fontSize: '0.9rem' }}>{r.userName}</div>
                      <div style={{ fontSize: '0.75rem', color: 'var(--muted)' }}>
                        {r.visitedAt
                          ? new Date(r.visitedAt).toLocaleDateString('ko-KR')
                          : new Date(r.createdAt).toLocaleDateString('ko-KR')}
                      </div>
                    </div>
                  </div>
                  <div style={{ display: 'flex', gap: 2 }}>
                    {'⭐'.repeat(r.rating).split('').map((s, i) => (
                      <span key={i} style={{ fontSize: '0.9rem' }}>{s}</span>
                    ))}
                  </div>
                </div>
                <p style={{ fontSize: '0.9rem', color: 'var(--ink)', lineHeight: 1.6, fontWeight: 500 }}>
                  {r.content}
                </p>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
