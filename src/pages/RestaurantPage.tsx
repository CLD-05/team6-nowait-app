import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import Header from '../components/Header';

const USE_DUMMY = true;
const API_BASE = '/api/v1';

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
const NAMES = ['미진', '스시콜', '왕가원', '비스트로 르', '방콕포차', '한상차림', '오마카세 정', '딘타이펑', '트라토리아', '쌀국수집'];
const MENU: Record<string, string> = { KOREAN: '제육볶음', JAPANESE: '특선 스시', CHINESE: '마파두부', WESTERN: '안심 스테이크', ASIAN: '팟타이' };

function getDummyRestaurant(id: number) {
  const cat = CATEGORIES[id % CATEGORIES.length];
  return {
    id,
    name: `${NAMES[id % NAMES.length]} ${(id % 5) + 1}호점`,
    category: cat,
    mainMenuName: MENU[cat],
    imageUrl: UNSPLASH[cat],
    address: '서울특별시 강남구 테헤란로 123',
    phoneNumber: '02-1234-5678',
    description: '신선한 재료로 만드는 정통 요리를 선보입니다. 매일 아침 직접 장을 봐 최상의 식재료만 사용합니다.',
    openTime: '11:30',
    closeTime: '21:30',
    closedDays: '매주 월요일',
    parkingAvailable: 'Y',
    wifiAvailable: 'Y',
    multilingualMenuAvailable: 'N',
    status: 'OPEN',
    reservationAvailable: 'Y',
    waitingAvailable: 'Y',
  };
}

function getDummySlots(date: string) {
  return ['11:30', '12:00', '12:30', '13:00', '13:30', '18:00', '18:30', '19:00', '19:30', '20:00'].map((time, i) => ({
    id: i + 1,
    slotDate: date,
    slotTime: time,
    totalCount: 4,
    remainCount: Math.max(0, 4 - (i % 3)),
    minHeadcount: 1,
    maxHeadcount: 8,
  }));
}

