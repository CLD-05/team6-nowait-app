import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Header from '../components/Header';
import { request } from '../lib/api';

type SessionStatus = 'OPEN' | 'PAUSED' | 'CLOSED';
type WaitingStatus = 'WAITING' | 'CALLED' | 'ENTERED' | 'CANCELLED';
type ReservationStatus = 'PENDING' | 'CONFIRMED' | 'VISITED' | 'NO_SHOW' | 'REJECTED';

type WaitingSession = {
  sessionId: number;
  status: SessionStatus;
  currentCount: number;
  maxWaitingCount: number;
  openedAt: string;
};

type OwnerWaiting = {
  waitingToken: string;
  waitingNumber: number;
  partySize: number;
  status: WaitingStatus;
  registeredAt: string;
};

type OwnerReservation = {
  reservationToken: string;
  reservationId: number | null;
  userName: string;
  slotDate: string;
  slotTime: string;
  headcount: number;
  status: ReservationStatus;
};

type Restaurant = {
  id: number;
  name: string;
};

const STATUS_LABEL: Record<SessionStatus | WaitingStatus | ReservationStatus, string> = {
  OPEN: '운영중',
  PAUSED: '일시정지',
  CLOSED: '마감',
  WAITING: '대기중',
  CALLED: '호출됨',
  ENTERED: '입장 완료',
  CANCELLED: '취소',
  PENDING: '예약 대기',
  CONFIRMED: '예약 확정',
  VISITED: '방문 완료',
  NO_SHOW: '노쇼',
  REJECTED: '예약 거부',
};

const STATUS_CLASS: Record<WaitingStatus | ReservationStatus, string> = {
  WAITING: 'tag-waiting',
  CALLED: 'tag-called',
  ENTERED: 'tag-entered',
  CANCELLED: 'tag-cancelled',
  PENDING: 'tag-waiting',
  CONFIRMED: 'tag-confirmed',
  VISITED: 'tag-visited',
  NO_SHOW: 'tag-noshow',
  REJECTED: 'tag-cancelled',
};

function formatDateTime(raw: string | null | undefined): string {
  if (!raw) return '';
  const d = new Date(raw);
  if (isNaN(d.getTime())) return raw;
  return `${d.getHours()}:${String(d.getMinutes()).padStart(2, '0')}`;
}

const POLL_MS = 30_000;

