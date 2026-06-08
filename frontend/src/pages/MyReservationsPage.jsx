import { useEffect, useState, useCallback } from "react";
import { useNavigate } from "react-router-dom";
import { getMyReservations, cancelReservation } from "../api/reservation";
import { tokenStorage } from "../utils/storage";

const STATUS_META = {
  CONFIRMED: { label: "예약 확정", color: "bg-blue-100 text-blue-700" },
  VISITED: { label: "방문 완료", color: "bg-green-100 text-green-700" },
  CANCELLED: { label: "취소됨", color: "bg-gray-200 text-gray-500" },
  NO_SHOW: { label: "노쇼", color: "bg-red-100 text-red-600" },
};

export default function MyReservationsPage() {
  const navigate = useNavigate();
  const [reservations, setReservations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [processing, setProcessing] = useState(false);
  const [confirmId, setConfirmId] = useState(null);
  const [toast, setToast] = useState("");

  const load = useCallback(async () => {
    try {
      const data = await getMyReservations();
      setReservations(data);
    } catch {
      setReservations([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!tokenStorage.get()) {
      navigate("/login");
      return;
    }
    load();
  }, [load, navigate]);

  const showToast = (m) => {
    setToast(m);
    setTimeout(() => setToast(""), 1800);
  };

  const handleCancel = async () => {
    setProcessing(true);
    try {
      await cancelReservation(confirmId);
      setConfirmId(null);
      showToast("예약이 취소됐어요");
      load();
    } catch (err) {
      showToast(err.response?.data?.message || "취소에 실패했어요");
    } finally {
      setProcessing(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen text-sm text-gray-400">
        불러오는 중...
      </div>
    );
  }

  return (
    <div className="flex flex-col min-h-screen pb-24 bg-gray-50">
      {/* 헤더 */}
      <div className="px-5 pt-6 pb-5 bg-white border-b border-gray-100">
        <button
          onClick={() => navigate(-1)}
          className="text-2xl text-gray-700 mb-3"
          aria-label="뒤로가기"
        >
          ←
        </button>
        <h2 className="text-2xl font-extrabold text-gray-900 tracking-tight">
          내 예약
        </h2>
      </div>

      {reservations.length === 0 ? (
        <EmptyState onBrowse={() => navigate("/home")} />
      ) : (
        <div className="flex flex-col gap-3 mt-4 px-5">
          {reservations.map((r) => (
            <ReservationCard
              key={r.reservationId}
              r={r}
              onClickRestaurant={() => navigate(`/restaurants/${r.restaurantId}`)}
              onCancel={() => setConfirmId(r.reservationId)}
            />
          ))}
        </div>
      )}

      {/* 취소 확인 */}
      {confirmId !== null && (
        <ConfirmModal
          title="예약을 취소할까요?"
          desc="취소 후엔 같은 시간대에 다시 잡기 어려울 수 있어요"
          confirmLabel="취소하기"
          onConfirm={handleCancel}
          onCancel={() => setConfirmId(null)}
          loading={processing}
        />
      )}

      {toast && (
        <div className="fixed bottom-8 left-1/2 -translate-x-1/2 z-50 px-5 py-3 bg-gray-900 text-white text-sm rounded-full shadow-lg">
          {toast}
        </div>
      )}
    </div>
  );
}

function ReservationCard({ r, onClickRestaurant, onCancel }) {
  const meta = STATUS_META[r.status] || {
    label: r.status,
    color: "bg-gray-100 text-gray-600",
  };
  const cancellable = r.status === "CONFIRMED";

  return (
    <div className="bg-white rounded-2xl border border-gray-100 p-4">
      <div className="flex items-start justify-between mb-2">
        <button onClick={onClickRestaurant} className="text-left flex-1 min-w-0">
          <h3 className="text-base font-bold text-gray-900 truncate">
            {r.restaurantName}
          </h3>
        </button>
        <span
          className={`shrink-0 ml-2 px-2 py-0.5 rounded-full text-[11px] font-bold ${meta.color}`}
        >
          {meta.label}
        </span>
      </div>

      <div className="flex items-center gap-2 text-sm text-gray-600 mb-3">
        <CalendarIcon className="w-3.5 h-3.5" />
        <span>
          {formatDateKo(r.slotDate)} · {formatTime(r.slotTime)} · {r.headcount}명
        </span>
      </div>

      {cancellable && (
        <button
          onClick={onCancel}
          className="w-full py-2.5 bg-gray-100 text-gray-700 rounded-xl text-sm font-bold active:bg-gray-200"
        >
          예약 취소
        </button>
      )}
    </div>
  );
}

function EmptyState({ onBrowse }) {
  return (
    <div className="flex flex-col items-center justify-center flex-1 px-6 text-center">
      <div className="w-20 h-20 rounded-full bg-primary-light flex items-center justify-center mb-5">
        <CalendarIcon className="w-8 h-8 text-primary" />
      </div>
      <h3 className="text-lg font-extrabold text-gray-900 mb-1 tracking-tight">
        예약 내역이 없어요
      </h3>
      <p className="text-sm text-gray-500 mb-8">
        가고 싶은 식당에 예약을 잡아보세요
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

function CalendarIcon({ className }) {
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
      <rect x="3" y="4" width="18" height="18" rx="2" />
      <path d="M16 2v4M8 2v4M3 10h18" />
    </svg>
  );
}

function formatDateKo(iso) {
  if (!iso) return "";
  try {
    const d = new Date(iso);
    return `${d.getMonth() + 1}월 ${d.getDate()}일`;
  } catch {
    return iso;
  }
}

function formatTime(t) {
  if (!t) return "";
  return t.length >= 5 ? t.slice(0, 5) : t;
}
