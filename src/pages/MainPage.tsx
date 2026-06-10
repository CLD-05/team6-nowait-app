import { useMemo, useState, useEffect } from 'react';
import { API_BASE } from '../lib/api';
import { useNavigate } from 'react-router-dom';
import Header from '../components/Header';

type CategoryKey = 'KOREAN' | 'JAPANESE' | 'CHINESE' | 'WESTERN' | 'ASIAN';

type Restaurant = {
  id: number;
  name: string;
  category: CategoryKey;
  mainMenuName: string;
  imageUrl: string;
  reservable: boolean;
  waitingAvailable: boolean;
};

const PAGE_SIZE = 12;

const CATEGORY_IMAGES: Record<CategoryKey, string> = {
  KOREAN: 'https://images.unsplash.com/photo-1583224944844-5b268c057b72?w=640&q=80',
  JAPANESE: 'https://images.unsplash.com/photo-1579871494447-9811cf80d66c?w=640&q=80',
  CHINESE: 'https://images.unsplash.com/photo-1563245372-f21724e3856d?w=640&q=80',
  WESTERN: 'https://images.unsplash.com/photo-1414235077428-338989a2e8c0?w=640&q=80',
  ASIAN: 'https://images.unsplash.com/photo-1559314809-0d155014e29e?w=640&q=80',
};

const CATEGORY_LABEL: Record<CategoryKey, string> = {
  KOREAN: '한식',
  JAPANESE: '일식',
  CHINESE: '중식',
  WESTERN: '양식',
  ASIAN: '아시안',
};

const MENU: Record<CategoryKey, string> = {
  KOREAN: '제육볶음',
  JAPANESE: '특선 스시',
  CHINESE: '마파두부',
  WESTERN: '안심 스테이크',
  ASIAN: '팟타이',
};

const NAMES = [
  '미진',
  '스시코우',
  '명가반점',
  '비스트로 루프',
  '방콕키친',
  '한상차림',
  '오마카세 봄',
  '포레스트 다이너',
  '트라토리아 문',
  '대구수집',
  '고깃집 오늘',
  '우전초밥',
  '마라공방',
  '파스타리아',
  '분짜하노이',
  '전주비빔',
  '돈카츠 하루',
  '목동치킨',
  '스테이크 하우스',
  '카오산',
];

const CATEGORIES: Array<{ label: string; value: '' | CategoryKey }> = [
  { label: '전체', value: '' },
  { label: '한식', value: 'KOREAN' },
  { label: '일식', value: 'JAPANESE' },
  { label: '중식', value: 'CHINESE' },
  { label: '양식', value: 'WESTERN' },
  { label: '아시안', value: 'ASIAN' },
];

function buildDummyRestaurants(): Restaurant[] {
  const categories: CategoryKey[] = ['KOREAN', 'JAPANESE', 'CHINESE', 'WESTERN', 'ASIAN'];
  let id = 1;

  return categories.flatMap(category =>
    Array.from({ length: 20 }, (_, index) => {
      const currentId = id++;

      return {
        id: currentId,
        name: `${NAMES[(currentId + index) % NAMES.length]} ${index + 1}호점`,
        category,
        mainMenuName: MENU[category],
        imageUrl: CATEGORY_IMAGES[category],
        reservable: true,
        waitingAvailable: currentId % 2 === 0,
      };
    }),
  );
}

const RESTAURANTS = buildDummyRestaurants();
const USE_DUMMY = false;

