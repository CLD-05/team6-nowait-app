import { NavLink, useLocation } from "react-router-dom";

/* 손님 화면에서 노출. 로그인/회원가입/상세/점주대시보드 등에선 숨김 */
const VISIBLE_PATHS = ["/home", "/my-waiting", "/me"];

export default function BottomNav() {
  const location = useLocation();

  if (!VISIBLE_PATHS.includes(location.pathname)) return null;

  return (
    <nav className="fixed bottom-0 left-0 right-0 z-30">
      <div className="max-w-[430px] mx-auto bg-white border-t border-gray-100">
        <div className="grid grid-cols-3">
          <Tab to="/home" label="홈" icon={<HomeIcon />} />
          <Tab to="/my-waiting" label="내 웨이팅" icon={<ListIcon />} />
          <Tab to="/me" label="내 정보" icon={<UserIcon />} />
        </div>
      </div>
    </nav>
  );
}

function Tab({ to, label, icon }) {
  return (
    <NavLink
      to={to}
      className={({ isActive }) =>
        `flex flex-col items-center gap-0.5 py-3 ${
          isActive ? "text-primary" : "text-gray-400"
        }`
      }
    >
      {icon}
      <span className="text-[10px] font-bold">{label}</span>
    </NavLink>
  );
}

function HomeIcon() {
  return (
    <svg
      className="w-5 h-5"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
      <path d="M9 22V12h6v10" />
    </svg>
  );
}

function ListIcon() {
  return (
    <svg
      className="w-5 h-5"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M8 6h13M8 12h13M8 18h13" />
      <path d="M3 6h.01M3 12h.01M3 18h.01" />
    </svg>
  );
}

function UserIcon() {
  return (
    <svg
      className="w-5 h-5"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
      <circle cx="12" cy="7" r="4" />
    </svg>
  );
}
