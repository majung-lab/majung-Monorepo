// W01 방문 카드 · W05 변경 결과 알림
import { useEffect, useMemo, useRef, useState } from 'react';
import type { FacilityWeather, MoveConditions } from '@majung/shared';
import { useNavigate } from 'react-router-dom';
import KakaoMap, { type MapHandle, type MapMarker } from '../../features/map/KakaoMap';
import { pointPosition, useNow, useVisit } from '../../data/store';
import { api } from '../../api/client';
import type { LatLng } from '../../data/geo';
import check from '../../assets/icons/check.svg';
import dotMe from '../../assets/icons/dot-me.svg';
import dotCar from '../../assets/icons/dot-car.svg';
import pinMeet from '../../assets/map/w01/pin-meet.svg';
import swap from '../../assets/icons/swap.svg';

type MapView = 'me' | 'car' | 'meet' | 'all';
const STEPS = ['출발 준비', '이동 중', '도착'];
// 준비 버튼 한 개로 단계를 넘긴다: 준비됐어요 → 지금 나갈게요 → 로비에 도착했어요
const NEXT_READINESS = { READY: 'HEADING_OUT', HEADING_OUT: 'AT_LOBBY' } as const;
const READINESS_BUTTON = { READY: '지금 나갈게요', HEADING_OUT: '로비에 도착했어요', AT_LOBBY: '로비에 있어요 · 전달됨' } as const;
const CONDITIONS: [keyof MoveConditions, string][] = [['stroller', '유모차'], ['luggage', '짐 많음'], ['avoidStairs', '계단 피하기'], ['indoorOnly', '실내로만']];
const hhmm = (t: number | string) => new Date(t).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', hour12: false });

