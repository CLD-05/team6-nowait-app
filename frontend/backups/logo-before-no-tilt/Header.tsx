import { useLocation, useNavigate } from 'react-router-dom';

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
  const token = localStorage.getItem('nowait_token');
  const user = readUser();
  const isLoggedIn = Boolean(token);
  const isOwner = user.role === 'OWNER';

  const handleLogout = () => {
    localStorage.removeItem('nowait_token');
    localStorage.removeItem('nowait_user');
    navigate('/');
    window.location.reload();
  };

  const isCurrent = (path: string) => location.pathname === path;

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
            gap: '3px',
            border: 'none',
            background: 'transparent',
            color: 'var(--ink)',
            fontSize: '25px',
            fontWeight: 900,
            letterSpacing: '-0.05em',
            lineHeight: 1.5,
            padding: 0,
          }}
        >
          No
          <span
            style={{
              background: 'var(--tomato)',
              color: '#fff',
              border: '2.5px solid var(--ink)',
              borderRadius: '9px',
              padding: '1px 9px',
              transform: 'rotate(-3deg)',
              boxShadow: '3px 3px 0 var(--ink)',
              lineHeight: 1.5,
            }}
          >
            wait
          </span>
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
