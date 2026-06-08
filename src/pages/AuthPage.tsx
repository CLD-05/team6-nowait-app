import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import Header from '../components/Header';

const USE_DUMMY = true;
const API_BASE = '/api/v1';

export default function AuthPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [tab, setTab] = useState<'login' | 'signup'>(
    searchParams.get('tab') === 'signup' ? 'signup' : 'login'
  );

  // 폼 상태
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [name, setName] = useState('');
  const [role, setRole] = useState<'USER' | 'OWNER'>('USER');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  // 로그인 처리
  async function handleLogin() {
    if (!email || !password) { setError('이메일과 비밀번호를 입력해주세요.'); return; }
    setLoading(true);
    setError('');
    try {
      if (USE_DUMMY) {
        // 더미 로그인 (백엔드 연동 전)
        await new Promise(r => setTimeout(r, 600));
        localStorage.setItem('nowait_token', 'dummy_token_12345');
        const savedRole = localStorage.getItem('nowait_signup_role') || 'USER';
        localStorage.setItem('nowait_user', JSON.stringify({
          email,
          name: savedRole === 'OWNER' ? '테스트 점주' : '테스트 유저',
          role: savedRole,
        }));
        localStorage.removeItem('nowait_signup_role');
        window.location.href = '/';
      } else {
        // 실제 API 호출
        const res = await fetch(`${API_BASE}/auth/login`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ email, password }),
        });
        if (!res.ok) { setError('이메일 또는 비밀번호가 일치하지 않습니다.'); return; }
        const data = await res.json();
        localStorage.setItem('nowait_token', data.accessToken);
        localStorage.setItem('nowait_user', JSON.stringify(data.user));
        window.location.href = '/';
      }
    } catch {
      setError('로그인 중 오류가 발생했습니다.');
    } finally {
      setLoading(false);
    }
  }

  // 회원가입 처리
  async function handleSignup() {
    if (!email || !password || !name) { setError('모든 항목을 입력해주세요.'); return; }
    if (password.length < 8) { setError('비밀번호는 8자 이상이어야 합니다.'); return; }
    setLoading(true);
    setError('');
    try {
      if (USE_DUMMY) {
        await new Promise(r => setTimeout(r, 600));
        localStorage.setItem('nowait_signup_role', role);
        alert('회원가입 완료! 로그인해주세요.');
        setTab('login');
      } else {
        const res = await fetch(`${API_BASE}/auth/signup`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ email, password, name, role }),
        });
        if (!res.ok) { setError('이미 사용 중인 이메일입니다.'); return; }
        alert('회원가입 완료! 로그인해주세요.');
        setTab('login');
      }
    } catch {
      setError('회원가입 중 오류가 발생했습니다.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg)' }}>
      <Header />

      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '60px 20px' }}>
        <div style={{ background: '#fff', borderRadius: 'var(--radius-lg)', boxShadow: 'var(--shadow)', padding: '40px', width: '100%', maxWidth: '420px' }}>

          {/* 로고 */}
          <div style={{ textAlign: 'center', marginBottom: '28px' }}>
            <div style={{ fontSize: '1.8rem', fontWeight: 800, color: 'var(--primary)' }}>
              No<span style={{ color: 'var(--text)' }}>wait</span>
            </div>
            <p style={{ color: 'var(--text-sub)', fontSize: '0.88rem', marginTop: '6px' }}>
              기다림 없이 예약하세요
            </p>
          </div>

          {/* 탭 */}
          <div style={{ display: 'flex', background: 'var(--bg)', borderRadius: 'var(--radius-sm)', padding: '4px', marginBottom: '28px' }}>
            {(['login', 'signup'] as const).map(t => (
              <button
                key={t}
                onClick={() => { setTab(t); setError(''); }}
                style={{
                  flex: 1, padding: '10px', border: 'none', borderRadius: '6px', cursor: 'pointer',
                  fontWeight: 600, fontSize: '0.9rem', transition: 'all 0.15s',
                  background: tab === t ? '#fff' : 'transparent',
                  color: tab === t ? 'var(--primary)' : 'var(--text-sub)',
                  boxShadow: tab === t ? 'var(--shadow)' : 'none',
                }}
              >
                {t === 'login' ? '로그인' : '회원가입'}
              </button>
            ))}
          </div>

          {/* 에러 메시지 */}
          {error && (
            <div style={{ background: '#FFF0F0', color: '#D32F2F', padding: '12px 16px', borderRadius: 'var(--radius-sm)', fontSize: '0.86rem', marginBottom: '16px', borderLeft: '3px solid #D32F2F' }}>
              {error}
            </div>
          )}

          {/* 폼 */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>

            {/* 이름 (회원가입만) */}
            {tab === 'signup' && (
              <div>
                <label style={{ fontSize: '0.86rem', fontWeight: 600, color: 'var(--text)', display: 'block', marginBottom: '6px' }}>이름</label>
                <input
                  type="text" placeholder="이름을 입력하세요" value={name}
                  onChange={e => setName(e.target.value)}
                  style={{ width: '100%', padding: '12px 16px', border: '1.5px solid var(--border)', borderRadius: 'var(--radius-sm)', fontSize: '0.92rem', outline: 'none', transition: 'border 0.15s' }}
                  onFocus={e => e.target.style.borderColor = 'var(--primary)'}
                  onBlur={e => e.target.style.borderColor = 'var(--border)'}
                />
              </div>
            )}

            {/* 이메일 */}
            <div>
              <label style={{ fontSize: '0.86rem', fontWeight: 600, color: 'var(--text)', display: 'block', marginBottom: '6px' }}>이메일</label>
              <input
                type="email" placeholder="이메일을 입력하세요" value={email}
                onChange={e => setEmail(e.target.value)}
                onKeyDown={e => e.key === 'Enter' && tab === 'login' && handleLogin()}
                style={{ width: '100%', padding: '12px 16px', border: '1.5px solid var(--border)', borderRadius: 'var(--radius-sm)', fontSize: '0.92rem', outline: 'none', transition: 'border 0.15s' }}
                onFocus={e => e.target.style.borderColor = 'var(--primary)'}
                onBlur={e => e.target.style.borderColor = 'var(--border)'}
              />
            </div>

            {/* 비밀번호 */}
            <div>
              <label style={{ fontSize: '0.86rem', fontWeight: 600, color: 'var(--text)', display: 'block', marginBottom: '6px' }}>비밀번호</label>
              <input
                type="password" placeholder={tab === 'signup' ? '8자 이상 입력하세요' : '비밀번호를 입력하세요'} value={password}
                onChange={e => setPassword(e.target.value)}
                onKeyDown={e => e.key === 'Enter' && tab === 'login' && handleLogin()}
                style={{ width: '100%', padding: '12px 16px', border: '1.5px solid var(--border)', borderRadius: 'var(--radius-sm)', fontSize: '0.92rem', outline: 'none', transition: 'border 0.15s' }}
                onFocus={e => e.target.style.borderColor = 'var(--primary)'}
                onBlur={e => e.target.style.borderColor = 'var(--border)'}
              />
            </div>

            {/* 역할 선택 (회원가입만) */}
            {tab === 'signup' && (
              <div>
                <label style={{ fontSize: '0.86rem', fontWeight: 600, color: 'var(--text)', display: 'block', marginBottom: '8px' }}>회원 유형</label>
                <div style={{ display: 'flex', gap: '10px' }}>
                  {(['USER', 'OWNER'] as const).map(r => (
                    <button
                      key={r}
                      onClick={() => setRole(r)}
                      style={{
                        flex: 1, padding: '12px', border: `1.5px solid ${role === r ? 'var(--primary)' : 'var(--border)'}`,
                        borderRadius: 'var(--radius-sm)', background: role === r ? 'var(--primary-light)' : '#fff',
                        color: role === r ? 'var(--primary)' : 'var(--text-sub)',
                        fontWeight: 600, fontSize: '0.88rem', cursor: 'pointer', transition: 'all 0.15s',
                      }}
                    >
                      {r === 'USER' ? '👤 일반 사용자' : '🏪 점주'}
                    </button>
                  ))}
                </div>
              </div>
            )}

            {/* 제출 버튼 */}
            <button
              onClick={tab === 'login' ? handleLogin : handleSignup}
              disabled={loading}
              style={{
                width: '100%', padding: '14px', background: loading ? '#ccc' : 'var(--primary)',
                color: '#fff', border: 'none', borderRadius: 'var(--radius-sm)',
                fontSize: '1rem', fontWeight: 700, cursor: loading ? 'not-allowed' : 'pointer',
                marginTop: '6px', transition: 'background 0.15s',
              }}
            >
              {loading ? '처리 중...' : tab === 'login' ? '로그인' : '회원가입'}
            </button>

          </div>
        </div>
      </div>
    </div>
  );
}