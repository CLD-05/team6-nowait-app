import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { getRestaurants } from "../api/restaurant";
import { userStorage } from "../utils/storage";

const CATEGORIES = [
  { value: null, label: "전체", icon: "🍽️" },
  { value: "KOREAN", label: "한식", icon: "🍚" },
  { value: "JAPANESE", label: "일식", icon: "🍣" },
  { value: "CHINESE", label: "중식", icon: "🥢" },
  { value: "WESTERN", label: "양식", icon: "🍝" },
  { value: "ASIAN", label: "아시안", icon: "🍜" },
];

const CATEGORY_LABEL = {
  KOREAN: "한식",
  JAPANESE: "일식",
  CHINESE: "중식",
  WESTERN: "양식",
  ASIAN: "아시안",
};

/* 시/도 단위 지역 목록. 시드 데이터 주소가 "서울특별시 마포구..."
   형태라 단순 prefix 매칭으로 필터 가능. */
const REGIONS = [
  "전국",
  "서울특별시",
  "부산광역시",
  "대구광역시",
  "인천광역시",
  "광주광역시",
  "대전광역시",
  "울산광역시",
  "세종시",
  "경기도",
  "강원도",
  "충청북도",
  "충청남도",
  "전라북도",
  "전라남도",
  "경상북도",
  "경상남도",
  "제주도",
];

const REGION_STORAGE_KEY = "nowait_region";

