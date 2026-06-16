import { useState, useEffect, useCallback, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { API_BASE, request } from '../lib/api';

const USE_DUMMY = false;
const STATUS_POLL_MS = 5000;

type WaitingStatus = {
  waitingToken: string;
  restaurantId: number;
  restaurantName?: string;
  waitingNumber: number;
  partySize: number;
  status: string;
  aheadCount: number;
  totalWaiting: number;
  estimatedMinutes: number;
  registeredAt: string;
};

const DUMMY_STATUS = {
  waitingToken: 'waiting-1',
  restaurantId: 1,
  restaurantName: '미진 1호점',
  waitingNumber: 8,
  partySize: 2,
  status: 'WAITING',
  aheadCount: 3,
  totalWaiting: 8,
  estimatedMinutes: 12,
  registeredAt: '12:30',
};

function formatDateTime(raw: string): string {
  if (!raw) return '';
  const d = new Date(raw);
  if (isNaN(d.getTime())) return raw;
  return `${d.getHours()}:${String(d.getMinutes()).padStart(2, '0')}`;
}

export default function WaitingStatusPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [data, setData] = useState<WaitingStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [pulse, setPulse] = useState(false);
  const [sseConnected, setSseConnected] = useState(false);
  const esRef = useRef<EventSource | null>(null);

  const fetchStatus = useCallback(async () => {
    try {
      if (USE_DUMMY) {
        await new Promise(r => setTimeout(r, 400));
        // 더미: 랜덤으로 aheadCount 변화
        setData(prev => prev
          ? { ...prev, aheadCount: Math.max(0, prev.aheadCount - Math.round(Math.random())) }
          : DUMMY_STATUS
        );
      } else {
        let waiting: Omit<WaitingStatus, 'restaurantName' | 'totalWaiting' | 'estimatedMinutes'>;
        try {
          waiting = await request<Omit<WaitingStatus, 'restaurantName' | 'totalWaiting' | 'estimatedMinutes'>>('/waitings/me');
        } catch {
          setData(null);
          return;
        }

        if (id && waiting.waitingToken !== id) {
          setData(null);
          return;
        }

        const restaurantResponse = await fetch(`${API_BASE}/restaurants/${waiting.restaurantId}`);
        const restaurant = restaurantResponse.ok
          ? await restaurantResponse.json() as { name?: string }
          : null;
        const aheadCount = waiting.aheadCount ?? 0;
        const registeredAt = formatDateTime(waiting.registeredAt);
        setData({
          ...waiting,
          aheadCount,
          restaurantName: restaurant?.name,
          totalWaiting: aheadCount + 1,
          estimatedMinutes: aheadCount * 5,
          registeredAt,
        });
      }
      setPulse(true);
      setTimeout(() => setPulse(false), 600);
    } finally {
      setLoading(false);
    }
  }, [id]);

  // 최초 로드 + polling fallback
useEffect(() => {
  const token = localStorage.getItem('nowait_token');

  if (!token) {
    navigate('/auth');
    return;
  }

  void fetchStatus();

  const timer = window.setInterval(() => {
    void fetchStatus();
  }, STATUS_POLL_MS);

  return () => {
    window.clearInterval(timer);
  };
}, [fetchStatus, navigate]);

  // SSE 연결 — 웨이팅 상태 변경 시 서버가 push, 폴링 없이 즉시 갱신
  useEffect(() => {
    const token = localStorage.getItem('nowait_token');
    if (!token) return;

    let es: EventSource | null = null;

    const connect = async () => {
      try {
        const res = await fetch(`${API_BASE}/notifications/stream/ticket`, {
          method: 'POST',
          headers: { Authorization: `Bearer ${token}` },
        });
        if (!res.ok) return;
        const { ticket } = await res.json() as { ticket: string };

        es = new EventSource(`${API_BASE}/notifications/stream?ticket=${ticket}`);
        esRef.current = es;

        es.addEventListener('connect', () => setSseConnected(true));

        es.addEventListener('notification', (e: MessageEvent) => {
          try {
            const payload = JSON.parse(e.data) as { type: string };
            if (
              payload.type === 'WAITING_CALLED' ||
              payload.type === 'WAITING_NEAR_CALL'
            ) {
              void fetchStatus();
            }
          } catch { /* 파싱 실패 무시 */ }
        });

        es.onerror = () => {
          setSseConnected(false);
          es?.close();
          esRef.current = null;
        };
      } catch { /* 연결 실패 무시 */ }
    };

    void connect();
    return () => {
      es?.close();
      esRef.current = null;
      setSseConnected(false);
    };
  }, [fetchStatus]);

  async function cancelWaiting() {
    if (!confirm('웨이팅을 취소하시겠어요?')) return;
    try {
      if (!USE_DUMMY) {
        await request(`/waitings/${id}/cancel`, { method: 'PATCH' });
      }
      alert('웨이팅이 취소됐어요.');
      navigate('/mypage');
    } catch { alert('취소 중 오류가 발생했습니다.'); }
  }

  if (loading) return (
    <div style={{ minHeight: '100vh', background: 'var(--cream)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <div className="spinner" />
    </div>
  );

  if (!data) return (
    <div style={{ minHeight: '100vh', background: 'var(--cream)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <p style={{ fontWeight: 800 }}>웨이팅 정보를 찾을 수 없어요</p>
    </div>
  );

  const progressPct = data.totalWaiting > 0
    ? Math.round(((data.totalWaiting - data.aheadCount) / data.totalWaiting) * 100)
    : 100;
  const isCalled = data.status === 'CALLED';
  const isNearCall = data.status === 'WAITING' && data.aheadCount <= 2 && data.aheadCount > 0;
  const isEntered = data.status === 'ENTERED';
  const isCancelled = data.status === 'CANCELLED';
  const canCancel = data.status === 'WAITING' || data.status === 'CALLED';

  return (
    <div style={{ minHeight: '100vh', background: isCalled ? 'var(--tomato)' : isNearCall ? '#fff7e6' : 'var(--cream)', transition: 'background 0.5s', opacity: isCancelled ? 0.85 : 1 }}>
      <div style={{ maxWidth: 480, margin: '0 auto', padding: '32px 20px 60px' }}>

        {/* 헤더 */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 28 }}>
          <button onClick={() => navigate('/mypage')}
            style={{
              width: 40, height: 40, borderRadius: '50%',
              background: isCalled ? 'rgba(255,255,255,0.2)' : '#fff',
              border: '2.5px solid var(--ink)', cursor: 'pointer', fontWeight: 900,
              boxShadow: '3px 3px 0 var(--ink)', color: isCalled ? '#fff' : 'var(--ink)'
            }}>
            ←
          </button>
          <div style={{ textAlign: 'center' }}>
            <div style={{ fontSize: '0.78rem', fontWeight: 800, color: isCalled ? 'rgba(255,255,255,0.8)' : 'var(--muted)', letterSpacing: 1 }}>
              WAITING STATUS
            </div>
            <div style={{ fontWeight: 900, fontSize: '1rem', color: isCalled ? '#fff' : 'var(--ink)' }}>
              {data.restaurantName}
            </div>
          </div>
          <div style={{ width: 40 }} />
        </div>

        {/* 곧 호출 상태 (2팀 이하 남음) */}
        {isNearCall && (
          <div style={{ textAlign: 'center', marginBottom: 24, padding: '16px', background: '#ffb22e', border: '2.5px solid var(--ink)', borderRadius: 16, boxShadow: '4px 4px 0 var(--ink)', animation: 'fadeUp 0.5s ease' }}>
            <div style={{ fontSize: '2.5rem', marginBottom: 6 }}>⚡</div>
            <div style={{ fontSize: '1.2rem', fontWeight: 900, color: 'var(--ink)', marginBottom: 4 }}>
              곧 호출될 예정이에요!
            </div>
            <div style={{ fontSize: '0.9rem', fontWeight: 700, color: 'rgba(0,0,0,0.7)' }}>
              앞에 {data.aheadCount}팀 남았습니다. 매장 근처에 대기해 주세요.
            </div>
          </div>
        )}

        {/* 호출됨 상태 */}
        {isCalled && (
          <div style={{ textAlign: 'center', marginBottom: 24, animation: 'fadeUp 0.5s ease' }}>
            <div style={{ fontSize: '4rem', marginBottom: 8 }}>🔔</div>
            <div style={{ fontSize: '1.6rem', fontWeight: 900, color: '#fff', marginBottom: 8 }}>
              입장 차례예요!
            </div>
            <div style={{ fontSize: '1rem', color: 'rgba(255,255,255,0.9)', fontWeight: 700 }}>
              10분 안에 매장 앞으로 와주세요
            </div>
          </div>
        )}

        {/* 입장 완료 상태 */}
        {isEntered && (
          <div style={{ textAlign: 'center', marginBottom: 24 }}>
            <div style={{ fontSize: '4rem', marginBottom: 8 }}>🎉</div>
            <div style={{ fontSize: '1.4rem', fontWeight: 900, marginBottom: 8 }}>입장 완료!</div>
            <div style={{ fontSize: '0.95rem', color: 'var(--muted)', fontWeight: 700 }}>즐거운 식사 되세요</div>
          </div>
        )}

        {/* 취소됨 상태 */}
        {isCancelled && (
          <div style={{ textAlign: 'center', marginBottom: 24 }}>
            <div style={{ fontSize: '4rem', marginBottom: 8 }}>❌</div>
            <div style={{ fontSize: '1.4rem', fontWeight: 900, marginBottom: 8 }}>웨이팅이 취소됐어요</div>
            <div style={{ fontSize: '0.95rem', color: 'var(--muted)', fontWeight: 700 }}>다음에 또 이용해주세요</div>
          </div>
        )}

        {/* 메인 티켓 */}
        <div style={{
          background: isCalled ? 'rgba(255,255,255,0.15)' : '#fff',
          border: `2.5px solid ${isCalled ? 'rgba(255,255,255,0.5)' : 'var(--ink)'}`,
          borderRadius: 24, boxShadow: isCalled ? 'none' : '6px 6px 0 var(--ink)',
          padding: 28, marginBottom: 16, transition: 'all 0.5s',
        }}>
          {/* 대기번호 */}
          <div style={{ textAlign: 'center', marginBottom: 24 }}>
            <div style={{
              fontSize: '0.82rem', fontWeight: 800,
              color: isCalled ? 'rgba(255,255,255,0.7)' : 'var(--muted)', marginBottom: 6
            }}>
              내 대기번호
            </div>
            <div style={{
              fontSize: '5rem', fontWeight: 900, lineHeight: 1, letterSpacing: '-3px',
              color: isCalled ? '#fff' : 'var(--tomato)',
              animation: pulse ? 'pulse 0.6s ease' : 'none',
            }}>
              {data.waitingNumber}
            </div>
            <div style={{ fontSize: '1rem', fontWeight: 800, color: isCalled ? 'rgba(255,255,255,0.8)' : 'var(--muted)', marginTop: 4 }}>
              번 · {data.partySize}명
            </div>
          </div>

          {/* 앞에 남은 팀 */}
          {!isEntered && (
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginBottom: 20 }}>
              <div style={{
                textAlign: 'center', padding: '16px 12px',
                background: isCalled ? 'rgba(255,255,255,0.1)' : 'var(--cream)',
                borderRadius: 14, border: `2px solid ${isCalled ? 'rgba(255,255,255,0.3)' : 'var(--ink)'}`
              }}>
                <div style={{
                  fontSize: '2rem', fontWeight: 900,
                  color: isCalled ? '#fff' : 'var(--tomato)', lineHeight: 1
                }}>
                  {isCalled ? 0 : data.aheadCount}
                </div>
                <div style={{
                  fontSize: '0.78rem', fontWeight: 800,
                  color: isCalled ? 'rgba(255,255,255,0.7)' : 'var(--muted)', marginTop: 4
                }}>
                  앞에 남은 팀
                </div>
              </div>
              <div style={{
                textAlign: 'center', padding: '16px 12px',
                background: isCalled ? 'rgba(255,255,255,0.1)' : 'var(--cream)',
                borderRadius: 14, border: `2px solid ${isCalled ? 'rgba(255,255,255,0.3)' : 'var(--ink)'}`
              }}>
                <div style={{
                  fontSize: '2rem', fontWeight: 900,
                  color: isCalled ? '#fff' : 'var(--amber)', lineHeight: 1
                }}>
                  {isCalled ? 0 : data.estimatedMinutes}
                </div>
                <div style={{
                  fontSize: '0.78rem', fontWeight: 800,
                  color: isCalled ? 'rgba(255,255,255,0.7)' : 'var(--muted)', marginTop: 4
                }}>
                  예상 대기(분)
                </div>
              </div>
            </div>
          )}

          {/* 진행바 */}
          {!isEntered && (
            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6 }}>
                <span style={{
                  fontSize: '0.78rem', fontWeight: 800,
                  color: isCalled ? 'rgba(255,255,255,0.7)' : 'var(--muted)'
                }}>입장까지</span>
                <span style={{
                  fontSize: '0.78rem', fontWeight: 900,
                  color: isCalled ? '#fff' : 'var(--tomato)'
                }}>{isCalled ? 100 : progressPct}%</span>
              </div>
              <div style={{
                height: 14, borderRadius: 999, background: isCalled ? 'rgba(255,255,255,0.15)' : 'var(--line)',
                border: `2px solid ${isCalled ? 'rgba(255,255,255,0.3)' : 'var(--ink)'}`, overflow: 'hidden'
              }}>
                <div style={{
                  height: '100%', borderRadius: 999,
                  background: isCalled ? '#fff' : 'var(--tomato)',
                  width: `${isCalled ? 100 : progressPct}%`, transition: 'width 0.8s ease'
                }} />
              </div>
            </div>
          )}
        </div>

        {/* 등록 정보 */}
        <div style={{
          background: isCalled ? 'rgba(255,255,255,0.1)' : '#fff',
          border: `2px solid ${isCalled ? 'rgba(255,255,255,0.3)' : 'var(--ink)'}`,
          borderRadius: 14, padding: '14px 18px', marginBottom: 16
        }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontSize: '0.84rem', fontWeight: 700, color: isCalled ? 'rgba(255,255,255,0.7)' : 'var(--muted)' }}>
              등록 시간
            </span>
            <span style={{ fontSize: '0.84rem', fontWeight: 900, color: isCalled ? '#fff' : 'var(--ink)' }}>
              {data.registeredAt}
            </span>
          </div>
        </div>

        {/* 실시간 연결 상태 — 연결됐을 때만 표시 */}
        {!isEntered && sseConnected && (
          <div style={{
            display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 6,
            padding: '8px 16px', marginBottom: 20
          }}>
            <div style={{
              width: 8, height: 8, borderRadius: '50%',
              background: isCalled ? '#fff' : 'var(--mint)',
              animation: 'blink 1s infinite'
            }} />
            <span style={{
              fontSize: '0.78rem', fontWeight: 800,
              color: isCalled ? 'rgba(255,255,255,0.7)' : 'var(--muted)'
            }}>
              실시간 연결됨
            </span>
          </div>
        )}

        {/* 버튼 */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          {canCancel && (
            <button onClick={cancelWaiting}
              style={{
                width: '100%', padding: 14, background: 'transparent',
                color: isCalled ? 'rgba(255,255,255,0.7)' : 'var(--muted)',
                border: `2px solid ${isCalled ? 'rgba(255,255,255,0.3)' : 'var(--line)'}`,
                borderRadius: 14, fontSize: '0.9rem', fontWeight: 700, cursor: 'pointer',
                fontFamily: 'inherit'
              }}>
              웨이팅 취소
            </button>
          )}
          {(isEntered || isCancelled) && (
            <button onClick={() => navigate('/')}
              style={{
                width: '100%', padding: 14, background: 'var(--tomato)', color: '#fff',
                border: '2.5px solid var(--ink)', borderRadius: 14, fontSize: '0.95rem',
                fontWeight: 900, cursor: 'pointer', fontFamily: 'inherit',
                boxShadow: '4px 4px 0 var(--ink)'
              }}>
              🏠 홈으로 돌아가기
            </button>
          )}
        </div>

      </div>

      <style>{`
        @keyframes pulse {
          0% { transform: scale(1); }
          50% { transform: scale(1.08); }
          100% { transform: scale(1); }
        }
        @keyframes blink {
          0%, 100% { opacity: 1; }
          50% { opacity: 0.3; }
        }
        @keyframes fadeUp {
          from { opacity: 0; transform: translateY(20px); }
          to { opacity: 1; transform: translateY(0); }
        }
      `}</style>
    </div>
  );
}
