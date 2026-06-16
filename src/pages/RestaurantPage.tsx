import { useState, useEffect, useRef, useCallback } from 'react';
import { useParams, useNavigate, useSearchParams } from 'react-router-dom';

const USE_DUMMY = false;
import { API_BASE, request, resolveImageUrl } from '../lib/api';

// ─── 더미 데이터 ───────────────────────────────────────────────
const UNSPLASH: Record<string, string> = {
  KOREAN: 'https://images.unsplash.com/photo-1583224944844-5b268c057b72?w=800&q=80',
  JAPANESE: 'https://images.unsplash.com/photo-1579871494447-9811cf80d66c?w=800&q=80',
  CHINESE: 'https://images.unsplash.com/photo-1563245372-f21724e3856d?w=800&q=80',
  WESTERN: 'https://images.unsplash.com/photo-1414235077428-338989a2e8c0?w=800&q=80',
  ASIAN: 'https://images.unsplash.com/photo-1559314809-0d155014e29e?w=800&q=80',
};
const CAT_LABEL: Record<string, string> = {
  KOREAN: '한식', JAPANESE: '일식', CHINESE: '중식', WESTERN: '양식', ASIAN: '아시안',
};
const CATEGORIES = ['KOREAN', 'JAPANESE', 'CHINESE', 'WESTERN', 'ASIAN'];
const NAMES = ['미진', '스시콜', '왕가원', '비스트로 르', '방콕포차', '한상차림', '오마카세 정', '딘타이펑'];
const MENU: Record<string, string> = {
  KOREAN: '제육볶음', JAPANESE: '특선 스시', CHINESE: '마파두부',
  WESTERN: '안심 스테이크', ASIAN: '팟타이',
};
const DUMMY_REVIEWS = [
  { reviewId: 1, userName: '김철수', rating: 5, content: '음식이 정말 맛있었어요! 서비스도 친절하고 분위기도 좋았습니다.', createdAt: '2026-06-08' },
  { reviewId: 2, userName: '이영희', rating: 4, content: '전반적으로 만족스러웠어요. 대기시간이 조금 있었지만 음식 퀄리티는 훌륭했습니다.', createdAt: '2026-06-05' },
  { reviewId: 3, userName: '박민준', rating: 5, content: '노웨이트 덕분에 웨이팅 없이 바로 입장했어요!', createdAt: '2026-06-01' },
];

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



function getDummyRestaurant(id: number) {
  const cat = CATEGORIES[id % CATEGORIES.length];
  return {
    id, name: `${NAMES[id % NAMES.length]} ${(id % 5) + 1}호점`,
    category: cat, mainMenuName: MENU[cat], imageUrl: UNSPLASH[cat],
    address: '서울특별시 강남구 테헤란로 123', phoneNumber: '02-1234-5678',
    description: '신선한 재료로 만드는 정통 요리를 선보입니다.',
    openTime: '11:30', closeTime: '21:30', closedDays: '매주 월요일',
    parkingAvailable: 'Y', wifiAvailable: 'Y', multilingualMenuAvailable: 'N',
    status: 'OPEN', reservationAvailable: 'Y', waitingAvailable: 'Y',
    avgRating: 4.7, reviewCount: 128,
  };
}
function getDummySlots(date: string) {
  return ['11:30', '12:00', '12:30', '13:00', '13:30', '18:00', '18:30', '19:00', '19:30', '20:00']
    .map((time, i) => ({
      slotId: i + 1, slotDate: date, slotTime: time,
      totalCount: 4, remainCount: Math.max(0, 4 - (i % 3)),
      minHeadcount: 1, maxHeadcount: 8,
    }));
}

// ─── 유틸 ──────────────────────────────────────────────────────

