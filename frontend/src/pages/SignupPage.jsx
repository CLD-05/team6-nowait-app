import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { signUp } from "../api/auth";
import { tokenStorage, userStorage } from "../utils/storage";

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export default function SignupPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [passwordConfirm, setPasswordConfirm] = useState("");
  const [name, setName] = useState("");
  const [loading, setLoading] = useState(false);
  const [errors, setErrors] = useState({});

  const validate = () => {
    const e = {};
    if (!name.trim()) e.name = "이름을 입력해주세요";
    if (!email.trim()) e.email = "이메일을 입력해주세요";
    else if (!EMAIL_REGEX.test(email)) e.email = "올바른 이메일을 입력해주세요";

    if (!password) e.password = "비밀번호를 입력해주세요";
    else if (password.length < 8)
      e.password = `8자 이상 입력해주세요 (현재 ${password.length}자)`;

    if (!passwordConfirm) e.passwordConfirm = "비밀번호를 다시 입력해주세요";
    else if (password !== passwordConfirm)
      e.passwordConfirm = "비밀번호가 일치하지 않습니다";

    return e;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    const v = validate();
    setErrors(v);
    if (Object.keys(v).length > 0) return;

    setLoading(true);
    try {
      const res = await signUp({ email, password, name });
      tokenStorage.set(res.accessToken);
      userStorage.set({
        userId: res.userId,
        email: res.email,
        name: res.name,
        role: res.role,
      });
      navigate("/home");
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

      <div className="relative mb-8">
        <button
          onClick={() => navigate(-1)}
          className="text-2xl text-gray-700 mb-6"
          aria-label="뒤로가기"
        >
          ←
        </button>
        <h2 className="text-2xl font-bold text-gray-900 mb-1">회원가입</h2>
        <p className="text-gray-400 text-xs">계정을 만들고 웨이팅을 시작하세요</p>
      </div>

      <form onSubmit={handleSubmit} className="relative flex flex-col gap-3" noValidate>
        <Field label="이름" error={errors.name}>
          <input
            type="text"
            placeholder="이름을 입력해주세요"
            value={name}
            onChange={(e) => setName(e.target.value)}
            maxLength={50}
            className={inputClass(errors.name)}
          />
        </Field>

        <Field label="이메일" error={errors.email}>
          <input
            type="email"
            placeholder="이메일을 입력해주세요"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className={inputClass(errors.email)}
          />
        </Field>

        <Field label="비밀번호" error={errors.password}>
          <input
            type="password"
            placeholder="비밀번호를 입력해주세요"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className={inputClass(errors.password)}
          />
        </Field>

        <Field label="비밀번호 확인" error={errors.passwordConfirm}>
          <input
            type="password"
            placeholder="비밀번호를 다시 입력해주세요"
            value={passwordConfirm}
            onChange={(e) => setPasswordConfirm(e.target.value)}
            className={inputClass(errors.passwordConfirm)}
          />
        </Field>

        {errors.submit && (
          <p className="text-red-500 text-sm px-1 mt-1">{errors.submit}</p>
        )}

        <button
          type="submit"
          disabled={loading}
          className="w-full py-4 mt-4 bg-primary text-white rounded-2xl font-bold text-base disabled:opacity-50 active:bg-primary-dark shadow-lg shadow-primary/30 transition"
        >
          {loading ? "가입 중..." : "회원가입"}
        </button>
      </form>

      <div className="relative mt-auto pt-10 text-center text-sm text-gray-500">
        이미 계정이 있으신가요?{" "}
        <Link to="/login" className="text-primary font-semibold">로그인</Link>
      </div>
    </div>
  );
}

function inputClass(hasError) {
  const base = "w-full px-4 py-4 rounded-2xl text-base outline-none transition";
  return hasError
    ? `${base} bg-red-50 ring-2 ring-red-300 focus:bg-white`
    : `${base} bg-gray-100 focus:bg-white focus:ring-2 focus:ring-primary`;
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
