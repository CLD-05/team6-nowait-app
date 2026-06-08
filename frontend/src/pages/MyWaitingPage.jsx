import { useEffect, useState, useCallback, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { getMyWaiting, cancelMyWaiting } from "../api/waiting";
import { getRestaurantDetail } from "../api/restaurant";
import { tokenStorage } from "../utils/storage";
import {
  ensureNotifyPermission,
  getNotifyPermission,
  notify,
  playDing,
  vibrate,
  setTabTitle,
  restoreTabTitle,
} from "../utils/notification";

const STATUS_META = {
  WAITING: {
    label: "대기 중",
    color: "bg-blue-100 text-blue-700",
    headline: "곧 입장 안내드릴게요",
    desc: "차례가 되면 알림으로 알려드려요",
  },
  CALLED: {
    label: "호출됨",
    color: "bg-primary-light text-primary",
    headline: "지금 입장해주세요!",
    desc: "5분 안에 매장에 도착해주세요",
  },
  ENTERED: {
    label: "입장 완료",
    color: "bg-green-100 text-green-700",
    headline: "맛있게 드세요",
    desc: "이용해주셔서 감사합니다",
  },
  CANCELLED: {
    label: "취소됨",
    color: "bg-gray-200 text-gray-600",
    headline: "웨이팅이 취소됐어요",
    desc: "다음에 또 이용해주세요",
  },
};

export default function MyWaitingPage() {
  const navigate = useNavigate();
  const [waiting, setWaiting] = useState(null);
  const [restaurant, setRestaurant] = useState(null);
  const [loading, setLoading] = useState(true);
  const [cancelling, setCancelling] = useState(false);
  const [confirm, setConfirm] = useState(false);
  const [toast, setToast] = useState("");
  const [notifPerm, setNotifPerm] = useState(() => getNotifyPermission());

  /* 직전 상태 보관 — WAITING → CALLED 전이 감지용 */
  const prevStatusRef = useRef(null);

  const isLoggedIn = !!tokenStorage.get();

  const load = useCallback(async () => {
    if (!isLoggedIn) return;
    try {
      const w = await getMyWaiting();
      setWaiting(w);
      try {
        const r = await getRestaurantDetail(w.restaurantId);
        setRestaurant(r);
      } catch {
        setRestaurant(null);
      }

      /* 상태 전이 감지: WAITING → CALLED 일 때만 발동 */
      const prev = prevStatusRef.current;
      if (prev === "WAITING" && w.status === "CALLED") {
        triggerCallAlert(w, /* useRestaurant */ null);
      }
      prevStatusRef.current = w.status;
    } catch (err) {
      if (err.response?.status === 404) {
        setWaiting(null);
        setRestaurant(null);
        prevStatusRef.current = null;
      }
    } finally {
      setLoading(false);
    }
  }, [isLoggedIn]);

  /* 호출 알림 발동 */
  const triggerCallAlert = (w) => {
    /* 1) 탭 타이틀 */
    setTabTitle("🔔 입장해주세요! - NoWait");
    /* 2) 소리 */
    playDing();
    /* 3) 진동 (지원 시) */
    vibrate([200, 100, 200, 100, 200]);
    /* 4) 브라우저 알림 */
    notify("입장 안내", `${w.waitingNumber}번 손님, 입장해주세요!`);
  };

  /* CALLED 외 상태로 바뀌면 탭 타이틀 복원, 페이지 떠나도 복원 */
  useEffect(() => {
    if (waiting?.status !== "CALLED") {
      restoreTabTitle();
    }
    return () => restoreTabTitle();
  }, [waiting?.status]);

  useEffect(() => {
    if (!isLoggedIn) {
      navigate("/login");
      return;
    }
    load();
    /* 5초마다 갱신 */
    const id = setInterval(load, 5000);
    return () => clearInterval(id);
  }, [isLoggedIn, load, navigate]);

  const showToast = (m) => {
    setToast(m);
    setTimeout(() => setToast(""), 1800);
  };

  const handleCancel = async () => {
    setCancelling(true);
    try {
      await cancelMyWaiting(waiting.waitingId);
      setConfirm(false);
      showToast("웨이팅이 취소됐어요");
      load();
    } catch (err) {
      showToast(err.response?.data?.message || "취소에 실패했어요");
    } finally {
      setCancelling(false);
    }
  };

  if (loading) return <Loading />;

  /* 진행 중 웨이팅 없음 */
  if (!waiting) return <EmptyState onBrowse={() => navigate("/home")} />;

  const meta = STATUS_META[waiting.status] || {
    label: waiting.status,
    color: "bg-gray-100 text-gray-700",
    headline: waiting.status,
    desc: "",
  };
  const active = waiting.status === "WAITING" || waiting.status === "CALLED";

  const handleEnableNotify = async () => {
    const result = await ensureNotifyPermission();
    setNotifPerm(result);
    if (result === "granted") {
      showToast("호출 알림이 활성화됐어요");
    } else if (result === "denied") {
      showToast("브라우저 설정에서 알림을 허용해주세요");
    }
  };

  return (
    <div className="flex flex-col min-h-screen pb-24 bg-gray-50">
      {/* 상단 */}
      <div className="px-5 pt-6 pb-4 bg-white border-b border-gray-100">
        <button
          onClick={() => navigate("/home")}
          className="text-2xl text-gray-700 mb-4"
          aria-label="뒤로가기"
        >
          ←
        </button>
        <h2 className="text-2xl font-extrabold text-gray-900 tracking-tight">
          내 웨이팅
        </h2>
      </div>

      {/* 알림 권한 안내 (WAITING 중 + default 권한 한정) */}
      {waiting.status === "WAITING" && notifPerm === "default" && (
        <button
          onClick={handleEnableNotify}
          className="mx-5 mt-4 bg-primary-light text-primary rounded-2xl px-4 py-3 text-sm font-semibold text-left flex items-center justify-between active:bg-primary/15"
        >
          <span className="flex items-center gap-2">
            🔔 호출 알림 받기
          </span>
          <span className="text-xs">활성화 →</span>
        </button>
      )}

      {/* 메인 상태 카드 */}
      <div
        className={`mx-5 mt-4 rounded-3xl bg-white p-6 shadow-sm border border-gray-100 relative overflow-hidden ${
          waiting.status === "CALLED" ? "animate-pulse-call ring-2 ring-primary" : ""
        }`}
      >
        {/* 데코 */}
        <div className="pointer-events-none absolute -top-10 -right-8 w-40 h-40 rounded-full bg-primary/10 blur-2xl" />

        <span
          className={`relative inline-block px-2.5 py-1 mb-4 rounded-full text-[11px] font-bold ${meta.color}`}
        >
          {meta.label}
        </span>
        <h3 className="relative text-xl font-extrabold text-gray-900 mb-1 tracking-tight">
          {meta.headline}
        </h3>
        <p className="relative text-sm text-gray-500 mb-6">{meta.desc}</p>

        {/* 큰 숫자 영역 */}
        <div className="relative flex items-end justify-between mb-2">
          <div>
            <p className="text-[11px] text-gray-400 mb-1">대기 번호</p>
            <p className="text-4xl font-black text-primary tracking-tighter">
              {waiting.waitingNumber}
            </p>
          </div>
          {active && (
            <div className="text-right">
              <p className="text-[11px] text-gray-400 mb-1">앞에</p>
              <p className="text-2xl font-extrabold text-gray-900">
                {waiting.aheadCount ?? 0}
                <span className="text-base text-gray-500 font-bold">팀</span>
              </p>
            </div>
          )}
        </div>
      </div>

      {/* 식당 정보 */}
      {restaurant && (
        <div
          onClick={() => navigate(`/restaurants/${restaurant.id}`)}
          className="mx-5 mt-4 bg-white rounded-2xl border border-gray-100 p-4 flex items-center gap-3 cursor-pointer active:scale-[0.99] transition"
        >
          <div className="w-14 h-14 rounded-2xl bg-gray-100 overflow-hidden flex items-center justify-center shrink-0">
            {restaurant.imageUrl ? (
              <img
                src={restaurant.imageUrl}
                alt={restaurant.name}
                className="w-full h-full object-cover"
              />
            ) : (
              <span className="text-2xl opacity-60">🍴</span>
            )}
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-sm font-bold text-gray-900 truncate">
              {restaurant.name}
            </p>
            <p className="text-xs text-gray-500 truncate">
              {restaurant.address}
            </p>
          </div>
          <ChevronRightIcon className="w-4 h-4 text-gray-400 shrink-0" />
        </div>
      )}

      {/* 상세 정보 */}
      <div className="mx-5 mt-4 bg-white rounded-2xl border border-gray-100 p-4">
        <DetailRow label="인원" value={`${waiting.partySize}명`} />
        <DetailRow label="등록 시각" value={formatDateTime(waiting.registeredAt)} />
        {waiting.calledAt && (
          <DetailRow label="호출 시각" value={formatDateTime(waiting.calledAt)} />
        )}
        {waiting.enteredAt && (
          <DetailRow label="입장 시각" value={formatDateTime(waiting.enteredAt)} last />
        )}
      </div>

      {/* 취소 버튼 */}
      {active && (
        <div className="mx-5 mt-6">
          <button
            onClick={() => setConfirm(true)}
            disabled={cancelling}
            className="w-full py-4 bg-white text-red-500 border border-red-200 rounded-2xl font-bold text-base disabled:opacity-50"
          >
            웨이팅 취소
          </button>
        </div>
      )}

      {/* 호출됨 상태 → 매장 도착 안내 */}
      {waiting.status === "CALLED" && (
        <div className="mx-5 mt-4 bg-primary-light text-primary rounded-2xl px-4 py-3 text-sm font-semibold text-center">
          매장에 도착하셨다면 직원에게 알려주세요
        </div>
      )}

      {/* 취소 확인 모달 */}
      {confirm && (
        <ConfirmModal
          title="웨이팅을 취소할까요?"
          desc="취소 후 다시 등록하면 맨 뒤로 가요"
          confirmLabel="취소하기"
          onConfirm={handleCancel}
          onCancel={() => setConfirm(false)}
          loading={cancelling}
        />
      )}

      {/* 토스트 */}
      {toast && (
        <div className="fixed bottom-8 left-1/2 -translate-x-1/2 z-50 px-5 py-3 bg-gray-900 text-white text-sm rounded-full shadow-lg">
          {toast}
        </div>
      )}
    </div>
  );
}

/* ─── 보조 ─── */

function DetailRow({ label, value, last }) {
  return (
    <div
      className={`flex items-center justify-between py-2.5 ${
        last ? "" : "border-b border-gray-100"
      }`}
    >
      <span className="text-xs text-gray-500">{label}</span>
      <span className="text-sm font-semibold text-gray-900">{value}</span>
    </div>
  );
}

function EmptyState({ onBrowse }) {
  return (
    <div className="flex flex-col items-center justify-center min-h-screen px-6 text-center">
      <div className="w-20 h-20 rounded-full bg-primary-light flex items-center justify-center mb-5">
        <EmptyIcon className="w-8 h-8 text-primary" />
      </div>
      <h3 className="text-lg font-extrabold text-gray-900 mb-1 tracking-tight">
        진행 중인 웨이팅이 없어요
      </h3>
      <p className="text-sm text-gray-500 mb-8">
        가고 싶은 식당에 먼저 등록해보세요
      </p>
      <button
        onClick={onBrowse}
        className="px-8 py-3.5 bg-primary text-white rounded-2xl font-bold text-sm shadow-lg shadow-primary/30"
      >
        식당 둘러보기
      </button>
    </div>
  );
}

function EmptyIcon({ className }) {
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
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7v5l3 2" />
    </svg>
  );
}

