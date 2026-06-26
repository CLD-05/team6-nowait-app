import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import Header from '../components/Header';
import { API_BASE, ApiError, request, resolveImageUrl } from '../lib/api';
import './StoreManagementPage.css';

type Category = 'KOREAN' | 'JAPANESE' | 'CHINESE' | 'WESTERN' | 'ASIAN';
type RestaurantStatus = 'OPEN' | 'CLOSED' | 'PERMANENTLY_CLOSED';
type Tab = 'basic' | 'operation' | 'hours' | 'amenities' | 'slot' | 'waiting';

type RestaurantForm = {
  name: string;
  category: Category;
  address: string;
  phoneNumber: string;
  description: string;
  imageUrl: string;
  mainMenuName: string;
  parkingAvailable: 'Y' | 'N';
  wifiAvailable: 'Y' | 'N';
  multilingualMenuAvailable: 'Y' | 'N';
  status: RestaurantStatus;
  reservationAvailable: 'Y' | 'N';
  waitingAvailable: 'Y' | 'N';
};

type RestaurantResponse = RestaurantForm & { id: number };

const EMPTY_FORM: RestaurantForm = {
  name: '',
  category: 'KOREAN',
  address: '',
  phoneNumber: '',
  description: '',
  imageUrl: '',
  mainMenuName: '',
  parkingAvailable: 'N',
  wifiAvailable: 'N',
  multilingualMenuAvailable: 'N',
  status: 'CLOSED',
  reservationAvailable: 'Y',
  waitingAvailable: 'Y',
};

const CATEGORY_LABEL: Record<Category, string> = {
  KOREAN: '한식',
  JAPANESE: '일식',
  CHINESE: '중식',
  WESTERN: '양식',
  ASIAN: '아시안',
};

const STATUS_LABEL: Record<RestaurantStatus, string> = {
  OPEN: '영업 중',
  CLOSED: '영업 종료',
  PERMANENTLY_CLOSED: '폐업',
};

type SlotInfo = {
  slotId: number;
  slotTime: string;
  totalCount: number;
  remainCount: number;
  available: boolean;
};

type SlotForm = {
  slotDate: string;
  slotTime: string;
  totalCount: number;
  minHeadcount: number;
  maxHeadcount: number;
};

type WaitingSession = {
  sessionId: number;
  restaurantId: number;
  sessionDate: string;
  status: 'OPEN' | 'PAUSED' | 'CLOSED';
  maxWaitingCount: number;
  currentCount: number;
};

const WAITING_SESSION_LABEL: Record<string, string> = {
  OPEN: '운영 중',
  PAUSED: '일시 정지',
  CLOSED: '마감',
};

/**
 * 오늘 날짜를 "YYYY-MM-DD" 형태로 반환.
 * toISOString()은 UTC 기준이라 KST 00:00~08:59 사이에는 하루 전 날짜를 반환해
 * 백엔드(KST 기준 LocalDate.now)와 어긋난다. 로컬 캘린더 날짜를 그대로 사용한다.
 */
function getTodayStr() {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

const DAY_LABEL: Record<string, string> = {
  MON: '월요일', TUE: '화요일', WED: '수요일', THU: '목요일',
  FRI: '금요일', SAT: '토요일', SUN: '일요일',
};

const DEFAULT_HOURS = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'].map(dayOfWeek => ({
  dayOfWeek,
  openTime: '11:00',
  closeTime: '22:00',
  isRegularHoliday: 'N',
}));

type HourRow = { dayOfWeek: string; openTime: string; closeTime: string; isRegularHoliday: string };

function readOwnerRole() {
  try {
    return JSON.parse(localStorage.getItem('nowait_user') || '{}').role === 'OWNER';
  } catch {
    return false;
  }
}

