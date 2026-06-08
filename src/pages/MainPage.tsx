import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import Header from '../components/Header';

// 더미 데이터 (백엔드 연동 전 화면 확인용)
// 나중에 USE_DUMMY = false 로 바꾸면 실제 API 호출
const USE_DUMMY = true;
const API_BASE = '/api/v1';

const UNSPLASH: Record<string, string> = {
  KOREAN: 'https://images.unsplash.com/photo-1583224944844-5b268c057b72?w=400&q=80',
  JAPANESE: 'https://images.unsplash.com/photo-1579871494447-9811cf80d66c?w=400&q=80',
  CHINESE: 'https://images.unsplash.com/photo-1563245372-f21724e3856d?w=400&q=80',
  WESTERN: 'https://images.unsplash.com/photo-1414235077428-338989a2e8c0?w=400&q=80',
  ASIAN: 'https://images.unsplash.com/photo-1559314809-0d155014e29e?w=400&q=80',
};

const CAT_LABEL: Record<string, string> = {
  KOREAN: '한식', JAPANESE: '일식', CHINESE: '중식',
  WESTERN: '양식', ASIAN: '아시안',
};

const NAMES = ['미진', '스시콜', '왕가원', '비스트로 르', '방콕포차', '한상차림', '오마카세 정', '딘타이펑', '트라토리아', '쌀국수집', '고깃집 화로', '회전초밥', '마라공방', '파스타리아', '분짜하노이', '전주비빔', '돈카츠 야마', '양꼬치 청', '스테이크 하우스', '카오산'];
const MENU: Record<string, string> = { KOREAN: '제육볶음', JAPANESE: '특선 스시', CHINESE: '마파두부', WESTERN: '안심 스테이크', ASIAN: '팟타이' };

// 더미 데이터 생성 함수
function buildDummy() {
  const list: any[] = [];
  let id = 1;
  ['KOREAN', 'JAPANESE', 'CHINESE', 'WESTERN', 'ASIAN'].forEach(cat => {
    for (let i = 0; i < 20; i++) {
      list.push({
        id: id++,
        name: `${NAMES[(id + i) % NAMES.length]} ${i + 1}호점`,
        category: cat,
        mainMenuName: MENU[cat],
        imageUrl: UNSPLASH[cat],
        reservable: true,
        waitingAvailable: id % 2 === 0,
      });
    }
  });
  return list;
}

const DUMMY_ALL = buildDummy();
const PAGE_SIZE = 12;

// 카테고리 목록
const CATEGORIES = [
  { label: '전체', value: '' },
  { label: '🍚 한식', value: 'KOREAN' },
  { label: '🍣 일식', value: 'JAPANESE' },
  { label: '🥢 중식', value: 'CHINESE' },
  { label: '🥩 양식', value: 'WESTERN' },
  { label: '🍜 아시안', value: 'ASIAN' },
];