export default function VisitPage() {
  const navigate = useNavigate();
  const { snapshot, token, busy, position: me, positionError, mutate } = useVisit();
  const { visit, facility, vehicle, proposals, passengerWaitSpot, passengerConditions: conditions } = snapshot;
  const now = useNow();
  const meet = facility.accessPoints.find((p) => p.id === visit.confirmedAccessPointId)!;
  const meetPos = pointPosition(meet);
  const car: LatLng | null = vehicle?.lat != null && vehicle.lng != null ? [vehicle.lat, vehicle.lng] : null;
  const [view, setView] = useState<MapView>('all');
  // 운전자 제안 화면을 다녀와도 닫은 알림이 다시 뜨지 않게 기기에 남긴다
  const seenKey = `majung:seen-result:${token}`;
  const [dismissed, setDismissed] = useState(() => localStorage.getItem(seenKey));
  const map = useRef<MapHandle | null>(null);
  const mine = proposals.find((p) => p.proposedBy === 'PASSENGER' && p.status === 'PENDING');
  const proposed = mine && facility.accessPoints.find((p) => p.id === mine.accessPointId);
  const latest = proposals[0]; // 서버가 최신순으로 준다
  const showsResult = latest && latest.id !== dismissed && (latest.proposedBy === 'PASSENGER' ? !['PENDING', 'CANCELLED'].includes(latest.status) : latest.status === 'CANCELLED');
  const result = showsResult ? latest : null;
  const dismiss = (id: string) => { localStorage.setItem(seenKey, id); setDismissed(id); };
  const changed = proposals.some((p) => p.status === 'ACCEPTED');
  const arrived = visit.phase === 'ARRIVED';
  const step = arrived ? 2 : visit.phase === 'MOVING' ? 1 : 0;
  const stale = !!vehicle && now - Date.parse(vehicle.recordedAt) > 60_000;
  // 운전자가 내비 안내를 끄면 남은 시간은 모른다. 차 위치는 계속 온다
  const navEnded = vehicle?.navResult === 'ended' && visit.phase === 'MOVING';
  const etaSec = !navEnded && !stale && vehicle?.etaSec != null ? Math.max(0, vehicle.etaSec - (now - Date.parse(vehicle.recordedAt)) / 1000) : null;
  // 안내 종료 알림은 한 번만. 링크를 다시 열어도 확인한 알림은 다시 띄우지 않는다
  const endedKey = `majung:seen-nav-ended:${token}`;
  const [endedSeen, setEndedSeen] = useState(() => { try { return localStorage.getItem(endedKey); } catch { return null; } });
  const showEnded = navEnded && !!vehicle?.navEndedAt && endedSeen !== vehicle.navEndedAt;
  const closeEnded = (thenCar: boolean) => {
    const at = vehicle?.navEndedAt ?? '';
    try { localStorage.setItem(endedKey, at); } catch { /* 저장 못 해도 이번 화면에서는 닫는다 */ }
    setEndedSeen(at);
    if (thenCar) choose('car');
  };

  const choose = (v: MapView) => {
    const m = map.current;
    if (!m) return;
    setView(v);
    if (v === 'me' && me) m.setView(me, 17);
    else if (v === 'car' && car) m.setView(car, 16);
    else if (v === 'meet') m.setView(meetPos, 17);
    else m.fit([meetPos, ...(me ? [me] : []), ...(car ? [car] : [])]);
  };
  const markers = useMemo<MapMarker[]>(() => [
    { pos: meetPos, kind: 'meet' },
    ...(proposed ? [{ pos: pointPosition(proposed), kind: 'proposed' as const, label: '제안' }] : []),
    ...(car ? [{ pos: car, kind: 'car' as const }] : []),
    ...(me ? [{ pos: me, kind: 'me' as const }] : []),
  ], [meet.lat, meet.lng, proposed?.id, vehicle?.lat, vehicle?.lng, me]);
  const readiness = visit.passengerReadiness;
  // 날씨와 추천: 대기 지점·이동 조건·만날 곳이 바뀔 때, 그리고 10분마다 다시 받는다. 못 받으면 숨긴다
  const [advice, setAdvice] = useState<FacilityWeather | null>(null);
  const adviceKey = `${passengerWaitSpot?.id}|${JSON.stringify(conditions)}|${visit.confirmedAccessPointId}`;
  useEffect(() => {
    let live = true;
    const load = () => void api.recommendation(token).then((r) => { if (live) setAdvice(r); });
    load();
    const timer = setInterval(load, 10 * 60_000);
    return () => { live = false; clearInterval(timer); };
  }, [token, adviceKey]);
  const weather = advice?.weather ?? null;
  const best = advice?.ranking.find((r) => r.recommended);
  const bestPoint = best && facility.accessPoints.find((p) => p.id === best.accessPointId);
  const here = advice?.ranking.find((r) => r.accessPointId === meet.id);
  // 고른 대기 지점에서 만날 곳까지 동선이 있으면 그 동선의 지붕 여부로 안내한다
  const meetCovered = passengerWaitSpot?.walkMinutes != null ? passengerWaitSpot.covered : meet.covered;

  return (
    <main className="screen">
      <KakaoMap center={pointPosition(facility)} markers={markers} fitTo={[meetPos, ...(car ? [car] : []), ...(me ? [me] : [])]} mapRef={map}>
        <span className="chip chip-brand">마중</span>
        <span className="chip chip-time">{navEnded && vehicle ? `차 위치 ${hhmm(vehicle.recordedAt)}` : `${hhmm(visit.updatedAt)} 갱신`}</span>
        <button className="zoom" style={{ top: 64 }} aria-label="지도 확대" onClick={() => map.current?.zoomIn()}>+</button>
        <button className="zoom" style={{ top: 116 }} aria-label="지도 축소" onClick={() => map.current?.zoomOut()}>−</button>
        <div className="map-views">
          <button disabled={!me} aria-pressed={view === 'me'} onClick={() => choose('me')}><img src={dotMe} alt="" width={12} height={12} />내 위치</button>
          <button disabled={!car} aria-pressed={view === 'car'} onClick={() => choose('car')}><img src={dotCar} alt="" width={12} height={12} />차 위치</button>
          <button aria-pressed={view === 'meet'} onClick={() => choose('meet')}><img src={pinMeet} alt="" width={10} height={14} />목적지</button>
          <button aria-pressed={view === 'all'} onClick={() => choose('all')}>전체</button>
        </div>
        {result && <div className={`result ${result.status === 'ACCEPTED' ? 'accepted' : 'rejected'}`} role="status">
          {result.status === 'ACCEPTED' && <img src={check} alt="" />}
          <div className="result-body">
            <p className="result-title">{result.status === 'ACCEPTED' ? '운전자가 수락했어요' : result.status === 'REJECTED' ? '운전자가 그대로 가자고 했어요' : result.status === 'CANCELLED' ? '운전자가 제안을 취소했어요' : '운전자가 답하지 않아 그대로예요'}</p>
            <p className="result-sub">{meet.name}에서 만나요</p>
          </div>
          <button onClick={() => dismiss(result.id)}>확인</button>
        </div>}
      </KakaoMap>
      <section className="sheet">
        <p className="data-notice">{facility.name} · 예시 시설·승하차 지점 데이터</p>
        {weather && <p className={`weather-note${weather.rainSoon ? ' wet' : ''}`} role="note">
          {weather.rainSoon ? '☂ ' : ''}{weather.summary}
          {weather.rainSoon && !arrived && <span>{meetCovered ? ' · 만날 곳은 지붕이 있어요' : ' · 만날 곳에 지붕이 없어요. 우산을 챙겨 주세요'}</span>}
        </p>}
        <div className="eta">
          <div>
            <p className="sub">{arrived ? '운전자가 도착을 확인했어요' : visit.phase === 'PREPARING' ? '운전자가 출발을 준비하고 있어요' : navEnded ? '운전자가 내비 안내를 종료했어요' : '차가 오고 있어요'}</p>
            <p className="eta-min">{arrived ? meet.name : navEnded ? '도착 시간을 알 수 없어요' : etaSec != null ? `약 ${Math.ceil(etaSec / 60)}분` : '도착 시간 확인 중'}</p>
            {navEnded && <p className="sub nav-ended-sub">차 위치는 지도에서 실시간으로 볼 수 있어요</p>}
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
        {!arrived && facility.waitSpots.length > 0 && <div className="wait-spots">
          <p className="place-label">지금 어디 계세요? · 운전자 화면에 보여요</p>
          <div className="wait-spot-list">
            {facility.waitSpots.map((w) => {
              const chosen = passengerWaitSpot?.id === w.id;
              const toMeet = meet.waitSpots.find((x) => x.id === w.id);
              return <button key={w.id} aria-pressed={chosen} disabled={busy}
                onClick={() => void mutate((s) => api.waitSpot(token, s.visit.version, chosen ? null : w.id))}>
                <span className="wait-spot-name">{w.name}</span>
                <span className="wait-spot-meta">{toMeet ? `만날 곳까지 걸어서 ${toMeet.walkMinutes}분${toMeet.covered ? ' · 지붕 있음' : ''}` : '만날 곳까지 동선 정보 없음'}</span>
              </button>;
            })}
          </div>
          <p className="place-label conditions-label">이동 조건 · 맞는 동선을 찾아 드려요</p>
          <div className="conditions">
            {CONDITIONS.map(([key, label]) => <button key={key} aria-pressed={conditions[key]} disabled={busy}
              onClick={() => void mutate((s) => api.conditions(token, s.visit.version, { ...s.passengerConditions, [key]: !s.passengerConditions[key] }))}>{label}</button>)}
          </div>
        </div>}
        {!arrived && passengerWaitSpot && here && (best && bestPoint && best.accessPointId !== meet.id
          ? <div className="advice" role="status">
              <p className="advice-label">{passengerWaitSpot.name}에서는 여기가 더 편해요</p>
              <p className="advice-name">{bestPoint.name}</p>
              <p className="advice-reason">{best.reason}</p>
              <p className="advice-sub">지금 만날 곳({meet.name}) · {here.excludedReason ?? here.reason}</p>
              <button className="btn-outline-accent" disabled={busy || !!mine}
                onClick={() => void mutate((s) => api.propose(token, s.visit.version, best.accessPointId, `${passengerWaitSpot.name}에서 ${best.reason}`))}>
                {mine ? '응답 대기 중' : '여기로 바꾸자고 하기'}
              </button>
            </div>
          : <p className={`advice-ok${here.excludedReason ? ' warn' : ''}`} role="status">
              {here.excludedReason ? `지금 만날 곳 · ${here.excludedReason}` : `지금 만날 곳이 가장 편해요 · ${here.reason}`}
            </p>)}
        <p className="location-note" role="status">{positionError || (me ? '내 실제 위치를 차량에 공유하고 있어요.' : '위치 권한을 허용하면 차량에 내 위치를 공유해요.')}{stale ? ' 차량 위치가 1분 이상 갱신되지 않았어요.' : !car ? ' 차량 위치는 아직 전송되지 않았어요.' : ''}</p>
        {mine && <button className="btn-cancel" onClick={() => navigate('move')}>보낸 제안 확인</button>}
        <button className="btn-primary" disabled={busy || readiness === 'AT_LOBBY'} onClick={() => void mutate((s) => api.readiness(token, s.visit.version, readiness === 'READY' || readiness === 'HEADING_OUT' ? NEXT_READINESS[readiness] : 'READY'))}>
          {readiness === 'READY' || readiness === 'HEADING_OUT' || readiness === 'AT_LOBBY' ? READINESS_BUTTON[readiness] : '준비됐어요'}
        </button>
      </section>
      {showEnded && <div className="dialog-backdrop" role="presentation">
        <div className="dialog" role="alertdialog" aria-labelledby="nav-ended-title" aria-describedby="nav-ended-body">
          <p id="nav-ended-title" className="dialog-title">운전자가 안내를 종료했어요</p>
          <p id="nav-ended-body" className="dialog-body">그래서 정확한 도착 시간은 알 수 없어요. 차 위치는 지도에서 계속 실시간으로 보여 드려요.</p>
          <div className="dialog-actions">
            <button className="btn-outline-accent" onClick={() => closeEnded(false)}>확인</button>
            <button className="btn-primary" disabled={!car} onClick={() => closeEnded(true)}>차 위치 보기</button>
          </div>
        </div>
      </div>}
    </main>
  );
}
