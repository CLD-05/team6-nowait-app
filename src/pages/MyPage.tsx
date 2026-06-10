import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';

const USE_DUMMY = true;
const API_BASE = '/api/v1';

const DUMMY_RESERVATIONS = [
  { reservationId: 1, restaurantName: '미진 1호점', slotDate: '2026-06-10', slotTime: '12:00', headcount: 2, status: 'CONFIRMED' },
  { reservationId: 2, restaurantName: '스시콜 2호점', slotDate: '2026-06-08', slotTime: '19:00', headcount: 4, status: 'VISITED' },
  { reservationId: 3, restaurantName: '왕가원 3호점', slotDate: '2026-06-05', slotTime: '13:00', headcount: 2, status: 'CANCELLED' },
  { reservationId: 4, restaurantName: '비스트로 르', slotDate: '2026-06-03', slotTime: '18:30', headcount: 3, status: 'NO_SHOW' },
];

const DUMMY_WAITINGS = [
  { waitingId: 1, restaurantName: '미진 1호점', waitingNumber: 5, partySize: 2, status: 'WAITING' },
  { waitingId: 2, restaurantName: '스시콜 2호점', waitingNumber: 3, partySize: 4, status: 'CALLED' },
  { waitingId: 3, restaurantName: '왕가원 3호점', waitingNumber: 7, partySize: 2, status: 'ENTERED' },
];

const DUMMY_FAVORITES = [
  { favoriteId: 1, restaurantId: 1, restaurantName: '미진 1호점', category: 'KOREAN', mainMenuName: '제육볶음', imageUrl: 'https://images.unsplash.com/photo-1583224944844-5b268c057b72?w=400&q=80' },
  { favoriteId: 2, restaurantId: 2, restaurantName: '스시콜 2호점', category: 'JAPANESE', mainMenuName: '특선 스시', imageUrl: 'https://images.unsplash.com/photo-1579871494447-9811cf80d66c?w=400&q=80' },
  { favoriteId: 3, restaurantId: 3, restaurantName: '왕가원 3호점', category: 'CHINESE', mainMenuName: '마파두부', imageUrl: 'https://images.unsplash.com/photo-1563245372-f21724e3856d?w=400&q=80' },
];

const DUMMY_NOTIFICATIONS = [
  { notificationId: 1, type: 'WAITING_CALLED', message: '미진 1호점 웨이팅 차례가 됐어요! 10분 안에 입장해주세요.', isRead: 'N', createdAt: '2026-06-10 12:30' },
  { notificationId: 2, type: 'RESERVATION_CONFIRMED', message: '스시콜 2호점 예약이 확정됐어요. 6월 8일 19:00 4명', isRead: 'N', createdAt: '2026-06-08 10:00' },
  { notificationId: 3, type: 'REVIEW_REQUEST', message: '왕가원 3호점 방문 어떠셨나요? 리뷰를 남겨주세요!', isRead: 'Y', createdAt: '2026-06-05 20:00' },
  { notificationId: 4, type: 'RESERVATION_CANCELLED', message: '비스트로 르 예약이 취소됐어요.', isRead: 'Y', createdAt: '2026-06-03 15:00' },
  { notificationId: 5, type: 'WAITING_REGISTERED', message: '방콕포차 웨이팅 8번으로 등록됐어요.', isRead: 'Y', createdAt: '2026-06-01 18:00' },
];

const CAT_LABEL: Record<string, string> = {
  KOREAN: '한식', JAPANESE: '일식', CHINESE: '중식', WESTERN: '양식', ASIAN: '아시안',
};

const STATUS_LABEL: Record<string, string> = {
  CONFIRMED: '예약확정', VISITED: '방문완료', CANCELLED: '취소', NO_SHOW: '노쇼',
  WAITING: '대기중', CALLED: '호출됨', ENTERED: '입장완료',
};

const NOTI_LABEL: Record<string, { icon: string; color: string }> = {
  WAITING_CALLED: { icon: '🔔', color: '#ff5a3c' },
  RESERVATION_CONFIRMED: { icon: '📋', color: '#16b886' },
  REVIEW_REQUEST: { icon: '⭐', color: '#ffb22e' },
  RESERVATION_CANCELLED: { icon: '❌', color: '#999' },
  WAITING_REGISTERED: { icon: '⏳', color: '#7c3aed' },
  WAITING_CANCELLED: { icon: '❌', color: '#999' },
};

