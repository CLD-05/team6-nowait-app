import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getRestaurantDetail, updateRestaurant } from "../api/restaurant";
import {
  tokenStorage,
  userStorage,
  restaurantIdStorage,
} from "../utils/storage";

export default function EditRestaurantPage() {
  const navigate = useNavigate();
  const restaurantId = restaurantIdStorage.get();
  const token = tokenStorage.get();
  const user = userStorage.get();

  const [loading, setLoading] = useState(true);
  const [restaurant, setRestaurant] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [toast, setToast] = useState("");

  const [form, setForm] = useState({
    phoneNumber: "",
    description: "",
    mainMenuName: "",
    openTime: "09:00",
    closeTime: "22:00",
    closedDays: "",
    imageUrl: "",
    parkingAvailable: false,
    wifiAvailable: false,
    multilingualMenuAvailable: false,
  });

  /* 보호: 로그인 + OWNER + 식당 ID */
  useEffect(() => {
    if (!token) {
      navigate("/login");
      return;
    }
    if (user?.role !== "OWNER" || !restaurantId) {
      navigate("/owner");
    }
  }, [token, user?.role, restaurantId, navigate]);

  /* 현재 정보 로드 */
  useEffect(() => {
    if (!restaurantId) return;
    getRestaurantDetail(restaurantId)
      .then((r) => {
        setRestaurant(r);
        setForm({
          phoneNumber: r.phoneNumber || "",
          description: r.description || "",
          mainMenuName: r.mainMenuName || "",
          openTime: trimSeconds(r.openTime) || "09:00",
          closeTime: trimSeconds(r.closeTime) || "22:00",
          closedDays: r.closedDays || "",
          imageUrl: r.imageUrl || "",
          parkingAvailable: toBool(r.parkingAvailable),
          wifiAvailable: toBool(r.wifiAvailable),
          multilingualMenuAvailable: toBool(r.multilingualMenuAvailable),
        });
      })
      .catch(() => setError("정보를 불러오지 못했어요"))
      .finally(() => setLoading(false));
  }, [restaurantId]);

  const showToast = (m) => {
    setToast(m);
    setTimeout(() => setToast(""), 1800);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError("");

    if (!form.mainMenuName.trim()) {
      setError("대표 메뉴를 입력해주세요");
      return;
    }
    if (!form.phoneNumber.trim()) {
      setError("전화번호를 입력해주세요");
      return;
    }

    setSubmitting(true);
    try {
      await updateRestaurant(restaurantId, {
        phoneNumber: form.phoneNumber,
        description: form.description,
        imageUrl: form.imageUrl,
        mainMenuName: form.mainMenuName,
        openTime: form.openTime + ":00",
        closeTime: form.closeTime + ":00",
        closedDays: form.closedDays,
        parkingAvailable: form.parkingAvailable ? "Y" : "N",
        wifiAvailable: form.wifiAvailable ? "Y" : "N",
        /* 백엔드 DTO 오타: multilingulMenuAvailable */
        multilingulMenuAvailable: form.multilingualMenuAvailable ? "Y" : "N",
      });
      showToast("저장됐어요");
      setTimeout(() => navigate(-1), 800);
    } catch (err) {
      setError(err.response?.data?.message || "저장에 실패했어요");
    } finally {
      setSubmitting(false);
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
    <div className="flex flex-col min-h-screen pb-28 bg-white">
      {/* 헤더 */}
      <div className="px-5 pt-6 pb-4 bg-white border-b border-gray-100 sticky top-0 z-10">
        <button
          onClick={() => navigate(-1)}
          className="text-2xl text-gray-700 mb-3"
          aria-label="뒤로가기"
        >
          ←
        </button>
        <h2 className="text-xl font-extrabold text-gray-900 tracking-tight">
          식당 정보 수정
        </h2>
        {restaurant && (
          <p className="text-sm text-gray-500 mt-0.5">{restaurant.name}</p>
        )}
      </div>

      <form onSubmit={handleSubmit} className="flex flex-col gap-3 px-5 mt-5" noValidate>
        {/* 변경 불가 안내 */}
        <div className="bg-gray-50 rounded-2xl px-4 py-3 text-[11px] text-gray-500">
          상호명·카테고리·주소는 변경할 수 없어요
        </div>

        <SectionTitle>기본 정보</SectionTitle>

        <Field label="대표 메뉴">
          <input
            type="text"
            placeholder="대표 메뉴를 입력해주세요"
            value={form.mainMenuName}
            onChange={(e) => setForm({ ...form, mainMenuName: e.target.value })}
            maxLength={255}
            className={inputClass()}
          />
        </Field>

        <Field label="전화번호">
          <input
            type="text"
            placeholder="02-0000-0000"
            value={form.phoneNumber}
            onChange={(e) => setForm({ ...form, phoneNumber: e.target.value })}
            maxLength={20}
            className={inputClass()}
          />
        </Field>

        <Field label="소개">
          <textarea
            placeholder="식당 소개"
            value={form.description}
            onChange={(e) => setForm({ ...form, description: e.target.value })}
            rows={3}
            className="w-full px-4 py-3 bg-gray-100 rounded-2xl text-base outline-none focus:bg-white focus:ring-2 focus:ring-primary transition resize-none"
          />
        </Field>

        <Field label="이미지 URL">
          <input
            type="text"
            placeholder="https://... (선택)"
            value={form.imageUrl}
            onChange={(e) => setForm({ ...form, imageUrl: e.target.value })}
            maxLength={50}
            className={inputClass()}
          />
        </Field>

        <SectionTitle>운영 시간</SectionTitle>

        <div className="grid grid-cols-2 gap-3">
          <Field label="오픈">
            <input
              type="time"
              value={form.openTime}
              onChange={(e) => setForm({ ...form, openTime: e.target.value })}
              className={inputClass()}
            />
          </Field>
          <Field label="마감">
            <input
              type="time"
              value={form.closeTime}
              onChange={(e) => setForm({ ...form, closeTime: e.target.value })}
              className={inputClass()}
            />
          </Field>
        </div>

        <Field label="휴무일">
          <input
            type="text"
            placeholder="예: 매주 월요일"
            value={form.closedDays}
            onChange={(e) => setForm({ ...form, closedDays: e.target.value })}
            maxLength={100}
            className={inputClass()}
          />
        </Field>

        <SectionTitle>편의 시설</SectionTitle>

        <CheckRow
          label="주차 가능"
          checked={form.parkingAvailable}
          onChange={(v) => setForm({ ...form, parkingAvailable: v })}
        />
        <CheckRow
          label="와이파이 제공"
          checked={form.wifiAvailable}
          onChange={(v) => setForm({ ...form, wifiAvailable: v })}
        />
        <CheckRow
          label="다국어 메뉴판"
          checked={form.multilingualMenuAvailable}
          onChange={(v) => setForm({ ...form, multilingualMenuAvailable: v })}
        />

        {error && (
          <p className="text-red-500 text-sm px-1 mt-1">{error}</p>
        )}

        <p className="text-[11px] text-gray-300 text-center mt-2">
          ⚠ RestaurantService.updateRestaurant 미구현 — 백엔드 반영 후 실제 저장됩니다
        </p>
      </form>

      {/* 하단 고정 저장 버튼 */}
      <div className="fixed bottom-0 left-0 right-0 z-30">
        <div className="max-w-[430px] mx-auto px-5 py-4 bg-white border-t border-gray-100">
          <button
            type="button"
            onClick={handleSubmit}
            disabled={submitting}
            className="w-full py-4 bg-primary text-white rounded-2xl font-bold text-base disabled:opacity-50 shadow-lg shadow-primary/30"
          >
            {submitting ? "저장 중..." : "변경 저장"}
          </button>
        </div>
      </div>

      {toast && (
        <div className="fixed bottom-24 left-1/2 -translate-x-1/2 z-50 px-5 py-3 bg-gray-900 text-white text-sm rounded-full shadow-lg">
          {toast}
        </div>
      )}
    </div>
  );
}

/* ─── 보조 ─── */

function SectionTitle({ children }) {
  return (
    <h3 className="text-sm font-bold text-gray-900 mt-2 mb-1">{children}</h3>
  );
}

function Field({ label, children }) {
  return (
    <label className="flex flex-col gap-1.5">
      <span className="text-xs font-semibold text-gray-600 px-1">{label}</span>
      {children}
    </label>
  );
}

function inputClass() {
  return "w-full px-4 py-4 bg-gray-100 rounded-2xl text-base outline-none focus:bg-white focus:ring-2 focus:ring-primary transition";
}

function CheckRow({ label, checked, onChange }) {
  return (
    <button
      type="button"
      onClick={() => onChange(!checked)}
      className={`flex items-center justify-between w-full px-4 py-4 rounded-2xl text-base font-medium transition ${
        checked ? "bg-primary-light text-primary" : "bg-gray-100 text-gray-700"
      }`}
    >
      <span>{label}</span>
      <span
        className={`w-5 h-5 rounded-full flex items-center justify-center text-xs ${
          checked ? "bg-primary text-white" : "bg-white border border-gray-300"
        }`}
      >
        {checked && "✓"}
      </span>
    </button>
  );
}

function trimSeconds(t) {
  if (!t) return "";
  return t.length >= 5 ? t.slice(0, 5) : t;
}

function toBool(v) {
  if (typeof v === "boolean") return v;
  if (v == null) return false;
  const s = String(v).toLowerCase();
  return s === "y" || s === "true" || s === "1";
}
