import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import Header from '../components/Header';

const USE_DUMMY = true;
const API_BASE = '/api/v1';

// 더미 예약 데이터
const DUMMY_RESERVATIONS = [
  { reservationId: 1, restaurantName: '미진 1호점', slotDate: '2026-06-10', slotTime: '12:00', headcount: 2, status: 'CONFIRMED' },
  { reservationId: 2, restaurantName: '스시콜 2호점', slotDate: '2026-06-08', slotTime: '19:00', headcount: 4, status: 'VISITED' },
  { reservationId: 3, restaurantName: '왕가원 3호점', slotDate: '2026-06-05', slotTime: '13:00', headcount: 2, status: 'CANCELLED' },
  { reservationId: 4, restaurantName: '비스트로 르 1호점', slotDate: '2026-06-03', slotTime: '18:30', headcount: 3, status: 'NO_SHOW' },
];

// 더미 웨이팅 데이터
const DUMMY_WAITINGS = [
  { waitingId: 1, restaurantName: '미진 1호점', waitingNumber: 5, partySize: 2, status: 'WAITING' },
  { waitingId: 2, restaurantName: '스시콜 2호점', waitingNumber: 3, partySize: 4, status: 'CALLED' },
  { waitingId: 3, restaurantName: '왕가원 3호점', waitingNumber: 7, partySize: 2, status: 'ENTERED' },
  { waitingId: 4, restaurantName: '트라토리아 1호점', waitingNumber: 2, partySize: 2, status: 'CANCELLED' },
];

const STATUS_LABEL: Record<string, string> = {
  CONFIRMED: '예약확정', VISITED: '방문완료', CANCELLED: '취소', NO_SHOW: '노쇼',
  WAITING: '대기중', CALLED: '호출됨', ENTERED: '입장완료',
};

const STATUS_COLOR: Record<string, { bg: string; color: string }> = {
  CONFIRMED: { bg: '#EFF6FF', color: '#2563EB' },
  VISITED: { bg: '#F0FDF4', color: '#16A34A' },
  CANCELLED: { bg: '#F5F5F5', color: '#9E9E9E' },
  NO_SHOW: { bg: '#FFF0F0', color: '#D32F2F' },
  WAITING: { bg: '#EFF6FF', color: '#2563EB' },
  CALLED: { bg: '#FFF3EF', color: 'var(--primary)' },
  ENTERED: { bg: '#F0FDF4', color: '#16A34A' },
};

