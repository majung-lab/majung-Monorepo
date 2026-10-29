// W01 방문 카드 · W05 변경 결과 알림
import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import MockMap from '../../features/map/MockMap';
import { visitMovedScene, visitScene } from '../../features/map/scenes';
import check from '../../assets/icons/check.svg';
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
  movedPlace: { name: '서문 로비', desc: '서문 회전문 앞 · 북문 로비에서 바뀜' },
};

type MapView = 'me' | 'car' | 'all';
const STEPS = ['출발', '이동 중', '도착'];
const CURRENT_STEP = 1;

export default function VisitPage() {
  const navigate = useNavigate();
  const [view, setView] = useState<MapView>('all');
  const [ready, setReady] = useState(false);
  // ponytail: 운전자 응답은 웹소켓 이벤트 전까지 ?result=accepted|rejected 로 띄운다.
  const [params, setParams] = useSearchParams();
  const result = params.get('result');
  const [moved, setMoved] = useState(result === 'accepted');
  const place = moved ? SAMPLE.movedPlace : SAMPLE.place;

  return (
    <main className="screen">
      <MockMap scene={moved ? visitMovedScene : visitScene}>
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
        {(result === 'accepted' || result === 'rejected') && (
          <div className={`result ${result}`} role="status">
            {result === 'accepted' && <img src={check} alt="" />}
            <div className="result-body">
              <p className="result-title">{result === 'accepted' ? '운전자가 수락했어요' : '운전자가 그대로 가자고 했어요'}</p>
              <p className="result-sub">{result === 'accepted' ? `이제 ${place.name}에서 만나요` : `${place.name} 그대로예요`}</p>
            </div>
            <button onClick={() => { setMoved(result === 'accepted'); setParams({}); }}>확인</button>
          </div>
        )}
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
            <p className="place-name">
              {place.name}
              {moved && <span className="badge-changed">바뀐 장소</span>}
            </p>
            <p className="place-desc">{place.desc}</p>
          </div>
          <button className="btn-outline-accent" onClick={() => navigate('move')}>
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
