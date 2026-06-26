import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import Header from '../components/Header';
import Logo from '../components/Logo';

import { API_BASE } from '../lib/api';

type AuthTab = 'login' | 'signup';
type UserRole = 'USER' | 'OWNER';

export default function AuthPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [tab, setTab] = useState<AuthTab>(searchParams.get('tab') === 'signup' ? 'signup' : 'login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [name, setName] = useState('');
  const [role, setRole] = useState<UserRole>('USER');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!localStorage.getItem('nowait_user')) return;
    navigate('/', { replace: true });
  }, [navigate]);

  const switchTab = (nextTab: AuthTab) => {
    setTab(nextTab);
    setError('');
  };

  async function handleLogin() {
    if (!email || !password) {
      setError('이메일과 비밀번호를 입력해주세요.');
      return;
    }

    setLoading(true);
    setError('');

    try {
      const response = await fetch(`${API_BASE}/auth/login`, {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password }),
      });

      if (!response.ok) {
        setError('이메일 또는 비밀번호가 일치하지 않습니다.');
        return;
      }

      const data = await response.json() as {
        userId: number;
        email: string;
        name: string;
        role: UserRole;
      };
      localStorage.setItem('nowait_user', JSON.stringify({
        id: data.userId,
        email: data.email,
        name: data.name,
        role: data.role,
      }));
      navigate('/', { replace: true });
    } catch {
      setError('로그인 중 오류가 발생했습니다.');
    } finally {
      setLoading(false);
    }
  }

  async function handleSignup() {
    if (!email || !password || !name) {
      setError('모든 항목을 입력해주세요.');
      return;
    }

    if (password.length < 8) {
      setError('비밀번호는 8자 이상이어야 합니다.');
      return;
    }

    setLoading(true);
    setError('');

    try {
      const signupPath = role === 'OWNER' ? '/auth/signup/owner' : '/auth/signup';
      const response = await fetch(`${API_BASE}${signupPath}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password, name }),
      });

      if (!response.ok) {
        const error = await response.json().catch(() => ({})) as {
          message?: string;
          errors?: Array<{ reason?: string }>;
        };
        setError(error.errors?.[0]?.reason || error.message || '회원가입에 실패했습니다.');
        return;
      }

      alert('회원가입이 완료되었습니다. 로그인해주세요.');
      switchTab('login');
    } catch {
      setError('회원가입 중 오류가 발생했습니다.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div style={{ minHeight: '100vh', background: 'var(--cream)' }}>
      <Header />

      <main
        className="container"
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 320px), 1fr))',
          gap: '32px',
          alignItems: 'center',
          padding: '58px 26px 76px',
        }}
      >
        <section>
          <span className="badge badge-amber" style={{ transform: 'rotate(-2deg)' }}>
            NOWAIT ACCOUNT
          </span>
          <h1
            style={{
              fontSize: 'clamp(36px, 5vw, 60px)',
              fontWeight: 900,
              letterSpacing: '-0.05em',
              lineHeight: 1.04,
              marginTop: '18px',
              maxWidth: '560px',
            }}
          >
            줄서기 전에
            <br />
            먼저 로그인하세요.
          </h1>
          <p
            style={{
              color: 'var(--muted)',
              fontSize: '18px',
              fontWeight: 600,
              lineHeight: 1.65,
              marginTop: '18px',
              maxWidth: '460px',
            }}
          >
            예약, 웨이팅, 방문 내역을 한 번에 관리할 수 있습니다.
          </p>
          <div
            style={{
              display: 'flex',
              flexWrap: 'wrap',
              gap: '12px',
              marginTop: '28px',
            }}
          >
            <span className="badge badge-white" style={{ boxShadow: '3px 3px 0 var(--ink)' }}>
              빠른 예약
            </span>
            <span className="badge badge-white" style={{ boxShadow: '3px 3px 0 var(--ink)' }}>
              실시간 웨이팅
            </span>
            <span className="badge badge-white" style={{ boxShadow: '3px 3px 0 var(--ink)' }}>
              사장님 관리
            </span>
          </div>
        </section>

        <section
          className="card-base"
          style={{
            background: '#fff',
            padding: '34px',
            maxWidth: '460px',
            width: '100%',
            justifySelf: 'end',
          }}
        >
          <div style={{ textAlign: 'center', marginBottom: '26px' }}>
            <div
              style={{
                display: 'flex',
                justifyContent: 'center',
              }}
            >
              <Logo width="min(190px, 72vw)" />
            </div>
            <p style={{ color: 'var(--muted)', fontSize: '14px', fontWeight: 700, marginTop: '8px' }}>
              기다림 없는 외식을 시작하세요
            </p>
          </div>

          <div
            style={{
              display: 'grid',
              gridTemplateColumns: '1fr 1fr',
              gap: '8px',
              background: 'var(--cream)',
              border: '2.5px solid var(--ink)',
              borderRadius: '18px',
              padding: '6px',
              marginBottom: '22px',
            }}
          >
            {(['login', 'signup'] as const).map(item => (
              <button
                key={item}
                type="button"
                onClick={() => switchTab(item)}
                className={`btn ${tab === item ? 'btn-tomato' : 'btn-white'} btn-sm`}
                style={{ width: '100%' }}
              >
                {item === 'login' ? '로그인' : '회원가입'}
              </button>
            ))}
          </div>

          {error && (
            <div
              style={{
                background: 'var(--tomato-light)',
                color: 'var(--tomato-d)',
                border: '2px solid var(--tomato)',
                borderRadius: '16px',
                padding: '12px 14px',
                fontSize: '14px',
                fontWeight: 800,
                marginBottom: '16px',
              }}
            >
              {error}
            </div>
          )}

          <div style={{ display: 'grid', gap: '14px' }}>
            {tab === 'signup' && (
              <label style={{ display: 'grid', gap: '7px', fontSize: '14px', fontWeight: 900 }}>
                이름
                <input
                  type="text"
                  placeholder="이름을 입력하세요"
                  value={name}
                  onChange={event => setName(event.target.value)}
                  style={{
                    width: '100%',
                    padding: '13px 15px',
                    border: '2.5px solid var(--ink)',
                    borderRadius: '14px',
                    fontSize: '15px',
                    fontWeight: 700,
                    outline: 'none',
                    background: '#fff',
                  }}
                />
              </label>
            )}

            <label style={{ display: 'grid', gap: '7px', fontSize: '14px', fontWeight: 900 }}>
              이메일
              <input
                type="email"
                placeholder="이메일을 입력하세요"
                value={email}
                onChange={event => setEmail(event.target.value)}
                onKeyDown={event => {
                  if (event.key === 'Enter' && tab === 'login') handleLogin();
                }}
                style={{
                  width: '100%',
                  padding: '13px 15px',
                  border: '2.5px solid var(--ink)',
                  borderRadius: '14px',
                  fontSize: '15px',
                  fontWeight: 700,
                  outline: 'none',
                  background: '#fff',
                }}
              />
            </label>

            <label style={{ display: 'grid', gap: '7px', fontSize: '14px', fontWeight: 900 }}>
              비밀번호
              <input
                type="password"
                placeholder={tab === 'signup' ? '8자 이상 입력하세요' : '비밀번호를 입력하세요'}
                value={password}
                onChange={event => setPassword(event.target.value)}
                onKeyDown={event => {
                  if (event.key === 'Enter' && tab === 'login') handleLogin();
                }}
                style={{
                  width: '100%',
                  padding: '13px 15px',
                  border: '2.5px solid var(--ink)',
                  borderRadius: '14px',
                  fontSize: '15px',
                  fontWeight: 700,
                  outline: 'none',
                  background: '#fff',
                }}
              />
            </label>

            {tab === 'signup' && (
              <div>
                <div style={{ fontSize: '14px', fontWeight: 900, marginBottom: '8px' }}>회원 유형</div>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px' }}>
                  {(['USER', 'OWNER'] as const).map(item => (
                    <button
                      key={item}
                      type="button"
                      onClick={() => setRole(item)}
                      className={`btn ${role === item ? 'btn-amber' : 'btn-white'} btn-sm`}
                      style={{ width: '100%' }}
                    >
                      {item === 'USER' ? '일반 회원' : '사장님'}
                    </button>
                  ))}
                </div>
              </div>
            )}

            <button
              type="button"
              onClick={tab === 'login' ? handleLogin : handleSignup}
              disabled={loading}
              className="btn btn-tomato btn-lg"
              style={{ width: '100%', marginTop: '8px', opacity: loading ? 0.65 : 1 }}
            >
              {loading ? '처리 중...' : tab === 'login' ? '로그인' : '회원가입'}
            </button>
          </div>
        </section>
      </main>
    </div>
  );
}
