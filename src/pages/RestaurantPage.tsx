import { useState, useEffect } from 'react';
import { useParams, useNavigate, useSearchParams } from 'react-router-dom';

const USE_DUMMY = true;
import { API_BASE } from '../lib/api';

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
  { reviewId: 1, userName: '김철수', rating: 5, content: '음식이 정말 맛있었어요! 서비스도 친절하고 분위기도 좋았습니다. 다음에 또 방문하고 싶어요.', createdAt: '2026-06-08' },
  { reviewId: 2, userName: '이영희', rating: 4, content: '전반적으로 만족스러웠어요. 대기시간이 조금 있었지만 음식 퀄리티는 훌륭했습니다.', createdAt: '2026-06-05' },
  { reviewId: 3, userName: '박민준', rating: 5, content: '노웨이트 덕분에 웨이팅 없이 바로 입장했어요! 음식도 최고였습니다.', createdAt: '2026-06-01' },
];

function getDummyRestaurant(id: number) {
  const cat = CATEGORIES[id % CATEGORIES.length];
  return {
    id, name: `${NAMES[id % NAMES.length]} ${(id % 5) + 1}호점`,
    category: cat, mainMenuName: MENU[cat], imageUrl: UNSPLASH[cat],
    address: '서울특별시 강남구 테헤란로 123',
    phoneNumber: '02-1234-5678',
    description: '신선한 재료로 만드는 정통 요리를 선보입니다. 매일 아침 직접 장을 봐 최상의 식재료만 사용합니다.',
    openTime: '11:30', closeTime: '21:30', closedDays: '매주 월요일',
    parkingAvailable: 'Y', wifiAvailable: 'Y', multilingualMenuAvailable: 'N',
    status: 'OPEN', reservationAvailable: 'Y', waitingAvailable: 'Y',
    avgRating: 4.7, reviewCount: 128,
  };
}

function getDummySlots(date: string) {
  return ['11:30', '12:00', '12:30', '13:00', '13:30', '18:00', '18:30', '19:00', '19:30', '20:00']
    .map((time, i) => ({ id: i + 1, slotDate: date, slotTime: time, totalCount: 4, remainCount: Math.max(0, 4 - (i % 3)), minHeadcount: 1, maxHeadcount: 8 }));
}

