import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { tokenStorage, userStorage, logout } from "../utils/storage";
import { updateMe } from "../api/auth";

export default function MyPage() {
  const navigate = useNavigate();
  const [user, setUser] = useState(() => userStorage.get());
  const [openEditName, setOpenEditName] = useState(false);
  const [openWithdraw, setOpenWithdraw] = useState(false);
  const [toast, setToast] = useState("");
  const isLoggedIn = !!tokenStorage.get();

  useEffect(() => {
    if (!isLoggedIn) navigate("/login");
  }, [isLoggedIn, navigate]);

  if (!user) return null;

  const handleLogout = () => {
    logout();
    navigate("/login");
  };

  const handleNameUpdated = (newName) => {
    const updated = { ...user, name: newName };
    userStorage.set(updated);
    setUser(updated);
    setOpenEditName(false);
    showToast("이름이 변경됐어요");
  };

  const showToast = (msg) => {
    setToast(msg);
    setTimeout(() => setToast(""), 1800);
  };

  return (
    <div className="flex flex-col min-h-screen pb-24 bg-gray-50">
      {/* 헤더 */}
      <div className="px-5 pt-6 pb-6 bg-white border-b border-gray-100">
        <button
          onClick={() => navigate(-1)}
          className="text-2xl text-gray-700 mb-4"
          aria-label="뒤로가기"
        >
          ←
        </button>
        <h2 className="text-2xl font-extrabold text-gray-900 mb-6 tracking-tight">
          내 정보
        </h2>

        <div className="flex items-center gap-4">
          <div className="w-16 h-16 rounded-full bg-primary-light flex items-center justify-center">
            <span className="text-2xl font-extrabold text-primary">
              {user.name?.[0] || "U"}
            </span>
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-lg font-bold text-gray-900 truncate">
              {user.name}
            </p>
            <p className="text-sm text-gray-500 truncate">{user.email}</p>
          </div>
          <span className="px-2 py-0.5 bg-primary-light text-primary rounded-full text-[11px] font-bold">
            {user.role === "OWNER" ? "점주" : "일반"}
          </span>
        </div>
      </div>

      {/* 계정 관리 */}
      <div className="mx-5 mt-5 bg-white rounded-2xl overflow-hidden border border-gray-100">
        <MenuRow label="이름 수정" onClick={() => setOpenEditName(true)} />
        <MenuRow
          label="회원 탈퇴"
          onClick={() => setOpenWithdraw(true)}
          last
          danger
        />
      </div>

      {/* 메뉴 리스트 */}
      <div className="mx-5 mt-3 bg-white rounded-2xl overflow-hidden border border-gray-100">
        <MenuRow
          label="내 웨이팅 보기"
          onClick={() => navigate("/my-waiting")}
        />
        <MenuRow
          label="내 예약 보기"
          onClick={() => navigate("/my-reservations")}
        />
        <MenuRow label="식당 둘러보기" onClick={() => navigate("/home")} />
        {user.role === "OWNER" && (
          <MenuRow
            label="점주 대시보드"
            onClick={() => navigate("/owner")}
            last
          />
        )}
      </div>

      <div className="mx-5 mt-3 bg-white rounded-2xl overflow-hidden border border-gray-100">
        <MenuRow label="공지사항" disabled />
        <MenuRow label="고객센터" disabled />
        <MenuRow label="이용약관" disabled last />
      </div>

      {/* 로그아웃 */}
      <div className="mx-5 mt-6">
        <button
          onClick={handleLogout}
          className="w-full py-4 bg-white border border-gray-200 text-gray-600 rounded-2xl font-bold text-sm"
        >
          로그아웃
        </button>
      </div>

      {/* 앱 정보 */}
      <p className="mt-auto pt-10 text-center text-[11px] text-gray-300">
        NoWait v0.1.0
      </p>

      {/* 이름 수정 바텀시트 */}
      {openEditName && (
        <EditNameSheet
          currentName={user.name}
          onClose={() => setOpenEditName(false)}
          onUpdated={handleNameUpdated}
          onError={showToast}
        />
      )}

      {/* 탈퇴 바텀시트 (프레임 — 백엔드 미구현) */}
      {openWithdraw && (
        <WithdrawSheet
          onClose={() => setOpenWithdraw(false)}
          onSubmit={() => {
            showToast("회원 탈퇴 API 미구현");
            setOpenWithdraw(false);
          }}
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

function EditNameSheet({ currentName, onClose, onUpdated, onError }) {
  const [name, setName] = useState(currentName);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError("");
    const trimmed = name.trim();
    if (!trimmed) {
      setError("이름을 입력해주세요");
      return;
    }
    if (trimmed.length > 50) {
      setError("이름은 50자 이하여야 해요");
      return;
    }
    if (trimmed === currentName) {
      onClose();
      return;
    }

    setSubmitting(true);
    try {
      const res = await updateMe({ name: trimmed });
      onUpdated(res.name);
    } catch (err) {
      const msg = err.response?.data?.message || "변경에 실패했어요";
      setError(msg);
      onError(msg);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-40 flex justify-center items-end">
      <div
        className="absolute inset-0 bg-black/40"
        onClick={submitting ? () => {} : onClose}
      />
      <div className="relative w-full max-w-[430px] bg-white rounded-t-3xl pt-3 shadow-2xl animate-slide-up">
        <div className="w-12 h-1 bg-gray-300 rounded-full mx-auto mb-3" />
        <form onSubmit={handleSubmit} className="px-5 pb-6">
          <h3 className="text-lg font-bold text-gray-900 mb-1">이름 수정</h3>
          <p className="text-sm text-gray-500 mb-5">
            앱에 표시될 이름을 바꿔주세요
          </p>

          <input
            type="text"
            placeholder="새 이름"
            value={name}
            onChange={(e) => setName(e.target.value)}
            maxLength={50}
            autoFocus
            className={`w-full px-4 py-4 rounded-2xl text-base outline-none transition ${
              error
                ? "bg-red-50 ring-2 ring-red-300 focus:bg-white"
                : "bg-gray-100 focus:bg-white focus:ring-2 focus:ring-primary"
            }`}
          />
          {error && (
            <p className="text-xs text-red-500 px-1 mt-1.5">{error}</p>
          )}

          <button
            type="submit"
            disabled={submitting}
            className="w-full mt-4 py-4 bg-primary text-white rounded-2xl font-bold text-base disabled:opacity-50 shadow-lg shadow-primary/30"
          >
            {submitting ? "변경 중..." : "저장"}
          </button>
        </form>
      </div>
    </div>
  );
}

function MenuRow({ label, onClick, disabled, last, danger }) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      className={`w-full flex items-center justify-between px-5 py-4 ${
        last ? "" : "border-b border-gray-100"
      } ${disabled ? "opacity-50" : "active:bg-gray-50"}`}
    >
      <span
        className={`text-sm font-medium ${
          danger ? "text-red-500" : "text-gray-800"
        }`}
      >
        {label}
      </span>
      <ChevronRightIcon
        className={`w-4 h-4 ${danger ? "text-red-300" : "text-gray-400"}`}
      />
    </button>
  );
}

function WithdrawSheet({ onClose, onSubmit }) {
  const [agreed, setAgreed] = useState(false);

  return (
    <div className="fixed inset-0 z-40 flex justify-center items-end">
      <div className="absolute inset-0 bg-black/40" onClick={onClose} />
      <div className="relative w-full max-w-[430px] bg-white rounded-t-3xl pt-3 shadow-2xl animate-slide-up">
        <div className="w-12 h-1 bg-gray-300 rounded-full mx-auto mb-3" />
        <div className="px-5 pb-6">
          <h3 className="text-lg font-bold text-gray-900 mb-1">
            정말 탈퇴하시겠어요?
          </h3>
          <p className="text-sm text-gray-500 mb-5">
            탈퇴 시 아래 정보가 모두 삭제되며 복구할 수 없어요
          </p>

          <div className="bg-gray-50 rounded-2xl px-4 py-4 mb-4 flex flex-col gap-2">
            <Bullet>예약 내역 · 웨이팅 내역</Bullet>
            <Bullet>등록한 식당 정보 (점주 회원)</Bullet>
            <Bullet>계정 / 프로필 정보</Bullet>
          </div>

          <p className="text-[11px] text-gray-400 mb-4 text-center">
            ⚠ DELETE /api/users/me 백엔드 미구현. 추가 후 실제 동작.
          </p>

          <label className="flex items-center gap-2.5 mb-5 cursor-pointer select-none">
            <button
              type="button"
              onClick={() => setAgreed(!agreed)}
              className={`w-5 h-5 rounded-md flex items-center justify-center transition ${
                agreed
                  ? "bg-red-500"
                  : "bg-white border border-gray-300"
              }`}
            >
              {agreed && (
                <svg
                  className="w-3 h-3 text-white"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="3.5"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                >
                  <path d="M20 6 9 17l-5-5" />
                </svg>
              )}
            </button>
            <span className="text-sm text-gray-700">
              위 내용을 확인했고 탈퇴에 동의합니다
            </span>
          </label>

          <div className="grid grid-cols-2 gap-3">
            <button
              type="button"
              onClick={onClose}
              className="py-4 bg-gray-100 text-gray-700 rounded-2xl font-bold text-sm"
            >
              돌아가기
            </button>
            <button
              type="button"
              onClick={onSubmit}
              disabled={!agreed}
              className="py-4 bg-red-500 text-white rounded-2xl font-bold text-sm disabled:opacity-40 shadow-lg shadow-red-300/30"
            >
              탈퇴하기
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

function Bullet({ children }) {
  return (
    <div className="flex items-start gap-2 text-xs text-gray-600">
      <span className="text-gray-400 mt-0.5">•</span>
      <span>{children}</span>
    </div>
  );
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
