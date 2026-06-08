import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import Header from '../components/Header';

const USE_DUMMY = true;
const API_BASE = '/api/v1';

const DUMMY_SESSION = {
  sessionId: 1,
  status: 'OPEN',
  currentCount: 7,
  maxWaitingCount: 30,
  openedAt: '2026-06-08 11:00',
};

const DUMMY_WAITINGS = [
  { waitingId: 1, waitingNumber: 1, partySize: 2, status: 'WAITING', registeredAt: '11:05' },
  { waitingId: 2, waitingNumber: 2, partySize: 4, status: 'CALLED', registeredAt: '11:10' },
  { waitingId: 3, waitingNumber: 3, partySize: 2, status: 'WAITING', registeredAt: '11:15' },
  { waitingId: 4, waitingNumber: 4, partySize: 3, status: 'ENTERED', registeredAt: '11:20' },
  { waitingId: 5, waitingNumber: 5, partySize: 2, status: 'WAITING', registeredAt: '11:25' },
];

const DUMMY_RESERVATIONS = [
  { reservationId: 1, userName: '김철수', slotTime: '12:00', headcount: 2, status: 'CONFIRMED' },
  { reservationId: 2, userName: '이영희', slotTime: '12:30', headcount: 4, status: 'CONFIRMED' },
  { reservationId: 3, userName: '박민준', slotTime: '13:00', headcount: 2, status: 'VISITED' },
  { reservationId: 4, userName: '최지은', slotTime: '13:30', headcount: 3, status: 'NO_SHOW' },
];

const STATUS_COLOR: Record<string, { bg: string; color: string }> = {
  WAITING: { bg: '#EFF6FF', color: '#2563EB' },
  CALLED: { bg: '#FFF3EF', color: '#FF5722' },
  ENTERED: { bg: '#F0FDF4', color: '#16A34A' },
  CANCELLED: { bg: '#F5F5F5', color: '#9E9E9E' },
  CONFIRMED: { bg: '#EFF6FF', color: '#2563EB' },
  VISITED: { bg: '#F0FDF4', color: '#16A34A' },
  NO_SHOW: { bg: '#FFF0F0', color: '#D32F2F' },
};

const STATUS_LABEL: Record<string, string> = {
  WAITING: '대기중', CALLED: '호출됨', ENTERED: '입장완료', CANCELLED: '취소',
  CONFIRMED: '예약확정', VISITED: '방문완료', NO_SHOW: '노쇼',
};

