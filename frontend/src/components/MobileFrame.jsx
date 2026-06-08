/*
  모바일 화면 프레임.
  PC에서도 모바일 사이즈로 가운데 정렬해서 보여줌.
  실제 모바일에서는 전체 화면 차지.
*/
export default function MobileFrame({ children }) {
  return (
    <div className="min-h-screen flex justify-center bg-gray-100">
      <div className="w-full max-w-[430px] min-h-screen bg-white shadow-sm relative flex flex-col">
        {children}
      </div>
    </div>
  );
}
