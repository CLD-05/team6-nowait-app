import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { getRestaurantDetail } from "../api/restaurant";
import {
  getWaitingSession,
  registerWaiting,
  getMyWaiting,
} from "../api/waiting";
import { tokenStorage, userStorage } from "../utils/storage";

const CATEGORY_LABEL = {
  KOREAN: "한식",
  JAPANESE: "일식",
  CHINESE: "중식",
  WESTERN: "양식",
  ASIAN: "아시안",
};

export default function RestaurantPage() {
  const { id } = useParams();
  const navigate = useNavigate();

  const [restaurant, setRestaurant] = useState(null);
  const [session, setSession] = useState(null);
  const [myWaiting, setMyWaiting] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  /* 등록 모달 상태 */
  const [openSheet, setOpenSheet] = useState(false);
  const [partySize, setPartySize] = useState(2);
  const [registering, setRegistering] = useState(false);
  const [registerError, setRegisterError] = useState("");

  const user = userStorage.get();
  const isLoggedIn = !!tokenStorage.get();

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError("");

    Promise.allSettled([
      getRestaurantDetail(id),
      getWaitingSession(id),
      isLoggedIn ? getMyWaiting() : Promise.reject(),
    ]).then((results) => {
      if (cancelled) return;
      const [r, s, m] = results;
      if (r.status === "fulfilled") setRestaurant(r.value);
      else setError("식당 정보를 불러오지 못했습니다.");
      if (s.status === "fulfilled") setSession(s.value);
      if (m.status === "fulfilled") setMyWaiting(m.value);
      setLoading(false);
    });

    return () => {
      cancelled = true;
    };
  }, [id, isLoggedIn]);

  const handleRegister = async () => {
    if (!isLoggedIn) {
      navigate("/login");
      return;
    }
    setRegistering(true);
    setRegisterError("");
    try {
      await registerWaiting(id, partySize);
      navigate("/my-waiting");
    } catch (err) {
      setRegisterError(err.response?.data?.message || "등록에 실패했습니다.");
    } finally {
      setRegistering(false);
    }
  };

  if (loading) return <DetailSkeleton />;
  if (error || !restaurant)
    return (
      <div className="p-6 text-center text-gray-400 text-sm">{error}</div>
    );

  /* 등록 가능 여부 판단 */
  const myActive =
    myWaiting && ["WAITING", "CALLED"].includes(myWaiting.status);
  const myWaitingForThis =
    myActive && Number(myWaiting.restaurantId) === Number(id);
  const sessionOpen = session && session.status === "OPEN";

  return (
    <div className="flex flex-col min-h-screen pb-28 bg-white">
      {/* 상단 이미지 + 뒤로가기 */}
      <div className="relative w-full h-64 bg-gray-100">
        {restaurant.imageUrl ? (
          <img
            src={restaurant.imageUrl}
            alt={restaurant.name}
            className="w-full h-full object-cover"
          />
        ) : (
          <div className="w-full h-full bg-gradient-to-br from-primary-light to-white flex items-center justify-center">
            <span className="text-6xl opacity-60">🍴</span>
          </div>
        )}
        <button
          onClick={() => navigate(-1)}
          className="absolute top-4 left-4 w-10 h-10 rounded-full bg-white/90 backdrop-blur flex items-center justify-center shadow-sm"
          aria-label="뒤로가기"
        >
          <ChevronLeftIcon className="w-5 h-5 text-gray-900" />
        </button>
      </div>

      {/* 식당 기본 정보 */}
      <div className="px-5 pt-5 pb-4 border-b border-gray-100">
        <span className="inline-block px-2.5 py-1 mb-2 bg-primary-light text-primary rounded-full text-[11px] font-bold">
          {CATEGORY_LABEL[restaurant.category]}
        </span>
        <h1 className="text-2xl font-extrabold text-gray-900 mb-1.5 tracking-tight">
          {restaurant.name}
        </h1>
        {restaurant.mainMenuName && (
          <p className="text-sm text-primary font-semibold mb-2">
            대표메뉴 · {restaurant.mainMenuName}
          </p>
        )}
        {restaurant.description && (
          <p className="text-sm text-gray-600 leading-relaxed mt-3">
            {restaurant.description}
          </p>
        )}
      </div>

      {/* 정보 섹션 */}
      <div className="px-5 py-5 flex flex-col gap-3.5">
        <InfoRow icon={<PinIcon />} label="주소" value={restaurant.address} />
        {restaurant.phoneNumber && (
          <InfoRow
            icon={<PhoneIcon />}
            label="전화"
            value={restaurant.phoneNumber}
          />
        )}
        <InfoRow
          icon={<ClockIcon />}
          label="영업시간"
          value={`${formatTime(restaurant.openTime)} - ${formatTime(
            restaurant.closeTime
          )}`}
        />
        {restaurant.closedDays && (
          <InfoRow
            icon={<CalendarIcon />}
            label="휴무일"
            value={restaurant.closedDays}
          />
        )}
      </div>

      {/* 편의 시설 */}
      <div className="px-5 pb-6 border-t border-gray-100 pt-5">
        <h3 className="text-sm font-bold text-gray-900 mb-3">편의 시설</h3>
        <div className="flex gap-2 flex-wrap">
          <Amenity active={restaurant.parkingAvailable === "Y" || restaurant.parkingAvailable === "true"}>주차</Amenity>
          <Amenity active={restaurant.wifiAvailable === "Y" || restaurant.wifiAvailable === "true"}>와이파이</Amenity>
          <Amenity active={restaurant.multilingualMenuAvailable === "Y" || restaurant.multilingualMenuAvailable === "true"}>다국어 메뉴</Amenity>
        </div>
      </div>

      {/* 하단 고정 액션바 */}
      <div className="fixed bottom-0 left-0 right-0 z-30">
        <div className="max-w-[430px] mx-auto px-5 py-4 bg-white border-t border-gray-100">
          <div className="grid grid-cols-2 gap-2">
            <button
              onClick={() => navigate(`/restaurants/${id}/reserve`)}
              className="py-4 bg-white border border-primary text-primary rounded-2xl font-bold text-base"
            >
              예약하기
            </button>
            {myWaitingForThis ? (
              <button
                onClick={() => navigate("/my-waiting")}
                className="py-4 bg-primary text-white rounded-2xl font-bold text-base shadow-lg shadow-primary/30"
              >
                내 웨이팅 보기
              </button>
            ) : !sessionOpen ? (
              <button
                disabled
                className="py-4 bg-gray-200 text-gray-500 rounded-2xl font-bold text-base"
              >
                {session?.status === "PAUSED" ? "일시정지" : "웨이팅 마감"}
              </button>
            ) : (
              <button
                onClick={() => setOpenSheet(true)}
                className="py-4 bg-primary text-white rounded-2xl font-bold text-base shadow-lg shadow-primary/30"
              >
                웨이팅 · {session.currentCount}팀
              </button>
            )}
          </div>
        </div>
      </div>

      {/* 등록 바텀시트 */}
      {openSheet && (
        <BottomSheet onClose={() => setOpenSheet(false)}>
          <div className="px-5 pt-2 pb-6">
            <h3 className="text-lg font-bold text-gray-900 mb-1">
              몇 분이서 오시나요?
            </h3>
            <p className="text-sm text-gray-500 mb-6">
              인원수에 맞춰 자리를 안내드려요
            </p>

            <div className="grid grid-cols-4 gap-2 mb-6">
              {[1, 2, 3, 4, 5, 6, 7, 8].map((n) => (
                <button
                  key={n}
                  onClick={() => setPartySize(n)}
                  className={`py-3.5 rounded-2xl font-bold transition ${
                    partySize === n
                      ? "bg-primary text-white"
                      : "bg-gray-100 text-gray-700"
                  }`}
                >
                  {n}명
                </button>
              ))}
            </div>

            {registerError && (
              <p className="text-red-500 text-sm mb-3 px-1">{registerError}</p>
            )}

            <button
              onClick={handleRegister}
              disabled={registering}
              className="w-full py-4 bg-primary text-white rounded-2xl font-bold text-base disabled:opacity-50 shadow-lg shadow-primary/30"
            >
              {registering
                ? "등록 중..."
                : `${partySize}명 웨이팅 등록하기`}
            </button>
          </div>
        </BottomSheet>
      )}
    </div>
  );
}