type Tab = 'reservation' | 'waiting' | 'favorite' | 'notification';

export default function MyPage() {
  const navigate = useNavigate();
  const [tab, setTab] = useState<Tab>('reservation');
  const [reservations, setReservations] = useState<any[]>([]);
  const [waitings, setWaitings] = useState<any[]>([]);
  const [favorites, setFavorites] = useState<any[]>([]);
  const [notifications, setNotifications] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [statusFilter, setStatusFilter] = useState('ALL');

  const user = JSON.parse(localStorage.getItem('nowait_user') || '{}');
  const unreadCount = notifications.filter(n => n.isRead === 'N').length;

  useEffect(() => {
    const token = localStorage.getItem('nowait_token');
    if (!token) { navigate('/auth'); return; }
    fetchData();
  }, []);

  async function fetchData() {
    setLoading(true);
    try {
      if (USE_DUMMY) {
        await new Promise(r => setTimeout(r, 300));
        setReservations(DUMMY_RESERVATIONS);
        setWaitings(DUMMY_WAITINGS);
        setFavorites(DUMMY_FAVORITES);
        setNotifications(DUMMY_NOTIFICATIONS);
      } else {
        const token = localStorage.getItem('nowait_token');
        const h = { Authorization: `Bearer ${token}` };
        const [r1, r2, r3, r4] = await Promise.all([
          fetch(`${API_BASE}/reservations/me`, { headers: h }),
          fetch(`${API_BASE}/waiting/me`, { headers: h }),
          fetch(`${API_BASE}/favorites`, { headers: h }),
          fetch(`${API_BASE}/notifications`, { headers: h }),
        ]);
        setReservations(await r1.json());
        setWaitings(await r2.json());
        setFavorites(await r3.json());
        setNotifications(await r4.json());
      }
    } finally { setLoading(false); }
  }

  async function cancelReservation(id: number) {
    if (!confirm('예약을 취소하시겠어요?')) return;
    if (USE_DUMMY) {
      setReservations(prev => prev.map(r => r.reservationId === id ? { ...r, status: 'CANCELLED' } : r));
    } else {
      const token = localStorage.getItem('nowait_token');
      await fetch(`${API_BASE}/reservations/${id}/cancel`, { method: 'PATCH', headers: { Authorization: `Bearer ${token}` } });
      fetchData();
    }
  }

  async function cancelWaiting(id: number) {
    if (!confirm('웨이팅을 취소하시겠어요?')) return;
    if (USE_DUMMY) {
      setWaitings(prev => prev.map(w => w.waitingId === id ? { ...w, status: 'CANCELLED' } : w));
    } else {
      const token = localStorage.getItem('nowait_token');
      await fetch(`${API_BASE}/waiting/${id}/cancel`, { method: 'PATCH', headers: { Authorization: `Bearer ${token}` } });
      fetchData();
    }
  }

  async function removeFavorite(id: number) {
    if (!confirm('즐겨찾기를 삭제하시겠어요?')) return;
    if (USE_DUMMY) {
      setFavorites(prev => prev.filter(f => f.favoriteId !== id));
    } else {
      const token = localStorage.getItem('nowait_token');
      await fetch(`${API_BASE}/favorites/${id}`, { method: 'DELETE', headers: { Authorization: `Bearer ${token}` } });
      fetchData();
    }
  }

  async function markAllRead() {
    if (USE_DUMMY) {
      setNotifications(prev => prev.map(n => ({ ...n, isRead: 'Y' })));
    }
  }

  const filteredReservations = statusFilter === 'ALL'
    ? reservations : reservations.filter(r => r.status === statusFilter);

  const s = {
    page: { minHeight: '100vh', background: 'var(--cream)' } as any,
    wrap: { maxWidth: 860, margin: '0 auto', padding: '32px 20px 60px' } as any,
    // 유저 프로필 카드
    profile: {
      background: '#fff', border: '2.5px solid var(--ink)', borderRadius: 20,
      boxShadow: '5px 5px 0 var(--ink)', padding: '24px', marginBottom: '24px',
      display: 'flex', alignItems: 'center', gap: 16
    } as any,
    avatar: {
      width: 56, height: 56, borderRadius: '50%', background: 'var(--tomato)',
      border: '2.5px solid var(--ink)', display: 'flex', alignItems: 'center',
      justifyContent: 'center', fontSize: '1.4rem', flexShrink: 0
    } as any,
    // 탭
    tabWrap: { display: 'flex', gap: 8, marginBottom: 20, flexWrap: 'wrap' as const } as any,
    tab: (active: boolean) => ({
      padding: '10px 18px', borderRadius: 999, border: '2.5px solid var(--ink)',
      fontWeight: 800, fontSize: '0.88rem', cursor: 'pointer', fontFamily: 'inherit',
      background: active ? 'var(--ink)' : '#fff',
      color: active ? '#fff' : 'var(--muted)',
      boxShadow: active ? '3px 3px 0 var(--tomato)' : '3px 3px 0 var(--ink)',
      transition: 'all 0.15s',
    }) as any,
    // 카드
    card: {
      background: '#fff', border: '2.5px solid var(--ink)', borderRadius: 16,
      boxShadow: '4px 4px 0 var(--ink)', padding: '18px 20px', marginBottom: 12,
      display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12
    } as any,
    // 상태 태그
    statusTag: (status: string) => {
      const map: Record<string, { bg: string; color: string }> = {
        CONFIRMED: { bg: 'var(--amber)', color: 'var(--ink)' },
        VISITED: { bg: '#d1fae5', color: '#065f46' },
        CANCELLED: { bg: '#f5f5f5', color: '#999' },
        NO_SHOW: { bg: '#fee2e2', color: '#991b1b' },
        WAITING: { bg: 'var(--tomato-light)', color: 'var(--tomato)' },
        CALLED: { bg: 'var(--amber)', color: 'var(--ink)' },
        ENTERED: { bg: '#d1fae5', color: '#065f46' },
        CANCELLED_W: { bg: '#f5f5f5', color: '#999' },
      };
      const c = map[status] || { bg: '#f5f5f5', color: '#999' };
      return {
        fontSize: '0.72rem', fontWeight: 800, padding: '4px 10px', borderRadius: 999,
        border: '2px solid var(--ink)', background: c.bg, color: c.color, whiteSpace: 'nowrap' as const
      };
    },
    // 버튼
    cancelBtn: {
      padding: '8px 14px', border: '2px solid var(--ink)', borderRadius: 999,
      background: '#fff', color: 'var(--tomato)', fontSize: '0.82rem', fontWeight: 800,
      cursor: 'pointer', boxShadow: '2px 2px 0 var(--ink)', whiteSpace: 'nowrap' as const
    } as any,
  };

  return (
    <div style={s.page}>
      <div style={s.wrap}>

        {/* 프로필 */}
        <div style={s.profile}>
          <div style={s.avatar}>👤</div>
          <div style={{ flex: 1 }}>
            <div style={{ fontSize: '1.1rem', fontWeight: 900 }}>{user.name || '사용자'}님 👋</div>
            <div style={{ fontSize: '0.84rem', color: 'var(--muted)', marginTop: 2 }}>{user.email}</div>
          </div>
          <button className="btn btn-outline btn-sm" onClick={() => navigate('/')}>홈으로</button>
        </div>

        {/* 탭 */}
        <div style={s.tabWrap}>
          {([
            { key: 'reservation', label: '📋 예약 내역' },
            { key: 'waiting', label: '⏳ 웨이팅 내역' },
            { key: 'favorite', label: '❤️ 즐겨찾기' },
            { key: 'notification', label: `🔔 알림${unreadCount > 0 ? ` (${unreadCount})` : ''}` },
          ] as { key: Tab; label: string }[]).map(t => (
            <button key={t.key} style={s.tab(tab === t.key)} onClick={() => setTab(t.key)}>
              {t.label}
            </button>
          ))}
        </div>

        {loading && <div className="spinner" />}

        {/* ===== 예약 탭 ===== */}
        {!loading && tab === 'reservation' && (
          <div>
            <div style={{ display: 'flex', gap: 8, marginBottom: 16, flexWrap: 'wrap' }}>
              {['ALL', 'CONFIRMED', 'VISITED', 'CANCELLED', 'NO_SHOW'].map(s => (
                <button key={s} onClick={() => setStatusFilter(s)}
                  style={{
                    padding: '7px 14px', borderRadius: 999, fontSize: '0.8rem', fontWeight: 800,
                    border: `2px solid var(--ink)`, fontFamily: 'inherit', cursor: 'pointer',
                    background: statusFilter === s ? 'var(--ink)' : '#fff',
                    color: statusFilter === s ? '#fff' : 'var(--muted)',
                    boxShadow: statusFilter === s ? '2px 2px 0 var(--tomato)' : '2px 2px 0 var(--ink)'
                  }}>
                  {s === 'ALL' ? '전체' : STATUS_LABEL[s]}
                </button>
              ))}
            </div>
            {filteredReservations.length === 0 ? (
              <div className="empty-state"><div className="icon">📋</div><p>예약 내역이 없어요</p></div>
            ) : filteredReservations.map(r => (
              <div key={r.reservationId} style={s.card}>
                <div style={{ flex: 1 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6 }}>
                    <span style={{ fontWeight: 900, fontSize: '1rem' }}>{r.restaurantName}</span>
                    <span style={s.statusTag(r.status)}>{STATUS_LABEL[r.status]}</span>
                  </div>
                  <div style={{ fontSize: '0.84rem', color: 'var(--muted)', fontWeight: 600 }}>
                    📅 {r.slotDate} · 🕐 {r.slotTime} · 👥 {r.headcount}명
                  </div>
                  {r.status === 'VISITED' && (
                    <button style={{
                      marginTop: 8, padding: '6px 12px', borderRadius: 999,
                      border: '2px solid var(--amber)', background: 'var(--amber)', color: 'var(--ink)',
                      fontSize: '0.78rem', fontWeight: 800, cursor: 'pointer'
                    }}
                      onClick={() => navigate(`/restaurant/${r.reservationId}?tab=review`)}>
                      ⭐ 리뷰 작성
                    </button>
                  )}
                </div>
                {r.status === 'CONFIRMED' && (
                  <button style={s.cancelBtn} onClick={() => cancelReservation(r.reservationId)}>취소</button>
                )}
              </div>
            ))}
          </div>
        )}

        {/* ===== 웨이팅 탭 ===== */}
        {!loading && tab === 'waiting' && (
          <div>
            {waitings.length === 0 ? (
              <div className="empty-state"><div className="icon">⏳</div><p>웨이팅 내역이 없어요</p></div>
            ) : waitings.map(w => (
              <div key={w.waitingId} style={s.card}>
                <div style={{ flex: 1 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6 }}>
                    <span style={{ fontWeight: 900, fontSize: '1rem' }}>{w.restaurantName}</span>
                    <span style={s.statusTag(w.status)}>{STATUS_LABEL[w.status]}</span>
                  </div>
                  <div style={{ fontSize: '0.84rem', color: 'var(--muted)', fontWeight: 600 }}>
                    🔢 대기번호 {w.waitingNumber}번 · 👥 {w.partySize}명
                  </div>
                  {w.status === 'CALLED' && (
                    <div style={{
                      marginTop: 8, padding: '10px 14px', background: 'var(--tomato)',
                      borderRadius: 10, border: '2px solid var(--ink)', color: '#fff',
                      fontSize: '0.84rem', fontWeight: 800
                    }}>
                      🔔 입장 차례예요! 10분 안에 매장 앞으로 와주세요.
                    </div>
                  )}
                  {w.status === 'WAITING' && (
                    <button style={{
                      marginTop: 8, padding: '6px 12px', borderRadius: 999,
                      border: '2px solid var(--ink)', background: 'var(--amber)', color: 'var(--ink)',
                      fontSize: '0.78rem', fontWeight: 800, cursor: 'pointer'
                    }}
                      onClick={() => navigate(`/waiting/${w.waitingId}`)}>
                      📊 순번 확인
                    </button>
                  )}
                </div>
                {w.status === 'WAITING' && (
                  <button style={s.cancelBtn} onClick={() => cancelWaiting(w.waitingId)}>취소</button>
                )}
              </div>
            ))}
          </div>
        )}

        {/* ===== 즐겨찾기 탭 ===== */}
        {!loading && tab === 'favorite' && (
          <div>
            {favorites.length === 0 ? (
              <div className="empty-state"><div className="icon">❤️</div><p>즐겨찾기한 식당이 없어요</p></div>
            ) : (
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))', gap: 16 }}>
                {favorites.map(f => (
                  <div key={f.favoriteId} style={{
                    background: '#fff', border: '2.5px solid var(--ink)',
                    borderRadius: 16, boxShadow: '4px 4px 0 var(--ink)', overflow: 'hidden',
                    cursor: 'pointer', transition: 'transform 0.15s, box-shadow 0.15s'
                  }}
                    onMouseEnter={e => { (e.currentTarget as HTMLElement).style.transform = 'translate(-2px,-2px)'; (e.currentTarget as HTMLElement).style.boxShadow = '7px 7px 0 var(--ink)'; }}
                    onMouseLeave={e => { (e.currentTarget as HTMLElement).style.transform = 'translate(0,0)'; (e.currentTarget as HTMLElement).style.boxShadow = '4px 4px 0 var(--ink)'; }}>
                    <div style={{ position: 'relative', aspectRatio: '4/3', overflow: 'hidden' }}
                      onClick={() => navigate(`/restaurant/${f.restaurantId}`)}>
                      <img src={f.imageUrl} alt={f.restaurantName}
                        style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                      <span style={{
                        position: 'absolute', top: 10, left: 10, background: 'var(--ink)',
                        color: '#fff', fontSize: '0.72rem', fontWeight: 800, padding: '3px 9px',
                        borderRadius: 999
                      }}>{CAT_LABEL[f.category]}</span>
                    </div>
                    <div style={{ padding: '12px 14px 14px' }}>
                      <div style={{ fontWeight: 900, fontSize: '0.95rem', marginBottom: 4 }}
                        onClick={() => navigate(`/restaurant/${f.restaurantId}`)}>
                        {f.restaurantName}
                      </div>
                      <div style={{ fontSize: '0.8rem', color: 'var(--muted)', marginBottom: 10 }}>
                        {f.mainMenuName}
                      </div>
                      <div style={{ display: 'flex', gap: 8 }}>
                        <button style={{
                          flex: 1, padding: '8px', border: '2px solid var(--ink)',
                          borderRadius: 999, background: 'var(--tomato)', color: '#fff',
                          fontSize: '0.78rem', fontWeight: 800, cursor: 'pointer'
                        }}
                          onClick={() => navigate(`/restaurant/${f.restaurantId}`)}>
                          예약하기
                        </button>
                        <button style={{
                          padding: '8px 12px', border: '2px solid var(--ink)',
                          borderRadius: 999, background: '#fff', color: 'var(--muted)',
                          fontSize: '0.78rem', fontWeight: 800, cursor: 'pointer'
                        }}
                          onClick={() => removeFavorite(f.favoriteId)}>
                          ❌
                        </button>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* ===== 알림 탭 ===== */}
        {!loading && tab === 'notification' && (
          <div>
            {unreadCount > 0 && (
              <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 12 }}>
                <button style={{
                  padding: '8px 16px', border: '2px solid var(--ink)', borderRadius: 999,
                  background: '#fff', color: 'var(--ink)', fontSize: '0.82rem', fontWeight: 800,
                  cursor: 'pointer', boxShadow: '2px 2px 0 var(--ink)'
                }}
                  onClick={markAllRead}>
                  전체 읽음 처리
                </button>
              </div>
            )}
            {notifications.length === 0 ? (
              <div className="empty-state"><div className="icon">🔔</div><p>알림이 없어요</p></div>
            ) : notifications.map(n => {
              const meta = NOTI_LABEL[n.type] || { icon: '📢', color: 'var(--ink)' };
              return (
                <div key={n.notificationId} style={{
                  ...s.card,
                  background: n.isRead === 'N' ? '#fff9f5' : '#fff',
                  borderColor: n.isRead === 'N' ? 'var(--tomato)' : 'var(--ink)'
                }}>
                  <div style={{
                    width: 42, height: 42, borderRadius: '50%', flexShrink: 0,
                    background: meta.color + '20', border: `2px solid ${meta.color}`,
                    display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '1.1rem'
                  }}>
                    {meta.icon}
                  </div>
                  <div style={{ flex: 1 }}>
                    <div style={{
                      fontSize: '0.9rem', fontWeight: n.isRead === 'N' ? 800 : 600,
                      color: 'var(--ink)', lineHeight: 1.5
                    }}>
                      {n.message}
                    </div>
                    <div style={{ fontSize: '0.78rem', color: 'var(--muted)', marginTop: 4 }}>
                      {n.createdAt}
                    </div>
                  </div>
                  {n.isRead === 'N' && (
                    <div style={{
                      width: 10, height: 10, borderRadius: '50%',
                      background: 'var(--tomato)', flexShrink: 0
                    }} />
                  )}
                </div>
              );
            })}
          </div>
        )}

      </div>
    </div>
  );
}