export default function HomePage() {
  const [category, setCategory] = useState(null);
  const [keyword, setKeyword] = useState("");
  const [searchInput, setSearchInput] = useState("");
  const [region, setRegion] = useState(
    () => localStorage.getItem(REGION_STORAGE_KEY) || "전국"
  );
  const [openRegionSheet, setOpenRegionSheet] = useState(false);
  const [restaurants, setRestaurants] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const user = userStorage.get();

  /* 지역 선택값 영속화 */
  useEffect(() => {
    localStorage.setItem(REGION_STORAGE_KEY, region);
  }, [region]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError("");

    /*
       검색어/지역 필터는 백엔드 미지원 → 클라이언트에서 처리.
       카테고리만 백엔드 파라미터로 전달.
    */
    getRestaurants({ category })
      .then((data) => {
        if (cancelled) return;
        let filtered = data;

        // 지역 필터
        if (region && region !== "전국") {
          filtered = filtered.filter((r) =>
            r.address?.startsWith(region)
          );
        }

        // 키워드 필터 (이름 + 메인메뉴)
        if (keyword) {
          const lower = keyword.toLowerCase();
          filtered = filtered.filter((r) => {
            const inName = r.name?.toLowerCase().includes(lower);
            const inMenu = r.mainMenuName?.toLowerCase().includes(lower);
            return inName || inMenu;
          });
        }

        setRestaurants(filtered);
      })
      .catch(() => {
        if (!cancelled) setError("식당 목록을 불러오지 못했습니다.");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [category, keyword, region]);

  const handleSearch = (e) => {
    e.preventDefault();
    setKeyword(searchInput.trim());
  };

  /* 섹션 헤더 문구 결정: 키워드 > 지역+카테고리 조합 > 기본 */
  const sectionTitle = (() => {
    if (keyword) return `"${keyword}" 검색 결과`;
    const parts = [];
    if (region !== "전국") parts.push(region);
    if (category) parts.push(CATEGORY_LABEL[category]);
    if (parts.length === 0) return "추천 식당";
    return `${parts.join(" ")} 식당`;
  })();

  /* 빈 결과 메시지 */
  const emptyMessage = (() => {
    if (keyword) return `"${keyword}" 검색 결과가 없어요`;
    if (region !== "전국") return `${region}에 등록된 식당이 없어요`;
    return "등록된 식당이 없어요";
  })();

  return (
    <div className="flex flex-col min-h-screen pb-20 bg-gray-50">
      {/* 히어로 영역 */}
      <div className="relative px-5 pt-10 pb-6 bg-gradient-to-br from-primary to-primary-dark text-white overflow-hidden">
        {/* 데코 원 */}
        <div className="absolute -top-16 -right-10 w-48 h-48 rounded-full bg-white/10 blur-2xl" />
        <div className="absolute -bottom-20 -left-10 w-56 h-56 rounded-full bg-white/10 blur-3xl" />

        <div className="relative">
          <p className="text-sm font-medium text-white/85 mb-1.5 tracking-tight">
            안녕하세요, {user?.name || "고객"}님
          </p>
          <h2 className="text-[26px] font-extrabold mb-6 leading-[1.25] tracking-tight">
            오늘은 어떤 메뉴가<br />좋으신가요?
          </h2>

          {/* 검색바 */}
          <form onSubmit={handleSearch} className="relative">
            <input
              type="text"
              placeholder="식당 또는 메뉴 검색"
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              className="w-full pl-12 pr-12 py-3.5 bg-white rounded-full text-sm text-gray-900 outline-none placeholder:text-gray-400 shadow-lg"
            />
            <SearchIcon className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
            {searchInput && (
              <button
                type="button"
                onClick={() => {
                  setSearchInput("");
                  setKeyword("");
                }}
                className="absolute right-4 top-1/2 -translate-y-1/2 w-5 h-5 rounded-full bg-gray-200 text-gray-600 text-xs flex items-center justify-center"
              >
                ✕
              </button>
            )}
          </form>
        </div>
      </div>

      {/* 지역 선택 칩 */}
      <div className="px-5 mt-5">
        <button
          type="button"
          onClick={() => setOpenRegionSheet(true)}
          className="inline-flex items-center gap-1.5 pl-3 pr-2.5 py-1.5 bg-white border border-gray-200 rounded-full text-sm font-bold text-gray-900 shadow-sm active:bg-gray-50"
        >
          <PinIcon className="w-3.5 h-3.5 text-primary" />
          <span>{region}</span>
          <ChevronDownIcon className="w-3.5 h-3.5 text-gray-500" />
        </button>
      </div>

      {/* 카테고리 */}
      <div className="px-5 mt-4">
        <h3 className="text-sm font-bold text-gray-900 mb-3">카테고리</h3>
        <div className="grid grid-cols-6 gap-2">
          {CATEGORIES.map((c) => (
            <button
              key={c.label}
              onClick={() => setCategory(c.value)}
              className={`flex flex-col items-center gap-1.5 py-3 rounded-2xl transition ${
                category === c.value
                  ? "bg-primary-light"
                  : "bg-white"
              }`}
            >
              <span className="text-2xl leading-none">{c.icon}</span>
              <span
                className={`text-[11px] font-medium ${
                  category === c.value ? "text-primary" : "text-gray-700"
                }`}
              >
                {c.label}
              </span>
            </button>
          ))}
        </div>
      </div>

      {/* 식당 리스트 */}
      <div className="px-5 mt-8">
        <div className="flex items-baseline justify-between mb-3">
          <h3 className="text-base font-bold text-gray-900">{sectionTitle}</h3>
          {!loading && !error && (
            <span className="text-xs text-gray-400">
              {restaurants.length}개
            </span>
          )}
        </div>

        {loading && <SkeletonList />}
        {!loading && error && (
          <div className="py-20 text-center text-gray-400 text-sm">{error}</div>
        )}
        {!loading && !error && restaurants.length === 0 && (
          <div className="py-20 text-center text-gray-400 text-sm">
            {emptyMessage}
          </div>
        )}
        {!loading && !error && restaurants.length > 0 && (
          <div className="flex flex-col gap-4">
            {restaurants.map((r) => (
              <RestaurantCard key={r.id} restaurant={r} />
            ))}
          </div>
        )}
      </div>

      {/* 지역 선택 바텀시트 */}
      {openRegionSheet && (
        <RegionSheet
          current={region}
          onSelect={(r) => {
            setRegion(r);
            setOpenRegionSheet(false);
          }}
          onClose={() => setOpenRegionSheet(false)}
        />
      )}
    </div>
  );
}

function RegionSheet({ current, onSelect, onClose }) {
  return (
    <div className="fixed inset-0 z-40 flex justify-center items-end">
      <div className="absolute inset-0 bg-black/40" onClick={onClose} />
      <div className="relative w-full max-w-[430px] bg-white rounded-t-3xl pt-3 shadow-2xl animate-slide-up max-h-[80vh] flex flex-col">
        <div className="w-12 h-1 bg-gray-300 rounded-full mx-auto mb-3" />
        <h3 className="px-5 pb-3 text-lg font-extrabold text-gray-900 tracking-tight">
          지역 선택
        </h3>
        <div className="overflow-y-auto pb-6">
          {REGIONS.map((r) => {
            const selected = r === current;
            return (
              <button
                key={r}
                type="button"
                onClick={() => onSelect(r)}
                className={`w-full flex items-center justify-between px-5 py-3.5 text-left transition ${
                  selected
                    ? "bg-primary-light"
                    : "active:bg-gray-50"
                }`}
              >
                <span
                  className={`text-sm font-semibold ${
                    selected ? "text-primary" : "text-gray-800"
                  }`}
                >
                  {r}
                </span>
                {selected && (
                  <CheckIcon className="w-4 h-4 text-primary" />
                )}
              </button>
            );
          })}
        </div>
      </div>
    </div>
  );
}

function RestaurantCard({ restaurant }) {
  return (
    <Link
      to={`/restaurants/${restaurant.id}`}
      className="block bg-white rounded-2xl overflow-hidden shadow-sm border border-gray-100 active:scale-[0.98] transition"
    >
      <div className="w-full h-44 bg-gray-100 relative">
        {restaurant.imageUrl ? (
          <img
            src={restaurant.imageUrl}
            alt={restaurant.name}
            className="w-full h-full object-cover"
          />
        ) : (
          <div className="w-full h-full bg-gradient-to-br from-gray-100 to-gray-200 flex items-center justify-center">
            <span className="text-5xl opacity-40">🍴</span>
          </div>
        )}
        <span className="absolute top-3 left-3 px-2.5 py-1 bg-white/95 backdrop-blur rounded-full text-[11px] font-semibold text-gray-700 shadow-sm">
          {CATEGORY_LABEL[restaurant.category] || restaurant.category}
        </span>
      </div>

      <div className="p-4">
        <h3 className="text-base font-bold text-gray-900 mb-1">
          {restaurant.name}
        </h3>
        {restaurant.mainMenuName && (
          <p className="text-sm text-primary font-semibold mb-1.5">
            {restaurant.mainMenuName}
          </p>
        )}
        <p className="text-xs text-gray-500 truncate flex items-center gap-1">
          <PinIcon className="w-3 h-3 shrink-0" />
          {restaurant.address}
        </p>
      </div>
    </Link>
  );
}

function SkeletonList() {
  return (
    <div className="flex flex-col gap-4">
      {[1, 2, 3].map((i) => (
        <div
          key={i}
          className="bg-white rounded-2xl overflow-hidden border border-gray-100"
        >
          <div className="w-full h-44 bg-gray-100 animate-pulse" />
          <div className="p-4">
            <div className="h-4 bg-gray-100 rounded animate-pulse mb-2 w-2/3" />
            <div className="h-3 bg-gray-100 rounded animate-pulse w-1/2" />
          </div>
        </div>
      ))}
    </div>
  );
}

/* ─── 아이콘 ─── */

function SearchIcon({ className }) {
  return (
    <svg
      className={className}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <circle cx="11" cy="11" r="7" />
      <path d="m20 20-3.5-3.5" />
    </svg>
  );
}

function PinIcon({ className }) {
  return (
    <svg
      className={className}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0Z" />
      <circle cx="12" cy="10" r="3" />
    </svg>
  );
}

function ChevronDownIcon({ className }) {
  return (
    <svg
      className={className}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.5"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="m6 9 6 6 6-6" />
    </svg>
  );
}

function CheckIcon({ className }) {
  return (
    <svg
      className={className}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="3"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M20 6 9 17l-5-5" />
    </svg>
  );
}
