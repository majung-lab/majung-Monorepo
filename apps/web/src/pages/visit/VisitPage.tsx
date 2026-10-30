// W01 방문 카드 · W05 변경 결과 알림
import { useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type L from 'leaflet';
import LeafletMap, { fitAll, type MapMarker } from '../../features/map/LeafletMap';
import { pointPosition, useNow, useVisit } from '../../data/store';
import { api } from '../../api/client';
import type { LatLng } from '../../data/geo';
import check from '../../assets/icons/check.svg';
import dotMe from '../../assets/icons/dot-me.svg';
import dotCar from '../../assets/icons/dot-car.svg';
import swap from '../../assets/icons/swap.svg';

type MapView = 'me' | 'car' | 'all';
const STEPS = ['출발 준비', '이동 중', '도착'];
const hhmm = (t: number | string) => new Date(t).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', hour12: false });

export default function VisitPage() {
  const navigate = useNavigate();
  const { snapshot, token, busy, position: me, positionError, mutate } = useVisit();
  const { visit, facility, vehicle, proposals } = snapshot;
  const now = useNow();
  const meet = facility.accessPoints.find((p) => p.id === visit.confirmedAccessPointId)!;
  const meetPos = pointPosition(meet);
  const car: LatLng | null = vehicle?.lat != null && vehicle.lng != null ? [vehicle.lat, vehicle.lng] : null;
  const [view, setView] = useState<MapView>('all');
  const [dismissed, setDismissed] = useState<string | null>(null);
  const map = useRef<L.Map | null>(null);
  const mine = proposals.find((p) => p.proposedBy === 'PASSENGER' && p.status === 'PENDING');
  const latest = [...proposals].sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt)).find((p) => p.proposedBy === 'PASSENGER');
  const result = latest && latest.status !== 'PENDING' && latest.id !== dismissed ? latest : null;
  const changed = proposals.some((p) => p.status === 'ACCEPTED');
  const arrived = visit.phase === 'ARRIVED';
  const step = arrived ? 2 : visit.phase === 'MOVING' ? 1 : 0;
  const stale = !!vehicle && now - Date.parse(vehicle.recordedAt) > 60_000;
  const etaSec = !stale && vehicle?.etaSec != null ? Math.max(0, vehicle.etaSec - (now - Date.parse(vehicle.recordedAt)) / 1000) : null;

  const choose = (v: MapView) => {
    const m = map.current;
    if (!m) return;
    setView(v);
    if (v === 'me' && me) m.setView(me, 17);
    else if (v === 'car' && car) m.setView(car, 16);
    else fitAll(m, [meetPos, ...(me ? [me] : []), ...(car ? [car] : [])]);
  };
  const markers = useMemo<MapMarker[]>(() => [
    { pos: meetPos, kind: 'meet' },
    ...(car ? [{ pos: car, kind: 'car' as const }] : []),
    ...(me ? [{ pos: me, kind: 'me' as const }] : []),
  ], [meet.lat, meet.lng, vehicle?.lat, vehicle?.lng, me]);
  const readiness = visit.passengerReadiness;

  return (
    <main className="screen">
      <LeafletMap center={pointPosition(facility)} markers={markers} fitTo={[meetPos, ...(car ? [car] : []), ...(me ? [me] : [])]} mapRef={map}>
        <span className="chip chip-brand">마중</span>
        <span className="chip chip-time">{hhmm(visit.updatedAt)} 갱신</span>
        <button className="zoom" style={{ top: 64 }} aria-label="지도 확대" onClick={() => map.current?.zoomIn()}>+</button>
        <button className="zoom" style={{ top: 116 }} aria-label="지도 축소" onClick={() => map.current?.zoomOut()}>−</button>
        <div className="map-views">
          <button disabled={!me} aria-pressed={view === 'me'} onClick={() => choose('me')}><img src={dotMe} alt="" width={12} height={12} />내 위치</button>
          <button disabled={!car} aria-pressed={view === 'car'} onClick={() => choose('car')}><img src={dotCar} alt="" width={12} height={12} />차 위치</button>
          <button aria-pressed={view === 'all'} onClick={() => choose('all')}>전체</button>
        </div>
        {result && <div className={`result ${result.status === 'ACCEPTED' ? 'accepted' : 'rejected'}`} role="status">
          {result.status === 'ACCEPTED' && <img src={check} alt="" />}
          <div className="result-body">
            <p className="result-title">{result.status === 'ACCEPTED' ? '운전자가 수락했어요' : result.status === 'REJECTED' ? '운전자가 그대로 가자고 했어요' : '변경 제안이 끝났어요'}</p>
            <p className="result-sub">{meet.name}에서 만나요</p>
          </div>
          <button onClick={() => setDismissed(result.id)}>확인</button>
        </div>}
      </LeafletMap>
      <section className="sheet">
        <p className="data-notice">{facility.name} · 예시 시설·승하차 지점 데이터</p>
        <div className="eta">
          <div>
            <p className="sub">{arrived ? '운전자가 도착을 확인했어요' : visit.phase === 'PREPARING' ? '운전자가 출발을 준비하고 있어요' : '차가 오고 있어요'}</p>
            <p className="eta-min">{arrived ? meet.name : etaSec != null ? `약 ${Math.ceil(etaSec / 60)}분` : '도착 시간 확인 중'}</p>
          </div>
          {!arrived && etaSec != null && <div><p className="sub">도착 예정</p><p className="eta-at">{hhmm(now + etaSec * 1000)}</p></div>}
        </div>
        <ol className="steps">{STEPS.map((s, i) => <li key={s} className={i <= step ? 'done' : undefined} aria-current={i === step ? 'step' : undefined}>{s}</li>)}</ol>
        <div className="place">
          <div className="place-body">
            <p className="place-label">만날 곳</p>
            <p className="place-name">{meet.name}{changed && <span className="badge-changed">바뀐 장소</span>}</p>
            <p className="place-desc">{meet.description}</p>
          </div>
          <button className="btn-outline-accent" disabled={busy || !!mine} onClick={() => navigate('move')}>
            <img src={swap} alt="" width={14} height={14} />{mine ? '응답 대기 중' : '위치 바꾸기'}
          </button>
        </div>
        <p className="location-note" role="status">{positionError || (me ? '내 실제 위치를 차량에 공유하고 있어요.' : '위치 권한을 허용하면 차량에 내 위치를 공유해요.')}{stale ? ' 차량 위치가 1분 이상 갱신되지 않았어요.' : !car ? ' 차량 위치는 아직 전송되지 않았어요.' : ''}</p>
        {mine && <button className="btn-cancel" onClick={() => navigate('move')}>보낸 제안 확인</button>}
        <button className="btn-primary" disabled={busy || readiness === 'HEADING_OUT'} onClick={() => void mutate((s) => api.readiness(token, s.visit.version, readiness === 'READY' ? 'HEADING_OUT' : 'READY'))}>
          {readiness === 'HEADING_OUT' ? '나가는 중이에요 · 전달됨' : readiness === 'READY' ? '지금 나갈게요' : '준비됐어요'}
        </button>
      </section>
    </main>
  );
}