export default function MyPage() {
  const navigate = useNavigate();
  const [tab, setTab] = useState<'reservation' | 'waiting'>('reservation');
  const [reservations, setReservations] = useState<any[]>([]);
  const [waitings, setWaitings] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [statusFilter, setStatusFilter] = useState('ALL');

  const user = JSON.parse(localStorage.getItem('nowait_user') || '{}');

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
      } else {
        const token = localStorage.getItem('nowait_token');
        const headers = { Authorization: `Bearer ${token}` };
        const [resRes, waitRes] = await Promise.all([
          fetch(`${API_BASE}/reservations/me`, { headers }),
          fetch(`${API_BASE}/waiting/me`, { headers }),
        ]);
        setReservations(await resRes.json());
        setWaitings(await waitRes.json());
      }
    } finally {
      setLoading(false);
    }
  }

  async function cancelReservation(reservationId: number) {
    if (!confirm('예약을 취소하시겠어요?')) return;
    try {
      if (USE_DUMMY) {
        setReservations(prev => prev.map(r => r.reservationId === reservationId ? { ...r, status: 'CANCELLED' } : r));
      } else {
        const token = localStorage.getItem('nowait_token');
        await fetch(`${API_BASE}/reservations/${reservationId}/cancel`, {
          method: 'PATCH',
          headers: { Authorization: `Bearer ${token}` },
        });
        fetchData();
      }
    } catch { alert('취소 중 오류가 발생했습니다.'); }
  }

  async function cancelWaiting(waitingId: number) {
    if (!confirm('웨이팅을 취소하시겠어요?')) return;
    try {
      if (USE_DUMMY) {
        setWaitings(prev => prev.map(w => w.waitingId === waitingId ? { ...w, status: 'CANCELLED' } : w));
      } else {
        const token = localStorage.getItem('nowait_token');
        await fetch(`${API_BASE}/waiting/${waitingId}/cancel`, {
          method: 'PATCH',
          headers: { Authorization: `Bearer ${token}` },
        });
        fetchData();
      }
    } catch { alert('취소 중 오류가 발생했습니다.'); }
  }

  const filteredReservations = statusFilter === 'ALL'
    ? reservations
    : reservations.filter(r => r.status === statusFilter);

  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg)' }}>
      <Header />

      <div style={{ maxWidth: '800px', margin: '0 auto', padding: '32px 20px 60px' }}>

        {/* 사용자 정보 */}
        <div style={{ background: '#fff', borderRadius: 'var(--radius-md)', boxShadow: 'var(--shadow)', padding: '24px', marginBottom: '24px', display: 'flex', alignItems: 'center', gap: '16px' }}>
          <div style={{ width: '56px', height: '56px', borderRadius: '50%', background: 'var(--primary-light)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '1.5rem' }}>
            👤
          </div>
          <div>
            <div style={{ fontSize: '1.1rem', fontWeight: 700 }}>{user.name || '사용자'}님</div>
            <div style={{ fontSize: '0.86rem', color: 'var(--text-sub)', marginTop: '2px' }}>{user.email || ''}</div>
          </div>
        </div>

        {/* 탭 */}
        <div style={{ display: 'flex', background: '#fff', borderRadius: 'var(--radius-md)', boxShadow: 'var(--shadow)', overflow: 'hidden', marginBottom: '20px' }}>
          {(['reservation', 'waiting'] as const).map(t => (
            <button
              key={t}
              onClick={() => setTab(t)}
              style={{
                flex: 1, padding: '16px', border: 'none', cursor: 'pointer',
                fontWeight: 700, fontSize: '0.95rem', transition: 'all 0.15s',
                background: tab === t ? 'var(--primary)' : '#fff',
                color: tab === t ? '#fff' : 'var(--text-sub)',
              }}
            >
              {t === 'reservation' ? '📋 예약 내역' : '⏳ 웨이팅 내역'}
            </button>
          ))}
        </div>

        {/* 예약 탭 */}
        {tab === 'reservation' && (
          <div>
            {/* 상태 필터 */}
            <div style={{ display: 'flex', gap: '8px', marginBottom: '16px', flexWrap: 'wrap' }}>
              {['ALL', 'CONFIRMED', 'VISITED', 'CANCELLED', 'NO_SHOW'].map(s => (
                <button
                  key={s}
                  onClick={() => setStatusFilter(s)}
                  style={{
                    padding: '7px 14px', borderRadius: '50px', fontSize: '0.82rem', fontWeight: 600,
                    border: `1.5px solid ${statusFilter === s ? 'var(--primary)' : 'var(--border)'}`,
                    background: statusFilter === s ? 'var(--primary)' : '#fff',
                    color: statusFilter === s ? '#fff' : 'var(--text-sub)',
                    cursor: 'pointer', transition: 'all 0.15s',
                  }}
                >
                  {s === 'ALL' ? '전체' : STATUS_LABEL[s]}
                </button>
              ))}
            </div>

            {loading && <div className="spinner" />}

            {!loading && filteredReservations.length === 0 && (
              <div className="empty-state">
                <div className="icon">📋</div>
                <p>예약 내역이 없어요</p>
              </div>
            )}

            {!loading && filteredReservations.map(r => (
              <div key={r.reservationId} style={{ background: '#fff', borderRadius: 'var(--radius-md)', boxShadow: 'var(--shadow)', padding: '20px', marginBottom: '12px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '12px' }}>
                <div style={{ flex: 1 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '6px' }}>
                    <span style={{ fontSize: '1rem', fontWeight: 700 }}>{r.restaurantName}</span>
                    <span style={{ fontSize: '0.75rem', fontWeight: 600, padding: '3px 9px', borderRadius: '50px', background: STATUS_COLOR[r.status]?.bg, color: STATUS_COLOR[r.status]?.color }}>
                      {STATUS_LABEL[r.status]}
                    </span>
                  </div>
                  <div style={{ fontSize: '0.86rem', color: 'var(--text-sub)' }}>
                    📅 {r.slotDate} · 🕐 {r.slotTime} · 👥 {r.headcount}명
                  </div>
                </div>
                {r.status === 'CONFIRMED' && (
                  <button
                    onClick={() => cancelReservation(r.reservationId)}
                    style={{ padding: '8px 14px', border: '1.5px solid #D32F2F', borderRadius: 'var(--radius-sm)', background: '#fff', color: '#D32F2F', fontSize: '0.82rem', fontWeight: 600, cursor: 'pointer', whiteSpace: 'nowrap' }}
                  >
                    취소
                  </button>
                )}
              </div>
            ))}
          </div>
        )}

        {/* 웨이팅 탭 */}
        {tab === 'waiting' && (
          <div>
            {loading && <div className="spinner" />}

            {!loading && waitings.length === 0 && (
              <div className="empty-state">
                <div className="icon">⏳</div>
                <p>웨이팅 내역이 없어요</p>
              </div>
            )}

            {!loading && waitings.map(w => (
              <div key={w.waitingId} style={{ background: '#fff', borderRadius: 'var(--radius-md)', boxShadow: 'var(--shadow)', padding: '20px', marginBottom: '12px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '12px' }}>
                <div style={{ flex: 1 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '6px' }}>
                    <span style={{ fontSize: '1rem', fontWeight: 700 }}>{w.restaurantName}</span>
                    <span style={{ fontSize: '0.75rem', fontWeight: 600, padding: '3px 9px', borderRadius: '50px', background: STATUS_COLOR[w.status]?.bg, color: STATUS_COLOR[w.status]?.color }}>
                      {STATUS_LABEL[w.status]}
                    </span>
                  </div>
                  <div style={{ fontSize: '0.86rem', color: 'var(--text-sub)' }}>
                    🔢 대기번호 {w.waitingNumber}번 · 👥 {w.partySize}명
                  </div>
                  {w.status === 'CALLED' && (
                    <div style={{ marginTop: '8px', padding: '8px 12px', background: 'var(--primary-light)', borderRadius: 'var(--radius-sm)', fontSize: '0.84rem', color: 'var(--primary)', fontWeight: 600 }}>
                      🔔 입장 차례입니다! 매장 앞으로 와주세요.
                    </div>
                  )}
                </div>
                {w.status === 'WAITING' && (
                  <button
                    onClick={() => cancelWaiting(w.waitingId)}
                    style={{ padding: '8px 14px', border: '1.5px solid #D32F2F', borderRadius: 'var(--radius-sm)', background: '#fff', color: '#D32F2F', fontSize: '0.82rem', fontWeight: 600, cursor: 'pointer', whiteSpace: 'nowrap' }}
                  >
                    취소
                  </button>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}