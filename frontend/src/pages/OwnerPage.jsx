import { useEffect, useState, useCallback } from "react";
import { useNavigate } from "react-router-dom";
import {
  getWaitingSession,
  openSession,
  pauseSession,
  resumeSession,
  closeSession,
  getOwnerWaitings,
  callWaiting,
  cancelByOwner,
  enterWaiting,
} from "../api/waiting";
import { getRestaurantDetail } from "../api/restaurant";
import { getSlots, createSlot, updateSlot } from "../api/reservation";
import {
  tokenStorage,
  userStorage,
  restaurantIdStorage,
  logout,
} from "../utils/storage";

const STATUS_BADGE = {
  OPEN: { label: "운영 중", color: "bg-green-100 text-green-700" },
  PAUSED: { label: "일시정지", color: "bg-amber-100 text-amber-700" },
  CLOSED: { label: "마감", color: "bg-gray-200 text-gray-600" },
};

export default function OwnerPage() {
  const navigate = useNavigate();
  const user = userStorage.get();
  const restaurantId = restaurantIdStorage.get();
  const token = tokenStorage.get();

  const [restaurant, setRestaurant] = useState(null);
  const [session, setSession] = useState(null);
  const [waitings, setWaitings] = useState([]);
  const [loading, setLoading] = useState(true);
  const [openSheet, setOpenSheet] = useState(false);
  const [maxWaitingCount, setMaxWaitingCount] = useState(50);
  const [processing, setProcessing] = useState(false);
  const [toast, setToast] = useState("");

  /* 슬롯 관리 상태 */
  const [slotDate, setSlotDate] = useState(formatDateInput(new Date()));
  const [slots, setSlots] = useState([]);
  const [slotsLoading, setSlotsLoading] = useState(false);
  const [openSlotSheet, setOpenSlotSheet] = useState(false);
  const [openBulkSheet, setOpenBulkSheet] = useState(false);
  const [bulkProgress, setBulkProgress] = useState(null);
  const [editSlot, setEditSlot] = useState(null);

  /* 보호: 로그인 / OWNER 체크. 식당 ID 없으면 입력 UI 분기 */
  useEffect(() => {
    if (!token) {
      navigate("/login");
      return;
    }
    if (user?.role !== "OWNER") {
      navigate("/home");
    }
  }, [token, user?.role, navigate]);

  const loadAll = useCallback(async () => {
    if (!restaurantId) return;
    try {
      const r = await getRestaurantDetail(restaurantId);
      setRestaurant(r);
    } catch {
      // pass
    }
    try {
      const s = await getWaitingSession(restaurantId);
      setSession(s);
    } catch {
      setSession(null);
    }
    try {
      const w = await getOwnerWaitings(restaurantId);
      setWaitings(w);
    } catch {
      setWaitings([]);
    }
    setLoading(false);
  }, [restaurantId]);

  useEffect(() => {
    loadAll();
    /* 5초마다 자동 갱신 */
    const id = setInterval(loadAll, 5000);
    return () => clearInterval(id);
  }, [loadAll]);

  /* 슬롯 조회 */
  const loadSlots = useCallback(async () => {
    if (!restaurantId) return;
    setSlotsLoading(true);
    try {
      const res = await getSlots(restaurantId, slotDate);
      setSlots(res.slots || []);
    } catch {
      setSlots([]);
    } finally {
      setSlotsLoading(false);
    }
  }, [restaurantId, slotDate]);

  useEffect(() => {
    loadSlots();
  }, [loadSlots]);

  const showToast = (msg) => {
    setToast(msg);
    setTimeout(() => setToast(""), 1800);
  };

  const handleOpen = async () => {
    setProcessing(true);
    try {
      await openSession(restaurantId, { maxWaitingCount });
      setOpenSheet(false);
      showToast("웨이팅이 시작되었어요");
      loadAll();
    } catch (err) {
      showToast(err.response?.data?.message || "세션을 열 수 없어요");
    } finally {
      setProcessing(false);
    }
  };

  const handleAction = async (fn, msg) => {
    setProcessing(true);
    try {
      await fn();
      showToast(msg);
      loadAll();
    } catch (err) {
      showToast(err.response?.data?.message || "처리에 실패했어요");
    } finally {
      setProcessing(false);
    }
  };

  const handleLogout = () => {
    logout();
    navigate("/login");
  };

  const handleAddSlot = async ({ slotTime, totalCount }) => {
    setProcessing(true);
    try {
      await createSlot(restaurantId, {
        slotDate,
        slotTime: slotTime + ":00",
        totalCount,
      });
      setOpenSlotSheet(false);
      showToast("슬롯이 추가됐어요");
      loadSlots();
    } catch (err) {
      showToast(err.response?.data?.message || "슬롯 추가 실패");
    } finally {
      setProcessing(false);
    }
  };

  /* 슬롯 단건 수정 */
  const handleUpdateSlot = async (slotId, totalCount) => {
    setProcessing(true);
    try {
      await updateSlot(slotId, { totalCount });
      setEditSlot(null);
      showToast("슬롯이 수정됐어요");
      loadSlots();
    } catch (err) {
      showToast(err.response?.data?.message || "수정 실패");
    } finally {
      setProcessing(false);
    }
  };

  /* 일괄 슬롯 생성 */
  const handleBulkSlot = async ({
    startDate,
    endDate,
    startTime,
    endTime,
    intervalMin,
    totalCount,
  }) => {
    const dates = enumerateDates(startDate, endDate);
    const times = enumerateTimes(startTime, endTime, intervalMin);
    const total = dates.length * times.length;

    if (total === 0) {
      showToast("생성할 슬롯이 없어요");
      return;
    }
    if (total > 500) {
      showToast("한 번에 최대 500개까지만 가능해요");
      return;
    }

    let done = 0;
    let failed = 0;
    setBulkProgress({ done: 0, total });

    for (const d of dates) {
      for (const t of times) {
        try {
          await createSlot(restaurantId, {
            slotDate: d,
            slotTime: t + ":00",
            totalCount,
          });
        } catch {
          failed++;
        }
        done++;
        setBulkProgress({ done, total });
      }
    }

    setBulkProgress(null);
    setOpenBulkSheet(false);
    showToast(
      failed === 0
        ? `슬롯 ${total}개 생성됐어요`
        : `${total - failed}개 성공 / ${failed}개 실패`
    );
    loadSlots();
  };

  /* 식당 ID 미설정 점주 → 입력 화면 */
  if (!restaurantId) return <RestaurantIdPrompt onLogout={handleLogout} />;

  if (loading) return <Loading />;

  const sessionStatus = session?.status;
  const badge = STATUS_BADGE[sessionStatus] || {
    label: "미오픈",
    color: "bg-gray-100 text-gray-500",
  };

  return (
    <div className="flex flex-col min-h-screen bg-gray-50">
      {/* 상단 헤더 */}
      <div className="bg-white px-5 pt-6 pb-5 border-b border-gray-100">
        <button
          onClick={() => navigate(-1)}
          className="text-2xl text-gray-700 mb-3"
          aria-label="뒤로가기"
        >
          ←
        </button>
        <div className="flex items-start justify-between">
          <div>
            <span
              className={`inline-block px-2 py-0.5 mb-2 rounded-full text-[11px] font-bold ${badge.color}`}
            >
              {badge.label}
            </span>
            <div className="flex items-center gap-2">
              <h1 className="text-xl font-extrabold text-gray-900 tracking-tight">
                {restaurant?.name || "내 식당"}
              </h1>
              <button
                onClick={() => navigate("/owner/edit-restaurant")}
                className="text-[11px] text-gray-500 px-2 py-0.5 bg-gray-100 rounded-full font-medium active:bg-gray-200"
              >
                수정
              </button>
            </div>
            <p className="text-xs text-gray-500 mt-0.5">
              {user?.name}님 · 점주
            </p>
          </div>
          <button
            onClick={handleLogout}
            className="text-xs text-gray-400 underline-offset-2 hover:underline"
          >
            로그아웃
          </button>
        </div>

        {/* 통계 */}
        {sessionStatus && (
          <div className="grid grid-cols-2 gap-3 mt-4">
            <Stat label="현재 대기" value={`${session.currentCount}팀`} />
            <Stat
              label="최대 대기"
              value={`${session.maxWaitingCount}팀`}
            />
          </div>
        )}
      </div>

      {/* 세션 액션 */}
      <div className="px-5 py-4 bg-white border-b border-gray-100">
        {!sessionStatus || sessionStatus === "CLOSED" ? (
          <button
            onClick={() => setOpenSheet(true)}
            className="w-full py-3.5 bg-primary text-white rounded-2xl font-bold text-base shadow-lg shadow-primary/30"
          >
            웨이팅 시작
          </button>
        ) : sessionStatus === "OPEN" ? (
          <div className="grid grid-cols-2 gap-3">
            <button
              disabled={processing}
              onClick={() =>
                handleAction(() => pauseSession(session.sessionId), "일시정지됨")
              }
              className="py-3 bg-amber-100 text-amber-800 rounded-2xl font-bold disabled:opacity-50"
            >
              일시정지
            </button>
            <button
              disabled={processing}
              onClick={() =>
                handleAction(() => closeSession(session.sessionId), "마감됨")
              }
              className="py-3 bg-gray-200 text-gray-700 rounded-2xl font-bold disabled:opacity-50"
            >
              마감
            </button>
          </div>
        ) : (
          <div className="grid grid-cols-2 gap-3">
            <button
              disabled={processing}
              onClick={() =>
                handleAction(() => resumeSession(session.sessionId), "재개됨")
              }
              className="py-3 bg-primary text-white rounded-2xl font-bold disabled:opacity-50"
            >
              재개
            </button>
            <button
              disabled={processing}
              onClick={() =>
                handleAction(() => closeSession(session.sessionId), "마감됨")
              }
              className="py-3 bg-gray-200 text-gray-700 rounded-2xl font-bold disabled:opacity-50"
            >
              마감
            </button>
          </div>
        )}
      </div>

      {/* 예약 슬롯 관리 */}
      <div className="px-5 py-5 bg-white border-b border-gray-100">
        <div className="flex items-center justify-between mb-3">
          <h2 className="text-base font-bold text-gray-900">예약 슬롯</h2>
          <div className="flex gap-2">
            <button
              onClick={() => setOpenBulkSheet(true)}
              className="px-3 py-1.5 bg-primary text-white rounded-full text-xs font-bold active:bg-primary-dark"
            >
              일괄 생성
            </button>
            <button
              onClick={() => setOpenSlotSheet(true)}
              className="px-3 py-1.5 bg-primary-light text-primary rounded-full text-xs font-bold active:bg-primary/20"
            >
              + 단건
            </button>
          </div>
        </div>

        <input
          type="date"
          value={slotDate}
          onChange={(e) => setSlotDate(e.target.value)}
          className="w-full px-4 py-3 bg-gray-100 rounded-2xl text-sm font-semibold text-gray-900 outline-none focus:ring-2 focus:ring-primary mb-3"
        />

        {slotsLoading ? (
          <p className="py-6 text-center text-xs text-gray-400">불러오는 중...</p>
        ) : slots.length === 0 ? (
          <p className="py-6 text-center text-xs text-gray-400">
            등록된 슬롯이 없어요
          </p>
        ) : (
          <div className="grid grid-cols-3 gap-2">
            {slots.map((s) => (
              <button
                key={s.slotId}
                onClick={() => setEditSlot(s)}
                className={`px-3 py-2.5 rounded-2xl flex flex-col items-center transition active:scale-[0.97] ${
                  s.available
                    ? "bg-gray-100 text-gray-900"
                    : "bg-gray-50 text-gray-400 line-through"
                }`}
              >
                <span className="text-sm font-bold">
                  {(s.slotTime || "").slice(0, 5)}
                </span>
                <span className="text-[10px] text-gray-500 mt-0.5">
                  {s.totalCount}팀
                </span>
              </button>
            ))}
          </div>
        )}
      </div>

      {/* 예약 목록 (프레임만 — 백엔드 API 연결 대기) */}
      <div className="px-5 py-5 bg-white border-b border-gray-100">
        <div className="flex items-center justify-between mb-3">
          <div className="flex items-baseline gap-2">
            <h2 className="text-base font-bold text-gray-900">예약 목록</h2>
            <span className="text-[10px] text-gray-400">미리보기</span>
          </div>
          <span className="text-xs text-gray-400">총 0건</span>
        </div>

        {/* placeholder 카드 */}
        <div className="flex flex-col gap-3">
          <PlaceholderReservation
            time="18:00"
            name="홍길동"
            partySize={4}
            status="CONFIRMED"
          />
          <PlaceholderReservation
            time="18:30"
            name="김철수"
            partySize={2}
            status="VISITED"
          />
        </div>

        <p className="mt-4 text-[11px] text-gray-300 text-center">
          GET /api/v1/owner/restaurants/{`{id}`}/reservations 추가 시 실제 데이터 표시
        </p>
      </div>

      {/* 대기 목록 */}
      <div className="px-5 py-5 flex-1">
        <div className="flex items-baseline justify-between mb-3">
          <h2 className="text-base font-bold text-gray-900">대기 목록</h2>
          <span className="text-xs text-gray-400">
            {waitings.length}건
          </span>
        </div>

        {waitings.length === 0 ? (
          <div className="py-20 text-center text-sm text-gray-400">
            아직 대기 손님이 없어요
          </div>
        ) : (
          <div className="flex flex-col gap-3">
            {waitings.map((w) => (
              <WaitingItem
                key={w.waitingId}
                w={w}
                processing={processing}
                onCall={() =>
                  handleAction(() => callWaiting(w.waitingId), "호출했어요")
                }
                onEnter={() =>
                  handleAction(() => enterWaiting(w.waitingId), "입장 처리됐어요")
                }
                onCancel={() =>
                  handleAction(
                    () => cancelByOwner(w.waitingId),
                    "취소 처리됐어요"
                  )
                }
              />
            ))}
          </div>
        )}
      </div>

      {/* 세션 오픈 바텀시트 */}
      {openSheet && (
        <BottomSheet onClose={() => setOpenSheet(false)}>
          <div className="px-5 pt-2 pb-6">
            <h3 className="text-lg font-bold text-gray-900 mb-1">
              최대 대기 팀 수
            </h3>
            <p className="text-sm text-gray-500 mb-6">
              오늘 받을 수 있는 최대 팀 수를 설정하세요
            </p>

            <div className="flex items-center justify-center gap-6 mb-6">
              <RoundButton
                onClick={() =>
                  setMaxWaitingCount(Math.max(1, maxWaitingCount - 10))
                }
              >
                −
              </RoundButton>
              <span className="text-3xl font-extrabold text-gray-900 w-20 text-center">
                {maxWaitingCount}
              </span>
              <RoundButton
                onClick={() =>
                  setMaxWaitingCount(Math.min(999, maxWaitingCount + 10))
                }
              >
                +
              </RoundButton>
            </div>

            <button
              onClick={handleOpen}
              disabled={processing}
              className="w-full py-4 bg-primary text-white rounded-2xl font-bold text-base disabled:opacity-50 shadow-lg shadow-primary/30"
            >
              {processing ? "처리 중..." : "웨이팅 시작하기"}
            </button>
          </div>
        </BottomSheet>
      )}

      {/* 슬롯 추가 바텀시트 */}
      {openSlotSheet && (
        <SlotCreateSheet
          date={slotDate}
          onClose={() => setOpenSlotSheet(false)}
          onSubmit={handleAddSlot}
          processing={processing}
        />
      )}

      {/* 일괄 생성 바텀시트 */}
      {openBulkSheet && (
        <BulkSlotSheet
          baseDate={slotDate}
          onClose={() => setOpenBulkSheet(false)}
          onSubmit={handleBulkSlot}
          progress={bulkProgress}
        />
      )}

      {/* 슬롯 단건 수정 바텀시트 */}
      {editSlot && (
        <SlotEditSheet
          slot={editSlot}
          processing={processing}
          onClose={() => setEditSlot(null)}
          onSubmit={(count) => handleUpdateSlot(editSlot.slotId, count)}
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

function SlotCreateSheet({ date, onClose, onSubmit, processing }) {
  const [slotTime, setSlotTime] = useState("18:00");
  const [totalCount, setTotalCount] = useState(2);

  return (
    <BottomSheet onClose={onClose}>
      <div className="px-5 pt-2 pb-6">
        <h3 className="text-lg font-bold text-gray-900 mb-1">
          예약 슬롯 추가
        </h3>
        <p className="text-sm text-gray-500 mb-5">{date}</p>

        <div className="flex flex-col gap-4">
          <div>
            <p className="text-xs font-semibold text-gray-600 mb-1.5 px-1">
              시간
            </p>
            <input
              type="time"
              value={slotTime}
              onChange={(e) => setSlotTime(e.target.value)}
              className="w-full px-4 py-3.5 bg-gray-100 rounded-2xl text-base font-semibold outline-none focus:bg-white focus:ring-2 focus:ring-primary"
            />
          </div>

          <div>
            <p className="text-xs font-semibold text-gray-600 mb-1 px-1">
              받을 수 있는 팀 수
            </p>
            <p className="text-[11px] text-gray-400 mb-1.5 px-1">
              이 시간대에 받을 예약 팀 수 (예약 1건 = 1팀)
            </p>
            <div className="flex items-center justify-center gap-6">
              <RoundButton
                onClick={() => setTotalCount(Math.max(1, totalCount - 1))}
              >
                −
              </RoundButton>
              <span className="text-3xl font-extrabold text-gray-900 w-20 text-center">
                {totalCount}
              </span>
              <RoundButton
                onClick={() => setTotalCount(Math.min(99, totalCount + 1))}
              >
                +
              </RoundButton>
            </div>
          </div>
        </div>

        <button
          onClick={() => onSubmit({ slotTime, totalCount })}
          disabled={processing}
          className="w-full mt-6 py-4 bg-primary text-white rounded-2xl font-bold text-base disabled:opacity-50 shadow-lg shadow-primary/30"
        >
          {processing ? "추가 중..." : "슬롯 추가"}
        </button>
      </div>
    </BottomSheet>
  );
}

function PlaceholderReservation({ time, name, partySize, status }) {
  const meta = {
    CONFIRMED: { label: "예약 확정", color: "bg-blue-100 text-blue-700" },
    VISITED: { label: "방문 완료", color: "bg-green-100 text-green-700" },
    CANCELLED: { label: "취소", color: "bg-gray-200 text-gray-500" },
    NO_SHOW: { label: "노쇼", color: "bg-red-100 text-red-600" },
  }[status];
  const active = status === "CONFIRMED";

  return (
    <div className="bg-gray-50 rounded-2xl px-4 py-3.5 border border-gray-100 opacity-70">
      <div className="flex items-center justify-between mb-2">
        <div className="flex items-center gap-2.5">
          <div className="w-10 h-10 rounded-full bg-primary-light flex items-center justify-center text-primary font-extrabold text-sm">
            {time.slice(0, 2)}
          </div>
          <div>
            <p className="text-sm font-bold text-gray-900">
              {name} · {partySize}명
            </p>
            <p className="text-[11px] text-gray-400">예약 시간 {time}</p>
          </div>
        </div>
        <span
          className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${meta.color}`}
        >
          {meta.label}
        </span>
      </div>

      {active && (
        <div className="grid grid-cols-2 gap-2 mt-2">
          <button
            disabled
            className="py-2 bg-green-500 text-white rounded-xl text-xs font-bold opacity-40"
          >
            방문 완료
          </button>
          <button
            disabled
            className="py-2 bg-gray-100 text-gray-700 rounded-xl text-xs font-bold opacity-40"
          >
            노쇼
          </button>
        </div>
      )}
    </div>
  );
}

function SlotEditSheet({ slot, processing, onClose, onSubmit }) {
  const [count, setCount] = useState(slot.totalCount);
  const booked = slot.totalCount - slot.remainCount;

  return (
    <BottomSheet onClose={onClose}>
      <div className="px-5 pt-2 pb-6">
        <h3 className="text-lg font-bold text-gray-900 mb-1">
          슬롯 수정
        </h3>
        <p className="text-sm text-gray-500 mb-5">
          {(slot.slotTime || "").slice(0, 5)} · 예약된 팀 {booked}팀
        </p>

        <p className="text-xs font-semibold text-gray-600 mb-1 px-1">
          받을 수 있는 팀 수
        </p>
        <p className="text-[11px] text-gray-400 mb-3 px-1">
          최소 {Math.max(1, booked)}팀 이상 (이미 예약된 팀 보호)
        </p>

        <div className="flex items-center justify-center gap-6 mb-5">
          <RoundButton
            onClick={() => setCount(Math.max(Math.max(1, booked), count - 1))}
          >
            −
          </RoundButton>
          <span className="text-3xl font-extrabold text-gray-900 w-20 text-center">
            {count}
          </span>
          <RoundButton onClick={() => setCount(Math.min(99, count + 1))}>
            +
          </RoundButton>
        </div>

        <button
          onClick={() => onSubmit(count)}
          disabled={processing || count === slot.totalCount}
          className="w-full py-4 bg-primary text-white rounded-2xl font-bold text-base disabled:opacity-50 shadow-lg shadow-primary/30"
        >
          {processing ? "수정 중..." : "변경 저장"}
        </button>
      </div>
    </BottomSheet>
  );
}

function BulkSlotSheet({ baseDate, onClose, onSubmit, progress }) {
  const [startDate, setStartDate] = useState(baseDate);
  const [endDate, setEndDate] = useState(addDays(baseDate, 13)); // 2주
  const [startTime, setStartTime] = useState("11:00");
  const [endTime, setEndTime] = useState("21:00");
  const [intervalMin, setIntervalMin] = useState(60);
  const [totalCount, setTotalCount] = useState(4);

  const dateCount = Math.max(0, daysBetween(startDate, endDate) + 1);
  const timeCount = countTimes(startTime, endTime, intervalMin);
  const totalSlots = dateCount * timeCount;

  const submitting = progress !== null;

  return (
    <BottomSheet onClose={submitting ? () => {} : onClose}>
      <div className="px-5 pt-2 pb-6 max-h-[80vh] overflow-y-auto">
        <h3 className="text-lg font-bold text-gray-900 mb-1">
          예약 슬롯 일괄 생성
        </h3>
        <p className="text-sm text-gray-500 mb-5">
          기간 × 시간대 조합으로 한번에 만들어요
        </p>

        {/* 기간 */}
        <p className="text-xs font-semibold text-gray-600 mb-1.5 px-1">기간</p>
        <div className="grid grid-cols-2 gap-2 mb-4">
          <input
            type="date"
            value={startDate}
            onChange={(e) => setStartDate(e.target.value)}
            disabled={submitting}
            className="px-3 py-3 bg-gray-100 rounded-2xl text-sm font-semibold outline-none focus:bg-white focus:ring-2 focus:ring-primary"
          />
          <input
            type="date"
            value={endDate}
            onChange={(e) => setEndDate(e.target.value)}
            disabled={submitting}
            className="px-3 py-3 bg-gray-100 rounded-2xl text-sm font-semibold outline-none focus:bg-white focus:ring-2 focus:ring-primary"
          />
        </div>

        {/* 시간대 */}
        <p className="text-xs font-semibold text-gray-600 mb-1.5 px-1">
          시간대
        </p>
        <div className="grid grid-cols-2 gap-2 mb-4">
          <input
            type="time"
            value={startTime}
            onChange={(e) => setStartTime(e.target.value)}
            disabled={submitting}
            className="px-3 py-3 bg-gray-100 rounded-2xl text-sm font-semibold outline-none focus:bg-white focus:ring-2 focus:ring-primary"
          />
          <input
            type="time"
            value={endTime}
            onChange={(e) => setEndTime(e.target.value)}
            disabled={submitting}
            className="px-3 py-3 bg-gray-100 rounded-2xl text-sm font-semibold outline-none focus:bg-white focus:ring-2 focus:ring-primary"
          />
        </div>

        {/* 간격 */}
        <p className="text-xs font-semibold text-gray-600 mb-1.5 px-1">간격</p>
        <div className="grid grid-cols-3 gap-2 mb-4">
          {[30, 60, 90].map((m) => (
            <button
              key={m}
              type="button"
              disabled={submitting}
              onClick={() => setIntervalMin(m)}
              className={`py-2.5 rounded-2xl text-sm font-bold transition ${
                intervalMin === m
                  ? "bg-primary text-white"
                  : "bg-gray-100 text-gray-700"
              }`}
            >
              {m}분
            </button>
          ))}
        </div>

        {/* 슬롯당 받을 팀 수 */}
        <p className="text-xs font-semibold text-gray-600 mb-1 px-1">
          슬롯당 받을 팀 수
        </p>
        <p className="text-[11px] text-gray-400 mb-2 px-1">
          한 시간대에 받을 예약 팀 수 (1팀 = 예약 1건)
        </p>
        <div className="flex items-center justify-center gap-6 mb-5">
          <RoundButton
            onClick={() => !submitting && setTotalCount(Math.max(1, totalCount - 1))}
          >
            −
          </RoundButton>
          <span className="text-3xl font-extrabold text-gray-900 w-20 text-center">
            {totalCount}
          </span>
          <RoundButton
            onClick={() => !submitting && setTotalCount(Math.min(99, totalCount + 1))}
          >
            +
          </RoundButton>
        </div>

        {/* 요약 */}
        <div className="bg-primary-light/60 rounded-2xl px-4 py-3 mb-5 text-center">
          <p className="text-xs text-gray-600 mb-0.5">총 생성 예정</p>
          <p className="text-lg font-extrabold text-primary">
            {dateCount}일 × {timeCount}시간대 = {totalSlots}개
          </p>
        </div>

        {/* 진행 상황 */}
        {submitting && (
          <div className="mb-4">
            <p className="text-xs text-gray-500 mb-1">
              생성 중... {progress.done}/{progress.total}
            </p>
            <div className="w-full bg-gray-200 rounded-full h-2">
              <div
                className="bg-primary h-2 rounded-full transition-all"
                style={{
                  width: `${(progress.done / progress.total) * 100}%`,
                }}
              />
            </div>
          </div>
        )}

        <button
          onClick={() =>
            onSubmit({
              startDate,
              endDate,
              startTime,
              endTime,
              intervalMin,
              totalCount,
            })
          }
          disabled={submitting || totalSlots === 0}
          className="w-full py-4 bg-primary text-white rounded-2xl font-bold text-base disabled:opacity-50 shadow-lg shadow-primary/30"
        >
          {submitting ? "생성 중..." : `${totalSlots}개 생성하기`}
        </button>
      </div>
    </BottomSheet>
  );
}

/* ─── 날짜/시간 헬퍼 ─── */

function formatDateInput(d) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

function addDays(isoDate, n) {
  const d = new Date(isoDate);
  d.setDate(d.getDate() + n);
  return formatDateInput(d);
}

function daysBetween(start, end) {
  const a = new Date(start);
  const b = new Date(end);
  return Math.round((b - a) / (1000 * 60 * 60 * 24));
}

function enumerateDates(start, end) {
  const out = [];
  const n = daysBetween(start, end);
  if (n < 0) return out;
  for (let i = 0; i <= n; i++) {
    out.push(addDays(start, i));
  }
  return out;
}

function toMin(t) {
  const [h, m] = t.split(":").map(Number);
  return h * 60 + m;
}

function toTimeStr(min) {
  const h = String(Math.floor(min / 60)).padStart(2, "0");
  const m = String(min % 60).padStart(2, "0");
  return `${h}:${m}`;
}

function enumerateTimes(start, end, interval) {
  const out = [];
  const s = toMin(start);
  const e = toMin(end);
  if (e <= s || interval <= 0) return out;
  for (let m = s; m < e; m += interval) {
    out.push(toTimeStr(m));
  }
  return out;
}

function countTimes(start, end, interval) {
  return enumerateTimes(start, end, interval).length;
}

/* ─── 보조 ─── */

function Stat({ label, value }) {
  return (
    <div className="bg-gray-50 rounded-2xl px-4 py-3">
      <p className="text-[11px] text-gray-500 mb-0.5">{label}</p>
      <p className="text-lg font-extrabold text-gray-900">{value}</p>
    </div>
  );
}

function WaitingItem({ w, processing, onCall, onEnter, onCancel }) {
  const statusInfo = {
    WAITING: { label: "대기 중", color: "bg-blue-100 text-blue-700" },
    CALLED: { label: "호출됨", color: "bg-primary-light text-primary" },
    ENTERED: { label: "입장 완료", color: "bg-green-100 text-green-700" },
    CANCELLED: { label: "취소됨", color: "bg-gray-200 text-gray-500" },
  }[w.status] || { label: w.status, color: "bg-gray-100 text-gray-600" };

  const active = w.status === "WAITING" || w.status === "CALLED";

  return (
    <div
      className={`bg-white rounded-2xl px-4 py-3.5 border ${
        active ? "border-gray-100" : "border-transparent opacity-60"
      }`}
    >
      <div className="flex items-center justify-between mb-2">
        <div className="flex items-center gap-2.5">
          <div className="w-9 h-9 rounded-full bg-primary-light flex items-center justify-center text-primary font-extrabold text-sm">
            {w.waitingNumber}
          </div>
          <div>
            <p className="text-sm font-bold text-gray-900">
              {w.partySize}명
            </p>
            <p className="text-[11px] text-gray-400">
              {formatTime(w.registeredAt)}
            </p>
          </div>
        </div>
        <span
          className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${statusInfo.color}`}
        >
          {statusInfo.label}
        </span>
      </div>

      {active && (
        <div className="grid grid-cols-3 gap-2 mt-2">
          {w.status === "WAITING" ? (
            <button
              disabled={processing}
              onClick={onCall}
              className="py-2 bg-primary text-white rounded-xl text-xs font-bold disabled:opacity-50"
            >
              호출
            </button>
          ) : (
            <button
              disabled={processing}
              onClick={onEnter}
              className="py-2 bg-green-500 text-white rounded-xl text-xs font-bold disabled:opacity-50"
            >
              입장
            </button>
          )}
          <button
            disabled={processing}
            onClick={onCancel}
            className="py-2 bg-gray-100 text-gray-700 rounded-xl text-xs font-bold disabled:opacity-50 col-span-2"
          >
            취소
          </button>
        </div>
      )}
    </div>
  );
}

function RoundButton({ children, onClick }) {
  return (
    <button
      onClick={onClick}
      className="w-12 h-12 rounded-full bg-gray-100 text-2xl font-bold text-gray-700 active:bg-gray-200"
    >
      {children}
    </button>
  );
}

function BottomSheet({ children, onClose }) {
  return (
    <div className="fixed inset-0 z-40 flex justify-center items-end">
      <div className="absolute inset-0 bg-black/40" onClick={onClose} />
      <div className="relative w-full max-w-[430px] bg-white rounded-t-3xl pt-3 shadow-2xl animate-slide-up">
        <div className="w-12 h-1 bg-gray-300 rounded-full mx-auto mb-2" />
        {children}
      </div>
    </div>
  );
}

function RestaurantIdPrompt({ onLogout }) {
  const [value, setValue] = useState("");
  const [error, setError] = useState("");

  const handleSubmit = async (e) => {
    e.preventDefault();
    const id = Number(value);
    if (!id || id < 1) {
      setError("올바른 식당 ID를 입력해주세요");
      return;
    }
    /* 유효성 확인: 해당 식당 상세 조회해서 존재하면 저장 */
    try {
      await getRestaurantDetail(id);
      restaurantIdStorage.set(id);
      window.location.reload();
    } catch {
      setError("해당 식당을 찾을 수 없어요");
    }
  };

  return (
    <div className="flex flex-col px-6 pt-16 pb-8 min-h-screen">
      <h2 className="text-xl font-extrabold text-gray-900 mb-1 tracking-tight">
        운영할 식당 ID를 입력해주세요
      </h2>
      <p className="text-sm text-gray-500 mb-8">
        한 번만 입력하면 다음부터 자동 이동돼요
      </p>

      <form onSubmit={handleSubmit} className="flex flex-col gap-3">
        <input
          type="number"
          placeholder="예: 12"
          value={value}
          onChange={(e) => setValue(e.target.value)}
          className="w-full px-4 py-4 bg-gray-100 rounded-2xl text-base outline-none focus:bg-white focus:ring-2 focus:ring-primary transition"
        />
        {error && <p className="text-sm text-red-500 px-1">{error}</p>}
        <button
          type="submit"
          className="w-full py-4 mt-2 bg-primary text-white rounded-2xl font-bold text-base shadow-lg shadow-primary/30"
        >
          확인
        </button>
      </form>

      <button
        onClick={onLogout}
        className="mt-auto text-xs text-gray-400 underline-offset-2 hover:underline"
      >
        로그아웃
      </button>
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

function formatTime(iso) {
  if (!iso) return "";
  try {
    const d = new Date(iso);
    return d.toLocaleTimeString("ko-KR", {
      hour: "2-digit",
      minute: "2-digit",
    });
  } catch {
    return "";
  }
}
