import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { getRestaurantDetail } from "../api/restaurant";
import { getSlots, createReservation } from "../api/reservation";
import { tokenStorage } from "../utils/storage";

export default function ReservationPage() {
  const { id } = useParams();
  const navigate = useNavigate();

  const [restaurant, setRestaurant] = useState(null);
  const [date, setDate] = useState(formatDate(new Date()));
  const [slots, setSlots] = useState([]);
  const [slotsLoading, setSlotsLoading] = useState(false);
  const [selectedSlotId, setSelectedSlotId] = useState(null);
  const [headcount, setHeadcount] = useState(2);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!tokenStorage.get()) {
      navigate("/login");
      return;
    }
    getRestaurantDetail(id).then(setRestaurant).catch(() => {});
  }, [id, navigate]);

  /* 날짜 바꾸면 슬롯 재조회 */
  useEffect(() => {
    let cancelled = false;
    setSlotsLoading(true);
    setSelectedSlotId(null);
    getSlots(id, date)
      .then((res) => {
        if (cancelled) return;
        setSlots(res.slots || []);
      })
      .catch(() => {
        if (!cancelled) setSlots([]);
      })
      .finally(() => {
        if (!cancelled) setSlotsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [id, date]);

  const handleSubmit = async () => {
    if (!selectedSlotId) {
      setError("시간대를 선택해주세요");
      return;
    }
    setSubmitting(true);
    setError("");
    try {
      await createReservation({
        restaurantId: Number(id),
        slotId: selectedSlotId,
        headcount,
      });
      navigate("/my-reservations");
    } catch (err) {
      setError(err.response?.data?.message || "예약에 실패했어요");
    } finally {
      setSubmitting(false);
    }
  };

  /* 향후 14일치 날짜 칩 */
  const dateOptions = nextNDays(14);

  return (
    <div className="flex flex-col min-h-screen pb-28 bg-white">
      {/* 상단 */}
      <div className="px-5 pt-6 pb-4 bg-white border-b border-gray-100 sticky top-0 z-10">
        <button
          onClick={() => navigate(-1)}
          className="text-2xl text-gray-700 mb-3"
          aria-label="뒤로가기"
        >
          ←
        </button>
        <h2 className="text-xl font-extrabold text-gray-900 tracking-tight">
          예약하기
        </h2>
        {restaurant && (
          <p className="text-sm text-gray-500 mt-0.5">{restaurant.name}</p>
        )}
      </div>

      {/* 날짜 선택 */}
      <Section title="날짜">
        <div className="flex gap-2 overflow-x-auto scrollbar-hide pb-1">
          {dateOptions.map((d) => {
            const selected = d.value === date;
            return (
              <button
                key={d.value}
                onClick={() => setDate(d.value)}
                className={`shrink-0 px-3 py-2.5 rounded-2xl flex flex-col items-center min-w-[58px] transition ${
                  selected
                    ? "bg-primary text-white"
                    : "bg-gray-100 text-gray-700"
                }`}
              >
                <span className="text-[10px] font-medium opacity-80">
                  {d.dow}
                </span>
                <span className="text-base font-extrabold">
                  {d.day}
                </span>
              </button>
            );
          })}
        </div>
      </Section>

      {/* 시간 선택 */}
      <Section title="시간">
        {slotsLoading ? (
          <div className="py-10 text-center text-sm text-gray-400">
            불러오는 중...
          </div>
        ) : slots.length === 0 ? (
          <div className="py-10 text-center text-sm text-gray-400">
            예약 가능한 시간이 없어요
          </div>
        ) : (
          <div className="grid grid-cols-3 gap-2">
            {slots.map((s) => {
              const selected = s.slotId === selectedSlotId;
              const disabled = !s.available || s.remainCount <= 0;
              return (
                <button
                  key={s.slotId}
                  disabled={disabled}
                  onClick={() => setSelectedSlotId(s.slotId)}
                  className={`py-4 rounded-2xl flex items-center justify-center transition ${
                    selected
                      ? "bg-primary text-white"
                      : disabled
                      ? "bg-gray-50 text-gray-300 line-through"
                      : "bg-gray-100 text-gray-800"
                  }`}
                >
                  <span className="text-sm font-bold">
                    {formatSlotTime(s.slotTime)}
                  </span>
                </button>
              );
            })}
          </div>
        )}
      </Section>

      {/* 인원 */}
      <Section title="인원">
        <div className="grid grid-cols-4 gap-2">
          {[1, 2, 3, 4, 5, 6, 7, 8].map((n) => (
            <button
              key={n}
              onClick={() => setHeadcount(n)}
              className={`py-3 rounded-2xl font-bold transition ${
                headcount === n
                  ? "bg-primary text-white"
                  : "bg-gray-100 text-gray-700"
              }`}
            >
              {n}명
            </button>
          ))}
        </div>
      </Section>

      {/* 에러 */}
      {error && (
        <p className="mx-5 mt-2 text-sm text-red-500">{error}</p>
      )}

      {/* 하단 고정 버튼 */}
      <div className="fixed bottom-0 left-0 right-0 z-30">
        <div className="max-w-[430px] mx-auto px-5 py-4 bg-white border-t border-gray-100">
          <button
            onClick={handleSubmit}
            disabled={submitting || !selectedSlotId}
            className="w-full py-4 bg-primary text-white rounded-2xl font-bold text-base disabled:opacity-50 shadow-lg shadow-primary/30"
          >
            {submitting ? "예약 중..." : "예약하기"}
          </button>
        </div>
      </div>
    </div>
  );
}

function Section({ title, children }) {
  return (
    <div className="px-5 py-5 border-b border-gray-100">
      <h3 className="text-sm font-bold text-gray-900 mb-3">{title}</h3>
      {children}
    </div>
  );
}

function formatDate(d) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

function nextNDays(n) {
  const out = [];
  const today = new Date();
  const dowMap = ["일", "월", "화", "수", "목", "금", "토"];
  for (let i = 0; i < n; i++) {
    const d = new Date(today);
    d.setDate(today.getDate() + i);
    out.push({
      value: formatDate(d),
      dow: i === 0 ? "오늘" : i === 1 ? "내일" : dowMap[d.getDay()],
      day: d.getDate(),
    });
  }
  return out;
}

function formatSlotTime(t) {
  if (!t) return "";
  // "11:30:00" → "11:30"
  return t.length >= 5 ? t.slice(0, 5) : t;
}
