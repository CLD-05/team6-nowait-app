import { BrowserRouter, Routes, Route } from 'react-router-dom';
import MainPage from './pages/MainPage';
import AuthPage from './pages/AuthPage';
import RestaurantPage from './pages/RestaurantPage';
import MyPage from './pages/MyPage';
import OwnerPage from './pages/OwnerPage';
import './index.css';
import LandingPage from './pages/LandingPage';
import WaitingStatusPage from './pages/WaitingStatusPage';

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* 메인 페이지 */}
        <Route path="/" element={<MainPage />} />

        {/* 랜딩 페이지 */}
        <Route path="/landing" element={<LandingPage />} />

        {/* 웨이팅 상세페이지 */}
        <Route path="/waiting/:id" element={<WaitingStatusPage />} />

        {/* 로그인/회원가입 */}
        <Route path="/auth" element={<AuthPage />} />

        {/* 식당 상세 */}
        <Route path="/restaurant/:id" element={<RestaurantPage />} />

        {/* 마이페이지 */}
        <Route path="/mypage" element={<MyPage />} />

        {/* 점주 대시보드 */}
        <Route path="/owner" element={<OwnerPage />} />
      </Routes>
    </BrowserRouter>
  );
}