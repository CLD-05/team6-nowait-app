import { useCallback, useEffect, useState, type CSSProperties } from 'react';
import { useNavigate } from 'react-router-dom';
import { API_BASE, resolveImageUrl } from '../lib/api';

const USE_DUMMY = false;

type Reservation = {
  reservationToken: string;
  reservationId: number | null;
  restaurantId: number;
  restaurantName: string;
  slotDate: string;
  slotTime: string;
  headcount: number;
  status: string;
  rejectionReason?: string | null;
};

type Waiting = {
  waitingToken: string;
  restaurantId: number;
  restaurantName?: string;
  waitingNumber: number;
  partySize: number;
  status: string;
  aheadCount?: number;
  registeredAt?: string;
};

type Favorite = {
  favoriteId: number;
  restaurantId: number;
  restaurantName: string;
  category: string;
  mainMenuName?: string;
  imageUrl?: string;
};

type Notification = {
  notificationId: number;
  type: string;
  message: string;
  isRead: string;
  createdAt: string;
};

type StoredUser = {
  name?: string;
  email?: string;
};



const DUMMY_RESERVATIONS: Reservation[] = [
  { reservationToken: 'reservation-1', reservationId: 1, restaurantId: 1, restaurantName: '미진 1호점', slotDate: '2026-06-10', slotTime: '12:00', headcount: 2, status: 'CONFIRMED' },
  { reservationToken: 'reservation-2', reservationId: 2, restaurantId: 2, restaurantName: '스시콜 2호점', slotDate: '2026-06-08', slotTime: '19:00', headcount: 4, status: 'VISITED' },
  { reservationToken: 'reservation-3', reservationId: 3, restaurantId: 3, restaurantName: '왕가원 3호점', slotDate: '2026-06-05', slotTime: '13:00', headcount: 2, status: 'CANCELLED' },
  { reservationToken: 'reservation-4', reservationId: 4, restaurantId: 4, restaurantName: '비스트로 르', slotDate: '2026-06-03', slotTime: '18:30', headcount: 3, status: 'NO_SHOW' },
];

const DUMMY_WAITINGS: Waiting[] = [
  { waitingToken: 'waiting-1', restaurantId: 1, restaurantName: '미진 1호점', waitingNumber: 5, partySize: 2, status: 'WAITING' },
  { waitingToken: 'waiting-2', restaurantId: 2, restaurantName: '스시콜 2호점', waitingNumber: 3, partySize: 4, status: 'CALLED' },
  { waitingToken: 'waiting-3', restaurantId: 3, restaurantName: '왕가원 3호점', waitingNumber: 7, partySize: 2, status: 'ENTERED' },
];

const DUMMY_FAVORITES: Favorite[] = [
  { favoriteId: 1, restaurantId: 1, restaurantName: '미진 1호점', category: 'KOREAN', mainMenuName: '제육볶음', imageUrl: 'https://images.unsplash.com/photo-1583224944844-5b268c057b72?w=400&q=80' },
  { favoriteId: 2, restaurantId: 2, restaurantName: '스시콜 2호점', category: 'JAPANESE', mainMenuName: '특선 스시', imageUrl: 'https://images.unsplash.com/photo-1579871494447-9811cf80d66c?w=400&q=80' },
  { favoriteId: 3, restaurantId: 3, restaurantName: '왕가원 3호점', category: 'CHINESE', mainMenuName: '마파두부', imageUrl: 'https://images.unsplash.com/photo-1563245372-f21724e3856d?w=400&q=80' },
];

const DUMMY_NOTIFICATIONS: Notification[] = [
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
  PENDING: '예약대기', CONFIRMED: '예약확정', VISITED: '방문완료',
  CANCELLED: '취소', NO_SHOW: '노쇼', REJECTED: '예약거부',
  WAITING: '대기중', CALLED: '호출됨', ENTERED: '입장완료',
};

const REJECTION_REASON_LABEL: Record<string, string> = {
  SLOT_FULL: '예약이 마감됐습니다.',
  STORE_CLOSED: '매장 사정으로 해당 일시에 운영이 불가합니다.',
  PARTY_SIZE_UNAVAILABLE: '요청하신 인원을 수용하기 어렵습니다.',
  SPECIAL_EVENT: '단체 행사 예약으로 좌석이 부족합니다.',
  OTHER: '기타 사유로 예약을 거부했습니다.',
};