export default function OwnerPage() {
  const navigate = useNavigate();
  const [tab, setTab] = useState<'waiting' | 'reservation'>('waiting');
  const [session, setSession] = useState<any>(null);
  const [waitings, setWaitings] = useState<any[]>([]);
  const [reservations, setReservations] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    // OWNER 권한 체크
    const user = JSON.parse(localStorage.getItem('nowait_user') || '{}');
    const token = localStorage.getItem('nowait_token');
    if (!token) { navigate('/auth'); return; }
    // 더미 모드에서는 권한 체크 스킵
    if (!USE_DUMMY && user.role !== 'OWNER') { navigate('/'); return; }
    fetchData();
  }, []);

  async function fetchData() {
    setLoading(true);
    try {
      if (USE_DUMMY) {
        await new Promise(r => setTimeout(r, 300));
        setSession(DUMMY_SESSION);
        setWaitings(DUMMY_WAITINGS);
        setReservations(DUMMY_RESERVATIONS);
      } else {
        const token = localStorage.getItem('nowait_token');
        const headers = { Authorization: `Bearer ${token}` };
        // 실제 API 호출 시 restaurantId 필요
      }
    } finally {
      setLoading(false);
    }
  }

  // 세션 상태 변경
  async function changeSessionStatus(action: 'pause' | 'resume' | 'close') {
    const actionLabel = { pause: '일시정지', resume: '재개', close: '마감' }[action];
    if (!confirm(`웨이팅을 ${actionLabel}하시겠어요?`)) return;
    if (USE_DUMMY) {
      const statusMap = { pause: 'PAUSED', resume: 'OPEN', close: 'CLOSED' };
      setSession((prev: any) => ({ ...prev, status: statusMap[action] }));
    } else {
      const token = localStorage.getItem('nowait_token');
      await fetch(`${API_BASE}/owner/waiting/sessions/${session.sessionId}/${action}`, {
        method: 'PATCH',
        headers: { Authorization: `Bearer ${token}` },
      });
      fetchData();
    }
  }

  // 웨이팅 호출
  async function callWaiting(waitingId: number) {
    if (USE_DUMMY) {
      setWaitings(prev => prev.map(w => w.waitingId === waitingId ? { ...w, status: 'CALLED' } : w));
    } else {
      const token = localStorage.getItem('nowait_token');
      await fetch(`${API_BASE}/owner/waiting/${waitingId}/call`, {
        method: 'PATCH',
        headers: { Authorization: `Bearer ${token}` },
      });
      fetchData();
    }
  }

  // 웨이팅 입장 처리
  async function enterWaiting(waitingId: number) {
    if (USE_DUMMY) {
      setWaitings(prev => prev.map(w => w.waitingId === waitingId ? { ...w, status: 'ENTERED' } : w));
      setSession((prev: any) => ({ ...prev, currentCount: Math.max(0, prev.currentCount - 1) }));
    } else {
      const token = localStorage.getItem('nowait_token');
      await fetch(`${API_BASE}/owner/waiting/${waitingId}/enter`, {
        method: 'PATCH',
        headers: { Authorization: `Bearer ${token}` },
      });
      fetchData();
    }
  }

  // 방문 완료
  async function markVisited(reservationId: number) {
    if (USE_DUMMY) {
      setReservations(prev => prev.map(r => r.reservationId === reservationId ? { ...r, status: 'VISITED' } : r));
    } else {
      const token = localStorage.getItem('nowait_token');
      await fetch(`${API_BASE}/owner/reservations/${reservationId}/visit`, {
        method: 'PATCH',
        headers: { Authorization: `Bearer ${token}` },
      });
      fetchData();
    }
  }

  // 노쇼 처리
  async function markNoShow(reservationId: number) {
    if (USE_DUMMY) {
      setReservations(prev => prev.map(r => r.reservationId === reservationId ? { ...r, status: 'NO_SHOW' } : r));
    } else {
      const token = localStorage.getItem('nowait_token');
      await fetch(`${API_BASE}/owner/reservations/${reservationId}/noshow`, {
        method: 'PATCH',
        headers: { Authorization: `Bearer ${token}` },
      });
      fetchData();
    }
  }

  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg)' }}>
      <Header />

      <div style={{ maxWidth: '900px', margin: '0 auto', padding: '32px 20px 60px' }}>

        {/* 타이틀 */}
        <div style={{ marginBottom: '24px' }}>
          <h1 style={{ fontSize: '1.5rem', fontWeight: 800 }}>🏪 점주 대시보드</h1>
          <p style={{ color: 'var(--text-sub)', fontSize: '0.88rem', marginTop: '4px' }}>오늘의 예약과 웨이팅을 관리하세요</p>
        </div>

        {/* 웨이팅 세션 카드 */}
        {session && (
          <div style={{ background: '#fff', borderRadius: 'var(--radius-md)', boxShadow: 'var(--shadow)', padding: '24px', marginBottom: '24px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px', flexWrap: 'wrap', gap: '12px' }}>
              <div>
                <div style={{ fontSize: '1rem', fontWeight: 700, marginBottom: '4px' }}>웨이팅 세션</div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span style={{ fontSize: '0.78rem', fontWeight: 600, padding: '3px 10px', borderRadius: '50px', background: session.status === 'OPEN' ? '#F0FDF4' : session.status === 'PAUSED' ? '#FFF3EF' : '#F5F5F5', color: session.status === 'OPEN' ? '#16A34A' : session.status === 'PAUSED' ? '#FF5722' : '#9E9E9E' }}>
                    {session.status === 'OPEN' ? '● 운영중' : session.status === 'PAUSED' ? '⏸ 일시정지' : '■ 마감'}
                  </span>
                  <span style={{ fontSize: '0.86rem', color: 'var(--text-sub)' }}>
                    {session.currentCount} / {session.maxWaitingCount}팀
                  </span>
                </div>
              </div>

              {/* 세션 제어 버튼 */}
              <div style={{ display: 'flex', gap: '8px' }}>
                {session.status === 'OPEN' && (
                  <button onClick={() => changeSessionStatus('pause')}
                    style={{ padding: '8px 14px', border: '1.5px solid var(--primary)', borderRadius: 'var(--radius-sm)', background: '#fff', color: 'var(--primary)', fontSize: '0.82rem', fontWeight: 600, cursor: 'pointer' }}>
                    ⏸ 일시정지
                  </button>
                )}
                {session.status === 'PAUSED' && (
                  <button onClick={() => changeSessionStatus('resume')}
                    style={{ padding: '8px 14px', border: '1.5px solid #16A34A', borderRadius: 'var(--radius-sm)', background: '#fff', color: '#16A34A', fontSize: '0.82rem', fontWeight: 600, cursor: 'pointer' }}>
                    ▶ 재개
                  </button>
                )}
                {session.status !== 'CLOSED' && (
                  <button onClick={() => changeSessionStatus('close')}
                    style={{ padding: '8px 14px', border: '1.5px solid #D32F2F', borderRadius: 'var(--radius-sm)', background: '#fff', color: '#D32F2F', fontSize: '0.82rem', fontWeight: 600, cursor: 'pointer' }}>
                    ■ 마감
                  </button>
                )}
              </div>
            </div>

            {/* 대기 현황 바 */}
            <div style={{ background: 'var(--bg)', borderRadius: '50px', height: '8px', overflow: 'hidden' }}>
              <div style={{ height: '100%', background: 'var(--primary)', borderRadius: '50px', width: `${(session.currentCount / session.maxWaitingCount) * 100}%`, transition: 'width 0.3s' }} />
            </div>
            <div style={{ fontSize: '0.78rem', color: 'var(--text-sub)', marginTop: '6px', textAlign: 'right' }}>
              최대 {session.maxWaitingCount}팀
            </div>
          </div>
        )}

        {/* 탭 */}
        <div style={{ display: 'flex', background: '#fff', borderRadius: 'var(--radius-md)', boxShadow: 'var(--shadow)', overflow: 'hidden', marginBottom: '20px' }}>
          {(['waiting', 'reservation'] as const).map(t => (
            <button key={t} onClick={() => setTab(t)}
              style={{ flex: 1, padding: '16px', border: 'none', cursor: 'pointer', fontWeight: 700, fontSize: '0.95rem', transition: 'all 0.15s', background: tab === t ? 'var(--primary)' : '#fff', color: tab === t ? '#fff' : 'var(--text-sub)' }}>
              {t === 'waiting' ? '⏳ 웨이팅 관리' : '📋 예약 관리'}
            </button>
          ))}
        </div>

        {loading && <div className="spinner" />}

        {/* 웨이팅 관리 탭 */}
        {!loading && tab === 'waiting' && (
          <div>
            {waitings.filter(w => w.status !== 'CANCELLED').length === 0 ? (
              <div className="empty-state"><div className="icon">⏳</div><p>대기 중인 손님이 없어요</p></div>
            ) : (
              waitings.filter(w => w.status !== 'CANCELLED').map(w => (
                <div key={w.waitingId} style={{ background: '#fff', borderRadius: 'var(--radius-md)', boxShadow: 'var(--shadow)', padding: '20px', marginBottom: '12px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '12px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                    <div style={{ width: '48px', height: '48px', borderRadius: '50%', background: 'var(--primary-light)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '1.1rem', fontWeight: 800, color: 'var(--primary)', flexShrink: 0 }}>
                      {w.waitingNumber}
                    </div>
                    <div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '4px' }}>
                        <span style={{ fontWeight: 700 }}>{w.waitingNumber}번</span>
                        <span style={{ fontSize: '0.75rem', fontWeight: 600, padding: '2px 8px', borderRadius: '50px', background: STATUS_COLOR[w.status]?.bg, color: STATUS_COLOR[w.status]?.color }}>
                          {STATUS_LABEL[w.status]}
                        </span>
                      </div>
                      <div style={{ fontSize: '0.84rem', color: 'var(--text-sub)' }}>
                        👥 {w.partySize}명 · 🕐 {w.registeredAt} 등록
                      </div>
                    </div>
                  </div>
                  <div style={{ display: 'flex', gap: '8px' }}>
                    {w.status === 'WAITING' && (
                      <button onClick={() => callWaiting(w.waitingId)}
                        style={{ padding: '8px 14px', border: '1.5px solid var(--primary)', borderRadius: 'var(--radius-sm)', background: 'var(--primary-light)', color: 'var(--primary)', fontSize: '0.82rem', fontWeight: 600, cursor: 'pointer' }}>
                        📣 호출
                      </button>
                    )}
                    {w.status === 'CALLED' && (
                      <button onClick={() => enterWaiting(w.waitingId)}
                        style={{ padding: '8px 14px', border: '1.5px solid #16A34A', borderRadius: 'var(--radius-sm)', background: '#F0FDF4', color: '#16A34A', fontSize: '0.82rem', fontWeight: 600, cursor: 'pointer' }}>
                        ✅ 입장
                      </button>
                    )}
                    {w.status === 'ENTERED' && (
                      <span style={{ fontSize: '0.82rem', color: '#16A34A', fontWeight: 600 }}>입장완료</span>
                    )}
                  </div>
                </div>
              ))
            )}
          </div>
        )}

        {/* 예약 관리 탭 */}
        {!loading && tab === 'reservation' && (
          <div>
            {reservations.length === 0 ? (
              <div className="empty-state"><div className="icon">📋</div><p>오늘 예약이 없어요</p></div>
            ) : (
              reservations.map(r => (
                <div key={r.reservationId} style={{ background: '#fff', borderRadius: 'var(--radius-md)', boxShadow: 'var(--shadow)', padding: '20px', marginBottom: '12px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '12px' }}>
                  <div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '6px' }}>
                      <span style={{ fontWeight: 700 }}>{r.userName}</span>
                      <span style={{ fontSize: '0.75rem', fontWeight: 600, padding: '2px 8px', borderRadius: '50px', background: STATUS_COLOR[r.status]?.bg, color: STATUS_COLOR[r.status]?.color }}>
                        {STATUS_LABEL[r.status]}
                      </span>
                    </div>
                    <div style={{ fontSize: '0.84rem', color: 'var(--text-sub)' }}>
                      🕐 {r.slotTime} · 👥 {r.headcount}명
                    </div>
                  </div>
                  {r.status === 'CONFIRMED' && (
                    <div style={{ display: 'flex', gap: '8px' }}>
                      <button onClick={() => markVisited(r.reservationId)}
                        style={{ padding: '8px 14px', border: '1.5px solid #16A34A', borderRadius: 'var(--radius-sm)', background: '#F0FDF4', color: '#16A34A', fontSize: '0.82rem', fontWeight: 600, cursor: 'pointer' }}>
                        ✅ 방문완료
                      </button>
                      <button onClick={() => markNoShow(r.reservationId)}
                        style={{ padding: '8px 14px', border: '1.5px solid #D32F2F', borderRadius: 'var(--radius-sm)', background: '#FFF0F0', color: '#D32F2F', fontSize: '0.82rem', fontWeight: 600, cursor: 'pointer' }}>
                        ❌ 노쇼
                      </button>
                    </div>
                  )}
                </div>
              ))
            )}
          </div>
        )}
      </div>
    </div>
  );
}