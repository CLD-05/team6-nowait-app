import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { login } from "../api/auth";
import { tokenStorage, userStorage } from "../utils/storage";

export default function LoginPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      const res = await login({ email, password });

      tokenStorage.set(res.accessToken);
      userStorage.set({
        userId: res.userId,
        email: res.email,
        name: res.name,
        role: res.role,
      });

      if (res.role === "OWNER") {
        navigate("/owner");
      } else {
        navigate("/home");
      }
    } catch (err) {
      setError(err.response?.data?.message || "로그인에 실패했습니다.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex flex-col px-6 pt-16 pb-8 min-h-screen relative overflow-hidden">
      {/* 데코: 배경 그라데이션 블롭 */}
      <div className="pointer-events-none absolute -top-20 -right-20 w-72 h-72 rounded-full bg-primary/20 blur-3xl" />
      <div className="pointer-events-none absolute -top-10 -left-16 w-56 h-56 rounded-full bg-primary-light blur-3xl" />

      {/* 로고/타이틀 영역 */}
      <div className="relative mb-16">
        <h1
          className="font-brand text-7xl font-black italic text-primary mb-3 tracking-tighter leading-none"
          style={{ WebkitTextStroke: "2px currentColor" }}
        >
          NoWait
        </h1>
        <p className="text-gray-600 text-base font-medium">
          기다림 없는 식사의 시작
        </p>
      </div>

      {/* 폼 */}
      <form onSubmit={handleSubmit} className="relative flex flex-col gap-3">
        <input
          type="email"
          placeholder="이메일"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
          className="w-full px-4 py-4 bg-gray-100 rounded-2xl text-base outline-none focus:bg-white focus:ring-2 focus:ring-primary transition"
        />
        <input
          type="password"
          placeholder="비밀번호"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
          className="w-full px-4 py-4 bg-gray-100 rounded-2xl text-base outline-none focus:bg-white focus:ring-2 focus:ring-primary transition"
        />

        {error && (
          <p className="text-red-500 text-sm px-1">{error}</p>
        )}

        <button
          type="submit"
          disabled={loading}
          className="w-full py-4 mt-3 bg-primary text-white rounded-2xl font-bold text-base disabled:opacity-50 active:bg-primary-dark shadow-lg shadow-primary/30 transition"
        >
          {loading ? "로그인 중..." : "로그인"}
        </button>
      </form>

      {/* 구분선 */}
      <div className="relative flex items-center my-10">
        <div className="flex-1 h-px bg-gray-200" />
        <span className="px-3 text-xs text-gray-400">또는</span>
        <div className="flex-1 h-px bg-gray-200" />
      </div>

      {/* 하단 링크 */}
      <div className="relative flex justify-center gap-4 text-sm text-gray-500">
        <Link to="/signup" className="hover:text-primary font-medium">회원가입</Link>
        <span className="text-gray-300">|</span>
        <Link to="/signup/owner" className="hover:text-primary font-medium">점주 회원가입</Link>
      </div>
    </div>
  );
}