export default function MainPage() {
  const navigate = useNavigate();
  const [category, setCategory] = useState('');
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(0);
  const [restaurants, setRestaurants] = useState<any[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(false);

  // 식당 목록 불러오기
  useEffect(() => {
    fetchRestaurants();
  }, [category, page]);

  async function fetchRestaurants() {
    setLoading(true);
    try {
      if (USE_DUMMY) {
        // 더미 데이터 필터링
        await new Promise(r => setTimeout(r, 300)); // 로딩 확인용
        const filtered = DUMMY_ALL.filter(r => {
          if (category && r.category !== category) return false;
          if (keyword && !r.name.includes(keyword)) return false;
          return true;
        });
        setTotalPages(Math.ceil(filtered.length / PAGE_SIZE));
        setRestaurants(filtered.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE));
      } else {
        // 실제 API 호출 (백엔드 연동 시 사용)
        const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
        if (category) params.append('category', category);
        if (keyword) params.append('keyword', keyword);
        const res = await fetch(`${API_BASE}/restaurants?${params}`);
        const data = await res.json();
        setRestaurants(data.content);
        setTotalPages(data.totalPages);
      }
    } finally {
      setLoading(false);
    }
  }

  function handleSearch() {
    setPage(0);
    fetchRestaurants();
  }

  function handleCategory(val: string) {
    setCategory(val);
    setPage(0);
  }

  return (
    <div>
      <Header />

      {/* Hero 섹션 */}
      <section style={{
        background: 'linear-gradient(135deg, #FFF3EF 0%, #fff8f6 50%, #fff 100%)',
        padding: '60px 20px 48px',
        textAlign: 'center',
      }}>
        <h1 style={{ fontSize: 'clamp(1.8rem, 4vw, 2.8rem)', fontWeight: 800, lineHeight: 1.2, letterSpacing: '-1px', marginBottom: '12px' }}>
          기다림 없이, <span style={{ color: 'var(--primary)' }}>지금 바로</span> 예약하세요
        </h1>
        <p style={{ color: 'var(--text-sub)', fontSize: '1rem', marginBottom: '32px' }}>
          전국 인기 맛집을 예약하고 실시간 웨이팅까지 한 번에
        </p>

        {/* 검색창 */}
        <div style={{
          maxWidth: '520px', margin: '0 auto', display: 'flex',
          background: '#fff', borderRadius: '50px',
          boxShadow: '0 4px 24px rgba(255,87,34,0.15)',
          padding: '6px 6px 6px 20px', alignItems: 'center',
        }}>
          <input
            type="text"
            placeholder="식당 이름이나 메뉴를 검색해보세요"
            value={keyword}
            onChange={e => setKeyword(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && handleSearch()}
            style={{ flex: 1, border: 'none', outline: 'none', fontSize: '0.95rem', background: 'transparent', color: 'var(--text)' }}
          />
          <button
            onClick={handleSearch}
            style={{ width: '44px', height: '44px', borderRadius: '50%', background: 'var(--primary)', border: 'none', color: '#fff', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}
          >
            <svg width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2.5" viewBox="0 0 24 24">
              <circle cx="11" cy="11" r="8" /><path d="m21 21-4.35-4.35" />
            </svg>
          </button>
        </div>
      </section>

      {/* 카테고리 탭 */}
      <div style={{ background: '#fff', borderBottom: '1px solid var(--border)', position: 'sticky', top: 'var(--header-height)', zIndex: 90, padding: '0 20px' }}>
        <div style={{ maxWidth: '1200px', margin: '0 auto', display: 'flex', gap: '8px', padding: '14px 0', overflowX: 'auto', scrollbarWidth: 'none' }}>
          {CATEGORIES.map(cat => (
            <button
              key={cat.value}
              onClick={() => handleCategory(cat.value)}
              style={{
                padding: '8px 18px', borderRadius: '50px', whiteSpace: 'nowrap',
                border: `1.5px solid ${category === cat.value ? 'var(--primary)' : 'var(--border)'}`,
                background: category === cat.value ? 'var(--primary)' : '#fff',
                color: category === cat.value ? '#fff' : 'var(--text-sub)',
                fontSize: '0.86rem', fontWeight: 600, cursor: 'pointer', transition: 'all 0.15s',
              }}
            >
              {cat.label}
            </button>
          ))}
        </div>
      </div>

      {/* 식당 목록 */}
      <main style={{ maxWidth: '1200px', margin: '0 auto', padding: '32px 20px 60px' }}>
        <div style={{ display: 'flex', alignItems: 'center', marginBottom: '20px' }}>
          <span style={{ fontSize: '1.1rem', fontWeight: 700 }}>식당 목록</span>
          <span style={{ color: 'var(--text-sub)', fontSize: '0.88rem', marginLeft: '8px' }}>
            {restaurants.length > 0 && `${totalPages * PAGE_SIZE}개`}
          </span>
        </div>

        {/* 로딩 */}
        {loading && <div className="spinner" />}

        {/* 카드 그리드 */}
        {!loading && (
          <>
            {restaurants.length === 0 ? (
              <div className="empty-state">
                <div className="icon">🍽️</div>
                <p>조건에 맞는 식당이 없어요</p>
              </div>
            ) : (
              <div style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))',
                gap: '20px',
              }}>
                {restaurants.map((r, i) => (
                  <div
                    key={r.id}
                    onClick={() => navigate(`/restaurant/${r.id}`)}
                    style={{
                      background: '#fff', borderRadius: 'var(--radius-md)',
                      overflow: 'hidden', cursor: 'pointer',
                      boxShadow: 'var(--shadow)', transition: 'transform 0.2s, box-shadow 0.2s',
                      animation: `fadeUp 0.4s ease ${i * 0.04}s both`,
                    }}
                    onMouseEnter={e => {
                      (e.currentTarget as HTMLElement).style.transform = 'translateY(-5px)';
                      (e.currentTarget as HTMLElement).style.boxShadow = 'var(--shadow-hover)';
                    }}
                    onMouseLeave={e => {
                      (e.currentTarget as HTMLElement).style.transform = 'translateY(0)';
                      (e.currentTarget as HTMLElement).style.boxShadow = 'var(--shadow)';
                    }}
                  >
                    {/* 이미지 */}
                    <div style={{ position: 'relative', aspectRatio: '4/3', overflow: 'hidden', background: '#f0f0f0' }}>
                      <img src={r.imageUrl} alt={r.name} loading="lazy" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                      <span style={{
                        position: 'absolute', top: '10px', left: '10px',
                        background: 'rgba(0,0,0,0.55)', color: '#fff',
                        fontSize: '0.72rem', fontWeight: 600, padding: '4px 9px',
                        borderRadius: '50px', backdropFilter: 'blur(4px)',
                      }}>
                        {CAT_LABEL[r.category]}
                      </span>
                    </div>

                    {/* 정보 */}
                    <div style={{ padding: '14px 16px 16px' }}>
                      <div style={{ fontSize: '0.98rem', fontWeight: 700, marginBottom: '4px', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                        {r.name}
                      </div>
                      <div style={{ fontSize: '0.82rem', color: 'var(--text-sub)', marginBottom: '10px' }}>
                        {r.mainMenuName}
                      </div>
                      <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
                        <span style={{ fontSize: '0.72rem', fontWeight: 600, padding: '3px 9px', borderRadius: '50px', background: '#F0FDF4', color: '#16A34A' }}>영업중</span>
                        {r.reservable && <span style={{ fontSize: '0.72rem', fontWeight: 600, padding: '3px 9px', borderRadius: '50px', background: 'var(--primary-light)', color: 'var(--primary)' }}>예약</span>}
                        {r.waitingAvailable && <span style={{ fontSize: '0.72rem', fontWeight: 600, padding: '3px 9px', borderRadius: '50px', background: '#EFF6FF', color: '#2563EB' }}>웨이팅</span>}
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}

            {/* 페이지네이션 */}
            {totalPages > 1 && (
              <div style={{ display: 'flex', justifyContent: 'center', gap: '6px', marginTop: '40px' }}>
                <button className="btn" onClick={() => setPage(p => p - 1)} disabled={page === 0}
                  style={{ width: '38px', height: '38px', padding: 0, border: '1.5px solid var(--border)', background: '#fff', color: 'var(--text-sub)', borderRadius: 'var(--radius-sm)', opacity: page === 0 ? 0.35 : 1 }}>
                  ‹
                </button>
                {Array.from({ length: totalPages }, (_, i) => (
                  <button key={i} onClick={() => { setPage(i); window.scrollTo({ top: 200, behavior: 'smooth' }); }}
                    style={{ width: '38px', height: '38px', padding: 0, border: `1.5px solid ${i === page ? 'var(--primary)' : 'var(--border)'}`, background: i === page ? 'var(--primary)' : '#fff', color: i === page ? '#fff' : 'var(--text-sub)', borderRadius: 'var(--radius-sm)', fontWeight: 600, fontSize: '0.88rem', cursor: 'pointer' }}>
                    {i + 1}
                  </button>
                ))}
                <button className="btn" onClick={() => setPage(p => p + 1)} disabled={page === totalPages - 1}
                  style={{ width: '38px', height: '38px', padding: 0, border: '1.5px solid var(--border)', background: '#fff', color: 'var(--text-sub)', borderRadius: 'var(--radius-sm)', opacity: page === totalPages - 1 ? 0.35 : 1 }}>
                  ›
                </button>
              </div>
            )}
          </>
        )}
      </main>

      {/* 푸터 */}
      <footer style={{ background: '#fff', borderTop: '1px solid var(--border)', padding: '32px 20px', textAlign: 'center', color: 'var(--text-sub)', fontSize: '0.82rem' }}>
        <p>© 2026 Nowait · 식당 예약 & 웨이팅 서비스 · Team 6</p>
      </footer>
    </div>
  );
}