export default function RestaurantPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [tab, setTab] = useState<'reserve' | 'waiting' | 'review'>(
    searchParams.get('tab') === 'review' ? 'review' : 'reserve'
  );
  const [restaurant, setRestaurant] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [isFavorite, setIsFavorite] = useState(false);

  // 예약 상태
  const [selectedDate, setSelectedDate] = useState('');
  const [slots, setSlots] = useState<any[]>([]);
  const [selectedSlot, setSelectedSlot] = useState<any>(null);
  const [headcount, setHeadcount] = useState(2);
  const [reserveLoading, setReserveLoading] = useState(false);

  // 웨이팅 상태
  const [waitingSession, setWaitingSession] = useState<any>(null);
  const [partySize, setPartySize] = useState(2);
  const [waitingLoading, setWaitingLoading] = useState(false);

  // 리뷰 상태
  const [reviews, setReviews] = useState<any[]>([]);
  const [reviewRating, setReviewRating] = useState(5);
  const [reviewContent, setReviewContent] = useState('');
  const [reviewLoading, setReviewLoading] = useState(false);
  const [hasVisited, setHasVisited] = useState(true); // 더미: 방문 완료 예약 있다고 가정

  useEffect(() => {
    const today = new Date().toISOString().split('T')[0];
    setSelectedDate(today);
    fetchRestaurant();
    fetchWaitingSession();
    fetchReviews();
  }, [id]);

  useEffect(() => { if (selectedDate) fetchSlots(); }, [selectedDate]);

  async function fetchRestaurant() {
    setLoading(true);
    try {
      if (USE_DUMMY) { await new Promise(r => setTimeout(r, 300)); setRestaurant(getDummyRestaurant(Number(id))); }
      else { const res = await fetch(`${API_BASE}/restaurants/${id}`); setRestaurant(await res.json()); }
    } finally { setLoading(false); }
  }

  async function fetchSlots() {
    try {
      if (USE_DUMMY) { setSlots(getDummySlots(selectedDate)); }
      else { const res = await fetch(`${API_BASE}/restaurants/${id}/slots?date=${selectedDate}`); setSlots(await res.json()); }
    } catch { setSlots([]); }
  }

  async function fetchWaitingSession() {
    try {
      if (USE_DUMMY) { setWaitingSession({ status: 'OPEN', currentCount: 7, maxWaitingCount: 30 }); }
      else { const res = await fetch(`${API_BASE}/restaurants/${id}/waiting/session`); setWaitingSession(await res.json()); }
    } catch { setWaitingSession(null); }
  }

  async function fetchReviews() {
    try {
      if (USE_DUMMY) { setReviews(DUMMY_REVIEWS); }
      else { const res = await fetch(`${API_BASE}/restaurants/${id}/reviews`); setReviews(await res.json()); }
    } catch { setReviews([]); }
  }

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
          body: JSON.stringify({ restaurantId: Number(id), slotId: selectedSlot.id, headcount }),
        });
        if (!res.ok) { alert('예약에 실패했습니다.'); return; }
        navigate('/mypage');
      }
    } finally { setReserveLoading(false); }
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
        const res = await fetch(`${API_BASE}/waiting`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
          body: JSON.stringify({ restaurantId: Number(id), partySize }),
        });
        if (!res.ok) { alert('웨이팅 등록에 실패했습니다.'); return; }
        navigate('/mypage');
      }
    } finally { setWaitingLoading(false); }
  }

  async function handleReview() {
    if (!reviewContent.trim()) { alert('리뷰 내용을 입력해주세요.'); return; }
    const token = localStorage.getItem('nowait_token');
    if (!token) { navigate('/auth'); return; }
    setReviewLoading(true);
    try {
      if (USE_DUMMY) {
        await new Promise(r => setTimeout(r, 500));
        const newReview = {
          reviewId: Date.now(), userName: '나',
          rating: reviewRating, content: reviewContent,
          createdAt: new Date().toISOString().split('T')[0],
        };
        setReviews(prev => [newReview, ...prev]);
        setReviewContent('');
        setReviewRating(5);
        setHasVisited(false);
        alert('리뷰가 등록됐어요!');
      }
    } finally { setReviewLoading(false); }
  }

  function toggleFavorite() {
    setIsFavorite(prev => !prev);
    // 실제 API: POST/DELETE /api/v1/favorites
  }

  if (loading) return <div style={{ minHeight: '100vh', background: 'var(--cream)' }}><div className="spinner" /></div>;
  if (!restaurant) return <div style={{ minHeight: '100vh', background: 'var(--cream)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}><p>식당을 찾을 수 없어요</p></div>;

  return (
    <div style={{ minHeight: '100vh', background: 'var(--cream)' }}>

      {/* 이미지 헤더 */}
      <div style={{ position: 'relative', height: 320, overflow: 'hidden', background: '#f0e3d7' }}>
        <img src={restaurant.imageUrl} alt={restaurant.name}
          style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
        <div style={{ position: 'absolute', inset: 0, background: 'linear-gradient(to top, rgba(35,26,20,0.7) 0%, transparent 50%)' }} />

        {/* 뒤로가기 */}
        <button onClick={() => navigate(-1)}
          style={{
            position: 'absolute', top: 16, left: 16, width: 40, height: 40,
            borderRadius: '50%', background: '#fff', border: '2.5px solid var(--ink)',
            cursor: 'pointer', fontWeight: 900, fontSize: '1rem', boxShadow: '3px 3px 0 var(--ink)'
          }}>
          ←
        </button>

        {/* 즐겨찾기 버튼 */}
        <button onClick={toggleFavorite}
          style={{
            position: 'absolute', top: 16, right: 16, width: 40, height: 40,
            borderRadius: '50%', background: isFavorite ? 'var(--tomato)' : '#fff',
            border: '2.5px solid var(--ink)', cursor: 'pointer', fontSize: '1.1rem',
            boxShadow: '3px 3px 0 var(--ink)', transition: 'all 0.15s'
          }}>
          {isFavorite ? '❤️' : '🤍'}
        </button>

        <div style={{ position: 'absolute', bottom: 20, left: 24, color: '#fff' }}>
          <span style={{
            background: 'var(--tomato)', padding: '4px 12px', borderRadius: 999,
            fontSize: '0.78rem', fontWeight: 800, border: '2px solid var(--ink)', marginBottom: 8, display: 'inline-block'
          }}>
            {CAT_LABEL[restaurant.category]}
          </span>
          <h1 style={{ fontSize: '1.8rem', fontWeight: 900, marginTop: 6, letterSpacing: '-1px' }}>
            {restaurant.name}
          </h1>
          <div style={{ fontSize: '0.88rem', marginTop: 4, opacity: 0.9 }}>
            ⭐ {restaurant.avgRating} · 리뷰 {restaurant.reviewCount}개
          </div>
        </div>
      </div>

      <div style={{ maxWidth: 800, margin: '0 auto', padding: '0 20px 60px' }}>

        {/* 식당 정보 */}
        <div style={{
          background: '#fff', border: '2.5px solid var(--ink)', borderRadius: 20,
          boxShadow: '5px 5px 0 var(--ink)', padding: 24, margin: '20px 0'
        }}>
          <p style={{ color: 'var(--muted)', fontSize: '0.92rem', lineHeight: 1.7, marginBottom: 16 }}>
            {restaurant.description}
          </p>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))', gap: 12 }}>
            {[
              { icon: '🕐', label: '영업시간', value: `${restaurant.openTime} ~ ${restaurant.closeTime}` },
              { icon: '📍', label: '주소', value: restaurant.address },
              { icon: '📞', label: '전화', value: restaurant.phoneNumber },
              { icon: '🚫', label: '휴무', value: restaurant.closedDays },
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
        <div style={{ display: 'flex', marginBottom: 20, border: '2.5px solid var(--ink)', borderRadius: 14, overflow: 'hidden', boxShadow: '4px 4px 0 var(--ink)' }}>
          {([
            { key: 'reserve', label: '📋 예약하기' },
            { key: 'waiting', label: '⏳ 웨이팅' },
            { key: 'review', label: `⭐ 리뷰 (${reviews.length})` },
          ] as { key: 'reserve' | 'waiting' | 'review'; label: string }[]).map((t, i) => (
            <button key={t.key} onClick={() => setTab(t.key)}
              style={{
                flex: 1, padding: 16, border: 'none', cursor: 'pointer',
                fontWeight: 800, fontSize: '0.92rem', fontFamily: 'inherit',
                borderRight: i < 2 ? '2px solid var(--ink)' : 'none',
                background: tab === t.key ? 'var(--ink)' : '#fff',
                color: tab === t.key ? '#fff' : 'var(--muted)',
                transition: 'all 0.15s'
              }}>
              {t.label}
            </button>
          ))}
        </div>

        {/* 예약 탭 */}
        {tab === 'reserve' && (
          <div style={{ background: '#fff', border: '2.5px solid var(--ink)', borderRadius: 20, boxShadow: '5px 5px 0 var(--ink)', padding: 24 }}>
            {/* 날짜 선택 */}
            <div style={{ marginBottom: 24 }}>
              <div style={{ fontSize: '0.92rem', fontWeight: 900, marginBottom: 10 }}>📅 날짜 선택</div>
              <input type="date" value={selectedDate}
                min={new Date().toISOString().split('T')[0]}
                onChange={e => { setSelectedDate(e.target.value); setSelectedSlot(null); }}
                style={{
                  padding: '10px 16px', border: '2.5px solid var(--ink)', borderRadius: 10,
                  fontSize: '0.92rem', outline: 'none', fontFamily: 'inherit', boxShadow: '3px 3px 0 var(--ink)'
                }} />
            </div>
            {/* 슬롯 */}
            <div style={{ marginBottom: 24 }}>
              <div style={{ fontSize: '0.92rem', fontWeight: 900, marginBottom: 10 }}>🕐 시간 선택</div>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(90px, 1fr))', gap: 8 }}>
                {slots.map(slot => (
                  <button key={slot.id} disabled={slot.remainCount === 0}
                    onClick={() => setSelectedSlot(slot)}
                    style={{
                      padding: '12px 8px', borderRadius: 10, textAlign: 'center',
                      border: `2.5px solid ${selectedSlot?.id === slot.id ? 'var(--tomato)' : 'var(--ink)'}`,
                      background: selectedSlot?.id === slot.id ? 'var(--tomato)' : slot.remainCount === 0 ? '#f5f5f5' : '#fff',
                      color: selectedSlot?.id === slot.id ? '#fff' : slot.remainCount === 0 ? '#ccc' : 'var(--ink)',
                      cursor: slot.remainCount === 0 ? 'not-allowed' : 'pointer',
                      fontWeight: 800, fontSize: '0.88rem',
                      boxShadow: slot.remainCount > 0 ? '2px 2px 0 var(--ink)' : 'none'
                    }}>
                    <div>{slot.slotTime}</div>
                    <div style={{ fontSize: '0.72rem', marginTop: 2 }}>{slot.remainCount === 0 ? '마감' : `잔여 ${slot.remainCount}`}</div>
                  </button>
                ))}
              </div>
            </div>
            {/* 인원 */}
            <div style={{ marginBottom: 24 }}>
              <div style={{ fontSize: '0.92rem', fontWeight: 900, marginBottom: 10 }}>👥 인원 선택</div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
                <button onClick={() => setHeadcount(h => Math.max(1, h - 1))}
                  style={{ width: 40, height: 40, borderRadius: '50%', border: '2.5px solid var(--ink)', background: '#fff', fontSize: '1.2rem', cursor: 'pointer', fontWeight: 900, boxShadow: '2px 2px 0 var(--ink)' }}>−</button>
                <span style={{ fontSize: '1.2rem', fontWeight: 900, minWidth: 40, textAlign: 'center' }}>{headcount}명</span>
                <button onClick={() => setHeadcount(h => Math.min(8, h + 1))}
                  style={{ width: 40, height: 40, borderRadius: '50%', border: '2.5px solid var(--ink)', background: '#fff', fontSize: '1.2rem', cursor: 'pointer', fontWeight: 900, boxShadow: '2px 2px 0 var(--ink)' }}>+</button>
              </div>
            </div>
            <button onClick={handleReserve} disabled={reserveLoading || !selectedSlot}
              style={{
                width: '100%', padding: 16, background: !selectedSlot ? '#f5f5f5' : 'var(--tomato)',
                color: !selectedSlot ? '#999' : '#fff', border: `2.5px solid ${!selectedSlot ? '#ddd' : 'var(--ink)'}`,
                borderRadius: 14, fontSize: '1rem', fontWeight: 900, cursor: !selectedSlot ? 'not-allowed' : 'pointer',
                boxShadow: !selectedSlot ? 'none' : '4px 4px 0 var(--ink)', fontFamily: 'inherit'
              }}>
              {reserveLoading ? '처리 중...' : selectedSlot ? `${selectedSlot.slotTime} · ${headcount}명 예약하기` : '시간을 선택해주세요'}
            </button>
          </div>
        )}

        {/* 웨이팅 탭 */}
        {tab === 'waiting' && (
          <div style={{ background: '#fff', border: '2.5px solid var(--ink)', borderRadius: 20, boxShadow: '5px 5px 0 var(--ink)', padding: 24 }}>
            {waitingSession ? (
              <>
                <div style={{
                  textAlign: 'center', padding: 24, background: 'var(--cream)',
                  borderRadius: 14, border: '2.5px solid var(--ink)', marginBottom: 24, boxShadow: '3px 3px 0 var(--ink)'
                }}>
                  <div style={{ fontSize: '0.88rem', color: 'var(--muted)', fontWeight: 700, marginBottom: 8 }}>현재 대기 팀 수</div>
                  <div style={{ fontSize: '3.5rem', fontWeight: 900, color: 'var(--tomato)', lineHeight: 1 }}>{waitingSession.currentCount}</div>
                  <div style={{ fontSize: '0.88rem', color: 'var(--muted)', marginTop: 8, fontWeight: 700 }}>팀 대기 중</div>
                  <div style={{ marginTop: 12 }}>
                    <span style={{
                      fontSize: '0.78rem', fontWeight: 800, padding: '4px 12px', borderRadius: 999,
                      border: '2px solid var(--ink)', background: waitingSession.status === 'OPEN' ? 'var(--mint)' : 'var(--tomato-light)',
                      color: waitingSession.status === 'OPEN' ? '#fff' : 'var(--tomato)'
                    }}>
                      {waitingSession.status === 'OPEN' ? '● 웨이팅 운영중' : waitingSession.status === 'PAUSED' ? '⏸ 일시정지' : '■ 마감'}
                    </span>
                  </div>
                </div>
                {waitingSession.status === 'OPEN' && (
                  <>
                    <div style={{ marginBottom: 24 }}>
                      <div style={{ fontSize: '0.92rem', fontWeight: 900, marginBottom: 10 }}>👥 인원 선택</div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
                        <button onClick={() => setPartySize(p => Math.max(1, p - 1))}
                          style={{ width: 40, height: 40, borderRadius: '50%', border: '2.5px solid var(--ink)', background: '#fff', fontSize: '1.2rem', cursor: 'pointer', fontWeight: 900, boxShadow: '2px 2px 0 var(--ink)' }}>−</button>
                        <span style={{ fontSize: '1.2rem', fontWeight: 900, minWidth: 40, textAlign: 'center' }}>{partySize}명</span>
                        <button onClick={() => setPartySize(p => Math.min(8, p + 1))}
                          style={{ width: 40, height: 40, borderRadius: '50%', border: '2.5px solid var(--ink)', background: '#fff', fontSize: '1.2rem', cursor: 'pointer', fontWeight: 900, boxShadow: '2px 2px 0 var(--ink)' }}>+</button>
                      </div>
                    </div>
                    <button onClick={handleWaiting} disabled={waitingLoading}
                      style={{
                        width: '100%', padding: 16, background: 'var(--tomato)', color: '#fff',
                        border: '2.5px solid var(--ink)', borderRadius: 14, fontSize: '1rem',
                        fontWeight: 900, cursor: 'pointer', boxShadow: '4px 4px 0 var(--ink)', fontFamily: 'inherit'
                      }}>
                      {waitingLoading ? '처리 중...' : `${partySize}명으로 웨이팅 등록하기`}
                    </button>
                  </>
                )}
              </>
            ) : (
              <div className="empty-state"><div className="icon">😅</div><p>운영 중인 웨이팅 세션이 없어요</p></div>
            )}
          </div>
        )}

        {/* 리뷰 탭 */}
        {tab === 'review' && (
          <div>
            {/* 리뷰 작성 폼 (방문 완료 시만) */}
            {hasVisited && (
              <div style={{
                background: 'var(--amber)', border: '2.5px solid var(--ink)', borderRadius: 20,
                boxShadow: '5px 5px 0 var(--ink)', padding: 24, marginBottom: 20
              }}>
                <div style={{ fontSize: '1rem', fontWeight: 900, marginBottom: 16 }}>✍️ 리뷰 작성하기</div>
                {/* 별점 */}
                <div style={{ marginBottom: 16 }}>
                  <div style={{ fontSize: '0.88rem', fontWeight: 800, marginBottom: 8 }}>평점</div>
                  <div style={{ display: 'flex', gap: 6 }}>
                    {[1, 2, 3, 4, 5].map(star => (
                      <button key={star} onClick={() => setReviewRating(star)}
                        style={{
                          fontSize: '1.6rem', background: 'none', border: 'none', cursor: 'pointer',
                          opacity: star <= reviewRating ? 1 : 0.3, transition: 'opacity 0.15s'
                        }}>
                        ⭐
                      </button>
                    ))}
                    <span style={{ fontSize: '0.88rem', fontWeight: 900, alignSelf: 'center', marginLeft: 4 }}>
                      {reviewRating}점
                    </span>
                  </div>
                </div>
                {/* 내용 */}
                <textarea value={reviewContent} onChange={e => setReviewContent(e.target.value)}
                  placeholder="방문 경험을 자유롭게 남겨주세요 (최소 10자)"
                  rows={4}
                  style={{
                    width: '100%', padding: '12px 16px', border: '2.5px solid var(--ink)',
                    borderRadius: 12, fontSize: '0.92rem', fontFamily: 'inherit', resize: 'none',
                    outline: 'none', background: '#fff', boxShadow: '3px 3px 0 var(--ink)'
                  }} />
                <button onClick={handleReview} disabled={reviewLoading}
                  style={{
                    marginTop: 12, width: '100%', padding: 14, background: 'var(--ink)',
                    color: '#fff', border: '2.5px solid var(--ink)', borderRadius: 12,
                    fontSize: '0.95rem', fontWeight: 900, cursor: 'pointer', fontFamily: 'inherit',
                    boxShadow: '3px 3px 0 var(--tomato)'
                  }}>
                  {reviewLoading ? '등록 중...' : '리뷰 등록하기'}
                </button>
              </div>
            )}

            {/* 리뷰 평균 */}
            <div style={{
              background: '#fff', border: '2.5px solid var(--ink)', borderRadius: 20,
              boxShadow: '5px 5px 0 var(--ink)', padding: '20px 24px', marginBottom: 16,
              display: 'flex', alignItems: 'center', gap: 20
            }}>
              <div style={{ textAlign: 'center' }}>
                <div style={{ fontSize: '3rem', fontWeight: 900, lineHeight: 1, color: 'var(--tomato)' }}>
                  {restaurant.avgRating}
                </div>
                <div style={{ fontSize: '1rem', marginTop: 4 }}>{'⭐'.repeat(Math.round(restaurant.avgRating))}</div>
              </div>
              <div style={{ flex: 1 }}>
                <div style={{ fontSize: '0.88rem', fontWeight: 800, color: 'var(--muted)' }}>
                  총 {restaurant.reviewCount}개 리뷰
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

            {/* 리뷰 목록 */}
            {reviews.length === 0 ? (
              <div className="empty-state"><div className="icon">⭐</div><p>아직 리뷰가 없어요. 첫 리뷰를 남겨주세요!</p></div>
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
                      <div style={{ fontSize: '0.75rem', color: 'var(--muted)' }}>{r.createdAt}</div>
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