/** 오늘 날짜를 "YYYY-MM-DD" 형태로 반환 */
function getTodayStr() {
  return new Date().toISOString().split('T')[0];
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
  const [selectedSlot, setSelectedSlot] = useState<Slot | null>(null);
  const [headcount, setHeadcount] = useState(2);
  const [reserveLoading, setReserveLoading] = useState(false);

  // 웨이팅
  const [waitingSession, setWaitingSession] = useState<WaitingSession | null>(null);
  const [waitingUpdatedAt, setWaitingUpdatedAt] = useState<Date | null>(null);
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
      if (USE_DUMMY) {
        await new Promise(r => setTimeout(r, 300));
        setRestaurant(getDummyRestaurant(Number(id)));
      } else {
        const [detailResponse, hoursResponse] = await Promise.all([
          fetch(`${API_BASE}/restaurants/${id}`),
          fetch(`${API_BASE}/restaurants/${id}/hours`),
        ]);
        if (!detailResponse.ok) throw new Error('식당 정보를 불러오지 못했습니다.');
        setRestaurant(await detailResponse.json() as RestaurantDetail);
        setRestaurantHours(hoursResponse.ok ? await hoursResponse.json() as RestaurantHour[] : []);
      }
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
      let raw: Slot[] = [];
      if (USE_DUMMY) {
        raw = getDummySlots(date);
      } else {
        const res = await fetch(`${API_BASE}/restaurants/${id}/slots?date=${date}`);
        if (!res.ok) throw new Error('슬롯 정보를 불러오지 못했습니다.');
        const data = await res.json() as { slots?: Slot[] } | Slot[];
        // 백엔드 응답: { restaurantId, date, slots: [...] }
        raw = Array.isArray(data) ? data : (data.slots || []);
      }

      // 지난 시간 슬롯을 remainCount=0 으로 표시 (서버 값 보존 + UI 비활성화)
      const processed = raw
        .filter(slot => slot.slotTime.slice(3, 5) === '00')
        .map(slot => ({
          ...slot,
          _isPast: isPastSlot(slot.slotDate ?? date, slot.slotTime),
        }));

      setSlots(processed);
      setSlotsUpdatedAt(new Date());

      // 선택된 슬롯이 이미 마감/과거가 됐으면 선택 해제
      setSelectedSlot(prev => {
        if (!prev) return prev;
        const refreshed = processed.find(s => s.slotId === prev.slotId);
        if (!refreshed || refreshed.remainCount === 0 || refreshed._isPast) return null;
        return refreshed; // 최신 remainCount 반영
      });
    } catch {
      setSlots([]);
    }
  }, [id]);

  /** 웨이팅 세션 조회 */
  const fetchWaitingSession = useCallback(async () => {
    try {
      if (USE_DUMMY) {
        setWaitingSession({ status: 'OPEN', currentCount: 7, maxWaitingCount: 30 });
      } else {
        const res = await fetch(`${API_BASE}/restaurants/${id}/waiting-session`);
        if (res.ok) {
          setWaitingSession(await res.json() as WaitingSession);
        } else {
          setWaitingSession(null);
        }
      }
      setWaitingUpdatedAt(new Date());
    } catch {
      setWaitingSession(null);
    }
  }, [id]);

  const fetchReviews = useCallback(async () => {
    try {
      if (USE_DUMMY) { setReviews(DUMMY_REVIEWS); return; }
      const data = await request<Review[]>(`/restaurants/${id}/reviews`);
      setReviews(data);
    } catch { setReviews([]); }
  }, [id]);

  const fetchFavoriteStatus = useCallback(async () => {
    const token = localStorage.getItem('nowait_token');
    if (!token || USE_DUMMY) return;
    try {
      const favorites = await request<FavoriteSummary[]>('/users/me/favorites');
      setIsFavorite(favorites.some(f => String(f.restaurantId) === id));
    } catch { /* 로그인 안 된 경우 등 무시 */ }
  }, [id]);

  // ── SSE 개인 알림 연결 ───────────────────────────────────────
  // 로그인한 경우에만 연결. "waiting_called" 이벤트 수신 시 웨이팅 즉시 갱신.
  const connectSse = useCallback(async () => {
    const token = localStorage.getItem('nowait_token');
    if (!token || USE_DUMMY) return;

    try {
      // 1) 단발 티켓 발급
      const res = await fetch(`${API_BASE}/notifications/stream/ticket`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!res.ok) return;
      const { ticket } = await res.json() as { ticket: string };

      // 2) SSE 연결
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
      if (USE_DUMMY) {
        await new Promise(r => setTimeout(r, 600));
        alert(`예약 완료!\n${selectedSlot.slotTime} / ${headcount}명`);
        navigate('/mypage');
      } else {
        const res = await fetch(`${API_BASE}/reservations`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
          body: JSON.stringify({
            restaurantId: Number(id),
            slotId: selectedSlot.slotId, // ← slotId 사용
            headcount,
          }),
        });
        if (!res.ok) {
          const err = await res.json().catch(() => ({}));
          alert(err.message || '예약에 실패했습니다.');
          // 실패 후 슬롯 즉시 재조회 (다른 사람이 예약해서 마감됐을 수 있음)
          void fetchSlots(selectedDate);
          return;
        }
        navigate('/mypage');
      }
    } finally {
      setReserveLoading(false);
    }
  }

  async function handleWaiting() {
    const token = localStorage.getItem('nowait_token');
    if (!token) { navigate('/auth'); return; }
    setWaitingLoading(true);
    try {
      if (USE_DUMMY) {
        await new Promise(r => setTimeout(r, 600));
        alert(`웨이팅 등록 완료!\n대기번호: ${(waitingSession?.currentCount || 0) + 1}번`);
        navigate('/mypage');
      } else {
        const res = await fetch(`${API_BASE}/restaurants/${id}/waitings`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
          body: JSON.stringify({ partySize }),
        });
        if (!res.ok) {
          const err = await res.json().catch(() => ({}));
          alert(err.message || '웨이팅 등록에 실패했습니다.');
          return;
        }
        // 등록 직후 웨이팅 세션 즉시 갱신
        await fetchWaitingSession();
        navigate('/mypage');
      }
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
      if (USE_DUMMY) {
        await new Promise(r => setTimeout(r, 500));
        setReviews(prev => [{
          reviewId: Date.now(), userName: '나',
          rating: reviewRating, content: reviewContent,
          createdAt: getTodayStr(),
        }, ...prev]);
        setReviewContent('');
        setReviewRating(5);
        alert('리뷰가 등록됐어요!');
      } else {
        await request(`/reservations/${reservationToken}/reviews`, {
          method: 'POST',
          body: JSON.stringify({ rating: reviewRating, content: reviewContent.trim() }),
        });
        setReviewContent('');
        setReviewRating(5);
        await fetchReviews();
        alert('리뷰가 등록됐어요!');
      }
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
                  📭 해당 날짜에 예약 가능한 슬롯이 없어요
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
                        onClick={() => setSelectedSlot(slot)}
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
                <button onClick={() => setHeadcount(h => Math.max(1, h - 1))} style={{
                  width: 40, height: 40, borderRadius: '50%', border: '2.5px solid var(--ink)',
                  background: '#fff', fontSize: '1.2rem', cursor: 'pointer', fontWeight: 900,
                  boxShadow: '2px 2px 0 var(--ink)'
                }}>−</button>
                <span style={{ fontSize: '1.2rem', fontWeight: 900, minWidth: 40, textAlign: 'center' }}>
                  {headcount}명
                </span>
                <button onClick={() => setHeadcount(h => Math.min(8, h + 1))} style={{
                  width: 40, height: 40, borderRadius: '50%', border: '2.5px solid var(--ink)',
                  background: '#fff', fontSize: '1.2rem', cursor: 'pointer', fontWeight: 900,
                  boxShadow: '2px 2px 0 var(--ink)'
                }}>+</button>
              </div>
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

                {waitingSession.status === 'OPEN' && (
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
                <div className="icon">😅</div>
                <p>운영 중인 웨이팅 세션이 없어요</p>
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
              {!USE_DUMMY && !reservationToken && (
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
              {(USE_DUMMY || reservationToken) && (
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
