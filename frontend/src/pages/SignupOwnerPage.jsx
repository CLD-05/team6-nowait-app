import { useState, useRef, useEffect } from "react";
import { Link, useNavigate } from "react-router-dom";
import { signUpOwner } from "../api/auth";
import { registerRestaurant } from "../api/restaurant";
import { tokenStorage, userStorage, restaurantIdStorage } from "../utils/storage";

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

const CATEGORIES = [
  { value: "KOREAN", label: "한식" },
  { value: "JAPANESE", label: "일식" },
  { value: "CHINESE", label: "중식" },
  { value: "WESTERN", label: "양식" },
  { value: "ASIAN", label: "아시안" },
];

export default function SignupOwnerPage() {
  const navigate = useNavigate();
  const [step, setStep] = useState(1);
  const [loading, setLoading] = useState(false);
  const [errors, setErrors] = useState({});

  const [account, setAccount] = useState({
    email: "",
    password: "",
    passwordConfirm: "",
    name: "",
  });

  const [restaurant, setRestaurant] = useState({
    restaurantName: "",
    category: "KOREAN",
    address: "",
    phoneNumber: "",
    description: "",
    mainMenuName: "",
    openTime: "09:00",
    closeTime: "22:00",
    closedDays: "",
    parkingAvailable: false,
    wifiAvailable: false,
    multilingualMenuAvailable: false,
  });

  const validateStep1 = () => {
    const e = {};
    if (!account.name.trim()) e.name = "이름을 입력해주세요";
    if (!account.email.trim()) e.email = "이메일을 입력해주세요";
    else if (!EMAIL_REGEX.test(account.email)) e.email = "올바른 이메일을 입력해주세요";

    if (!account.password) e.password = "비밀번호를 입력해주세요";
    else if (account.password.length < 8)
      e.password = `8자 이상 입력해주세요 (현재 ${account.password.length}자)`;

    if (!account.passwordConfirm) e.passwordConfirm = "비밀번호를 다시 입력해주세요";
    else if (account.password !== account.passwordConfirm)
      e.passwordConfirm = "비밀번호가 일치하지 않습니다";

    return e;
  };

  const validateStep2 = () => {
    const e = {};
    if (!restaurant.restaurantName.trim()) e.restaurantName = "상호명을 입력해주세요";
    if (!restaurant.address.trim()) e.address = "주소를 입력해주세요";
    if (!restaurant.phoneNumber.trim()) e.phoneNumber = "전화번호를 입력해주세요";
    if (!restaurant.mainMenuName.trim()) e.mainMenuName = "메인 메뉴를 입력해주세요";
    return e;
  };

  const handleNext = (e) => {
    e.preventDefault();
    const v = validateStep1();
    setErrors(v);
    if (Object.keys(v).length > 0) return;
    setStep(2);
    setErrors({});
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    const v = validateStep2();
    setErrors(v);
    if (Object.keys(v).length > 0) return;

    setLoading(true);
    try {
      /* 1) 점주 계정 생성 + 토큰 발급 */
      const res = await signUpOwner({
        email: account.email,
        password: account.password,
        name: account.name,
        restaurantInfo: {
          ...restaurant,
          openTime: restaurant.openTime + ":00",
          closeTime: restaurant.closeTime + ":00",
        },
      });
      tokenStorage.set(res.accessToken);
      userStorage.set({
        userId: res.userId,
        email: res.email,
        name: res.name,
        role: res.role,
      });

      /*
         2) 백엔드 AuthService가 식당까지 생성하지 않으므로,
            방금 받은 점주 토큰으로 식당 등록 API를 별도 호출.
            (RestaurantRegisterRequest 스펙에 맞춰 필드명/타입 변환)
      */
      const newRestaurantId = await registerRestaurant({
        name: restaurant.restaurantName,
        category: restaurant.category,
        address: restaurant.address,
        phoneNumber: restaurant.phoneNumber,
        description: restaurant.description,
        mainMenuName: restaurant.mainMenuName,
        openTime: restaurant.openTime + ":00",
        closeTime: restaurant.closeTime + ":00",
        closedDays: restaurant.closedDays,
        parkingAvailable: String(restaurant.parkingAvailable),
        wifiAvailable: String(restaurant.wifiAvailable),
        multilingualMenuAvailable: String(restaurant.multilingualMenuAvailable),
      });
      restaurantIdStorage.set(newRestaurantId);

      navigate("/owner");
    } catch (err) {
      setErrors({
        submit: err.response?.data?.message || "회원가입에 실패했습니다.",
      });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex flex-col px-6 pt-8 pb-8 min-h-screen relative overflow-hidden">
      <div className="pointer-events-none absolute -top-20 -right-20 w-72 h-72 rounded-full bg-primary/20 blur-3xl" />

      <div className="relative mb-6">
        <button
          onClick={() => (step === 1 ? navigate(-1) : setStep(1))}
          className="text-2xl text-gray-700 mb-6"
          aria-label="뒤로가기"
        >
          ←
        </button>
        <h2 className="text-2xl font-bold text-gray-900 mb-1">점주 회원가입</h2>
        <p className="text-gray-400 text-xs">사장님 계정으로 식당을 운영하세요</p>
      </div>

      <div className="relative flex items-center gap-2 mb-8">
        <StepDot active>1</StepDot>
        <div className={`flex-1 h-0.5 ${step === 2 ? "bg-primary" : "bg-gray-200"}`} />
        <StepDot active={step === 2}>2</StepDot>
        {/* DEV ONLY: 입력 검증 건너뛰기 (배포 전 삭제) */}
        {import.meta.env.DEV && step === 1 && (
          <button
            type="button"
            onClick={() => { setErrors({}); setStep(2); }}
            className="ml-2 text-xs text-gray-400 underline"
          >
            skip
          </button>
        )}
      </div>

      {step === 1 ? (
        <form onSubmit={handleNext} className="relative flex flex-col gap-3" noValidate>
          <SectionTitle>계정 정보</SectionTitle>

          <Field label="이름" error={errors.name}>
            <input
              type="text"
              placeholder="이름을 입력해주세요"
              value={account.name}
              onChange={(e) => setAccount({ ...account, name: e.target.value })}
              maxLength={50}
              className={inputClass(errors.name)}
            />
          </Field>

          <Field label="이메일" error={errors.email}>
            <input
              type="email"
              placeholder="이메일을 입력해주세요"
              value={account.email}
              onChange={(e) => setAccount({ ...account, email: e.target.value })}
              className={inputClass(errors.email)}
            />
          </Field>

          <Field label="비밀번호" error={errors.password}>
            <input
              type="password"
              placeholder="비밀번호를 입력해주세요"
              value={account.password}
              onChange={(e) => setAccount({ ...account, password: e.target.value })}
              className={inputClass(errors.password)}
            />
          </Field>

          <Field label="비밀번호 확인" error={errors.passwordConfirm}>
            <input
              type="password"
              placeholder="비밀번호를 다시 입력해주세요"
              value={account.passwordConfirm}
              onChange={(e) => setAccount({ ...account, passwordConfirm: e.target.value })}
              className={inputClass(errors.passwordConfirm)}
            />
          </Field>

          <button
            type="submit"
            className="w-full py-4 mt-4 bg-primary text-white rounded-2xl font-bold text-base active:bg-primary-dark shadow-lg shadow-primary/30 transition"
          >
            다음
          </button>
        </form>
      ) : (
        <form onSubmit={handleSubmit} className="relative flex flex-col gap-3" noValidate>
          <SectionTitle>기본 정보</SectionTitle>

          <Field label="상호명" error={errors.restaurantName}>
            <input
              type="text"
              placeholder="상호명을 입력해주세요"
              value={restaurant.restaurantName}
              onChange={(e) => setRestaurant({ ...restaurant, restaurantName: e.target.value })}
              maxLength={100}
              className={inputClass(errors.restaurantName)}
            />
          </Field>

          <Field label="카테고리">
            <div className="grid grid-cols-5 gap-2">
              {CATEGORIES.map((c) => (
                <button
                  key={c.value}
                  type="button"
                  onClick={() => setRestaurant({ ...restaurant, category: c.value })}
                  className={`py-3 rounded-xl text-sm font-medium transition ${
                    restaurant.category === c.value
                      ? "bg-primary text-white"
                      : "bg-gray-100 text-gray-700"
                  }`}
                >
                  {c.label}
                </button>
              ))}
            </div>
          </Field>

          <Field label="주소" error={errors.address}>
            <input
              type="text"
              placeholder="주소를 입력해주세요"
              value={restaurant.address}
              onChange={(e) => setRestaurant({ ...restaurant, address: e.target.value })}
              maxLength={255}
              className={inputClass(errors.address)}
            />
          </Field>

          <Field label="전화번호" error={errors.phoneNumber}>
            <input
              type="text"
              placeholder="전화번호를 입력해주세요"
              value={restaurant.phoneNumber}
              onChange={(e) => setRestaurant({ ...restaurant, phoneNumber: e.target.value })}
              maxLength={20}
              className={inputClass(errors.phoneNumber)}
            />
          </Field>

          <Field label="메인 메뉴" error={errors.mainMenuName}>
            <input
              type="text"
              placeholder="대표 메뉴를 입력해주세요"
              value={restaurant.mainMenuName}
              onChange={(e) => setRestaurant({ ...restaurant, mainMenuName: e.target.value })}
              maxLength={255}
              className={inputClass(errors.mainMenuName)}
            />
          </Field>

          <Field label="소개">
            <textarea
              placeholder="식당 소개를 입력해주세요"
              value={restaurant.description}
              onChange={(e) => setRestaurant({ ...restaurant, description: e.target.value })}
              rows={3}
              className="w-full px-4 py-3 bg-gray-100 rounded-2xl text-base outline-none focus:bg-white focus:ring-2 focus:ring-primary transition resize-none"
            />
          </Field>

          <SectionTitle>운영 시간</SectionTitle>

          <div className="grid grid-cols-2 gap-3">
            <Field label="오픈">
              <TimeSelect
                value={restaurant.openTime}
                onChange={(v) => setRestaurant({ ...restaurant, openTime: v })}
              />
            </Field>
            <Field label="마감">
              <TimeSelect
                value={restaurant.closeTime}
                onChange={(v) => setRestaurant({ ...restaurant, closeTime: v })}
              />
            </Field>
          </div>

          <Field label="휴무일">
            <input
              type="text"
              placeholder="휴무일을 입력해주세요 (예: 매주 월요일)"
              value={restaurant.closedDays}
              onChange={(e) => setRestaurant({ ...restaurant, closedDays: e.target.value })}
              maxLength={100}
              className={inputClass(false)}
            />
          </Field>

          <SectionTitle>편의 시설</SectionTitle>

          <CheckRow
            label="주차 가능"
            checked={restaurant.parkingAvailable}
            onChange={(v) => setRestaurant({ ...restaurant, parkingAvailable: v })}
          />
          <CheckRow
            label="와이파이 제공"
            checked={restaurant.wifiAvailable}
            onChange={(v) => setRestaurant({ ...restaurant, wifiAvailable: v })}
          />
          <CheckRow
            label="다국어 메뉴판"
            checked={restaurant.multilingualMenuAvailable}
            onChange={(v) => setRestaurant({ ...restaurant, multilingualMenuAvailable: v })}
          />

          {errors.submit && (
            <p className="text-red-500 text-sm px-1 mt-1">{errors.submit}</p>
          )}

          <button
            type="submit"
            disabled={loading}
            className="w-full py-4 mt-4 bg-primary text-white rounded-2xl font-bold text-base disabled:opacity-50 active:bg-primary-dark shadow-lg shadow-primary/30 transition"
          >
            {loading ? "가입 중..." : "가입 완료"}
          </button>
        </form>
      )}

      <div className="relative mt-auto pt-10 text-center text-sm text-gray-500">
        이미 계정이 있으신가요?{" "}
        <Link to="/login" className="text-primary font-semibold">로그인</Link>
      </div>
    </div>
  );
}

/* ─── 보조 컴포넌트 ─── */

function StepDot({ active, children }) {
  return (
    <div
      className={`w-7 h-7 rounded-full flex items-center justify-center text-xs font-bold ${
        active ? "bg-primary text-white" : "bg-gray-200 text-gray-500"
      }`}
    >
      {children}
    </div>
  );
}

function SectionTitle({ children }) {
  return (
    <h3 className="text-sm font-bold text-gray-900 mt-2 mb-1">{children}</h3>
  );
}

function Field({ label, error, children }) {
  return (
    <label className="flex flex-col gap-1.5">
      <span className="text-xs font-semibold text-gray-600 px-1">{label}</span>
      {children}
      {error && (
        <span className="text-xs text-red-500 px-1">{error}</span>
      )}
    </label>
  );
}

function inputClass(hasError) {
  const base = "w-full px-4 py-4 rounded-2xl text-base outline-none transition";
  return hasError
    ? `${base} bg-red-50 ring-2 ring-red-300 focus:bg-white`
    : `${base} bg-gray-100 focus:bg-white focus:ring-2 focus:ring-primary`;
}

/* HH:mm 형식 시간 선택 (시 + 분 커스텀 드롭다운) */
function TimeSelect({ value, onChange }) {
  const [hour, minute] = value.split(":");
  const update = (h, m) => onChange(`${h}:${m}`);

  const hours = Array.from({ length: 24 }, (_, i) => String(i).padStart(2, "0"));
  const minutes = ["00", "05", "10", "15", "20", "25", "30", "35", "40", "45", "50", "55"];

  return (
    <div className="flex items-center gap-1 bg-gray-100 rounded-2xl px-2 py-2">
      <CustomDropdown
        value={hour}
        options={hours}
        onChange={(v) => update(v, minute)}
      />
      <span className="text-gray-400 font-bold">:</span>
      <CustomDropdown
        value={minute}
        options={minutes}
        onChange={(v) => update(hour, v)}
      />
    </div>
  );
}

/* 커스텀 드롭다운 — 회색 테두리 + 둥근 모서리 + primary 강조 */
function CustomDropdown({ value, options, onChange }) {
  const [open, setOpen] = useState(false);
  const wrapRef = useRef(null);
  const listRef = useRef(null);

  // 바깥 클릭 시 닫기
  useEffect(() => {
    if (!open) return;
    const onClick = (e) => {
      if (wrapRef.current && !wrapRef.current.contains(e.target)) setOpen(false);
    };
    document.addEventListener("mousedown", onClick);
    return () => document.removeEventListener("mousedown", onClick);
  }, [open]);

  // 열렸을 때 선택 항목으로 자동 스크롤
  useEffect(() => {
    if (!open || !listRef.current) return;
    const selectedEl = listRef.current.querySelector("[data-selected='true']");
    if (selectedEl) {
      selectedEl.scrollIntoView({ block: "center" });
    }
  }, [open]);

  return (
    <div ref={wrapRef} className="relative flex-1">
      <button
        type="button"
        onClick={() => setOpen(!open)}
        className="w-full py-2 text-base font-semibold text-gray-900 text-center"
      >
        {value}
      </button>
      {open && (
        <div
          ref={listRef}
          className="absolute left-0 right-0 top-full mt-2 z-20 max-h-48 overflow-y-auto bg-white rounded-2xl border border-gray-200 shadow-lg py-1"
        >
          {options.map((opt) => {
            const selected = opt === value;
            return (
              <button
                key={opt}
                type="button"
                data-selected={selected}
                onClick={() => {
                  onChange(opt);
                  setOpen(false);
                }}
                className={`block w-full px-3 py-2 text-center text-base transition ${
                  selected
                    ? "bg-primary-light text-primary font-bold"
                    : "text-gray-700 hover:bg-gray-50"
                }`}
              >
                {opt}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
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
