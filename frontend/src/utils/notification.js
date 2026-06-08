/* 브라우저 알림 / 소리 / 탭 타이틀 유틸 */

/* 권한 요청 (사용자 제스처 안에서 호출해야 동작) */
export async function ensureNotifyPermission() {
  if (!("Notification" in window)) return "unsupported";
  if (Notification.permission === "granted") return "granted";
  if (Notification.permission === "denied") return "denied";
  return await Notification.requestPermission();
}

export function getNotifyPermission() {
  if (!("Notification" in window)) return "unsupported";
  return Notification.permission; // 'default' | 'granted' | 'denied'
}

/* 브라우저 알림 띄우기 (탭이 백그라운드여도 OS 레벨로 떠오름) */
export function notify(title, body, options = {}) {
  if (!("Notification" in window)) return null;
  if (Notification.permission !== "granted") return null;
  try {
    return new Notification(title, {
      body,
      icon: "/vite.svg",
      tag: "nowait-call",
      ...options,
    });
  } catch {
    return null;
  }
}

/* Web Audio로 '딩동' 비프음 합성 (외부 파일 없음) */
export function playDing() {
  try {
    const Ctx = window.AudioContext || window.webkitAudioContext;
    if (!Ctx) return;
    const ctx = new Ctx();

    const tones = [
      { freq: 880, start: 0, dur: 0.2 },
      { freq: 660, start: 0.22, dur: 0.3 },
    ];

    tones.forEach(({ freq, start, dur }) => {
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = "sine";
      osc.frequency.value = freq;

      const t0 = ctx.currentTime + start;
      gain.gain.setValueAtTime(0.0001, t0);
      gain.gain.exponentialRampToValueAtTime(0.25, t0 + 0.03);
      gain.gain.exponentialRampToValueAtTime(0.0001, t0 + dur);

      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.start(t0);
      osc.stop(t0 + dur + 0.05);
    });

    setTimeout(() => ctx.close().catch(() => {}), 800);
  } catch {
    // ignore
  }
}

/* 진동 (지원하는 환경에서만) */
export function vibrate(pattern = [120, 60, 120]) {
  if (navigator.vibrate) {
    try {
      navigator.vibrate(pattern);
    } catch {
      // ignore
    }
  }
}

/* 탭 타이틀 변경. cleanup으로 복원 함수 리턴 */
const ORIGINAL_TITLE = "NoWait";
export function setTabTitle(t) {
  document.title = t;
}
export function restoreTabTitle() {
  document.title = ORIGINAL_TITLE;
}
