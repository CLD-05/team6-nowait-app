import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Header from '../components/Header';

type SessionStatus = 'OPEN' | 'PAUSED' | 'CLOSED';
type WaitingStatus = 'WAITING' | 'CALLED' | 'ENTERED' | 'CANCELLED';
type ReservationStatus = 'CONFIRMED' | 'VISITED' | 'NO_SHOW';

type WaitingSession = {
  sessionId: number;
  status: SessionStatus;
  currentCount: number;
  maxWaitingCount: number;
  openedAt: string;
};

type OwnerWaiting = {
  waitingId: number;
  waitingNumber: number;
  partySize: number;
  status: WaitingStatus;
  registeredAt: string;
};

type OwnerReservation = {
  reservationId: number;
  userName: string;
  slotTime: string;
  headcount: number;
  status: ReservationStatus;
};

const DUMMY_SESSION: WaitingSession = {
  sessionId: 1,
  status: 'OPEN',
  currentCount: 7,
  maxWaitingCount: 30,
  openedAt: '2026-06-08 11:00',
};

const DUMMY_WAITINGS: OwnerWaiting[] = [
  { waitingId: 1, waitingNumber: 1, partySize: 2, status: 'WAITING', registeredAt: '11:05' },
  { waitingId: 2, waitingNumber: 2, partySize: 4, status: 'CALLED', registeredAt: '11:10' },
  { waitingId: 3, waitingNumber: 3, partySize: 2, status: 'WAITING', registeredAt: '11:15' },
  { waitingId: 4, waitingNumber: 4, partySize: 3, status: 'ENTERED', registeredAt: '11:20' },
  { waitingId: 5, waitingNumber: 5, partySize: 2, status: 'WAITING', registeredAt: '11:25' },
];

const DUMMY_RESERVATIONS: OwnerReservation[] = [
  { reservationId: 1, userName: '김철수', slotTime: '12:00', headcount: 2, status: 'CONFIRMED' },
  { reservationId: 2, userName: '이영희', slotTime: '12:30', headcount: 4, status: 'CONFIRMED' },
  { reservationId: 3, userName: '박민준', slotTime: '13:00', headcount: 2, status: 'VISITED' },
  { reservationId: 4, userName: '최지우', slotTime: '13:30', headcount: 3, status: 'NO_SHOW' },
];

const STATUS_LABEL: Record<SessionStatus | WaitingStatus | ReservationStatus, string> = {
  OPEN: '운영중',
  PAUSED: '일시정지',
  CLOSED: '마감',
  WAITING: '대기중',
  CALLED: '호출됨',
  ENTERED: '입장 완료',
  CANCELLED: '취소',
  CONFIRMED: '예약 확정',
  VISITED: '방문 완료',
  NO_SHOW: '노쇼',
};

const STATUS_CLASS: Record<WaitingStatus | ReservationStatus, string> = {
  WAITING: 'tag-waiting',
  CALLED: 'tag-called',
  ENTERED: 'tag-entered',
  CANCELLED: 'tag-cancelled',
  CONFIRMED: 'tag-confirmed',
  VISITED: 'tag-visited',
  NO_SHOW: 'tag-noshow',
};