function ConfirmModal({ title, desc, confirmLabel, onConfirm, onCancel, loading }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center px-6">
      <div className="absolute inset-0 bg-black/50" onClick={onCancel} />
      <div className="relative w-full max-w-sm bg-white rounded-3xl p-6">
        <h4 className="text-base font-extrabold text-gray-900 mb-1 tracking-tight">
          {title}
        </h4>
        <p className="text-sm text-gray-500 mb-5">{desc}</p>
        <div className="grid grid-cols-2 gap-3">
          <button
            onClick={onCancel}
            disabled={loading}
            className="py-3 bg-gray-100 text-gray-700 rounded-2xl font-bold text-sm disabled:opacity-50"
          >
            돌아가기
          </button>
          <button
            onClick={onConfirm}
            disabled={loading}
            className="py-3 bg-red-500 text-white rounded-2xl font-bold text-sm disabled:opacity-50"
          >
            {loading ? "처리 중..." : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}

function Loading() {
  return (
    <div className="flex items-center justify-center min-h-screen text-sm text-gray-400">
      불러오는 중...
    </div>
  );
}

function formatDateTime(iso) {
  if (!iso) return "";
  try {
    const d = new Date(iso);
    const hh = String(d.getHours()).padStart(2, "0");
    const mm = String(d.getMinutes()).padStart(2, "0");
    return `${d.getMonth() + 1}월 ${d.getDate()}일 ${hh}:${mm}`;
  } catch {
    return "";
  }
}

function ChevronRightIcon({ className }) {
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
      <path d="m9 18 6-6-6-6" />
    </svg>
  );
}