export default function MainPage() {
  const navigate = useNavigate();
  const [category, setCategory] = useState<'' | CategoryKey>('');
  const [keyword, setKeyword] = useState('');
  const [appliedKeyword, setAppliedKeyword] = useState('');
  const [page, setPage] = useState(0);
  const [restaurants2, setRestaurants2] = useState<Restaurant[]>([]);
  const [_loading, setLoading] = useState(false);

  useEffect(() => {
    if (USE_DUMMY) return;
    fetchRestaurants();
  }, [category, page]);

  async function fetchRestaurants() {
    setLoading(true);
    try {
      const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
      if (category) params.append('category', category);
      const res = await fetch(`${API_BASE}/restaurants?${params}`, {
        headers: { 'Content-Type': 'application/json' },
      });
      const data = await res.json();
      const list = Array.isArray(data) ? data : (data.content || []);

      // API 응답 필드명 → React 타입 매핑
      setRestaurants2(list.map((r: any) => ({
        id: r.id,
        name: r.name,
        category: r.category,
        mainMenuName: r.mainMenuName,
        imageUrl: r.imageUrl
          ? `http://localhost:8080${r.imageUrl}`
          : CATEGORY_IMAGES[r.category as CategoryKey],
        reservable: r.reservationAvailable === 'Y',
        waitingAvailable: r.waitingAvailable === 'Y',
      })));
    } catch (e) {
      console.error('식당 목록 조회 실패', e);
    } finally {
      setLoading(false);
    }
  }

  const filteredRestaurants = useMemo(() => {
    const normalizedKeyword = appliedKeyword.trim();

    return RESTAURANTS.filter(restaurant => {
      const matchesCategory = category === '' || restaurant.category === category;
      const matchesKeyword =
        normalizedKeyword === '' ||
        restaurant.name.includes(normalizedKeyword) ||
        restaurant.mainMenuName.includes(normalizedKeyword);

      return matchesCategory && matchesKeyword;
    });
  }, [appliedKeyword, category]);

  const totalPages = Math.max(1, Math.ceil(filteredRestaurants.length / PAGE_SIZE));
  const restaurants = USE_DUMMY
    ? filteredRestaurants.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE)
    : restaurants2;

  const handleSearch = () => {
    setPage(0);
    setAppliedKeyword(keyword);
  };

  const handleCategory = (value: '' | CategoryKey) => {
    setCategory(value);
    setPage(0);
  };

  return (
    <div style={{ minHeight: '100vh', background: 'var(--cream)' }}>
      <Header />

      <section
        style={{
          borderBottom: '3px solid var(--ink)',
          background:
            'linear-gradient(135deg, #fff6ef 0%, #ffe6df 55%, #fff 100%)',
          padding: '58px 20px 46px',
        }}
      >
        <div
          className="container"
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 320px), 1fr))',
            gap: '28px',
            alignItems: 'center',
          }}
        >
          <div>
            <span className="badge badge-amber" style={{ transform: 'rotate(-2deg)' }}>
              오늘 바로 줄서기
            </span>
            <h1
              style={{
                fontSize: 'clamp(36px, 5.2vw, 62px)',
                fontWeight: 900,
                letterSpacing: '-0.05em',
                lineHeight: 1.04,
                marginTop: '20px',
                maxWidth: '680px',
              }}
            >
              기다림은 줄이고,
              <br />
              맛있는 시간은 더 길게.
            </h1>
            <p
              style={{
                color: 'var(--muted)',
                fontSize: '18px',
                fontWeight: 600,
                lineHeight: 1.65,
                marginTop: '18px',
                maxWidth: '560px',
              }}
            >
              인기 맛집의 예약 가능 시간과 실시간 웨이팅을 한 곳에서 확인하세요.
            </p>

            <div
              style={{
                display: 'flex',
                maxWidth: '560px',
                marginTop: '28px',
                background: '#fff',
                border: '3px solid var(--ink)',
                borderRadius: '22px',
                boxShadow: '5px 5px 0 var(--ink)',
                padding: '8px',
                gap: '8px',
              }}
            >
              <input
                type="text"
                placeholder="식당 이름 또는 메뉴 검색"
                value={keyword}
                onChange={event => setKeyword(event.target.value)}
                onKeyDown={event => {
                  if (event.key === 'Enter') handleSearch();
                }}
                style={{
                  flex: 1,
                  minWidth: 0,
                  border: 'none',
                  outline: 'none',
                  background: 'transparent',
                  color: 'var(--ink)',
                  fontSize: '16px',
                  fontWeight: 700,
                  padding: '0 12px',
                }}
              />
              <button className="btn btn-tomato btn-sm" type="button" onClick={handleSearch}>
                검색
              </button>
            </div>
          </div>

          <div
            style={{
              background: 'var(--tomato)',
              color: '#fff',
              border: '3px solid var(--ink)',
              borderRadius: '28px',
              boxShadow: '8px 8px 0 var(--ink)',
              padding: '26px',
            }}
          >
            <div style={{ fontSize: '15px', fontWeight: 900, opacity: 0.92 }}>
              현재 등록 맛집
            </div>
            <div
              style={{
                fontSize: 'clamp(52px, 8vw, 86px)',
                fontWeight: 900,
                lineHeight: 1,
                letterSpacing: '-0.06em',
                marginTop: '8px',
              }}
            >
              {RESTAURANTS.length}
            </div>
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: '1fr 1fr',
                gap: '10px',
                marginTop: '24px',
              }}
            >
              <div
                style={{
                  background: '#fff',
                  color: 'var(--ink)',
                  border: '2.5px solid var(--ink)',
                  borderRadius: '16px',
                  padding: '14px',
                  fontWeight: 900,
                }}
              >
                예약 가능
                <br />
                <span style={{ color: 'var(--tomato)' }}>즉시 확인</span>
              </div>
              <div
                style={{
                  background: 'var(--amber)',
                  color: 'var(--ink)',
                  border: '2.5px solid var(--ink)',
                  borderRadius: '16px',
                  padding: '14px',
                  fontWeight: 900,
                }}
              >
                웨이팅
                <br />
                <span>실시간</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <div
        style={{
          background: '#fff',
          borderBottom: '3px solid var(--ink)',
          position: 'sticky',
          top: 'var(--header-height)',
          zIndex: 90,
          padding: '0 20px',
        }}
      >
        <div
          className="container"
          style={{
            display: 'flex',
            gap: '10px',
            padding: '14px 26px',
            overflowX: 'auto',
            scrollbarWidth: 'none',
          }}
        >
          {CATEGORIES.map(item => {
            const selected = category === item.value;

            return (
              <button
                key={item.label}
                type="button"
                onClick={() => handleCategory(item.value)}
                className={`btn ${selected ? 'btn-amber' : 'btn-white'} btn-sm`}
                style={{ flexShrink: 0 }}
              >
                {item.label}
              </button>
            );
          })}
        </div>
      </div>

      <main className="container" style={{ padding: '34px 26px 70px' }}>
        <div
          style={{
            display: 'flex',
            alignItems: 'end',
            justifyContent: 'space-between',
            gap: '18px',
            marginBottom: '22px',
            flexWrap: 'wrap',
          }}
        >
          <div>
            <span className="kicker">RESTAURANTS</span>
            <h2
              style={{
                fontSize: 'clamp(28px, 4vw, 42px)',
                fontWeight: 900,
                letterSpacing: '-0.04em',
                lineHeight: 1.06,
              }}
            >
              지금 둘러볼 맛집
            </h2>
          </div>
          <span
            className="badge badge-white"
            style={{
              boxShadow: '3px 3px 0 var(--ink)',
            }}
          >
            {filteredRestaurants.length}개 결과
          </span>
        </div>

        {restaurants.length === 0 ? (
          <div className="empty-state card-base" style={{ background: '#fff' }}>
            <p>조건에 맞는 식당이 없습니다.</p>
          </div>
        ) : (
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))',
              gap: '22px',
            }}
          >
            {restaurants.map((restaurant, index) => (
              <article
                key={restaurant.id}
                className="card-base"
                onClick={() => navigate(`/restaurant/${restaurant.id}`)}
                style={{
                  overflow: 'hidden',
                  cursor: 'pointer',
                  animation: `fadeUp 0.42s ease ${index * 0.035}s both`,
                }}
              >
                <div
                  style={{
                    position: 'relative',
                    aspectRatio: '4 / 3',
                    overflow: 'hidden',
                    borderBottom: '3px solid var(--ink)',
                    background: 'var(--line)',
                  }}
                >
                  <img
                    src={restaurant.imageUrl}
                    alt={restaurant.name}
                    loading="lazy"
                    style={{
                      width: '100%',
                      height: '100%',
                      objectFit: 'cover',
                      display: 'block',
                    }}
                  />
                  <span
                    className="badge badge-amber"
                    style={{
                      position: 'absolute',
                      top: '12px',
                      left: '12px',
                      boxShadow: '3px 3px 0 var(--ink)',
                    }}
                  >
                    {CATEGORY_LABEL[restaurant.category]}
                  </span>
                </div>

                <div style={{ padding: '18px' }}>
                  <h3
                    style={{
                      fontSize: '20px',
                      fontWeight: 900,
                      letterSpacing: '-0.04em',
                      whiteSpace: 'nowrap',
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                    }}
                  >
                    {restaurant.name}
                  </h3>
                  <p
                    style={{
                      color: 'var(--muted)',
                      fontSize: '14px',
                      fontWeight: 700,
                      marginTop: '4px',
                    }}
                  >
                    대표 메뉴 · {restaurant.mainMenuName}
                  </p>
                  <div style={{ display: 'flex', gap: '7px', flexWrap: 'wrap', marginTop: '14px' }}>
                    <span className="tag tag-open">영업중</span>
                    {restaurant.reservable && <span className="tag tag-confirmed">예약</span>}
                    {restaurant.waitingAvailable && <span className="tag tag-waiting">웨이팅</span>}
                  </div>
                </div>
              </article>
            ))}
          </div>
        )}

        {totalPages > 1 && (
          <div style={{ display: 'flex', justifyContent: 'center', gap: '8px', marginTop: '42px' }}>
            <button
              className="btn btn-white btn-sm"
              type="button"
              onClick={() => setPage(current => Math.max(0, current - 1))}
              disabled={page === 0}
              style={{ opacity: page === 0 ? 0.45 : 1 }}
            >
              이전
            </button>
            {Array.from({ length: totalPages }, (_, index) => (
              <button
                key={index}
                className={`btn ${index === page ? 'btn-tomato' : 'btn-white'} btn-sm`}
                type="button"
                onClick={() => {
                  setPage(index);
                  window.scrollTo({ top: 220, behavior: 'smooth' });
                }}
                style={{ width: '42px', padding: 0 }}
              >
                {index + 1}
              </button>
            ))}
            <button
              className="btn btn-white btn-sm"
              type="button"
              onClick={() => setPage(current => Math.min(totalPages - 1, current + 1))}
              disabled={page === totalPages - 1}
              style={{ opacity: page === totalPages - 1 ? 0.45 : 1 }}
            >
              다음
            </button>
          </div>
        )}
      </main>

      <footer
        style={{
          background: 'var(--ink)',
          color: '#fff',
          padding: '28px 20px',
          textAlign: 'center',
          fontSize: '14px',
          fontWeight: 700,
        }}
      >
        © 2026 Nowait · 식당 예약 & 웨이팅 서비스 · Team 6
      </footer>
    </div>
  );
}