export default function OwnerPage() {
  const navigate = useNavigate();
  const [tab, setTab] = useState<'waiting' | 'reservation'>('waiting');
  const [session, setSession] = useState<WaitingSession>(DUMMY_SESSION);
  const [waitings, setWaitings] = useState<OwnerWaiting[]>(DUMMY_WAITINGS);
  const [reservations, setReservations] = useState<OwnerReservation[]>(DUMMY_RESERVATIONS);

  useEffect(() => {
    if (!localStorage.getItem('nowait_token')) navigate('/auth');
  }, [navigate]);

  const activeWaitings = useMemo(
    () => waitings.filter(waiting => waiting.status !== 'CANCELLED'),
    [waitings],
  );
  const progress = Math.min(100, Math.round((session.currentCount / session.maxWaitingCount) * 100));

  function changeSessionStatus(action: 'pause' | 'resume' | 'close') {
    const actionLabel = { pause: '일시정지', resume: '재개', close: '마감' }[action];
    if (!confirm(`웨이팅을 ${actionLabel}하시겠어요?`)) return;

    const statusMap: Record<typeof action, SessionStatus> = {
      pause: 'PAUSED',
      resume: 'OPEN',
      close: 'CLOSED',
    };

    setSession(current => ({ ...current, status: statusMap[action] }));
  }

  function callWaiting(waitingId: number) {
    setWaitings(current =>
      current.map(waiting => (waiting.waitingId === waitingId ? { ...waiting, status: 'CALLED' } : waiting)),
    );
  }

  function enterWaiting(waitingId: number) {
    setWaitings(current =>
      current.map(waiting => (waiting.waitingId === waitingId ? { ...waiting, status: 'ENTERED' } : waiting)),
    );
    setSession(current => ({ ...current, currentCount: Math.max(0, current.currentCount - 1) }));
  }

  function markVisited(reservationId: number) {
    setReservations(current =>
      current.map(reservation =>
        reservation.reservationId === reservationId ? { ...reservation, status: 'VISITED' } : reservation,
      ),
    );
  }

  function markNoShow(reservationId: number) {
    setReservations(current =>
      current.map(reservation =>
        reservation.reservationId === reservationId ? { ...reservation, status: 'NO_SHOW' } : reservation,
      ),
    );
  }

  return (
    <div style={{ minHeight: '100vh', background: 'var(--cream)' }}>
      <Header />

      <main className="container" style={{ padding: '34px 26px 76px' }}>
        <section
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 320px), 1fr))',
            gap: '22px',
            alignItems: 'stretch',
            marginBottom: '24px',
          }}
        >
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
              <span
                className="badge badge-amber"
                style={{
                  color: 'var(--ink)',
                  boxShadow: '3px 3px 0 var(--ink)',
                }}
              >
                OWNER DASHBOARD
              </span>
              <h1
                style={{
                  fontSize: 'clamp(30px, 4.6vw, 50px)',
                  fontWeight: 900,
                  letterSpacing: '-0.05em',
                  lineHeight: 1.06,
                  marginTop: '14px',
                }}
              >
                오늘의 예약과
                <br />
                웨이팅을 관리하세요.
              </h1>
            </div>
            <p style={{ fontSize: '15px', fontWeight: 800, opacity: 0.92 }}>
              호출, 입장, 방문 완료, 노쇼 처리까지 한 화면에서 빠르게 진행할 수 있습니다.
            </p>
          </div>

          <div className="card-base" style={{ background: '#fff', padding: '24px' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '12px' }}>
              <div>
                <span className="kicker">WAITING SESSION</span>
                <h2 style={{ fontSize: '24px', fontWeight: 900, letterSpacing: '-0.04em' }}>
                  웨이팅 세션
                </h2>
              </div>
              <span
                className={`tag ${
                  session.status === 'OPEN'
                    ? 'tag-open'
                    : session.status === 'PAUSED'
                      ? 'tag-called'
                      : 'tag-cancelled'
                }`}
              >
                {STATUS_LABEL[session.status]}
              </span>
            </div>

            <div
              style={{
                display: 'flex',
                alignItems: 'end',
                justifyContent: 'space-between',
                marginTop: '22px',
                gap: '10px',
              }}
            >
              <div>
                <div style={{ color: 'var(--muted)', fontSize: '13px', fontWeight: 900 }}>현재 대기</div>
                <div style={{ color: 'var(--tomato)', fontSize: '58px', fontWeight: 900, lineHeight: 1 }}>
                  {session.currentCount}
                </div>
              </div>
              <div style={{ color: 'var(--muted)', fontSize: '14px', fontWeight: 800, textAlign: 'right' }}>
                최대 {session.maxWaitingCount}팀
                <br />
                시작 {session.openedAt}
              </div>
            </div>

            <div
              style={{
                height: '14px',
                background: 'var(--cream)',
                border: '2.5px solid var(--ink)',
                borderRadius: '999px',
                overflow: 'hidden',
                marginTop: '16px',
              }}
            >
              <div
                style={{
                  width: `${progress}%`,
                  height: '100%',
                  background: 'var(--amber)',
                  transition: 'width .2s ease',
                }}
              />
            </div>

            <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', marginTop: '20px' }}>
              {session.status === 'OPEN' && (
                <button type="button" className="btn btn-white btn-sm" onClick={() => changeSessionStatus('pause')}>
                  일시정지
                </button>
              )}
              {session.status === 'PAUSED' && (
                <button type="button" className="btn btn-amber btn-sm" onClick={() => changeSessionStatus('resume')}>
                  재개
                </button>
              )}
              {session.status !== 'CLOSED' && (
                <button
                  type="button"
                  className="btn btn-white btn-sm"
                  onClick={() => changeSessionStatus('close')}
                  style={{ color: 'var(--tomato)' }}
                >
                  마감
                </button>
              )}
            </div>
          </div>
        </section>

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
              {item === 'waiting' ? '웨이팅 관리' : '예약 관리'}
            </button>
          ))}
        </div>

        {tab === 'waiting' && (
          <section>
            {activeWaitings.length === 0 ? (
              <div className="empty-state card-base" style={{ background: '#fff' }}>
                <p>대기 중인 손님이 없습니다.</p>
              </div>
            ) : (
              <div style={{ display: 'grid', gap: '14px' }}>
                {activeWaitings.map(waiting => (
                  <article
                    key={waiting.waitingId}
                    className="card-base"
                    style={{
                      background: '#fff',
                      padding: '20px',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      gap: '16px',
                      flexWrap: 'wrap',
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: '16px', minWidth: 0 }}>
                      <div
                        style={{
                          width: '56px',
                          height: '56px',
                          borderRadius: '18px',
                          background: 'var(--amber)',
                          border: '2.5px solid var(--ink)',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          fontSize: '22px',
                          fontWeight: 900,
                          boxShadow: '3px 3px 0 var(--ink)',
                          flexShrink: 0,
                        }}
                      >
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
                        <button type="button" className="btn btn-tomato btn-sm" onClick={() => callWaiting(waiting.waitingId)}>
                          호출
                        </button>
                      )}
                      {waiting.status === 'CALLED' && (
                        <button type="button" className="btn btn-amber btn-sm" onClick={() => enterWaiting(waiting.waitingId)}>
                          입장
                        </button>
                      )}
                      {waiting.status === 'ENTERED' && <span className="tag tag-entered">처리 완료</span>}
                    </div>
                  </article>
                ))}
              </div>
            )}
          </section>
        )}

        {tab === 'reservation' && (
          <section>
            {reservations.length === 0 ? (
              <div className="empty-state card-base" style={{ background: '#fff' }}>
                <p>오늘 예약이 없습니다.</p>
              </div>
            ) : (
              <div style={{ display: 'grid', gap: '14px' }}>
                {reservations.map(reservation => (
                  <article
                    key={reservation.reservationId}
                    className="card-base"
                    style={{
                      background: '#fff',
                      padding: '20px',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      gap: '16px',
                      flexWrap: 'wrap',
                    }}
                  >
                    <div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
                        <h3 style={{ fontSize: '20px', fontWeight: 900, letterSpacing: '-0.04em' }}>
                          {reservation.userName}
                        </h3>
                        <span className={`tag ${STATUS_CLASS[reservation.status]}`}>
                          {STATUS_LABEL[reservation.status]}
                        </span>
                      </div>
                      <p style={{ color: 'var(--muted)', fontSize: '14px', fontWeight: 700, marginTop: '6px' }}>
                        {reservation.slotTime} · {reservation.headcount}명
                      </p>
                    </div>

                    {reservation.status === 'CONFIRMED' && (
                      <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                        <button
                          type="button"
                          className="btn btn-amber btn-sm"
                          onClick={() => markVisited(reservation.reservationId)}
                        >
                          방문 완료
                        </button>
                        <button
                          type="button"
                          className="btn btn-white btn-sm"
                          onClick={() => markNoShow(reservation.reservationId)}
                          style={{ color: 'var(--tomato)' }}
                        >
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
