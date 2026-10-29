// W01 방문 카드
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import MockMap from '../../features/map/MockMap';
import { visitScene } from '../../features/map/scenes';
import dotMe from '../../assets/icons/dot-me.svg';
import dotCar from '../../assets/icons/dot-car.svg';
import swap from '../../assets/icons/swap.svg';

// ponytail: API에 ETA·장소 상세가 아직 없어 Figma 값 그대로. 응답 필드 생기면 교체.
const SAMPLE = {
  updatedAt: '14:40',
  distanceKm: 4.1,
  etaMin: 12,
  etaAt: '14:52',
  place: { name: '북문 로비', desc: '엘리베이터 앞 벤치 · 지붕 있음' },
};

type MapView = 'me' | 'car' | 'all';
const STEPS = ['출발', '이동 중', '도착'];
const CURRENT_STEP = 1;

export default function VisitPage() {
  const navigate = useNavigate();
  const [view, setView] = useState<MapView>('all');
  const [ready, setReady] = useState(false);

  return (
    <main className="screen">
      <MockMap scene={visitScene}>
        <span className="chip chip-brand">마중</span>
        <span className="chip chip-time">{SAMPLE.updatedAt} 기준</span>
        <button className="zoom" style={{ top: 64 }} aria-label="지도 확대">+</button>
        <button className="zoom" style={{ top: 116 }} aria-label="지도 축소">−</button>
        <div className="map-views">
          <button aria-pressed={view === 'me'} onClick={() => setView('me')}>
            <img src={dotMe} alt="" width={12} height={12} />내 위치
          </button>
          <button aria-pressed={view === 'car'} onClick={() => setView('car')}>
            <img src={dotCar} alt="" width={12} height={12} />차 위치
          </button>
          <button aria-pressed={view === 'all'} onClick={() => setView('all')}>전체</button>
        </div>
      </MockMap>

      <section className="sheet">
        <div className="eta">
          <div>
            <p className="sub">차가 오고 있어요 · {SAMPLE.distanceKm}km</p>
            <p className="eta-min">약 {SAMPLE.etaMin}분</p>
          </div>
          <div>
            <p className="sub">도착 예정</p>
            <p className="eta-at">{SAMPLE.etaAt}</p>
          </div>
        </div>

        <ol className="steps">
          {STEPS.map((s, i) => (
            <li key={s} className={i <= CURRENT_STEP ? 'done' : undefined} aria-current={i === CURRENT_STEP ? 'step' : undefined}>
              {s}
            </li>
          ))}
        </ol>

        <div className="place">
          <div className="place-photo">[사진]</div>
          <div className="place-body">
            <p className="place-label">만날 곳</p>
            <p className="place-name">{SAMPLE.place.name}</p>
            <p className="place-desc">{SAMPLE.place.desc}</p>
          </div>
          <button className="btn-outline-accent" onClick={() => navigate('proposal')}>
            <img src={swap} alt="" width={14} height={14} />위치 바꾸기
          </button>
        </div>

        <button className="btn-primary" onClick={() => setReady((r) => !r)}>
          {ready ? '나가는 중이에요' : '준비됐어요'}
        </button>
      </section>
    </main>
  );
}