/* ─── 보조 ─── */

function formatTime(t) {
  if (!t) return "";
  // "09:00:00" → "09:00"
  return t.length >= 5 ? t.slice(0, 5) : t;
}

function InfoRow({ icon, label, value }) {
  return (
    <div className="flex items-start gap-3">
      <div className="w-9 h-9 rounded-full bg-gray-100 flex items-center justify-center shrink-0 text-gray-500">
        {icon}
      </div>
      <div className="flex-1 pt-1">
        <p className="text-[11px] text-gray-400 mb-0.5">{label}</p>
        <p className="text-sm text-gray-800">{value}</p>
      </div>
    </div>
  );
}

function Amenity({ active, children }) {
  return (
    <span
      className={`px-3 py-1.5 rounded-full text-xs font-semibold ${
        active
          ? "bg-primary-light text-primary"
          : "bg-gray-100 text-gray-400 line-through"
      }`}
    >
      {children}
    </span>
  );
}

function BottomSheet({ children, onClose }) {
  return (
    <div className="fixed inset-0 z-40 flex justify-center items-end">
      <div
        className="absolute inset-0 bg-black/40"
        onClick={onClose}
      />
      <div className="relative w-full max-w-[430px] bg-white rounded-t-3xl pt-3 shadow-2xl animate-slide-up">
        <div className="w-12 h-1 bg-gray-300 rounded-full mx-auto mb-2" />
        {children}
      </div>
    </div>
  );
}

function DetailSkeleton() {
  return (
    <div className="flex flex-col">
      <div className="w-full h-64 bg-gray-100 animate-pulse" />
      <div className="p-5 space-y-3">
        <div className="h-4 bg-gray-100 rounded w-1/3 animate-pulse" />
        <div className="h-6 bg-gray-100 rounded w-2/3 animate-pulse" />
        <div className="h-3 bg-gray-100 rounded w-full animate-pulse" />
      </div>
    </div>
  );
}

/* ─── 아이콘 ─── */

function ChevronLeftIcon({ className }) {
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
      <path d="m15 18-6-6 6-6" />
    </svg>
  );
}

function PinIcon() {
  return (
    <svg
      className="w-4 h-4"
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

function PhoneIcon() {
  return (
    <svg
      className="w-4 h-4"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.86 19.86 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.86 19.86 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92Z" />
    </svg>
  );
}

function ClockIcon() {
  return (
    <svg
      className="w-4 h-4"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7v5l3 2" />
    </svg>
  );
}

function CalendarIcon() {
  return (
    <svg
      className="w-4 h-4"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <rect x="3" y="4" width="18" height="18" rx="2" />
      <path d="M16 2v4M8 2v4M3 10h18" />
    </svg>
  );
}
