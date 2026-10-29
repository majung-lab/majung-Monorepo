// W01 방문 카드 · W05 변경 결과 알림
import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import type L from 'leaflet';
import LeafletMap, { fitAll, type MapMarker } from '../../features/map/LeafletMap';
import { FACILITY, apById, type LatLng } from '../../data/dummy';
import { carAt, resetVisit, SIM, update, useNow, useVisit } from '../../data/store';
import { useMyPosition } from '../../data/useMyPosition';
import check from '../../assets/icons/check.svg';
import dotMe from '../../assets/icons/dot-me.svg';
import dotCar from '../../assets/icons/dot-car.svg';
import swap from '../../assets/icons/swap.svg';

type MapView = 'me' | 'car' | 'all';
const STEPS = ['출발', '이동 중', '도착'];
const FIRST_MEET = 'north';

const hhmm = (t: number) => new Date(t).toTimeString().slice(0, 5);
const km = (m: number) => (m >= 1000 ? `${(m / 1000).toFixed(1)}km` : `${Math.round(m / 10) * 10}m`);

export default function VisitPage() {
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const visit = useVisit();
  const now = useNow();
  const me = useMyPosition();
  const car = carAt(visit, now);
  const meet = apById(visit.meetId);
  const [view, setView] = useState<MapView>('all');
  const map = useRef<L.Map | null>(null);

  // ?reset 으로 시뮬레이션 처음부터 (시연용)
  useEffect(() => {
    if (params.has('reset')) { resetVisit(Number(params.get('speed')) || 1); setParams({}, { replace: true }); }
  }, [params, setParams]);

  useEffect(() => {
    if (visit.driverProposal?.status === 'PENDING') navigate('proposal');
    else if (car.arrivedAt && now - car.arrivedAt >= SIM.boardMs) navigate('ended');
  }, [visit.driverProposal, car.arrivedAt, now, navigate]);

  // 지도 보기 버튼 → 내 위치 / 차 위치 / 전체
  const focus = (v: MapView, pts: { me: LatLng; car: LatLng; meet: LatLng }) => {
    const m = map.current;
    if (!m) return;
    if (v === 'me') m.setView(pts.me, 17);
    else if (v === 'car') m.setView(pts.car, 16);
    else fitAll(m, [pts.me, pts.car, pts.meet]);
  };
  const choose = (v: MapView) => { setView(v); focus(v, { me, car: car.pos, meet: meet.pos }); };

  const markers = useMemo<MapMarker[]>(() => [
    { pos: meet.pos, kind: 'meet' },
    { pos: car.pos, kind: 'car' },
    { pos: me, kind: 'me' },
  ], [meet.pos, car.pos, me]);
  const lines = useMemo(() => [{ points: car.rest }], [car.rest]);

  const arrived = car.progress >= 1;
  const step = arrived ? 2 : 1;
  const changed = visit.meetId !== FIRST_MEET;
  const banner = visit.banner;

  return (
    <main className="screen">
      <LeafletMap center={FACILITY.pos} markers={markers} lines={lines} fitTo={[me, car.pos, meet.pos]} mapRef={map}>
        <span className="chip chip-brand">마중</span>
        <span className="chip chip-time">{hhmm(now)} 기준</span>
        <button className="zoom" style={{ top: 64 }} aria-label="지도 확대" onClick={() => map.current?.zoomIn()}>+</button>
        <button className="zoom" style={{ top: 116 }} aria-label="지도 축소" onClick={() => map.current?.zoomOut()}>−</button>
        <div className="map-views">
          <button aria-pressed={view === 'me'} onClick={() => choose('me')}>
            <img src={dotMe} alt="" width={12} height={12} />내 위치
          </button>
          <button aria-pressed={view === 'car'} onClick={() => choose('car')}>
            <img src={dotCar} alt="" width={12} height={12} />차 위치
          </button>
          <button aria-pressed={view === 'all'} onClick={() => choose('all')}>전체</button>
        </div>
        {banner && (
          <div className={`result ${banner.kind}`} role="status">
            {banner.kind === 'accepted' && <img src={check} alt="" />}
            <div className="result-body">
              <p className="result-title">{banner.kind === 'accepted' ? '운전자가 수락했어요' : '운전자가 그대로 가자고 했어요'}</p>
              <p className="result-sub">
                {banner.kind === 'accepted' ? `이제 ${apById(banner.placeId).name}에서 만나요` : `${apById(banner.placeId).name} 그대로예요`}
              </p>
            </div>
            <button onClick={() => update((v) => ({ ...v, banner: null }))}>확인</button>
          </div>
        )}
      </LeafletMap>

      <section className="sheet">
        <div className="eta">
          <div>
            <p className="sub">{arrived ? '차가 도착했어요' : `차가 오고 있어요 · ${km(car.remainingM)}`}</p>
            <p className="eta-min">{arrived ? meet.name : `약 ${car.etaMin}분`}</p>
          </div>
          {!arrived && (
            <div>
              <p className="sub">도착 예정</p>
              <p className="eta-at">{car.etaAt}</p>
            </div>
          )}
        </div>

        <ol className="steps">
          {STEPS.map((s, i) => (
            <li key={s} className={i <= step ? 'done' : undefined} aria-current={i === step ? 'step' : undefined}>
              {s}
            </li>
          ))}
        </ol>

        <div className="place">
          <div className="place-photo">[사진]</div>
          <div className="place-body">
            <p className="place-label">만날 곳</p>
            <p className="place-name">
              {meet.name}
              {changed && <span className="badge-changed">바뀐 장소</span>}
            </p>
            <p className="place-desc">{changed ? `${meet.desc} · ${apById(FIRST_MEET).name}에서 바뀜` : meet.desc}</p>
          </div>
          {!arrived && (
            <button
              className="btn-outline-accent"
              disabled={visit.myProposal?.status === 'PENDING'}
              onClick={() => navigate('move')}
            >
              <img src={swap} alt="" width={14} height={14} />
              {visit.myProposal?.status === 'PENDING' ? '응답 대기 중' : '위치 바꾸기'}
            </button>
          )}
        </div>

        <button className="btn-primary" onClick={() => update((v) => ({ ...v, ready: !v.ready }))}>
          {visit.ready ? '나가는 중이에요' : '준비됐어요'}
        </button>
      </section>
    </main>
  );
}
