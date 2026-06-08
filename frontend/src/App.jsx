import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import MobileFrame from "./components/MobileFrame";
import BottomNav from "./components/BottomNav";
import LoginPage from "./pages/LoginPage";
import SignupPage from "./pages/SignupPage";
import SignupOwnerPage from "./pages/SignupOwnerPage";
import HomePage from "./pages/HomePage";
import RestaurantPage from "./pages/RestaurantPage";
import MyWaitingPage from "./pages/MyWaitingPage";
import MyPage from "./pages/MyPage";
import OwnerPage from "./pages/OwnerPage";
import ReservationPage from "./pages/ReservationPage";
import MyReservationsPage from "./pages/MyReservationsPage";
import EditRestaurantPage from "./pages/EditRestaurantPage";

function App() {
  return (
    <BrowserRouter>
      <MobileFrame>
        <Routes>
          <Route path="/" element={<Navigate to="/home" replace />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/signup" element={<SignupPage />} />
          <Route path="/signup/owner" element={<SignupOwnerPage />} />
          <Route path="/home" element={<HomePage />} />
          <Route path="/restaurants/:id" element={<RestaurantPage />} />
          <Route path="/restaurants/:id/reserve" element={<ReservationPage />} />
          <Route path="/my-waiting" element={<MyWaitingPage />} />
          <Route path="/my-reservations" element={<MyReservationsPage />} />
          <Route path="/me" element={<MyPage />} />
          <Route path="/owner" element={<OwnerPage />} />
          <Route path="/owner/edit-restaurant" element={<EditRestaurantPage />} />
        </Routes>
        <BottomNav />
      </MobileFrame>
    </BrowserRouter>
  );
}

export default App;