export default function StoreManagementPage() {
  const navigate = useNavigate();
  const [tab, setTab] = useState<Tab>('basic');
  const [form, setForm] = useState<RestaurantForm>(EMPTY_FORM);
  const [restaurantId, setRestaurantId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [statusMenuOpen, setStatusMenuOpen] = useState(false);
  const [categoryMenuOpen, setCategoryMenuOpen] = useState(false);

  // 슬롯 관리
  const [slotDate, setSlotDate] = useState(getTodayStr);
  const [slots, setSlots] = useState<SlotInfo[]>([]);
  const [slotLoading, setSlotLoading] = useState(false);
  const [slotForm, setSlotForm] = useState<SlotForm>({
    slotDate: getTodayStr(), slotTime: '12:00',
    totalCount: 4, minHeadcount: 1, maxHeadcount: 8,
  });
  const [editingSlot, setEditingSlot] = useState<{ slotId: number; totalCount: number; minHeadcount: number; maxHeadcount: number } | null>(null);
  const [slotMsg, setSlotMsg] = useState('');

  // 영업시간
  const [hours, setHours] = useState<HourRow[]>(DEFAULT_HOURS);
  const [hoursMsg, setHoursMsg] = useState('');
  const [hoursSaving, setHoursSaving] = useState(false);

  // 이미지 업로드
  const [imageUploading, setImageUploading] = useState(false);
  const [imageMsg, setImageMsg] = useState('');
  const imageInputRef = useRef<HTMLInputElement>(null);
  // 매장을 아직 등록하기 전(restaurantId 없음)에 골라둔 이미지 — 등록 직후 자동 업로드된다.
  const [pendingImageFile, setPendingImageFile] = useState<File | null>(null);
  const [pendingPreviewUrl, setPendingPreviewUrl] = useState('');

  // 웨이팅 세션
  const [waitingSession, setWaitingSession] = useState<WaitingSession | null>(null);
  const [sessionLoading, setSessionLoading] = useState(false);
  const [maxWaitingCount, setMaxWaitingCount] = useState(30);
  const [sessionMsg, setSessionMsg] = useState('');

  useEffect(() => {
    if (!localStorage.getItem('nowait_user')) {
      navigate('/auth', { replace: true });
      return;
    }
    if (!readOwnerRole()) {
      navigate('/', { replace: true });
      return;
    }

    async function loadRestaurant() {
      try {
        const data = await request<RestaurantResponse>('/restaurants/mine');
        setRestaurantId(data.id);
        setForm({
          name: data.name || '',
          category: data.category || 'KOREAN',
          address: data.address || '',
          phoneNumber: data.phoneNumber || '',
          description: data.description || '',
          imageUrl: data.imageUrl || '',
          mainMenuName: data.mainMenuName || '',
          parkingAvailable: data.parkingAvailable || 'N',
          wifiAvailable: data.wifiAvailable || 'N',
          multilingualMenuAvailable: data.multilingualMenuAvailable || 'N',
          status: data.status || 'CLOSED',
          reservationAvailable: data.reservationAvailable || 'Y',
          waitingAvailable: data.waitingAvailable || 'Y',
        });
      } catch (loadError) {
        if (!(loadError instanceof ApiError) || loadError.status !== 404) {
          setError(loadError instanceof Error ? loadError.message : '매장 정보를 불러오지 못했습니다.');
        }
      } finally {
        setLoading(false);
      }
    }

    loadRestaurant();
  }, [navigate]);

  // 슬롯 탭 진입 시 로드
  useEffect(() => {
    if (tab === 'slot' && restaurantId) void fetchSlots(slotDate);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tab, restaurantId]);

  // 영업시간 탭 진입 시 로드
  useEffect(() => {
    if (tab === 'hours' && restaurantId) void fetchHours();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tab, restaurantId]);

  // 웨이팅 세션 탭 진입 시 로드
  useEffect(() => {
    if (tab === 'waiting' && restaurantId) void fetchWaitingSession();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tab, restaurantId]);

  async function fetchHours() {
    if (!restaurantId) return;
    try {
      const data = await request<HourRow[]>(`/owners/restaurants/${restaurantId}/hours`);
      if (data && data.length > 0) {
        // API가 반환한 순서가 불규칙할 수 있으므로 요일 순서대로 정렬
        const order = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'];
        const merged = order.map(day => {
          const found = data.find(h => h.dayOfWeek === day);
          return found
            ? { ...found, openTime: found.openTime?.slice(0, 5) ?? '11:00', closeTime: found.closeTime?.slice(0, 5) ?? '22:00' }
            : { dayOfWeek: day, openTime: '11:00', closeTime: '22:00', isRegularHoliday: 'N' };
        });
        setHours(merged);
      }
    } catch {
      // 데이터 없으면 기본값 유지
    }
  }

  async function handleSaveHours() {
    if (!restaurantId) return;
    setHoursSaving(true);
    setHoursMsg('');
    try {
      await request(`/owners/restaurants/${restaurantId}/hours`, {
        method: 'POST',
        body: JSON.stringify(
          hours.map(h => ({
            dayOfWeek: h.dayOfWeek,
            openTime: h.isRegularHoliday === 'Y' ? null : `${h.openTime}:00`,
            closeTime: h.isRegularHoliday === 'Y' ? null : `${h.closeTime}:00`,
            isRegularHoliday: h.isRegularHoliday,
          }))
        ),
      });
      setHoursMsg('영업시간이 저장됐어요.');
    } catch (err) {
      setHoursMsg(err instanceof Error ? err.message : '저장에 실패했습니다.');
    } finally {
      setHoursSaving(false);
    }
  }

  function updateHour(day: string, field: keyof HourRow, value: string) {
    setHours(prev => prev.map(h => h.dayOfWeek === day ? { ...h, [field]: value } : h));
    setHoursMsg('');
  }

  async function fetchSlots(date: string) {
    if (!restaurantId) return;
    setSlotLoading(true);
    try {
      const data = await request<{ slots?: SlotInfo[] }>(`/restaurants/${restaurantId}/slots?date=${date}`);
      setSlots(data.slots ?? []);
    } catch {
      setSlots([]);
    } finally {
      setSlotLoading(false);
    }
  }

  async function handleCreateSlot() {
    if (!restaurantId) return;
    setSlotMsg('');
    try {
      await request(`/owner/restaurants/${restaurantId}/slots`, {
        method: 'POST',
        body: JSON.stringify({
          slotDate: slotForm.slotDate,
          slotTime: slotForm.slotTime + ':00',
          totalCount: slotForm.totalCount,
          minHeadcount: slotForm.minHeadcount,
          maxHeadcount: slotForm.maxHeadcount,
        }),
      });
      setSlotMsg('슬롯이 추가됐어요.');
      await fetchSlots(slotForm.slotDate === slotDate ? slotDate : slotForm.slotDate);
      if (slotForm.slotDate !== slotDate) setSlotDate(slotForm.slotDate);
    } catch (err) {
      setSlotMsg(err instanceof Error ? err.message : '슬롯 추가에 실패했습니다.');
    }
  }

  async function handleUpdateSlot() {
    if (!editingSlot) return;
    setSlotMsg('');
    try {
      await request(`/owner/slots/${editingSlot.slotId}`, {
        method: 'PATCH',
        body: JSON.stringify({
          totalCount: editingSlot.totalCount,
          minHeadcount: editingSlot.minHeadcount,
          maxHeadcount: editingSlot.maxHeadcount,
        }),
      });
      setSlotMsg('슬롯이 수정됐어요.');
      setEditingSlot(null);
      await fetchSlots(slotDate);
    } catch (err) {
      setSlotMsg(err instanceof Error ? err.message : '슬롯 수정에 실패했습니다.');
    }
  }

  async function handleDeleteSlot(slotId: number) {
    if (!confirm('슬롯을 삭제할까요?')) return;
    setSlotMsg('');
    try {
      await request(`/owner/slots/${slotId}`, { method: 'DELETE' });
      setSlots(prev => prev.filter(s => s.slotId !== slotId));
      setSlotMsg('슬롯이 삭제됐어요.');
    } catch (err) {
      setSlotMsg(err instanceof Error ? err.message : '슬롯 삭제에 실패했습니다.');
    }
  }

  async function fetchWaitingSession() {
    if (!restaurantId) return;
    try {
      const data = await request<WaitingSession>(`/restaurants/${restaurantId}/waiting-session`);
      setWaitingSession(data);
    } catch {
      setWaitingSession(null);
    }
  }

  async function handleOpenSession() {
    if (!restaurantId) return;
    setSessionLoading(true);
    setSessionMsg('');
    try {
      const data = await request<WaitingSession>(`/owners/restaurants/${restaurantId}/waiting-sessions`, {
        method: 'POST',
        body: JSON.stringify({ maxWaitingCount }),
      });
      setWaitingSession(data);
      setSessionMsg('웨이팅 세션이 오픈됐어요.');
    } catch (err) {
      setSessionMsg(err instanceof Error ? err.message : '세션 오픈에 실패했습니다.');
    } finally {
      setSessionLoading(false);
    }
  }

  async function handleSessionAction(action: 'pause' | 'resume' | 'close') {
    if (!waitingSession) return;
    setSessionLoading(true);
    setSessionMsg('');
    try {
      const data = await request<WaitingSession>(`/owners/waiting-sessions/${waitingSession.sessionId}/${action}`, { method: 'PATCH' });
      setWaitingSession(data);
      const label = action === 'pause' ? '일시 정지' : action === 'resume' ? '재개' : '마감';
      setSessionMsg(`웨이팅 세션이 ${label}됐어요.`);
    } catch (err) {
      setSessionMsg(err instanceof Error ? err.message : '처리에 실패했습니다.');
    } finally {
      setSessionLoading(false);
    }
  }

  function updateField<K extends keyof RestaurantForm>(key: K, value: RestaurantForm[K]) {
    setForm(current => ({ ...current, [key]: value }));
    setMessage('');
  }

  async function uploadImage(targetRestaurantId: number, file: File) {
    setImageUploading(true);
    setImageMsg('');
    try {
      // 1) Presigned URL 발급
      const presignRes = await fetch(
        `${API_BASE}/images/presigned-url?restaurantId=${targetRestaurantId}&filename=${encodeURIComponent(file.name)}`,
        { method: 'POST', credentials: 'include' }
      );
      if (!presignRes.ok) throw new Error('Presigned URL 발급에 실패했습니다.');
      const { presignedUrl, imageKey } = await presignRes.json() as { presignedUrl: string; imageKey: string };

      // 2) S3 직접 업로드 (S3 presigned URL은 자체 인증이므로 credentials 불필요)
      const uploadRes = await fetch(presignedUrl, {
        method: 'PUT',
        headers: { 'Content-Type': file.type },
        body: file,
      });
      if (!uploadRes.ok) throw new Error('S3 업로드에 실패했습니다.');

      // 3) complete API 호출 → DB에 S3 URL 저장
      const completeRes = await fetch(`${API_BASE}/images/complete`, {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ restaurantId: targetRestaurantId, imageKey }),
      });
      if (!completeRes.ok) throw new Error('이미지 저장에 실패했습니다.');
      const { imageUrl } = await completeRes.json() as { imageUrl: string };

      // 4) 화면에 반영
      updateField('imageUrl', imageUrl);
      setImageMsg('이미지가 업로드되었습니다.');
    } catch (err) {
      setImageMsg(err instanceof Error ? err.message : '이미지 업로드 중 오류가 발생했습니다.');
    } finally {
      setImageUploading(false);
    }
  }

  /*
   * 매장 등록 전(restaurantId 없음)에는 업로드 API 자체가 restaurantId를 필수로 받기 때문에
   * 바로 올릴 수 없다. 대신 파일을 잠시 들고 있다가(로컬 미리보기만 표시) "매장 등록" 제출
   * 직후 새로 발급된 restaurantId로 자동 업로드한다 (handleSubmit 참고).
   */
  function handleImagePick(file: File) {
    if (restaurantId) {
      void uploadImage(restaurantId, file);
      return;
    }
    setPendingImageFile(file);
    setPendingPreviewUrl(URL.createObjectURL(file));
    setImageMsg('매장 등록을 완료하면 이미지가 함께 업로드됩니다.');
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError('');
    setMessage('');

    if (!form.name.trim() || !form.address.trim()) {
      setTab('basic');
      setError('매장명과 주소를 입력해주세요.');
      return;
    }

    setSaving(true);
    try {
      if (restaurantId) {
        await request(`/restaurants/${restaurantId}`, {
          method: 'PUT',
          body: JSON.stringify(form),
        });
        setMessage('매장 정보가 저장되었습니다.');
      } else {
        const id = await request<number>('/restaurants', {
          method: 'POST',
          body: JSON.stringify({ ...form, restaurantHours: DEFAULT_HOURS }),
        });
        setRestaurantId(id);
        setMessage('매장이 등록되었습니다.');

        if (pendingImageFile) {
          const file = pendingImageFile;
          setPendingImageFile(null);
          setPendingPreviewUrl('');
          await uploadImage(id, file);
        }
      }
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : '매장 정보를 저장하지 못했습니다.');
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return (
      <div className="store-page">
        <Header />
        <div className="spinner" aria-label="불러오는 중" />
      </div>
    );
  }

  return (
    <div className="store-page">
      <Header />
      <main className="store-shell">
        <div className="store-heading">
          <div>
            <button type="button" className="store-back" onClick={() => navigate('/owner')}>
              ← 사장님 페이지
            </button>
            <div className="store-title-row">
              <h1>매장 관리</h1>
              <span className={`store-status store-status-${form.status.toLowerCase()}`}>
                {restaurantId ? STATUS_LABEL[form.status] : '등록 전'}
              </span>
            </div>
            <p>{restaurantId ? form.name : '운영할 매장 정보를 등록해주세요.'}</p>
          </div>
          {restaurantId && (
            <button type="button" className="btn btn-white btn-sm" onClick={() => navigate(`/restaurant/${restaurantId}`)}>
              매장 페이지 보기
            </button>
          )}
        </div>

        <form className="store-form-card" onSubmit={handleSubmit}>
          <div className="store-tabs store-tabs-6" role="tablist" aria-label="매장 정보 구분">
            {([
              ['basic', '기본 정보'],
              ['operation', '운영 설정'],
              ['hours', '영업시간'],
              ['amenities', '편의 정보'],
              ['slot', '슬롯 관리'],
              ['waiting', '웨이팅 세션'],
            ] as const).map(([key, label]) => (
              <button
                key={key}
                type="button"
                role="tab"
                aria-selected={tab === key}
                className={tab === key ? 'active' : ''}
                onClick={() => setTab(key)}
              >
                {label}
              </button>
            ))}
          </div>

          <section className="store-panel">
            {tab === 'basic' && (
              <div className="store-form-grid">
                <label className="store-field">
                  <span>매장명 *</span>
                  <input value={form.name} onChange={event => updateField('name', event.target.value)} placeholder="노웨이트 식당" />
                </label>
                <label className="store-field">
                  <span>업종 *</span>
                  <div
                    className="store-category-select"
                    onBlur={event => {
                      if (!event.currentTarget.contains(event.relatedTarget as Node | null)) {
                        setCategoryMenuOpen(false);
                      }
                    }}
                  >
                    <button
                      type="button"
                      className="store-category-trigger"
                      aria-haspopup="listbox"
                      aria-expanded={categoryMenuOpen}
                      onClick={() => setCategoryMenuOpen(open => !open)}
                    >
                      <span>{CATEGORY_LABEL[form.category]}</span>
                      <span className="store-category-arrow" aria-hidden="true" />
                    </button>
                    {categoryMenuOpen && (
                      <div className="store-category-menu" role="listbox" aria-label="업종 선택">
                        {(Object.keys(CATEGORY_LABEL) as Category[]).map(category => (
                          <button
                            key={category}
                            type="button"
                            role="option"
                            aria-selected={form.category === category}
                            className={form.category === category ? 'is-selected' : ''}
                            onClick={() => {
                              updateField('category', category);
                              setCategoryMenuOpen(false);
                            }}
                          >
                            {CATEGORY_LABEL[category]}
                          </button>
                        ))}
                      </div>
                    )}
                  </div>
                </label>
                <label className="store-field store-field-wide">
                  <span>주소 *</span>
                  <input value={form.address} onChange={event => updateField('address', event.target.value)} placeholder="서울특별시 강남구 테헤란로 123" />
                </label>
                <label className="store-field">
                  <span>전화번호</span>
                  <input value={form.phoneNumber} onChange={event => updateField('phoneNumber', event.target.value)} placeholder="02-1234-5678" />
                </label>
                <label className="store-field">
                  <span>대표 메뉴</span>
                  <input value={form.mainMenuName} onChange={event => updateField('mainMenuName', event.target.value)} placeholder="대표 메뉴명" />
                </label>
                <div className="store-field store-field-wide">
                  <span>대표 이미지</span>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                    {(pendingPreviewUrl || form.imageUrl) && (
                      <img
                        src={pendingPreviewUrl || resolveImageUrl(form.imageUrl)}
                        alt="대표 이미지 미리보기"
                        style={{ width: '120px', height: '80px', objectFit: 'cover', borderRadius: '6px', border: '1px solid #e5e7eb' }}
                        onError={e => { (e.target as HTMLImageElement).style.display = 'none'; }}
                      />
                    )}
                    <input
                      ref={imageInputRef}
                      type="file"
                      accept="image/*"
                      style={{ display: 'none' }}
                      onChange={e => {
                        const file = e.target.files?.[0];
                        if (file) handleImagePick(file);
                        e.target.value = '';
                      }}
                    />
                    <button
                      type="button"
                      onClick={() => imageInputRef.current?.click()}
                      disabled={imageUploading}
                      style={{ alignSelf: 'flex-start', padding: '6px 14px', borderRadius: '6px', border: '1px solid #d1d5db', background: '#f9fafb', cursor: 'pointer', fontSize: '14px' }}
                    >
                      {imageUploading ? '업로드 중...' : '이미지 선택'}
                    </button>
                    {imageMsg && <span style={{ fontSize: '13px', color: imageMsg.includes('실패') || imageMsg.includes('오류') ? '#ef4444' : '#22c55e' }}>{imageMsg}</span>}
                  </div>
                </div>
                <label className="store-field store-field-wide">
                  <span>매장 소개</span>
                  <textarea value={form.description} onChange={event => updateField('description', event.target.value)} rows={5} placeholder="매장의 분위기와 대표 메뉴를 소개해주세요." />
                </label>
              </div>
            )}

            {tab === 'operation' && (
              <div className="store-setting-list">
                <div className="store-section-heading">
                  <span className="kicker">OPERATION</span>
                  <h2>운영 상태</h2>
                </div>
                <div className="store-status-control">
                  <span>
                    <strong>현재 영업 상태</strong>
                    <small>{STATUS_LABEL[form.status]}</small>
                  </span>
                  <div
                    className="store-status-picker"
                    onBlur={event => {
                      if (!event.currentTarget.contains(event.relatedTarget as Node | null)) {
                        setStatusMenuOpen(false);
                      }
                    }}
                  >
                    <button
                      type="button"
                      className="store-status-trigger"
                      aria-haspopup="listbox"
                      aria-expanded={statusMenuOpen}
                      onClick={() => setStatusMenuOpen(open => !open)}
                    >
                      <span>{STATUS_LABEL[form.status]}</span>
                      <span className="store-status-arrow" aria-hidden="true" />
                    </button>
                    {statusMenuOpen && (
                      <div className="store-status-menu" role="listbox" aria-label="영업 상태 선택">
                        {(Object.keys(STATUS_LABEL) as RestaurantStatus[]).map(status => (
                          <button
                            key={status}
                            type="button"
                            role="option"
                            aria-selected={form.status === status}
                            className={form.status === status ? 'is-selected' : ''}
                            onClick={async () => {
                              updateField('status', status);
                              setStatusMenuOpen(false);
                              if (restaurantId) {
                                try {
                                  await request(`/restaurants/${restaurantId}/status`, {
                                    method: 'PATCH',
                                    body: JSON.stringify({ status }),
                                  });
                                  setMessage(`영업 상태가 "${STATUS_LABEL[status]}"(으)로 변경됐어요.`);
                                } catch (err) {
                                  setError(err instanceof Error ? err.message : '상태 변경에 실패했습니다.');
                                }
                              }
                            }}
                          >
                            <span className={`store-status-dot store-status-dot-${status.toLowerCase()}`} />
                            {STATUS_LABEL[status]}
                            {form.status === status && <span className="store-status-check">선택</span>}
                          </button>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
                <div className="store-setting-grid">
                  <ToggleRow label="예약 받기" description="예약 기능" checked={form.reservationAvailable === 'Y'} onChange={checked => updateField('reservationAvailable', checked ? 'Y' : 'N')} />
                  <ToggleRow label="웨이팅 받기" description="현장 웨이팅 기능" checked={form.waitingAvailable === 'Y'} onChange={checked => updateField('waitingAvailable', checked ? 'Y' : 'N')} />
                </div>
              </div>
            )}

            {tab === 'hours' && (
              <div className="store-setting-list">
                <div className="store-section-heading">
                  <span className="kicker">HOURS</span>
                  <h2>영업시간 설정</h2>
                </div>
                {!restaurantId ? (
                  <p style={{ color: 'var(--muted)', fontWeight: 700 }}>매장을 먼저 등록해주세요.</p>
                ) : (
                  <>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginBottom: 24 }}>
                      {hours.map(h => (
                        <div key={h.dayOfWeek} style={{
                          display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap',
                          padding: '14px 18px', background: h.isRegularHoliday === 'Y' ? '#f5f5f5' : '#fff',
                          border: '2px solid var(--ink)', borderRadius: 12, boxShadow: '3px 3px 0 var(--ink)',
                        }}>
                          <span style={{ minWidth: 56, fontWeight: 900, fontSize: '0.9rem', color: ['SAT'].includes(h.dayOfWeek) ? '#1d4ed8' : ['SUN'].includes(h.dayOfWeek) ? 'var(--tomato)' : 'var(--ink)' }}>
                            {DAY_LABEL[h.dayOfWeek]}
                          </span>
                          {h.isRegularHoliday === 'Y' ? (
                            <span style={{ fontSize: '0.88rem', fontWeight: 800, color: 'var(--muted)', flex: 1 }}>정기 휴무일</span>
                          ) : (
                            <>
                              <label style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: '0.84rem', fontWeight: 700 }}>
                                오픈
                                <input type="time" value={h.openTime}
                                  style={{ padding: '5px 8px', border: '2px solid var(--ink)', borderRadius: 8, fontFamily: 'inherit', fontWeight: 700 }}
                                  onChange={e => updateHour(h.dayOfWeek, 'openTime', e.target.value)} />
                              </label>
                              <span style={{ color: 'var(--muted)', fontWeight: 800 }}>~</span>
                              <label style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: '0.84rem', fontWeight: 700 }}>
                                마감
                                <input type="time" value={h.closeTime}
                                  style={{ padding: '5px 8px', border: '2px solid var(--ink)', borderRadius: 8, fontFamily: 'inherit', fontWeight: 700 }}
                                  onChange={e => updateHour(h.dayOfWeek, 'closeTime', e.target.value)} />
                              </label>
                            </>
                          )}
                          <label style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: 6, fontSize: '0.82rem', fontWeight: 800, cursor: 'pointer' }}>
                            <input type="checkbox" checked={h.isRegularHoliday === 'Y'}
                              onChange={e => updateHour(h.dayOfWeek, 'isRegularHoliday', e.target.checked ? 'Y' : 'N')} />
                            정기 휴무
                          </label>
                        </div>
                      ))}
                    </div>
                    <button type="button" className="btn btn-tomato btn-sm" disabled={hoursSaving}
                      onClick={() => void handleSaveHours()}>
                      {hoursSaving ? '저장 중...' : '영업시간 저장'}
                    </button>
                    {hoursMsg && (
                      <p style={{ marginTop: 12, fontWeight: 800, color: hoursMsg.includes('실패') ? 'var(--tomato)' : '#087451' }}>{hoursMsg}</p>
                    )}
                  </>
                )}
              </div>
            )}

            {tab === 'amenities' && (
              <div className="store-setting-list">
                <div className="store-section-heading">
                  <span className="kicker">AMENITIES</span>
                  <h2>편의 정보</h2>
                </div>
                <div className="store-setting-grid store-setting-grid-three">
                  <ToggleRow label="주차 가능" description="주차 공간" checked={form.parkingAvailable === 'Y'} onChange={checked => updateField('parkingAvailable', checked ? 'Y' : 'N')} />
                  <ToggleRow label="와이파이 제공" description="무료 와이파이" checked={form.wifiAvailable === 'Y'} onChange={checked => updateField('wifiAvailable', checked ? 'Y' : 'N')} />
                  <ToggleRow label="다국어 메뉴" description="외국어 메뉴판" checked={form.multilingualMenuAvailable === 'Y'} onChange={checked => updateField('multilingualMenuAvailable', checked ? 'Y' : 'N')} />
                </div>
              </div>
            )}

            {/* ── 슬롯 관리 탭 ── */}
            {tab === 'slot' && (
              <div className="store-setting-list">
                <div className="store-section-heading">
                  <span className="kicker">SLOT</span>
                  <h2>슬롯 관리</h2>
                </div>

                {!restaurantId ? (
                  <p style={{ color: 'var(--muted)', fontWeight: 700 }}>매장을 먼저 등록해주세요.</p>
                ) : (
                  <>
                    {/*
                      슬롯 추가 — <div>로 둔다 (절대 <form>으로 만들지 말 것).
                      이 블록 전체가 페이지 최상단의 <form onSubmit={handleSubmit}> 안에
                      들어있어서, 여기에 또 <form>을 쓰면 HTML 중첩 form이 되어 브라우저가
                      안쪽 form을 무시한다. 그 결과 "슬롯 추가" 버튼이 바깥 매장정보 저장
                      폼으로 잘못 제출되며 슬롯은 생성되지 않고 탭만 기본정보로 리셋된다.
                    */}
                    <div style={{ marginBottom: 28, padding: '20px', background: 'var(--cream)', borderRadius: 14, border: '2px solid var(--ink)' }}>
                      <div style={{ fontWeight: 900, fontSize: '0.92rem', marginBottom: 14 }}>➕ 새 슬롯 추가</div>
                      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(150px, 1fr))', gap: 12, marginBottom: 14 }}>
                        <label className="store-field">
                          <span>날짜</span>
                          <input type="date" value={slotForm.slotDate} min={getTodayStr()}
                            onChange={e => setSlotForm(f => ({ ...f, slotDate: e.target.value }))} />
                        </label>
                        <label className="store-field">
                          <span>시간</span>
                          <input type="time" value={slotForm.slotTime}
                            onChange={e => setSlotForm(f => ({ ...f, slotTime: e.target.value }))} />
                        </label>
                        <label className="store-field">
                          <span>테이블 수 (받을 예약 건수)</span>
                          <input type="number" min={1} value={slotForm.totalCount}
                            onChange={e => setSlotForm(f => ({ ...f, totalCount: Number(e.target.value) }))} />
                        </label>
                        <label className="store-field">
                          <span>최소 인원</span>
                          <input type="number" min={1} value={slotForm.minHeadcount}
                            onChange={e => setSlotForm(f => ({ ...f, minHeadcount: Number(e.target.value) }))} />
                        </label>
                        <label className="store-field">
                          <span>최대 인원</span>
                          <input type="number" min={1} value={slotForm.maxHeadcount}
                            onChange={e => setSlotForm(f => ({ ...f, maxHeadcount: Number(e.target.value) }))} />
                        </label>
                      </div>
                      <button type="button" className="btn btn-tomato btn-sm" onClick={() => void handleCreateSlot()}>슬롯 추가</button>
                    </div>

                    {/* 날짜별 슬롯 조회 */}
                    <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 16 }}>
                      <input type="date" value={slotDate}
                        style={{ padding: '8px 12px', border: '2px solid var(--ink)', borderRadius: 10, fontFamily: 'inherit', fontWeight: 700 }}
                        onChange={e => { setSlotDate(e.target.value); void fetchSlots(e.target.value); }} />
                      <span style={{ fontSize: '0.84rem', color: 'var(--muted)', fontWeight: 700 }}>날짜 슬롯 조회</span>
                    </div>

                    {slotLoading ? (
                      <div className="spinner" />
                    ) : slots.length === 0 ? (
                      <p style={{ color: 'var(--muted)', fontWeight: 700 }}>해당 날짜에 슬롯이 없어요.</p>
                    ) : (
                      <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                        {slots.map(slot => (
                          <div key={slot.slotId} style={{ padding: '14px 18px', background: '#fff', border: '2px solid var(--ink)', borderRadius: 12, boxShadow: '3px 3px 0 var(--ink)' }}>
                            {editingSlot?.slotId === slot.slotId ? (
                              <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
                                <strong style={{ minWidth: 56 }}>{slot.slotTime.slice(0, 5)}</strong>
                                <label style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: '0.84rem', fontWeight: 700 }}>
                                  테이블 수
                                  <input type="number" min={1} value={editingSlot.totalCount} style={{ width: 64, padding: '4px 8px', border: '2px solid var(--ink)', borderRadius: 8 }}
                                    onChange={e => setEditingSlot(s => s && ({ ...s, totalCount: Number(e.target.value) }))} />
                                </label>
                                <label style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: '0.84rem', fontWeight: 700 }}>
                                  최소
                                  <input type="number" min={1} value={editingSlot.minHeadcount} style={{ width: 56, padding: '4px 8px', border: '2px solid var(--ink)', borderRadius: 8 }}
                                    onChange={e => setEditingSlot(s => s && ({ ...s, minHeadcount: Number(e.target.value) }))} />
                                </label>
                                <label style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: '0.84rem', fontWeight: 700 }}>
                                  최대
                                  <input type="number" min={1} value={editingSlot.maxHeadcount} style={{ width: 56, padding: '4px 8px', border: '2px solid var(--ink)', borderRadius: 8 }}
                                    onChange={e => setEditingSlot(s => s && ({ ...s, maxHeadcount: Number(e.target.value) }))} />
                                </label>
                                <button type="button" className="btn btn-tomato btn-sm" onClick={() => void handleUpdateSlot()}>저장</button>
                                <button type="button" className="btn btn-white btn-sm" onClick={() => setEditingSlot(null)}>취소</button>
                              </div>
                            ) : (
                              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                <div style={{ display: 'flex', gap: 16, alignItems: 'center' }}>
                                  <strong style={{ fontSize: '1rem' }}>{slot.slotTime.slice(0, 5)}</strong>
                                  <span style={{ fontSize: '0.84rem', color: 'var(--muted)', fontWeight: 700 }}>
                                    잔여 {slot.remainCount}/{slot.totalCount}테이블
                                  </span>
                                  <span style={{
                                    fontSize: '0.75rem', fontWeight: 800, padding: '2px 10px',
                                    borderRadius: 999, border: '1.5px solid var(--ink)',
                                    background: slot.available ? '#d9f7eb' : '#eee',
                                    color: slot.available ? '#087451' : '#666',
                                  }}>{slot.available ? '예약가능' : '마감'}</span>
                                </div>
                                <div style={{ display: 'flex', gap: 8 }}>
                                  <button type="button" className="btn btn-white btn-sm"
                                    onClick={() => setEditingSlot({ slotId: slot.slotId, totalCount: slot.totalCount, minHeadcount: 1, maxHeadcount: 8 })}>
                                    수정
                                  </button>
                                  <button type="button" className="btn btn-sm"
                                    style={{ background: '#fff', border: '2px solid var(--ink)', color: 'var(--tomato)', fontWeight: 800 }}
                                    onClick={() => void handleDeleteSlot(slot.slotId)}>
                                    삭제
                                  </button>
                                </div>
                              </div>
                            )}
                          </div>
                        ))}
                      </div>
                    )}

                    {slotMsg && (
                      <p style={{ marginTop: 12, fontWeight: 800, color: slotMsg.includes('실패') ? 'var(--tomato)' : '#087451' }}>{slotMsg}</p>
                    )}
                  </>
                )}
              </div>
            )}

            {/* ── 웨이팅 세션 탭 ── */}
            {tab === 'waiting' && (
              <div className="store-setting-list">
                <div className="store-section-heading">
                  <span className="kicker">WAITING</span>
                  <h2>웨이팅 세션</h2>
                </div>

                {!restaurantId ? (
                  <p style={{ color: 'var(--muted)', fontWeight: 700 }}>매장을 먼저 등록해주세요.</p>
                ) : (
                  <>
                    {/* 현재 세션 상태 카드 */}
                    <div style={{ padding: '20px 24px', background: 'var(--cream)', border: '2.5px solid var(--ink)', borderRadius: 16, boxShadow: '4px 4px 0 var(--ink)', marginBottom: 24 }}>
                      {waitingSession ? (
                        <>
                          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
                            <div>
                              <div style={{ fontSize: '0.78rem', fontWeight: 800, color: 'var(--muted)', marginBottom: 4 }}>오늘의 웨이팅 세션</div>
                              <div style={{ fontWeight: 900, fontSize: '1rem' }}>{waitingSession.sessionDate}</div>
                            </div>
                            <span style={{
                              padding: '4px 14px', borderRadius: 999, border: '2px solid var(--ink)',
                              fontWeight: 900, fontSize: '0.82rem',
                              background: waitingSession.status === 'OPEN' ? '#d9f7eb' : waitingSession.status === 'PAUSED' ? '#fff1cf' : '#eee',
                              color: waitingSession.status === 'OPEN' ? '#087451' : waitingSession.status === 'PAUSED' ? '#8a5500' : '#666',
                            }}>
                              {WAITING_SESSION_LABEL[waitingSession.status]}
                            </span>
                          </div>
                          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginBottom: 20 }}>
                            <div style={{ textAlign: 'center', padding: '16px', background: '#fff', border: '2px solid var(--ink)', borderRadius: 12 }}>
                              <div style={{ fontSize: '2.2rem', fontWeight: 900, color: 'var(--tomato)', lineHeight: 1 }}>{waitingSession.currentCount}</div>
                              <div style={{ fontSize: '0.78rem', color: 'var(--muted)', fontWeight: 800, marginTop: 4 }}>현재 대기 팀</div>
                            </div>
                            <div style={{ textAlign: 'center', padding: '16px', background: '#fff', border: '2px solid var(--ink)', borderRadius: 12 }}>
                              <div style={{ fontSize: '2.2rem', fontWeight: 900, color: 'var(--ink)', lineHeight: 1 }}>{waitingSession.maxWaitingCount}</div>
                              <div style={{ fontSize: '0.78rem', color: 'var(--muted)', fontWeight: 800, marginTop: 4 }}>최대 대기 팀</div>
                            </div>
                          </div>
                          <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
                            {waitingSession.status === 'OPEN' && (
                              <button type="button" className="btn btn-white btn-sm" disabled={sessionLoading}
                                onClick={() => void handleSessionAction('pause')}>⏸ 일시 정지</button>
                            )}
                            {waitingSession.status === 'PAUSED' && (
                              <button type="button" className="btn btn-white btn-sm" disabled={sessionLoading}
                                onClick={() => void handleSessionAction('resume')}>▶ 재개</button>
                            )}
                            {(waitingSession.status === 'OPEN' || waitingSession.status === 'PAUSED') && (
                              <button type="button" className="btn btn-white btn-sm" disabled={sessionLoading}
                                style={{ color: 'var(--tomato)', borderColor: 'var(--tomato)' }}
                                onClick={() => { if (confirm('오늘 웨이팅을 마감할까요?')) void handleSessionAction('close'); }}>
                                ■ 마감
                              </button>
                            )}
                          </div>
                        </>
                      ) : (
                        <p style={{ color: 'var(--muted)', fontWeight: 700, marginBottom: 0 }}>오늘 오픈된 세션이 없어요.</p>
                      )}
                    </div>

                    {/* 세션 오픈 */}
                    {(!waitingSession || waitingSession.status === 'CLOSED') && (
                      <div style={{ padding: '20px', background: '#fff', border: '2px solid var(--ink)', borderRadius: 14 }}>
                        <div style={{ fontWeight: 900, fontSize: '0.92rem', marginBottom: 14 }}>🔓 웨이팅 세션 오픈</div>
                        <label className="store-field" style={{ maxWidth: 200, marginBottom: 16 }}>
                          <span>최대 대기 팀 수</span>
                          <input type="number" min={1} max={999} value={maxWaitingCount}
                            onChange={e => setMaxWaitingCount(Number(e.target.value))} />
                        </label>
                        <button type="button" className="btn btn-tomato btn-sm" disabled={sessionLoading}
                          onClick={() => void handleOpenSession()}>
                          {sessionLoading ? '처리 중...' : '세션 오픈'}
                        </button>
                      </div>
                    )}

                    {sessionMsg && (
                      <p style={{ marginTop: 12, fontWeight: 800, color: sessionMsg.includes('실패') ? 'var(--tomato)' : '#087451' }}>{sessionMsg}</p>
                    )}
                  </>
                )}
              </div>
            )}

          </section>

          {(tab === 'basic' || tab === 'operation' || tab === 'amenities') && (
            <div className="store-actions">
              <div aria-live="polite">
                {error && <p className="store-error">{error}</p>}
                {message && <p className="store-success">{message}</p>}
              </div>
              <button type="submit" className="btn btn-tomato" disabled={saving}>
                {saving ? '저장 중...' : restaurantId ? '변경사항 저장' : '매장 등록'}
              </button>
            </div>
          )}
        </form>
      </main>
    </div>
  );
}

function ToggleRow({
  label,
  description,
  checked,
  onChange,
}: {
  label: string;
  description: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
}) {
  return (
    <label className={`store-toggle-row ${checked ? 'is-active' : ''}`}>
      <span className="store-toggle-copy">
        <strong>{label}</strong>
        <small>{description}</small>
      </span>
      <input type="checkbox" checked={checked} onChange={event => onChange(event.target.checked)} />
      <span className="store-toggle-side">
        <span className="store-toggle-state">{checked ? '사용' : '미사용'}</span>
        <span className="store-toggle" aria-hidden="true" />
      </span>
    </label>
  );
}
