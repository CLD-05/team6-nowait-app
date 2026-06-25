import { useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import Logo from '../components/Logo';

const styles = `
  @import url('https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/dist/web/static/pretendard.min.css');
  :root {
    --cream:#fff6ef; --ink:#231a14; --muted:#7d6f63;
    --tomato:#ff5a3c; --tomato-d:#f23c1d; --amber:#ffb22e;
    --mint:#16b886; --white:#fff; --line:#f0e3d7; --maxw:1180px;
  }
  .lp *{box-sizing:border-box;margin:0;padding:0}
  .lp{font-family:'Pretendard',sans-serif;background:var(--cream);color:var(--ink);-webkit-font-smoothing:antialiased;line-height:1.5;letter-spacing:-.02em}
  .lp a{color:inherit;text-decoration:none}
  .lp .wrap{max-width:var(--maxw);margin:0 auto;padding:0 26px}
  .lp header{position:sticky;top:0;z-index:50;background:rgba(255,246,239,.92);backdrop-filter:blur(14px);border-bottom:2px solid var(--ink)}
  .lp .nav{display:flex;align-items:center;justify-content:space-between;height:76px}
  .lp .logo{display:inline-flex;align-items:center;line-height:0;cursor:pointer}
  .lp .logo img{height:auto;max-width:100%}
  .lp .nav-links{display:none;gap:30px;font-size:15.5px;font-weight:700}
  @media(min-width:920px){.lp .nav-links{display:flex}}
  .lp .nav-links a{opacity:.7;transition:all .15s;cursor:pointer}
  .lp .nav-links a:hover{opacity:1;color:var(--tomato)}
  .lp .btn{display:inline-flex;align-items:center;justify-content:center;gap:8px;font-weight:800;white-space:nowrap;font-size:15.5px;border-radius:999px;padding:13px 24px;cursor:pointer;border:3px solid var(--ink);transition:transform .14s ease,box-shadow .18s ease;font-family:'Pretendard',sans-serif}
  .lp .btn:active{transform:translateY(2px)}
  .lp .btn-tomato{background:var(--tomato);color:#fff;box-shadow:4px 4px 0 var(--ink)}
  .lp .btn-tomato:hover{transform:translate(-1px,-1px);box-shadow:6px 6px 0 var(--ink)}
  .lp .btn-amber{background:var(--amber);color:var(--ink);box-shadow:4px 4px 0 var(--ink)}
  .lp .btn-amber:hover{transform:translate(-1px,-1px);box-shadow:6px 6px 0 var(--ink)}
  .lp .btn-white{background:#fff;color:var(--ink);box-shadow:4px 4px 0 var(--ink)}
  .lp .btn-white:hover{transform:translate(-1px,-1px);box-shadow:6px 6px 0 var(--ink)}
  .lp .btn-sm{padding:10px 18px;font-size:14.5px;border-width:2px;box-shadow:3px 3px 0 var(--ink)}
  .lp .btn-lg{padding:16px 30px;font-size:17px}
  .lp .badge{display:inline-flex;align-items:center;gap:8px;background:var(--amber);color:var(--ink);font-weight:800;font-size:14px;padding:8px 16px;border-radius:999px;border:2.5px solid var(--ink);transform:rotate(-2deg);box-shadow:3px 3px 0 var(--ink)}
  .lp .hero{padding:56px 0 30px}
  .lp .hero-grid{display:grid;grid-template-columns:1fr;gap:40px;align-items:center}
  @media(min-width:980px){.lp .hero-grid{grid-template-columns:1.06fr .94fr}}
  .lp h1.title{font-size:clamp(40px,5.6vw,66px);font-weight:900;line-height:1.02;letter-spacing:-.05em;margin-top:22px}
  .lp .title .hl{position:relative;display:inline-block;color:var(--tomato);white-space:nowrap;background:linear-gradient(transparent 62%,rgba(255,178,46,.85) 62%,rgba(255,178,46,.85) 92%,transparent 92%)}
  .lp .lead{font-size:clamp(17px,2.1vw,20px);color:var(--muted);margin-top:24px;max-width:470px;line-height:1.6;font-weight:500}
  .lp .hero-actions{display:flex;flex-wrap:wrap;gap:14px;margin-top:30px}
  .lp .hero-meta{display:flex;gap:14px;margin-top:34px;flex-wrap:wrap}
  .lp .chip-stat{background:#fff;border:2.5px solid var(--ink);border-radius:16px;padding:12px 18px;box-shadow:3px 3px 0 var(--ink)}
  .lp .chip-stat b{display:block;font-size:23px;font-weight:900;line-height:1}
  .lp .chip-stat span{font-size:12.5px;color:var(--muted);font-weight:700}
  .lp .phone-stage{position:relative;display:flex;justify-content:center}
  .lp .deco{position:absolute;font-size:0}
  .lp .deco.d1{top:-6px;left:8%;width:46px;height:46px;background:var(--amber);border-radius:14px;border:3px solid var(--ink);transform:rotate(12deg)}
  .lp .deco.d2{bottom:30px;right:4%;width:54px;height:54px;background:var(--mint);border-radius:50%;border:3px solid var(--ink)}
  .lp .deco.d3{top:40%;left:-2%;width:34px;height:34px;background:var(--tomato);border-radius:50%;border:3px solid var(--ink)}
  .lp .phone{position:relative;z-index:2;width:298px;border-radius:40px;background:var(--ink);padding:9px;border:3px solid var(--ink);box-shadow:10px 12px 0 rgba(35,26,20,.16)}
  .lp .screen{background:#fff;border-radius:33px;overflow:hidden;height:596px;position:relative}
  .lp .notch{position:absolute;top:0;left:50%;transform:translateX(-50%);width:118px;height:25px;background:var(--ink);border-radius:0 0 16px 16px;z-index:5}
  .lp .sbar{display:flex;justify-content:space-between;padding:14px 22px 6px;font-size:12px;font-weight:800}
  .lp .app-head{padding:8px 22px 12px}
  .lp .app-head .hi{font-size:12.5px;font-weight:800;color:var(--tomato)}
  .lp .app-head .rest{font-size:21px;font-weight:900;letter-spacing:-.04em;margin-top:3px}
  .lp .ticket{margin:6px 18px;background:var(--tomato);color:#fff;border-radius:24px;padding:22px;border:3px solid var(--ink)}
  .lp .ticket .lbl{font-size:12.5px;font-weight:800;opacity:.92}
  .lp .ticket .num{font-size:64px;font-weight:900;line-height:1;letter-spacing:-.05em;margin-top:6px}
  .lp .ticket .num small{font-size:20px;margin-left:4px}
  .lp .ticket .est{margin-top:12px;display:flex;align-items:center;gap:8px;font-size:13px;font-weight:700}
  .lp .ticket .est .pill{background:#fff;color:var(--tomato);font-weight:900;padding:3px 11px;border-radius:999px;font-size:12px}
  .lp .prog{margin:16px 20px 0}
  .lp .prog .bar{height:12px;border-radius:999px;background:#f1e7dc;border:2.5px solid var(--ink);overflow:hidden}
  .lp .prog .fill{height:100%;width:72%;background:var(--amber)}
  .lp .prog .row{display:flex;justify-content:space-between;font-size:12px;font-weight:800;margin-top:8px}
  .lp .app-list{margin:16px 18px 0}
  .lp .li{display:flex;align-items:center;gap:11px;padding:10px;background:var(--cream);border:2.5px solid var(--ink);border-radius:16px;margin-bottom:10px}
  .lp .li .ico{width:34px;height:34px;border-radius:10px;background:#fff;border:2px solid var(--ink);display:flex;align-items:center;justify-content:center;font-size:16px}
  .lp .li .t{font-size:13.5px;font-weight:800}
  .lp .li .s{font-size:11px;color:var(--muted);font-weight:600}
  .lp .li .meta{margin-left:auto;font-size:11.5px;font-weight:900;color:var(--tomato)}
  .lp .strip{background:var(--ink);color:#fff;padding:16px 0;overflow:hidden;margin-top:40px;transform:rotate(-1deg);border-top:3px solid var(--ink);border-bottom:3px solid var(--ink)}
  .lp .mq{display:flex;gap:34px;white-space:nowrap;font-weight:900;font-size:18px;letter-spacing:-.02em;animation:mq 22s linear infinite}
  .lp .mq span{display:flex;align-items:center;gap:34px}
  .lp .mq .o{color:var(--amber)}
  @keyframes mq{from{transform:translateX(0)}to{transform:translateX(-50%)}}
  .lp .block{padding:84px 0}
  .lp .sec-head{max-width:600px}
  .lp .kicker{display:inline-block;font-weight:900;font-size:14px;color:var(--tomato);background:#ffe6df;padding:6px 14px;border-radius:999px;border:2px solid var(--tomato)}
  .lp .sec-head h2{font-size:clamp(30px,4.6vw,48px);font-weight:900;letter-spacing:-.04em;line-height:1.04;margin-top:16px}
  .lp .sec-head p{color:var(--muted);font-size:17px;margin-top:14px;line-height:1.6;font-weight:500}
  .lp .feat-grid{display:grid;grid-template-columns:1fr;gap:18px;margin-top:44px}
  @media(min-width:680px){.lp .feat-grid{grid-template-columns:1fr 1fr}}
  @media(min-width:980px){.lp .feat-grid{grid-template-columns:repeat(3,1fr)}}
  .lp .feat-card{background:#fff;border:3px solid var(--ink);border-radius:24px;padding:28px 26px;box-shadow:5px 5px 0 var(--ink);transition:transform .16s ease,box-shadow .16s ease}
  .lp .feat-card:hover{transform:translate(-2px,-2px);box-shadow:8px 8px 0 var(--ink)}
  .lp .feat-card .ic{width:52px;height:52px;border-radius:15px;display:flex;align-items:center;justify-content:center;font-size:24px;border:2.5px solid var(--ink);margin-bottom:18px}
  .lp .feat-card:nth-child(3n+1) .ic{background:var(--amber)}
  .lp .feat-card:nth-child(3n+2) .ic{background:var(--mint)}
  .lp .feat-card:nth-child(3n) .ic{background:#ffd1c6}
  .lp .feat-card h3{font-size:20px;font-weight:900;letter-spacing:-.03em}
  .lp .feat-card p{color:var(--muted);font-size:14.5px;margin-top:9px;line-height:1.6;font-weight:500}
  .lp .steps{display:grid;grid-template-columns:1fr;gap:18px;margin-top:44px}
  @media(min-width:820px){.lp .steps{grid-template-columns:repeat(3,1fr)}}
  .lp .step{background:#fff;border:3px solid var(--ink);border-radius:24px;padding:26px;box-shadow:5px 5px 0 var(--ink)}
  .lp .step .n{width:46px;height:46px;border-radius:50%;background:var(--tomato);color:#fff;border:3px solid var(--ink);display:flex;align-items:center;justify-content:center;font-weight:900;font-size:20px}
  .lp .step h4{font-size:21px;font-weight:900;letter-spacing:-.03em;margin-top:16px}
  .lp .step p{color:var(--muted);font-size:14.5px;margin-top:8px;line-height:1.6;font-weight:500}
  .lp .show{display:grid;grid-template-columns:1fr;gap:16px;margin-top:44px}
  @media(min-width:820px){.lp .show{grid-template-columns:2fr 1fr 1fr;grid-auto-rows:200px}}
  .lp .show-img{border-radius:22px;border:3px solid var(--ink);box-shadow:5px 5px 0 var(--ink);overflow:hidden}
  .lp .show-img img{width:100%;height:100%;object-fit:cover}
  .lp .show-img.big{grid-row:span 2}
  @media(max-width:819px){.lp .show-img{height:200px}}
  .lp .band{background:var(--amber);border:3px solid var(--ink);border-radius:30px;padding:48px 36px;display:grid;grid-template-columns:repeat(2,1fr);gap:28px;box-shadow:7px 7px 0 var(--ink)}
  @media(min-width:820px){.lp .band{grid-template-columns:repeat(4,1fr)}}
  .lp .stat-item{text-align:center}
  .lp .stat-item b{font-size:clamp(36px,4.6vw,52px);font-weight:900;letter-spacing:-.04em;display:block;line-height:1}
  .lp .stat-item span{font-size:13.5px;font-weight:800;margin-top:8px;display:block}
  .lp .cta{text-align:center;background:var(--tomato);color:#fff;border:3px solid var(--ink);border-radius:34px;padding:84px 30px;margin:30px 0 80px;box-shadow:8px 8px 0 var(--ink)}
  .lp .cta h2{font-size:clamp(34px,5.4vw,60px);font-weight:900;letter-spacing:-.04em;line-height:1.02}
  .lp .cta p{font-size:18px;margin-top:16px;font-weight:600;opacity:.95}
  .lp .cta .hero-actions{justify-content:center;margin-top:30px}
  .lp footer{padding:56px 0 40px;color:var(--muted);border-top:3px solid var(--ink)}
  .lp .foot{display:grid;grid-template-columns:1fr;gap:34px}
  @media(min-width:760px){.lp .foot{grid-template-columns:2fr 1fr 1fr 1fr}}
  .lp .foot h5{color:var(--ink);font-size:14px;font-weight:900;margin-bottom:14px}
  .lp .foot a{display:block;font-size:14.5px;padding:5px 0;font-weight:600;cursor:pointer}
  .lp .foot a:hover{color:var(--tomato)}
  .lp .foot .desc{font-size:14.5px;line-height:1.6;max-width:260px;margin-top:14px;font-weight:500}
  .lp .foot-bot{margin-top:42px;padding-top:22px;border-top:2px solid var(--line);display:flex;justify-content:space-between;flex-wrap:wrap;gap:12px;font-size:13px;font-weight:600}
  .lp .reveal{opacity:0;transform:translateY(26px);transition:opacity .7s cubic-bezier(.16,1,.3,1),transform .7s cubic-bezier(.16,1,.3,1)}
  .lp .reveal.in{opacity:1;transform:none}
  @media(prefers-reduced-motion:reduce){.lp .reveal{opacity:1!important;transform:none!important}}
`;

const FEATURES = [
  { ic: '📍', title: '원격 줄서기', desc: '매장에 없어도 탭 한 번으로 줄서기 완료! 도착 시간 맞춰 줄이 줄어요.' },
  { ic: '⏱️', title: '실시간 현황', desc: '내 앞에 몇 팀 남았는지, 얼마나 기다리는지 1초 단위로 확인!' },
  { ic: '🔔', title: '입장 알림', desc: '차례 가까워지면 띵! 알림으로 알려줄게요. 그동안 자유롭게.' },
  { ic: '📅', title: '간편 예약', desc: '날짜·시간·인원 골라서 미리 예약. 웨이팅이랑 예약을 한 번에!' },
  { ic: '⭐', title: '맛집 추천', desc: '내 취향이랑 위치 딱 분석해서 지금 갈 수 있는 핫플 추천!' },
  { ic: '🛡️', title: '노쇼 방지', desc: '방문 확인이랑 자동 취소로 사장님 노쇼 걱정 뚝!' },
];
const STEPS = [
  { n: '1', title: '맛집 찾기', desc: '주변 인기 맛집 검색하고 대기 현황 슥 확인해요.' },
  { n: '2', title: '줄서기·예약', desc: '인원 고르고 탭 한 번! 대기열에 쏙 합류해요.' },
  { n: '3', title: '알림·입장', desc: '차례 오면 알림 띵! 바로 들어가서 맛있게 즐겨요.' },
];
const STATS = [
  { num: '12,000+', label: '전국 제휴 맛집' },
  { num: '850만', label: '누적 웨이팅' },
  { num: '32%↓', label: '평균 대기시간' },
  { num: '4.8★', label: '앱 평점' },
];
const IMGS = [
  { url: 'https://images.unsplash.com/photo-1414235077428-338989a2e8c0?w=800&q=80', big: true },
  { url: 'https://images.unsplash.com/photo-1579871494447-9811cf80d66c?w=400&q=80' },
  { url: 'https://images.unsplash.com/photo-1583224944844-5b268c057b72?w=400&q=80' },
  { url: 'https://images.unsplash.com/photo-1563245372-f21724e3856d?w=400&q=80' },
  { url: 'https://images.unsplash.com/photo-1559314809-0d155014e29e?w=400&q=80' },
];
export default function LandingPage() {
  const navigate = useNavigate();
  const injected = useRef(false);

  useEffect(() => {
    if (injected.current) return;
    injected.current = true;
    const tag = document.createElement('style');
    tag.innerHTML = styles;
    document.head.appendChild(tag);
  }, []);

  useEffect(() => {
    const io = new IntersectionObserver(
      es => es.forEach(e => { if (e.isIntersecting) { e.target.classList.add('in'); io.unobserve(e.target); } }),
      { threshold: 0.08 }
    );
    setTimeout(() => {
      document.querySelectorAll('.lp .reveal').forEach(el => {
        io.observe(el);
        if (el.getBoundingClientRect().top < window.innerHeight * 0.95) el.classList.add('in');
      });
    }, 100);
    return () => io.disconnect();
  }, []);

  const go = (id: string) => document.getElementById(id)?.scrollIntoView({ behavior: 'smooth' });

  return (
    <div className="lp">
      <header>
        <div className="wrap nav">
          <div className="logo" onClick={() => navigate('/')}><Logo width="clamp(142px, 15vw, 176px)" /></div>
          <nav className="nav-links">
            <a onClick={() => go('features')}>기능</a>
            <a onClick={() => go('how')}>이용방법</a>
            <a onClick={() => go('showcase')}>맛집</a>
            <a onClick={() => go('owner')}>사장님</a>
          </nav>
          <button className="btn btn-tomato btn-sm" onClick={() => navigate('/')}>지금 시작해보기</button>
        </div>
      </header>

      <main className="wrap">
        <section className="hero">
          <div className="hero-grid">
            <div className="reveal">
              <span className="badge">🔥 지금 줄서기 인기 폭발</span>
              <h1 className="title">줄은 그만,<br /><span className="hl">노웨이트</span><br />하자!</h1>
              <p className="lead">매장 안 가도 OK! 앱으로 미리 줄 서고, 내 차례 되면 띵— 하고 알려줄게요. 기다림은 빼고 맛집만 쏙쏙 즐기세요.</p>
              <div className="hero-actions">
                <button className="btn btn-tomato btn-lg" onClick={() => navigate('/auth')}>지금 줄서기</button>
                <button className="btn btn-white btn-lg" onClick={() => go('how')}>어떻게 써요?</button>
              </div>
              <div className="hero-meta">
                {[{ num: '12,000+', label: '제휴 맛집' }, { num: '850만', label: '누적 웨이팅' }, { num: '4.8★', label: '앱 평점' }].map(s => (
                  <div key={s.label} className="chip-stat"><b>{s.num}</b><span>{s.label}</span></div>
                ))}
              </div>
            </div>
            <div className="phone-stage reveal">
              <div className="deco d1" /><div className="deco d2" /><div className="deco d3" />
              <div className="phone">
                <div className="screen">
                  <div className="notch" />
                  <div className="sbar"><span>9:41</span><span>● ● ●  ⌁</span></div>
                  <div className="app-head"><div className="hi">웨이팅 중 · 강남점</div><div className="rest">을지로 골목식당</div></div>
                  <div className="ticket">
                    <div className="lbl">내 앞에 남은 팀</div>
                    <div className="num">3<small>팀</small></div>
                    <div className="est">예상 대기 <span className="pill">약 12분</span></div>
                  </div>
                  <div className="prog"><div className="bar"><div className="fill" /></div><div className="row"><span>입장까지</span><span>72%</span></div></div>
                  <div className="app-list">
                    <div className="li"><div className="ico">🔔</div><div><div className="t">입장 알림</div><div className="s">차례 오면 알려줄게요</div></div><div className="meta">ON</div></div>
                    <div className="li"><div className="ico">🍽️</div><div><div className="t">2인 · 홀 좌석</div><div className="s">방문 예정</div></div><div className="meta">변경</div></div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>
      </main>

      <div className="strip">
        <div className="mq">
          {[0, 1].map(i => <span key={i}>줄서기 끝 <em className="o">●</em> 원격 웨이팅 <em className="o">●</em> 실시간 현황 <em className="o">●</em> 입장 알림 <em className="o">●</em> 간편 예약 <em className="o">●</em> 노쇼 방지 <em className="o">●</em>&nbsp;</span>)}
        </div>
      </div>

      <main className="wrap">
        <section className="block" id="features">
          <div className="sec-head reveal"><span className="kicker">FEATURES</span><h2>이거 하나면<br />웨이팅 끝!</h2><p>줄서기부터 예약, 입장 알림까지. nowait 하나로 다 됩니다.</p></div>
          <div className="feat-grid">
            {FEATURES.map(f => <div key={f.title} className="feat-card reveal"><div className="ic">{f.ic}</div><h3>{f.title}</h3><p>{f.desc}</p></div>)}
          </div>
        </section>

        <section className="block" id="how" style={{ paddingTop: 0 }}>
          <div className="sec-head reveal"><span className="kicker">HOW IT WORKS</span><h2>세 번만 탭하면 끝!</h2></div>
          <div className="steps">
            {STEPS.map(s => <div key={s.n} className="step reveal"><div className="n">{s.n}</div><h4>{s.title}</h4><p>{s.desc}</p></div>)}
          </div>
        </section>

        <section className="block" id="showcase" style={{ paddingTop: 0 }}>
          <div className="sec-head reveal"><span className="kicker">PARTNERS</span><h2>줄 서서 먹던 그 맛집,<br />이젠 노웨이트!</h2><p>전국 12,000개 맛집이 함께해요.</p></div>
          <div className="show">
            {IMGS.map((img, i) => <div key={i} className={`show-img reveal${img.big ? ' big' : ''}`}><img src={img.url} alt="맛집" loading="lazy" /></div>)}
          </div>
        </section>

        <section style={{ paddingBottom: 84 }}>
          <div className="band reveal">
            {STATS.map(s => <div key={s.label} className="stat-item"><b>{s.num}</b><span>{s.label}</span></div>)}
          </div>
        </section>

        <section className="block" id="owner" style={{ paddingTop: 0 }}>
          <div className="hero-grid">
            <div className="reveal">
              <span className="kicker">FOR OWNERS</span>
              <h2 style={{ fontSize: 'clamp(30px,4.6vw,48px)', fontWeight: 900, letterSpacing: '-.04em', lineHeight: 1.04, marginTop: 16 }}>사장님은<br />관리만 쏙!</h2>
              <p style={{ color: 'var(--muted)', fontSize: 17, marginTop: 14, lineHeight: 1.6, fontWeight: 500, maxWidth: 450 }}>태블릿 하나로 대기열이랑 예약을 한눈에. 호출·입장·노쇼 관리까지 자동으로!</p>
              <div className="hero-actions"><button className="btn btn-amber btn-lg" onClick={() => navigate('/auth?tab=signup')}>무료로 매장 등록</button></div>
            </div>
            <div className="reveal" style={{ height: 340, borderRadius: 22, border: '3px solid var(--ink)', overflow: 'hidden', boxShadow: '5px 5px 0 var(--ink)' }}>
              <img src="https://images.unsplash.com/photo-1414235077428-338989a2e8c0?w=600&q=80" alt="점주" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
            </div>
          </div>
        </section>

        <section className="cta" id="cta">
          <div className="reveal">
            <h2>오늘 저녁,<br />줄 서지 말고 노웨이트!</h2>
            <p>지금 바로 가입하고 가까운 맛집부터 줄서기 시작해요.</p>
            <div className="hero-actions">
              <button className="btn btn-white btn-lg" onClick={() => navigate('/auth')}>일반 회원가입</button>
              <button className="btn btn-amber btn-lg" onClick={() => navigate('/auth?tab=signup')}>사장님 등록</button>
            </div>
          </div>
        </section>
      </main>

      <footer>
        <div className="wrap">
          <div className="foot">
            <div><div className="logo"><Logo width="clamp(128px, 14vw, 150px)" /></div><p className="desc">기다림 없는 외식, 노웨이트! 손님과 맛집을 가장 신나게 연결하는 웨이팅·예약 서비스.</p></div>
            <div><h5>서비스</h5><a onClick={() => navigate('/')}>맛집 찾기</a><a onClick={() => go('features')}>원격 줄서기</a><a onClick={() => go('features')}>예약하기</a></div>
            <div><h5>사장님</h5><a onClick={() => navigate('/auth?tab=signup')}>매장 등록</a><a onClick={() => navigate('/owner')}>대시보드</a><a>요금 안내</a></div>
            <div><h5>회사</h5><a>회사 소개</a><a>이용약관</a><a>개인정보처리방침</a><a>고객센터</a></div>
          </div>
          <div className="foot-bot"><span>© 2026 Nowait Inc.</span><span>서울특별시 강남구 · hello@nowait.kr</span></div>
        </div>
      </footer>
    </div>
  );
}