export default function RestaurantPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [tab, setTab] = useState<'reserve' | 'waiting'>('reserve');
  const [restaurant, setRestaurant] = useState<any>(null);
  const [loading, setLoading] = useState(true);

  // 예약 관련 상태
  const [selectedDate, setSelectedDate] = useState('');
  const [slots, setSlots] = useState<any[]>([]);
  const [selectedSlot, setSelectedSlot] = useState<any>(null);
  const [headcount, setHeadcount] = useState(2);
  const [reserveLoading, setReserveLoading] = useState(false);

  // 웨이팅 관련 상태
  const [waitingSession, setWaitingSession] = useState<any>(null);
  const [partySize, setPartySize] = useState(2);
  const [waitingLoading, setWaitingLoading] = useState(false);

  // 오늘 날짜 기본값
  useEffect(() => {
    const today = new Date().toISOString().split('T')[0];
    setSelectedDate(today);
    fetchRestaurant();
    fetchWaitingSession();
  }, [id]);

  useEffect(() => {
    if (selectedDate) fetchSlots();
  }, [selectedDate]);

  async function fetchRestaurant() {
    setLoading(true);
    try {
      if (USE_DUMMY) {
        await new Promise(r => setTimeout(r, 300));
        setRestaurant(getDummyRestaurant(Number(id)));
      } else {
        const res = await fetch(`${API_BASE}/restaurants/${id}`);
        setRestaurant(await res.json());
      }
    } finally {
      setLoading(false);
    }
  }

  async function fetchSlots() {
    try {
      if (USE_DUMMY) {
        setSlots(getDummySlots(selectedDate));
      } else {
        const res = await fetch(`${API_BASE}/restaurants/${id}/slots?date=${selectedDate}`);
        setSlots(await res.json());
      }
    } catch { setSlots([]); }
  }

  async function fetchWaitingSession() {
    try {
      if (USE_DUMMY) {
        setWaitingSession({ status: 'OPEN', currentCount: 7, maxWaitingCount: 30 });
      } else {
        const res = await fetch(`${API_BASE}/restaurants/${id}/waiting/session`);
        setWaitingSession(await res.json());
      }
    } catch { setWaitingSession(null); }
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
        alert('예약이 완료됐어요!');
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
        alert(`웨이팅 등록 완료!\n대기번호: ${(waitingSession?.currentCount || 0) + 1}번 / ${partySize}명`);
        navigate('/mypage');
      } else {
        const res = await fetch(`${API_BASE}/waiting`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
          body: JSON.stringify({ restaurantId: Number(id), partySize }),
        });
        if (!res.ok) { alert('웨이팅 등록에 실패했습니다.'); return; }
        alert('웨이팅 등록이 완료됐어요!');
        navigate('/mypage');
      }
    } finally {
      setWaitingLoading(false);
    }
  }

  if (loading) return <div><Header /><div className="spinner" /></div>;
  if (!restaurant) return <div><Header /><div className="empty-state"><p>식당을 찾을 수 없어요</p></div></div>;

  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg)' }}>
      <Header />

      {/* 식당 이미지 */}
      <div style={{ position: 'relative', height: '320px', overflow: 'hidden', background: '#f0f0f0' }}>
        <img src={restaurant.imageUrl} alt={restaurant.name} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
        <div style={{ position: 'absolute', inset: 0, background: 'linear-gradient(to top, rgba(0,0,0,0.5) 0%, transparent 50%)' }} />
        <div style={{ position: 'absolute', bottom: '24px', left: '24px', color: '#fff' }}>
          <span style={{ background: 'var(--primary)', padding: '4px 12px', borderRadius: '50px', fontSize: '0.78rem', fontWeight: 600, marginBottom: '8px', display: 'inline-block' }}>
            {CAT_LABEL[restaurant.category]}
          </span>
          <h1 style={{ fontSize: '1.8rem', fontWeight: 800, marginTop: '6px' }}>{restaurant.name}</h1>
        </div>
      </div>

      <div style={{ maxWidth: '800px', margin: '0 auto', padding: '0 20px 60px' }}>

        {/* 식당 정보 */}
        <div style={{ background: '#fff', borderRadius: 'var(--radius-md)', boxShadow: 'var(--shadow)', padding: '24px', margin: '24px 0' }}>
          <p style={{ color: 'var(--text-sub)', fontSize: '0.92rem', lineHeight: 1.7, marginBottom: '16px' }}>{restaurant.description}</p>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))', gap: '12px' }}>
            {[
              { icon: '🕐', label: '영업시간', value: `${restaurant.openTime} ~ ${restaurant.closeTime}` },
              { icon: '📍', label: '주소', value: restaurant.address },
              { icon: '📞', label: '전화', value: restaurant.phoneNumber },
              { icon: '🚫', label: '휴무', value: restaurant.closedDays },
            ].map(item => (
              <div key={item.label} style={{ display: 'flex', gap: '8px', alignItems: 'flex-start' }}>
                <span>{item.icon}</span>
                <div>
                  <div style={{ fontSize: '0.75rem', color: 'var(--text-sub)', marginBottom: '2px' }}>{item.label}</div>
                  <div style={{ fontSize: '0.88rem', fontWeight: 600 }}>{item.value}</div>
                </div>
              </div>
            ))}
          </div>

          {/* 편의시설 */}
          <div style={{ display: 'flex', gap: '8px', marginTop: '16px', flexWrap: 'wrap' }}>
            {restaurant.parkingAvailable === 'Y' && <span style={{ fontSize: '0.78rem', fontWeight: 600, padding: '4px 12px', borderRadius: '50px', background: '#F0FDF4', color: '#16A34A' }}>🅿️ 주차가능</span>}
            {restaurant.wifiAvailable === 'Y' && <span style={{ fontSize: '0.78rem', fontWeight: 600, padding: '4px 12px', borderRadius: '50px', background: '#EFF6FF', color: '#2563EB' }}>📶 와이파이</span>}
            {restaurant.multilingualMenuAvailable === 'Y' && <span style={{ fontSize: '0.78rem', fontWeight: 600, padding: '4px 12px', borderRadius: '50px', background: '#FFF3EF', color: 'var(--primary)' }}>🌐 다국어메뉴</span>}
          </div>
        </div>

        {/* 예약 / 웨이팅 탭 */}
        <div style={{ display: 'flex', background: '#fff', borderRadius: 'var(--radius-md)', boxShadow: 'var(--shadow)', overflow: 'hidden', marginBottom: '20px' }}>
          {(['reserve', 'waiting'] as const).map(t => (
            <button
              key={t}
              onClick={() => setTab(t)}
              style={{
                flex: 1, padding: '16px', border: 'none', cursor: 'pointer', fontWeight: 700,
                fontSize: '0.95rem', transition: 'all 0.15s',
                background: tab === t ? 'var(--primary)' : '#fff',
                color: tab === t ? '#fff' : 'var(--text-sub)',
              }}
            >
              {t === 'reserve' ? '📋 예약하기' : '⏳ 웨이팅 등록'}
            </button>
          ))}
        </div>

        {/* 예약 탭 */}
        {tab === 'reserve' && (
          <div style={{ background: '#fff', borderRadius: 'var(--radius-md)', boxShadow: 'var(--shadow)', padding: '24px' }}>

            {/* 날짜 선택 */}
            <div style={{ marginBottom: '24px' }}>
              <label style={{ fontSize: '0.9rem', fontWeight: 700, display: 'block', marginBottom: '10px' }}>📅 날짜 선택</label>
              <input
                type="date" value={selectedDate}
                min={new Date().toISOString().split('T')[0]}
                onChange={e => { setSelectedDate(e.target.value); setSelectedSlot(null); }}
                style={{ padding: '10px 16px', border: '1.5px solid var(--border)', borderRadius: 'var(--radius-sm)', fontSize: '0.92rem', outline: 'none', cursor: 'pointer' }}
              />
            </div>

            {/* 슬롯 선택 */}
            <div style={{ marginBottom: '24px' }}>
              <label style={{ fontSize: '0.9rem', fontWeight: 700, display: 'block', marginBottom: '10px' }}>🕐 시간 선택</label>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(100px, 1fr))', gap: '8px' }}>
                {slots.map(slot => (
                  <button
                    key={slot.id}
                    disabled={slot.remainCount === 0}
                    onClick={() => setSelectedSlot(slot)}
                    style={{
                      padding: '12px 8px', borderRadius: 'var(--radius-sm)', border: `1.5px solid ${selectedSlot?.id === slot.id ? 'var(--primary)' : slot.remainCount === 0 ? 'var(--border)' : 'var(--border)'}`,
                      background: selectedSlot?.id === slot.id ? 'var(--primary-light)' : slot.remainCount === 0 ? '#f5f5f5' : '#fff',
                      color: selectedSlot?.id === slot.id ? 'var(--primary)' : slot.remainCount === 0 ? '#ccc' : 'var(--text)',
                      cursor: slot.remainCount === 0 ? 'not-allowed' : 'pointer',
                      fontWeight: 600, fontSize: '0.88rem', transition: 'all 0.15s',
                      textAlign: 'center',
                    }}
                  >
                    <div>{slot.slotTime}</div>
                    <div style={{ fontSize: '0.72rem', marginTop: '2px', color: slot.remainCount === 0 ? '#ccc' : 'var(--text-sub)' }}>
                      {slot.remainCount === 0 ? '마감' : `잔여 ${slot.remainCount}`}
                    </div>
                  </button>
                ))}
              </div>
            </div>

            {/* 인원 선택 */}
            <div style={{ marginBottom: '24px' }}>
              <label style={{ fontSize: '0.9rem', fontWeight: 700, display: 'block', marginBottom: '10px' }}>👥 인원 선택</label>
              <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                <button onClick={() => setHeadcount(h => Math.max(1, h - 1))}
                  style={{ width: '40px', height: '40px', borderRadius: '50%', border: '1.5px solid var(--border)', background: '#fff', fontSize: '1.2rem', cursor: 'pointer' }}>−</button>
                <span style={{ fontSize: '1.2rem', fontWeight: 700, minWidth: '40px', textAlign: 'center' }}>{headcount}명</span>
                <button onClick={() => setHeadcount(h => Math.min(8, h + 1))}
                  style={{ width: '40px', height: '40px', borderRadius: '50%', border: '1.5px solid var(--border)', background: '#fff', fontSize: '1.2rem', cursor: 'pointer' }}>+</button>
              </div>
            </div>

            {/* 예약 버튼 */}
            <button
              onClick={handleReserve} disabled={reserveLoading || !selectedSlot}
              style={{ width: '100%', padding: '16px', background: !selectedSlot ? '#ccc' : 'var(--primary)', color: '#fff', border: 'none', borderRadius: 'var(--radius-sm)', fontSize: '1rem', fontWeight: 700, cursor: !selectedSlot ? 'not-allowed' : 'pointer', transition: 'background 0.15s' }}
            >
              {reserveLoading ? '처리 중...' : selectedSlot ? `${selectedSlot.slotTime} · ${headcount}명 예약하기` : '시간을 선택해주세요'}
            </button>
          </div>
        )}

        {/* 웨이팅 탭 */}
        {tab === 'waiting' && (
          <div style={{ background: '#fff', borderRadius: 'var(--radius-md)', boxShadow: 'var(--shadow)', padding: '24px' }}>

            {/* 웨이팅 현황 */}
            {waitingSession ? (
              <>
                <div style={{ textAlign: 'center', padding: '24px', background: 'var(--primary-light)', borderRadius: 'var(--radius-md)', marginBottom: '24px' }}>
                  <div style={{ fontSize: '0.88rem', color: 'var(--text-sub)', marginBottom: '8px' }}>현재 대기 팀 수</div>
                  <div style={{ fontSize: '3rem', fontWeight: 800, color: 'var(--primary)', lineHeight: 1 }}>{waitingSession.currentCount}</div>
                  <div style={{ fontSize: '0.88rem', color: 'var(--text-sub)', marginTop: '8px' }}>팀 대기 중</div>
                  <div style={{ marginTop: '12px' }}>
                    <span style={{ fontSize: '0.78rem', fontWeight: 600, padding: '4px 12px', borderRadius: '50px', background: waitingSession.status === 'OPEN' ? '#F0FDF4' : '#FFF3EF', color: waitingSession.status === 'OPEN' ? '#16A34A' : 'var(--primary)' }}>
                      {waitingSession.status === 'OPEN' ? '● 웨이팅 운영중' : waitingSession.status === 'PAUSED' ? '⏸ 일시정지' : '■ 마감'}
                    </span>
                  </div>
                </div>

                {waitingSession.status === 'OPEN' && (
                  <>
                    {/* 인원 선택 */}
                    <div style={{ marginBottom: '24px' }}>
                      <label style={{ fontSize: '0.9rem', fontWeight: 700, display: 'block', marginBottom: '10px' }}>👥 인원 선택</label>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                        <button onClick={() => setPartySize(p => Math.max(1, p - 1))}
                          style={{ width: '40px', height: '40px', borderRadius: '50%', border: '1.5px solid var(--border)', background: '#fff', fontSize: '1.2rem', cursor: 'pointer' }}>−</button>
                        <span style={{ fontSize: '1.2rem', fontWeight: 700, minWidth: '40px', textAlign: 'center' }}>{partySize}명</span>
                        <button onClick={() => setPartySize(p => Math.min(8, p + 1))}
                          style={{ width: '40px', height: '40px', borderRadius: '50%', border: '1.5px solid var(--border)', background: '#fff', fontSize: '1.2rem', cursor: 'pointer' }}>+</button>
                      </div>
                    </div>

                    <button
                      onClick={handleWaiting} disabled={waitingLoading}
                      style={{ width: '100%', padding: '16px', background: 'var(--primary)', color: '#fff', border: 'none', borderRadius: 'var(--radius-sm)', fontSize: '1rem', fontWeight: 700, cursor: 'pointer' }}
                    >
                      {waitingLoading ? '처리 중...' : `${partySize}명으로 웨이팅 등록하기`}
                    </button>
                  </>
                )}

                {waitingSession.status !== 'OPEN' && (
                  <div className="empty-state">
                    <div className="icon">⏸️</div>
                    <p>현재 웨이팅을 받지 않고 있어요</p>
                  </div>
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
      </div>
    </div>
  );
}