export default function OwnerPage() {
  const navigate = useNavigate();
  const [tab, setTab] = useState<'waiting' | 'reservation'>('waiting');

  const [restaurant, setRestaurant] = useState<Restaurant | null>(null);
  const [restaurantLoading, setRestaurantLoading] = useState(true);

  const [session, setSession] = useState<WaitingSession | null>(null);
  const [waitings, setWaitings] = useState<OwnerWaiting[]>([]);
  const [dataLoading, setDataLoading] = useState(false);

  const [reservations, setReservations] = useState<OwnerReservation[]>([]);
  const [rejectingToken, setRejectingToken] = useState<string | null>(null);
  const [rejectReason, setRejectReason] = useState('SLOT_FULL');

  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // ── 데이터 로드 ─────────────────────────────────────────────

  const fetchLiveData = useCallback(async (restaurantId: number) => {
    const [sessionRes, waitingsRes, reservationsRes] = await Promise.allSettled([
      request<WaitingSession>(`/restaurants/${restaurantId}/waiting-session`),
      request<OwnerWaiting[]>(`/owners/restaurants/${restaurantId}/waitings`),
      request<Array<{
        reservationToken: string; reservationId: number | null;
        slotDate: string; slotTime: string; headcount: number;
        status: ReservationStatus; userName?: string;
      }>>(`/owner/restaurants/${restaurantId}/reservations`),
    ]);

    if (sessionRes.status === 'fulfilled') setSession(sessionRes.value);
    else setSession(null);

    if (waitingsRes.status === 'fulfilled') {
      setWaitings(waitingsRes.value.map(w => ({
        ...w,
        registeredAt: formatDateTime(w.registeredAt),
      })));
    } else {
      setWaitings([]);
    }

    if (reservationsRes.status === 'fulfilled') {
      setReservations(reservationsRes.value.map(r => ({
        reservationToken: r.reservationToken,
        reservationId: r.reservationId,
        userName: r.userName ?? '고객',
        slotDate: r.slotDate,
        slotTime: r.slotTime,
        headcount: r.headcount,
        status: r.status,
      })));
    } else {
      setReservations([]);
      if (reservationsRes.reason instanceof Error) {
        console.error('예약 목록 조회 실패:', reservationsRes.reason.message);
      }
    }
  }, []);

  useEffect(() => {
    const token = localStorage.getItem('nowait_token');
    if (!token) { navigate('/auth'); return; }

    async function init() {
      setRestaurantLoading(true);
      try {
        const data = await request<Restaurant>('/restaurants/mine');
        setRestaurant(data);
        setDataLoading(true);
        await fetchLiveData(data.id);
        setDataLoading(false);
        pollRef.current = setInterval(() => void fetchLiveData(data.id), POLL_MS);
      } catch {
        setRestaurant(null);
      } finally {
        setRestaurantLoading(false);
      }
    }

    void init();
    return () => { if (pollRef.current) clearInterval(pollRef.current); };
  }, [navigate, fetchLiveData]);

  // ── 액션 핸들러 ─────────────────────────────────────────────

  async function callWaiting(token: string) {
    try {
      await request(`/owners/waitings/${token}/call`, { method: 'PATCH' });
      setWaitings(prev => prev.map(w => w.waitingToken === token ? { ...w, status: 'CALLED' } : w));
    } catch (err) {
      alert(err instanceof Error ? err.message : '호출에 실패했습니다.');
    }
  }

  async function enterWaiting(token: string) {
    try {
      await request(`/owners/waitings/${token}/enter`, { method: 'PATCH' });
      setWaitings(prev => prev.map(w => w.waitingToken === token ? { ...w, status: 'ENTERED' } : w));
      setSession(s => s ? { ...s, currentCount: Math.max(0, s.currentCount - 1) } : s);
    } catch (err) {
      alert(err instanceof Error ? err.message : '입장 처리에 실패했습니다.');
    }
  }

  async function approveReservation(token: string) {
    try {
      await request(`/owner/reservations/${token}/approve`, { method: 'PATCH' });
      setReservations(prev => prev.map(r => r.reservationToken === token ? { ...r, status: 'CONFIRMED' } : r));
    } catch (err) {
      alert(err instanceof Error ? err.message : '승인 처리에 실패했습니다.');
    }
  }

  async function rejectReservation(token: string, reason: string) {
    try {
      await request(`/owner/reservations/${token}/reject`, {
        method: 'PATCH',
        body: JSON.stringify({ reason }),
      });
      setReservations(prev => prev.map(r => r.reservationToken === token ? { ...r, status: 'REJECTED' } : r));
      setRejectingToken(null);
    } catch (err) {
      alert(err instanceof Error ? err.message : '거부 처리에 실패했습니다.');
    }
  }

  async function markVisited(token: string) {
    try {
      await request(`/owner/reservations/${token}/visit`, { method: 'PATCH' });
      setReservations(prev => prev.map(r => r.reservationToken === token ? { ...r, status: 'VISITED' } : r));
    } catch (err) {
      alert(err instanceof Error ? err.message : '방문완료 처리에 실패했습니다.');
    }
  }

  async function markNoShow(token: string) {
    if (!confirm('노쇼 처리하시겠어요?')) return;
    try {
      await request(`/owner/reservations/${token}/noshow`, { method: 'PATCH' });
      setReservations(prev => prev.map(r => r.reservationToken === token ? { ...r, status: 'NO_SHOW' } : r));
    } catch (err) {
      alert(err instanceof Error ? err.message : '노쇼 처리에 실패했습니다.');
    }
  }

  async function changeSessionStatus(action: 'pause' | 'resume' | 'close') {
    if (!session) return;
    const label = { pause: '일시정지', resume: '재개', close: '마감' }[action];
    if (!confirm(`웨이팅을 ${label}하시겠어요?`)) return;
    try {
      const updated = await request<WaitingSession>(
        `/owners/waiting-sessions/${session.sessionId}/${action}`,
        { method: 'PATCH' },
      );
      setSession(updated);
    } catch (err) {
      alert(err instanceof Error ? err.message : '처리에 실패했습니다.');
    }
  }

  // ── 파생 상태 ────────────────────────────────────────────────

  const activeWaitings = useMemo(
    () => waitings.filter(w => w.status !== 'CANCELLED' && w.status !== 'ENTERED'),
    [waitings],
  );
  const progress = session
    ? Math.min(100, Math.round((session.currentCount / session.maxWaitingCount) * 100))
    : 0;

  // ── 로딩 ────────────────────────────────────────────────────

  if (restaurantLoading) {
    return (
      <div style={{ minHeight: '100vh', background: 'var(--cream)' }}>
        <Header />
        <div className="spinner" />
      </div>
    );
  }

  // ── 식당 미등록 ──────────────────────────────────────────────

  if (!restaurant) {
    return (
      <div style={{ minHeight: '100vh', background: 'var(--cream)' }}>
        <Header />
        <main className="container" style={{ padding: '60px 26px', textAlign: 'center' }}>
          <div style={{
            maxWidth: 480, margin: '0 auto', background: '#fff',
            border: '2.5px solid var(--ink)', borderRadius: 20,
            boxShadow: '6px 6px 0 var(--ink)', padding: '40px 32px',
          }}>
            <div style={{ fontSize: '3rem', marginBottom: 16 }}>🏪</div>
            <h2 style={{ fontSize: '1.4rem', fontWeight: 900, marginBottom: 10 }}>
              아직 등록된 매장이 없어요
            </h2>
            <p style={{ color: 'var(--muted)', fontWeight: 700, marginBottom: 28, lineHeight: 1.7 }}>
              대시보드를 사용하려면 먼저 매장 정보를 등록해주세요.
            </p>
            <button
              type="button"
              className="btn btn-tomato"
              onClick={() => navigate('/owner/store')}
            >
              매장 등록하기
            </button>
          </div>
        </main>
      </div>
    );
  }

  // ── 메인 렌더 ────────────────────────────────────────────────

  return (
    <div style={{ minHeight: '100vh', background: 'var(--cream)' }}>
      <Header />

      <main className="container" style={{ padding: '34px 26px 76px' }}>
        {/* 매장 정보 배너 */}
        <section
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: '18px',
            flexWrap: 'wrap',
            background: '#fff',
            border: '2.5px solid var(--ink)',
            borderRadius: '8px',
            padding: '18px 20px',
            marginBottom: '22px',
          }}
        >
          <div>
            <strong style={{ display: 'block', fontSize: '18px', fontWeight: 900 }}>{restaurant.name}</strong>
            <span style={{ color: 'var(--muted)', fontSize: '14px', fontWeight: 700 }}>
              기본 정보와 예약·웨이팅 운영 여부를 관리합니다.
            </span>
          </div>
          <button type="button" className="btn btn-amber btn-sm" onClick={() => navigate('/owner/store')}>
            매장 관리
          </button>
        </section>

        {/* 히어로 + 세션 카드 */}
        <section
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 320px), 1fr))',
            gap: '22px',
            alignItems: 'stretch',
            marginBottom: '24px',
          }}
        >
          {/* 히어로 */}
          <div
            className="card-base"
            style={{
              background: 'var(--tomato)',
              color: '#fff',
              padding: '28px',
              display: 'flex',
              flexDirection: 'column',
              justifyContent: 'space-between',
              gap: '18px',
            }}
          >
            <div>
              <span className="badge badge-amber" style={{ color: 'var(--ink)', boxShadow: '3px 3px 0 var(--ink)' }}>
                OWNER DASHBOARD
              </span>
              <h1 style={{ fontSize: 'clamp(30px, 4.6vw, 50px)', fontWeight: 900, letterSpacing: '-0.05em', lineHeight: 1.06, marginTop: '14px' }}>
                오늘의 예약과
                <br />
                웨이팅을 관리하세요.
              </h1>
            </div>
            <p style={{ fontSize: '15px', fontWeight: 800, opacity: 0.92 }}>
              호출, 입장, 방문 완료, 노쇼 처리까지 한 화면에서 빠르게 진행할 수 있습니다.
            </p>
          </div>

          {/* 웨이팅 세션 카드 */}
          <div className="card-base" style={{ background: '#fff', padding: '24px' }}>
            {session ? (
              <>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '12px' }}>
                  <div>
                    <span className="kicker">WAITING SESSION</span>
                    <h2 style={{ fontSize: '24px', fontWeight: 900, letterSpacing: '-0.04em' }}>웨이팅 세션</h2>
                  </div>
                  <span className={`tag ${session.status === 'OPEN' ? 'tag-open' : session.status === 'PAUSED' ? 'tag-called' : 'tag-cancelled'}`}>
                    {STATUS_LABEL[session.status]}
                  </span>
                </div>

                <div style={{ display: 'flex', alignItems: 'end', justifyContent: 'space-between', marginTop: '22px', gap: '10px' }}>
                  <div>
                    <div style={{ color: 'var(--muted)', fontSize: '13px', fontWeight: 900 }}>현재 대기</div>
                    <div style={{ color: 'var(--tomato)', fontSize: '58px', fontWeight: 900, lineHeight: 1 }}>
                      {dataLoading ? '—' : session.currentCount}
                    </div>
                  </div>
                  <div style={{ color: 'var(--muted)', fontSize: '14px', fontWeight: 800, textAlign: 'right' }}>
                    최대 {session.maxWaitingCount}팀
                    <br />
                    시작 {formatDateTime(session.openedAt)}
                  </div>
                </div>

                <div style={{ height: '14px', background: 'var(--cream)', border: '2.5px solid var(--ink)', borderRadius: '999px', overflow: 'hidden', marginTop: '16px' }}>
                  <div style={{ width: `${progress}%`, height: '100%', background: 'var(--amber)', transition: 'width .2s ease' }} />
                </div>

                <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', marginTop: '20px' }}>
                  {session.status === 'OPEN' && (
                    <button type="button" className="btn btn-white btn-sm" onClick={() => void changeSessionStatus('pause')}>일시정지</button>
                  )}
                  {session.status === 'PAUSED' && (
                    <button type="button" className="btn btn-amber btn-sm" onClick={() => void changeSessionStatus('resume')}>재개</button>
                  )}
                  {session.status !== 'CLOSED' && (
                    <button type="button" className="btn btn-white btn-sm" style={{ color: 'var(--tomato)' }}
                      onClick={() => void changeSessionStatus('close')}>마감</button>
                  )}
                </div>
              </>
            ) : (
              <div style={{ height: '100%', display: 'flex', flexDirection: 'column', justifyContent: 'center', alignItems: 'center', gap: 14 }}>
                <span className="kicker">WAITING SESSION</span>
                <p style={{ fontWeight: 800, color: 'var(--muted)', textAlign: 'center' }}>
                  오늘 오픈된 웨이팅 세션이 없어요.
                </p>
                <button type="button" className="btn btn-amber btn-sm" onClick={() => navigate('/owner/store')}>
                  세션 오픈하기
                </button>
              </div>
            )}
          </div>
        </section>

        {/* 탭 */}
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: '1fr 1fr',
            gap: '8px',
            background: '#fff',
            border: '3px solid var(--ink)',
            borderRadius: '22px',
            boxShadow: '5px 5px 0 var(--ink)',
            padding: '7px',
            marginBottom: '22px',
          }}
        >
          {(['waiting', 'reservation'] as const).map(item => (
            <button
              key={item}
              type="button"
              onClick={() => setTab(item)}
              className={`btn ${tab === item ? 'btn-tomato' : 'btn-white'} btn-sm`}
            >
              {item === 'waiting' ? `웨이팅 관리 (${activeWaitings.length})` : '예약 관리'}
            </button>
          ))}
        </div>

        {/* 웨이팅 탭 */}
        {tab === 'waiting' && (
          <section>
            {dataLoading ? (
              <div className="spinner" />
            ) : activeWaitings.length === 0 ? (
              <div className="empty-state card-base" style={{ background: '#fff' }}>
                <p>대기 중인 손님이 없습니다.</p>
              </div>
            ) : (
              <div style={{ display: 'grid', gap: '14px' }}>
                {activeWaitings.map(waiting => (
                  <article
                    key={waiting.waitingToken}
                    className="card-base"
                    style={{ background: '#fff', padding: '20px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '16px', flexWrap: 'wrap' }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: '16px', minWidth: 0 }}>
                      <div style={{
                        width: '56px', height: '56px', borderRadius: '18px',
                        background: 'var(--amber)', border: '2.5px solid var(--ink)',
                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                        fontSize: '22px', fontWeight: 900, boxShadow: '3px 3px 0 var(--ink)', flexShrink: 0,
                      }}>
                        {waiting.waitingNumber}
                      </div>
                      <div>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
                          <h3 style={{ fontSize: '20px', fontWeight: 900, letterSpacing: '-0.04em' }}>
                            {waiting.waitingNumber}번 손님
                          </h3>
                          <span className={`tag ${STATUS_CLASS[waiting.status]}`}>
                            {STATUS_LABEL[waiting.status]}
                          </span>
                        </div>
                        <p style={{ color: 'var(--muted)', fontSize: '14px', fontWeight: 700, marginTop: '6px' }}>
                          {waiting.partySize}명 · {waiting.registeredAt} 등록
                        </p>
                      </div>
                    </div>

                    <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                      {waiting.status === 'WAITING' && (
                        <button type="button" className="btn btn-tomato btn-sm"
                          onClick={() => void callWaiting(waiting.waitingToken)}>
                          호출
                        </button>
                      )}
                      {waiting.status === 'CALLED' && (
                        <>
                          <button type="button" className="btn btn-amber btn-sm"
                            onClick={() => void enterWaiting(waiting.waitingToken)}>
                            입장
                          </button>
                          <button type="button" className="btn btn-tomato btn-sm"
                            onClick={() => void callWaiting(waiting.waitingToken)}>
                            재호출
                          </button>
                        </>
                      )}
                    </div>
                  </article>
                ))}
              </div>
            )}
          </section>
        )}

        {/* 예약 탭 */}
        {tab === 'reservation' && (
          <section>
            {dataLoading ? (
              <div className="spinner" />
            ) : reservations.length === 0 ? (
              <div className="empty-state card-base" style={{ background: '#fff' }}>
                <p>예약 내역이 없습니다.</p>
              </div>
            ) : (
              <div style={{ display: 'grid', gap: '14px' }}>
                {reservations.map(reservation => (
                  <article
                    key={reservation.reservationToken}
                    className="card-base"
                    style={{ background: '#fff', padding: '20px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '16px', flexWrap: 'wrap' }}
                  >
                    <div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
                        <h3 style={{ fontSize: '20px', fontWeight: 900, letterSpacing: '-0.04em' }}>{reservation.userName}</h3>
                        <span className={`tag ${STATUS_CLASS[reservation.status]}`}>{STATUS_LABEL[reservation.status]}</span>
                      </div>
                      <p style={{ color: 'var(--muted)', fontSize: '14px', fontWeight: 700, marginTop: '6px' }}>
                        📅 {reservation.slotDate} · 🕐 {reservation.slotTime} · 👥 {reservation.headcount}명
                      </p>
                    </div>
                    {reservation.status === 'PENDING' && (
                      rejectingToken === reservation.reservationToken ? (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 8, minWidth: 200 }}>
                          <select value={rejectReason} onChange={e => setRejectReason(e.target.value)}
                            style={{ padding: '6px 10px', border: '2px solid var(--ink)', borderRadius: 8, fontFamily: 'inherit', fontWeight: 700, fontSize: '0.82rem' }}>
                            <option value="SLOT_FULL">예약이 마감됐습니다</option>
                            <option value="STORE_CLOSED">매장 사정으로 운영 불가</option>
                            <option value="PARTY_SIZE_UNAVAILABLE">요청 인원 수용 불가</option>
                            <option value="SPECIAL_EVENT">단체 행사로 좌석 부족</option>
                            <option value="OTHER">기타 사유</option>
                          </select>
                          <div style={{ display: 'flex', gap: 6 }}>
                            <button type="button" className="btn btn-sm"
                              style={{ background: 'var(--tomato)', color: '#fff', border: '2px solid var(--ink)', flex: 1 }}
                              onClick={() => void rejectReservation(reservation.reservationToken, rejectReason)}>
                              거부 확인
                            </button>
                            <button type="button" className="btn btn-white btn-sm"
                              onClick={() => setRejectingToken(null)}>
                              취소
                            </button>
                          </div>
                        </div>
                      ) : (
                        <div style={{ display: 'flex', gap: '8px' }}>
                          <button type="button" className="btn btn-amber btn-sm"
                            onClick={() => void approveReservation(reservation.reservationToken)}>
                            승인
                          </button>
                          <button type="button" className="btn btn-sm"
                            style={{ background: '#fff', border: '2px solid var(--tomato)', color: 'var(--tomato)' }}
                            onClick={() => { setRejectingToken(reservation.reservationToken); setRejectReason('SLOT_FULL'); }}>
                            거부
                          </button>
                        </div>
                      )
                    )}
                    {reservation.status === 'CONFIRMED' && (
                      <div style={{ display: 'flex', gap: '8px' }}>
                        <button type="button" className="btn btn-amber btn-sm"
                          onClick={() => void markVisited(reservation.reservationToken)}>
                          방문완료
                        </button>
                        <button type="button" className="btn btn-sm"
                          style={{ background: '#f5f5f5', border: '2px solid var(--ink)', color: '#666' }}
                          onClick={() => void markNoShow(reservation.reservationToken)}>
                          노쇼
                        </button>
                      </div>
                    )}
                  </article>
                ))}
              </div>
            )}
          </section>
        )}
      </main>
    </div>
  );
}
