import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';

export default function Header() {
  const navigate = useNavigate();
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [isOwner, setIsOwner] = useState(false);

  // 페이지 이동할 때마다 localStorage 다시 읽기
  useEffect(() => {
    const token = localStorage.getItem('nowait_token');
    const user = JSON.parse(localStorage.getItem('nowait_user') || '{}');
    setIsLoggedIn(!!token);
    setIsOwner(user?.role === 'OWNER');
  }, [window.location.pathname]);

  const handleLogout = () => {
    localStorage.removeItem('nowait_token');
    localStorage.removeItem('nowait_user');
    navigate('/');
    window.location.reload();
  };

  return (
    <header style={{
      height: 'var(--header-height)',
      background: 'rgba(255,255,255,0.95)',
      backdropFilter: 'blur(10px)',
      borderBottom: '1px solid var(--border)',
      position: 'sticky',
      top: 0,
      zIndex: 100,
      display: 'flex',
      alignItems: 'center',
      padding: '0 20px',
    }}>
      <div className="container" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', width: '100%' }}>

        {/* 로고 */}
        <div
          onClick={() => navigate('/')}
          style={{ fontSize: '1.5rem', fontWeight: 800, color: 'var(--primary)', cursor: 'pointer', letterSpacing: '-0.5px' }}
        >
          No<span style={{ color: 'var(--text)' }}>wait</span>
        </div>

        {/* 네비게이션 */}
        <nav style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
          {isLoggedIn ? (
            <>
              {isOwner && (
                <button className="btn btn-ghost" onClick={() => navigate('/owner')}>
                  점주 대시보드
                </button>
              )}
              <button className="btn btn-ghost" onClick={() => navigate('/mypage')}>
                마이페이지
              </button>
              <button className="btn btn-outline" onClick={handleLogout}>
                로그아웃
              </button>
            </>
          ) : (
            <>
              <button className="btn btn-ghost" onClick={() => navigate('/auth')}>
                로그인
              </button>
              <button className="btn btn-primary" onClick={() => navigate('/auth?tab=signup')}>
                회원가입
              </button>
            </>
          )}
        </nav>
      </div>
    </header>
  );
}