const NOTI_LABEL: Record<string, { icon: string; color: string }> = {
  WAITING_CALLED: { icon: '🔔', color: '#ff5a3c' },
  RESERVATION_CONFIRMED: { icon: '📋', color: '#16b886' },
  REVIEW_REQUEST: { icon: '⭐', color: '#ffb22e' },
  RESERVATION_CANCELLED: { icon: '❌', color: '#999' },
  WAITING_REGISTERED: { icon: '⏳', color: '#7c3aed' },
  WAITING_CANCELLED: { icon: '❌', color: '#999' },
};

type Tab = 'reservation' | 'waiting' | 'favorite' | 'review' | 'notification';

interface Review {
  reviewId: number;
  restaurantId: number;
  restaurantName: string;
  rating: number;
  content: string;
  visitedAt: string;
  createdAt: string;
}

export default function MyPage() {
  const navigate = useNavigate();
  const [tab, setTab] = useState<Tab>('reservation');
  const [reservations, setReservations] = useState<Reservation[]>([]);
  const [waitings, setWaitings] = useState<Waiting[]>([]);
  const [favorites, setFavorites] = useState<Favorite[]>([]);
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [reviews, setReviews] = useState<Review[]>([]);
  const [loading, setLoading] = useState(false);
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [selectedTokens, setSelectedTokens] = useState<Set<string>>(new Set());
  const [deleteLoading, setDeleteLoading] = useState(false);
  const [editingName, setEditingName] = useState(false);
  const [nameInput, setNameInput] = useState('');

  const user = JSON.parse(localStorage.getItem('nowait_user') || '{}') as StoredUser;
  const unreadCount = notifications.filter(n => n.isRead === 'N').length;

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      if (USE_DUMMY) {
        await new Promise(r => setTimeout(r, 300));
        setReservations(DUMMY_RESERVATIONS);
        setWaitings(DUMMY_WAITINGS);
        setFavorites(DUMMY_FAVORITES);
        setNotifications(DUMMY_NOTIFICATIONS);
        return;
      }

      const token = localStorage.getItem('nowait_token');
      if (!token) { navigate('/auth'); return; }
      const h = { Authorization: `Bearer ${token}` };

      // 각 API를 독립적으로 처리 — 하나가 실패해도 나머지는 정상 표시
      const [r1, r2, r3, r4, r5] = await Promise.all([
        fetch(`${API_BASE}/reservations/me`, { headers: h }),
        fetch(`${API_BASE}/waitings/me/history`, { headers: h }),
        fetch(`${API_BASE}/users/me/favorites`, { headers: h }),
        fetch(`${API_BASE}/notifications/me`, { headers: h }),
        fetch(`${API_BASE}/users/me/reviews`, { headers: h }),
      ]);

      if (r1.status === 401) { navigate('/auth'); return; }

      if (r1.ok) {
        setReservations(await r1.json() as Reservation[]);
      } else {
        console.error('예약 목록 조회 실패:', r1.status);
        setReservations([]);
      }

      if (r3.ok) {
        setFavorites(await r3.json() as Favorite[]);
      } else {
        console.error('즐겨찾기 조회 실패:', r3.status);
        setFavorites([]);
      }

      if (r4.ok) {
        setNotifications(await r4.json() as Notification[]);
      } else {
        console.error('알림 조회 실패:', r4.status);
        setNotifications([]);
      }

      if (r5.ok) {
        setReviews(await r5.json() as Review[]);
      } else {
        console.error('리뷰 목록 조회 실패:', r5.status);
        setReviews([]);
      }

      if (r2.ok) {
        const waitingList = await r2.json() as Waiting[];
        const waitingsWithNames = await Promise.all(
          waitingList.map(async (waiting) => {
            try {
              const restaurantResponse = await fetch(`${API_BASE}/restaurants/${waiting.restaurantId}`);
              const restaurant = restaurantResponse.ok
                ? await restaurantResponse.json() as { name?: string }
                : null;
              return { ...waiting, restaurantName: restaurant?.name };
            } catch {
              return waiting;
            }
          })
        );
        setWaitings(waitingsWithNames);
      } else {
        setWaitings([]);
      }
    } catch (error) {
      console.error('마이페이지 데이터 로드 오류:', error);
    } finally {
      setLoading(false);
    }
  }, [navigate]);

  useEffect(() => {
    // 비동기 요청 완료 후 상태를 갱신한다.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void fetchData();
  }, [fetchData]);

  async function updateName() {
    if (!nameInput.trim()) return;
    const token = localStorage.getItem('nowait_token');
    const res = await fetch(`${API_BASE}/users/me`, {
      method: 'PATCH',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: nameInput.trim() }),
    });
    if (res.ok) {
      const updated = await res.json() as { name: string; email: string };
      const stored = JSON.parse(localStorage.getItem('nowait_user') || '{}') as StoredUser;
      localStorage.setItem('nowait_user', JSON.stringify({ ...stored, name: updated.name }));
      setEditingName(false);
      window.location.reload();
    } else {
      alert('이름 변경에 실패했습니다.');
    }
  }

  function logout() {
    localStorage.removeItem('nowait_token');
    localStorage.removeItem('nowait_user');
    navigate('/auth');
  }

  async function withdrawAccount() {
    if (!confirm('정말 탈퇴하시겠어요? 모든 데이터가 삭제됩니다.')) return;
    const token = localStorage.getItem('nowait_token');
    const res = await fetch(`${API_BASE}/users/me`, {
      method: 'DELETE',
      headers: { Authorization: `Bearer ${token}` },
    });
    if (res.ok) {
      alert('탈퇴가 완료됐습니다.');
      logout();
    } else {
      alert('탈퇴 처리 중 오류가 발생했습니다.');
    }
  }

  async function cancelReservation(reservationToken: string) {
    if (!confirm('예약을 취소하시겠어요?')) return;
    try {
      if (USE_DUMMY) {
        setReservations(prev => prev.map(r => r.reservationToken === reservationToken ? { ...r, status: 'CANCELLED' } : r));
      } else {
        const token = localStorage.getItem('nowait_token');
        const response = await fetch(`${API_BASE}/reservations/${reservationToken}/cancel`, {
          method: 'PATCH',
          headers: { Authorization: `Bearer ${token}` },
        });
        if (!response.ok) {
          const err = await response.json().catch(() => ({})) as { message?: string };
          alert(err.message || '예약 취소에 실패했습니다.');
          return;
        }
        await fetchData();
      }
    } catch {
      alert('예약 취소 중 오류가 발생했습니다.');
    }
  }

  async function cancelWaiting(waitingToken: string) {
    if (!confirm('웨이팅을 취소하시겠어요?')) return;
    try {
      if (USE_DUMMY) {
        setWaitings(prev => prev.map(w => w.waitingToken === waitingToken ? { ...w, status: 'CANCELLED' } : w));
      } else {
        const token = localStorage.getItem('nowait_token');
        const response = await fetch(`${API_BASE}/waitings/${waitingToken}/cancel`, {
          method: 'PATCH',
          headers: { Authorization: `Bearer ${token}` },
        });
        if (!response.ok) {
          const err = await response.json().catch(() => ({})) as { message?: string };
          alert(err.message || '웨이팅 취소에 실패했습니다.');
          return;
        }
        await fetchData();
      }
    } catch {
      alert('웨이팅 취소 중 오류가 발생했습니다.');
    }
  }

  function toggleSelect(reservationToken: string) {
    setSelectedTokens(prev => {
      const next = new Set(prev);
      if (next.has(reservationToken)) next.delete(reservationToken);
      else next.add(reservationToken);
      return next;
    });
  }

  function toggleSelectAll() {
    const deletable = filteredReservations.filter(r => r.status === 'CANCELLED' || r.status === 'NO_SHOW' || r.status === 'REJECTED');
    const allSelected = deletable.every(r => selectedTokens.has(r.reservationToken));
    if (allSelected) {
      setSelectedTokens(prev => {
        const next = new Set(prev);
        deletable.forEach(r => next.delete(r.reservationToken));
        return next;
      });
    } else {
      setSelectedTokens(prev => {
        const next = new Set(prev);
        deletable.forEach(r => next.add(r.reservationToken));
        return next;
      });
    }
  }

  async function deleteSelectedReservations() {
    if (selectedTokens.size === 0) return;
    if (!confirm(`선택한 ${selectedTokens.size}건의 내역을 삭제하시겠어요?`)) return;
    setDeleteLoading(true);
    try {
      if (USE_DUMMY) {
        setReservations(prev => prev.filter(r => !selectedTokens.has(r.reservationToken)));
        setSelectedTokens(new Set());
        return;
      }
      const jwtToken = localStorage.getItem('nowait_token');
      const results = await Promise.all(
        [...selectedTokens].map(resToken =>
          fetch(`${API_BASE}/reservations/${resToken}`, {
            method: 'DELETE',
            headers: { Authorization: `Bearer ${jwtToken}` },
          })
        )
      );
      const failed = results.filter(r => !r.ok).length;
      if (failed > 0) alert(`${failed}건 삭제에 실패했습니다.`);
      setSelectedTokens(new Set());
      await fetchData();
    } catch {
      alert('삭제 중 오류가 발생했습니다.');
    } finally {
      setDeleteLoading(false);
    }
  }

  async function removeFavorite(restaurantId: number) {
    if (!confirm('즐겨찾기를 삭제하시겠어요?')) return;
    if (USE_DUMMY) {
      setFavorites(prev => prev.filter(f => f.restaurantId !== restaurantId));
    } else {
      const token = localStorage.getItem('nowait_token');
      const response = await fetch(`${API_BASE}/restaurants/${restaurantId}/favorite`, { method: 'POST', headers: { Authorization: `Bearer ${token}` } });
      if (!response.ok) throw new Error('즐겨찾기 해제에 실패했습니다.');
      await fetchData();
    }
  }

  async function markAllRead() {
    if (USE_DUMMY) {
      setNotifications(prev => prev.map(n => ({ ...n, isRead: 'Y' })));
    } else {
      const token = localStorage.getItem('nowait_token');
      const response = await fetch(`${API_BASE}/notifications/read-all`, { method: 'PATCH', headers: { Authorization: `Bearer ${token}` } });
      if (!response.ok) throw new Error('알림 읽음 처리에 실패했습니다.');
      setNotifications(prev => prev.map(n => ({ ...n, isRead: 'Y' })));
    }
  }

  const filteredReservations = statusFilter === 'ALL'
    ? reservations : reservations.filter(r => r.status === statusFilter);

  const s = {
    page: { minHeight: '100vh', background: 'var(--cream)' } satisfies CSSProperties,
    wrap: { maxWidth: 860, margin: '0 auto', padding: '32px 20px 60px' } satisfies CSSProperties,
    // 유저 프로필 카드
    profile: {
      background: '#fff', border: '2.5px solid var(--ink)', borderRadius: 20,
      boxShadow: '5px 5px 0 var(--ink)', padding: '24px', marginBottom: '24px',
      display: 'flex', alignItems: 'center', gap: 16
    } satisfies CSSProperties,
    avatar: {
      width: 56, height: 56, borderRadius: '50%', background: 'var(--tomato)',
      border: '2.5px solid var(--ink)', display: 'flex', alignItems: 'center',
      justifyContent: 'center', fontSize: '1.4rem', flexShrink: 0
    } satisfies CSSProperties,
    // 탭
    tabWrap: { display: 'flex', gap: 8, marginBottom: 20, flexWrap: 'wrap' } satisfies CSSProperties,
    tab: (active: boolean): CSSProperties => ({
      padding: '10px 18px', borderRadius: 999, border: '2.5px solid var(--ink)',
      fontWeight: 800, fontSize: '0.88rem', cursor: 'pointer', fontFamily: 'inherit',
      background: active ? 'var(--ink)' : '#fff',
      color: active ? '#fff' : 'var(--muted)',
      boxShadow: active ? '3px 3px 0 var(--tomato)' : '3px 3px 0 var(--ink)',
      transition: 'all 0.15s',
    }),
    // 카드
    card: {
      background: '#fff', border: '2.5px solid var(--ink)', borderRadius: 16,
      boxShadow: '4px 4px 0 var(--ink)', padding: '18px 20px', marginBottom: 12,
      display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12
    } satisfies CSSProperties,
    // 상태 태그
    statusTag: (status: string) => {
      const map: Record<string, { bg: string; color: string }> = {
        PENDING: { bg: '#e0f2fe', color: '#0369a1' },
        CONFIRMED: { bg: 'var(--amber)', color: 'var(--ink)' },
        VISITED: { bg: '#d1fae5', color: '#065f46' },
        CANCELLED: { bg: '#f5f5f5', color: '#999' },
        NO_SHOW: { bg: '#fee2e2', color: '#991b1b' },
        REJECTED: { bg: '#fce7f3', color: '#9d174d' },
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
    } satisfies CSSProperties,
  };

  return (
    <div style={s.page}>
      <div style={s.wrap}>

        {/* 프로필 */}
        <div style={s.profile}>
          <div style={s.avatar}>👤</div>
          <div style={{ flex: 1 }}>
            {editingName ? (
              <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                <input
                  autoFocus
                  value={nameInput}
                  onChange={e => setNameInput(e.target.value)}
                  onKeyDown={e => { if (e.key === 'Enter') void updateName(); if (e.key === 'Escape') setEditingName(false); }}
                  style={{ padding: '4px 10px', border: '2px solid var(--ink)', borderRadius: 8, fontFamily: 'inherit', fontWeight: 700, fontSize: '0.95rem', width: 130 }}
                />
                <button className="btn btn-tomato btn-sm" onClick={() => void updateName()}>저장</button>
                <button className="btn btn-sm" style={{ background: '#f5f5f5', border: '2px solid var(--ink)' }} onClick={() => setEditingName(false)}>취소</button>
              </div>
            ) : (
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <div style={{ fontSize: '1.1rem', fontWeight: 900 }}>{user.name || '사용자'}님 👋</div>
                <button className="btn btn-sm" style={{ background: 'transparent', border: 'none', boxShadow: 'none', padding: '2px 6px', fontSize: '0.8rem', color: 'var(--muted)' }}
                  onClick={() => { setNameInput(user.name || ''); setEditingName(true); }}>✏️</button>
              </div>
            )}
            <div style={{ fontSize: '0.84rem', color: 'var(--muted)', marginTop: 2 }}>{user.email}</div>
          </div>
          <div style={{ display: 'flex', gap: 6, flexDirection: 'column', alignItems: 'flex-end' }}>
            <button className="btn btn-outline btn-sm" onClick={() => navigate('/')}>홈으로</button>
            <button className="btn btn-sm" style={{ background: '#fff', border: '2px solid var(--ink)', fontSize: '0.75rem', color: '#666' }} onClick={logout}>로그아웃</button>
            <button className="btn btn-sm" style={{ background: '#fff', border: '2px solid var(--tomato)', fontSize: '0.75rem', color: 'var(--tomato)' }} onClick={() => void withdrawAccount()}>회원탈퇴</button>
          </div>
        </div>

        {/* 탭 */}
        <div style={s.tabWrap}>
          {([
            { key: 'reservation', label: '📋 예약 내역' },
            { key: 'waiting', label: '⏳ 웨이팅 내역' },
            { key: 'favorite', label: '❤️ 즐겨찾기' },
            { key: 'review', label: '⭐ 리뷰 내역' },
            { key: 'notification', label: `🔔 알림${unreadCount > 0 ? ` (${unreadCount})` : ''}` },
          ] as { key: Tab; label: string }[]).map(t => (
            <button key={t.key} style={s.tab(tab === t.key)} onClick={() => { setTab(t.key); setSelectedTokens(new Set()); }}>
              {t.label}
            </button>
          ))}
        </div>

        {loading && <div className="spinner" />}

        {/* ===== 예약 탭 ===== */}
        {!loading && tab === 'reservation' && (
          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16, gap: 8, flexWrap: 'wrap' }}>
              <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                {['ALL', 'PENDING', 'CONFIRMED', 'VISITED', 'CANCELLED', 'NO_SHOW', 'REJECTED'].map(s => (
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
              {(() => {
                const deletable = filteredReservations.filter(r => r.status === 'CANCELLED' || r.status === 'NO_SHOW' || r.status === 'REJECTED');
                const allSelected = deletable.length > 0 && deletable.every(r => selectedTokens.has(r.reservationToken));
                return deletable.length > 0 ? (
                  <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                    <button
                      onClick={toggleSelectAll}
                      style={{
                        padding: '7px 14px', borderRadius: 999, fontSize: '0.8rem', fontWeight: 800,
                        border: '2px solid var(--ink)', fontFamily: 'inherit', cursor: 'pointer',
                        background: allSelected ? 'var(--ink)' : '#fff',
                        color: allSelected ? '#fff' : 'var(--muted)',
                        boxShadow: '2px 2px 0 var(--ink)', whiteSpace: 'nowrap' as const,
                      }}>
                      {allSelected ? '선택 해제' : '전체 선택'}
                    </button>
                    {selectedTokens.size > 0 && (
                      <button
                        onClick={() => void deleteSelectedReservations()}
                        disabled={deleteLoading}
                        style={{
                          padding: '7px 14px', borderRadius: 999, fontSize: '0.8rem', fontWeight: 800,
                          border: '2px solid var(--ink)', fontFamily: 'inherit', cursor: 'pointer',
                          background: 'var(--tomato)', color: '#fff',
                          boxShadow: '2px 2px 0 var(--ink)', whiteSpace: 'nowrap' as const,
                          opacity: deleteLoading ? 0.6 : 1,
                        }}>
                        🗑 {selectedTokens.size}건 삭제
                      </button>
                    )}
                  </div>
                ) : null;
              })()}
            </div>
            {filteredReservations.length === 0 ? (
              <div className="empty-state"><div className="icon">📋</div><p>예약 내역이 없어요</p></div>
            ) : filteredReservations.map(r => {
              const isDeletable = r.status === 'CANCELLED' || r.status === 'NO_SHOW' || r.status === 'REJECTED';
              const isSelected = selectedTokens.has(r.reservationToken);
              return (
                <div key={r.reservationToken} style={{
                  ...s.card,
                  borderColor: isSelected ? 'var(--tomato)' : 'var(--ink)',
                  background: isSelected ? '#fff9f5' : '#fff',
                  cursor: isDeletable ? 'pointer' : 'default',
                }}
                  onClick={isDeletable ? () => toggleSelect(r.reservationToken) : undefined}
                >
                  {isDeletable && (
                    <div style={{
                      width: 20, height: 20, borderRadius: 6, flexShrink: 0,
                      border: `2.5px solid ${isSelected ? 'var(--tomato)' : 'var(--muted)'}`,
                      background: isSelected ? 'var(--tomato)' : '#fff',
                      display: 'flex', alignItems: 'center', justifyContent: 'center',
                      marginRight: 4,
                    }}>
                      {isSelected && <span style={{ color: '#fff', fontSize: '0.7rem', fontWeight: 900, lineHeight: 1 }}>✓</span>}
                    </div>
                  )}
                  <div style={{ flex: 1 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6 }}>
                      <span style={{ fontWeight: 900, fontSize: '1rem' }}>{r.restaurantName}</span>
                      <span style={s.statusTag(r.status)}>{STATUS_LABEL[r.status]}</span>
                    </div>
                    <div style={{ fontSize: '0.84rem', color: 'var(--muted)', fontWeight: 600 }}>
                      📅 {r.slotDate} · 🕐 {r.slotTime} · 👥 {r.headcount}명
                    </div>
                    {r.status === 'PENDING' && (
                      <div style={{ marginTop: 6, fontSize: '0.8rem', color: '#0369a1', fontWeight: 700, background: '#e0f2fe', padding: '4px 10px', borderRadius: 8, display: 'inline-block' }}>
                        ⏳ 점주 승인 대기 중입니다
                      </div>
                    )}
                    {r.status === 'REJECTED' && r.rejectionReason && (
                      <div style={{ marginTop: 6, fontSize: '0.8rem', color: '#9d174d', fontWeight: 700, background: '#fce7f3', padding: '4px 10px', borderRadius: 8, display: 'inline-block' }}>
                        ❌ 거부 사유: {REJECTION_REASON_LABEL[r.rejectionReason] ?? r.rejectionReason}
                      </div>
                    )}
                    {r.status === 'VISITED' && (
                      <button style={{
                        marginTop: 8, padding: '6px 12px', borderRadius: 999,
                        border: '2px solid var(--amber)', background: 'var(--amber)', color: 'var(--ink)',
                        fontSize: '0.78rem', fontWeight: 800, cursor: 'pointer'
                      }}
                        onClick={e => { e.stopPropagation(); navigate(`/restaurant/${r.restaurantId}?tab=review&reservationToken=${r.reservationToken}`); }}>
                        ⭐ 리뷰 작성
                      </button>
                    )}
                  </div>
                  {(r.status === 'CONFIRMED' || r.status === 'PENDING') && (
                    <button style={s.cancelBtn} onClick={e => { e.stopPropagation(); void cancelReservation(r.reservationToken); }}>취소</button>
                  )}
                </div>
              );
            })}
          </div>
        )}

        {/* ===== 웨이팅 탭 ===== */}
        {!loading && tab === 'waiting' && (
          <div>
            {waitings.length === 0 ? (
              <div className="empty-state"><div className="icon">⏳</div><p>웨이팅 내역이 없어요</p></div>
            ) : waitings.map(w => (
              <div key={w.waitingToken} style={s.card}>
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
                      onClick={() => navigate(`/waiting/${w.waitingToken}`)}>
                      📊 순번 확인
                    </button>
                  )}
                </div>
                {w.status === 'WAITING' && (
                  <button style={s.cancelBtn} onClick={() => void cancelWaiting(w.waitingToken)}>취소</button>
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
                      <img src={resolveImageUrl(f.imageUrl)} alt={f.restaurantName}
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
                          onClick={() => void removeFavorite(f.restaurantId)}>
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

        {/* ===== 리뷰 내역 탭 ===== */}
        {!loading && tab === 'review' && (
          <div>
            {reviews.length === 0 ? (
              <div className="empty-state"><div className="icon">⭐</div><p>작성한 리뷰가 없어요</p></div>
            ) : (
              <div style={{ display: 'grid', gap: 14 }}>
                {reviews.map(r => (
                  <div key={r.reviewId} style={{
                    background: '#fff', border: '2.5px solid var(--ink)',
                    borderRadius: 16, boxShadow: '4px 4px 0 var(--ink)', padding: '18px 20px',
                    display: 'flex', flexDirection: 'column', gap: 8,
                  }}>
                    <div style={{ fontWeight: 900, fontSize: '0.95rem', marginBottom: 4 }}>{r.restaurantName}</div>
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
                      <div style={{ display: 'flex', gap: 4 }}>
                        {Array.from({ length: 5 }, (_, i) => (
                          <span key={i} style={{ fontSize: '1.1rem', color: i < r.rating ? '#ffb22e' : '#e0e0e0' }}>★</span>
                        ))}
                      </div>
                      <div style={{ display: 'flex', gap: 8 }}>
                        <button className="btn btn-sm" style={{ background: '#f5f5f5', border: '2px solid var(--ink)', fontSize: '0.78rem' }}
                          onClick={() => navigate(`/restaurant/${r.restaurantId}`)}>
                          식당 보기
                        </button>
                        <button className="btn btn-sm" style={{ background: 'var(--tomato)', color: '#fff', border: '2px solid var(--ink)', fontSize: '0.78rem' }}
                          onClick={async () => {
                            if (!confirm('리뷰를 삭제할까요?')) return;
                            const token = localStorage.getItem('nowait_token');
                            const res = await fetch(`${API_BASE}/reviews/${r.reviewId}`, {
                              method: 'DELETE',
                              headers: { Authorization: `Bearer ${token}` },
                            });
                            if (res.ok) setReviews(prev => prev.filter(x => x.reviewId !== r.reviewId));
                            else alert('삭제에 실패했습니다.');
                          }}>
                          삭제
                        </button>
                      </div>
                    </div>
                    <p style={{ margin: 0, fontSize: '0.9rem', lineHeight: 1.6 }}>{r.content}</p>
                    <div style={{ fontSize: '0.78rem', color: 'var(--muted)' }}>
                      방문일: {r.visitedAt ? r.visitedAt.slice(0, 10) : '-'} · 작성일: {r.createdAt.slice(0, 10)}
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
                  onClick={() => void markAllRead()}>
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
