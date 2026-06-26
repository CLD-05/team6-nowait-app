import { useLocation, useNavigate } from 'react-router-dom';
import Logo from './Logo';
import { API_BASE } from '../lib/api';

type StoredUser = {
  role?: 'USER' | 'OWNER';
};

function readUser(): StoredUser {
  try {
    return JSON.parse(localStorage.getItem('nowait_user') || '{}') as StoredUser;
  } catch {
    return {};
  }
}

export default function Header() {
  const navigate = useNavigate();
  const location = useLocation();
  const user = readUser();
  const isLoggedIn = Boolean(localStorage.getItem('nowait_user'));
  const isOwner = user.role === 'OWNER';

  const handleLogout = async () => {
    try {
      await fetch(`${API_BASE}/auth/logout`, { method: 'POST', credentials: 'include' });
    } catch {
      // 쿠키 삭제는 서버 응답 실패해도 클라 상태는 정리한다.
    }
    localStorage.removeItem('nowait_user');
    navigate('/');
    window.location.reload();
  };

  const isCurrent = (path: string) => location.pathname === path;
  const isOwnerPage = location.pathname.startsWith('/owner');

  return (
    <header
      style={{
        height: 'var(--header-height)',
        background: 'rgba(255, 246, 239, 0.94)',
        backdropFilter: 'blur(14px)',
        borderBottom: '3px solid var(--ink)',
        position: 'sticky',
        top: 0,
        zIndex: 100,
      }}
    >
      <div
        className="container"
        style={{
          height: '100%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: '18px',
        }}
      >
        <button
          type="button"
          onClick={() => navigate('/')}
          aria-label="Nowait 홈으로 이동"
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            border: 'none',
            background: 'transparent',
            padding: 0,
            cursor: 'pointer',
          }}
        >
          <Logo width="clamp(132px, 14vw, 166px)" />
        </button>

        <nav
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'flex-end',
            gap: '8px',
            flexWrap: 'wrap',
          }}
        >
          <button
            className="btn btn-white btn-sm"
            type="button"
            onClick={() => navigate('/landing')}
            style={{
              background: isCurrent('/landing') ? 'var(--amber)' : '#fff',
            }}
          >
            서비스 소개
          </button>

          {isLoggedIn ? (
            <>
              {isOwner && (
                <button
                  className="btn btn-amber btn-sm"
                  type="button"
                  onClick={() => navigate('/owner')}
                  style={{
                    background: isOwnerPage ? 'var(--tomato)' : 'var(--amber)',
                    color: isOwnerPage ? '#fff' : 'var(--ink)',
                  }}
                >
                  사장님
                </button>
              )}
              <button
                className="btn btn-white btn-sm"
                type="button"
                onClick={() => navigate('/mypage')}
                style={{
                  background: isCurrent('/mypage') ? 'var(--amber)' : '#fff',
                }}
              >
                마이페이지
              </button>
              <button className="btn btn-outline btn-sm" type="button" onClick={handleLogout}>
                로그아웃
              </button>
            </>
          ) : (
            <>
              <button className="btn btn-white btn-sm" type="button" onClick={() => navigate('/auth')}>
                로그인
              </button>
              <button
                className="btn btn-tomato btn-sm"
                type="button"
                onClick={() => navigate('/auth?tab=signup')}
              >
                회원가입
              </button>
            </>
          )}
        </nav>
      </div>
    </header>
  );
}
