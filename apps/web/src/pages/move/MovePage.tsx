// W04 위치 바꾸기 (동승자) — 서버에 제안 → 실제 운전자 응답 대기
import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import KakaoMap, { type MapHandle, type MapMarker } from '../../features/map/KakaoMap';
import { distanceM, nearest, SNAP_RADIUS_M, type LatLng } from '../../data/geo';
import { pointPosition, useVisit } from '../../data/store';
import { api } from '../../api/client';
import back from '../../assets/icons/back.svg';
import pinTarget from '../../assets/icons/pin-target.svg';
import locate from '../../assets/icons/locate.svg';

export default function MovePage() {
  const navigate = useNavigate();
  const { snapshot, token, busy, position: me, mutate } = useVisit();
  const { facility, visit, proposals } = snapshot;
  const current = facility.accessPoints.find((p) => p.id === visit.confirmedAccessPointId)!;
  const points = useMemo(() => facility.accessPoints.filter((p) => p.allowed && p.kind === (visit.mode === 'PARK' ? 'PARKING_ENTRANCE' : 'PICKUP')).map((p) => ({ ...p, pos: pointPosition(p) })), [facility, visit.mode]);
  const [center, setCenter] = useState<LatLng>(pointPosition(current));
  const [sentId, setSentId] = useState<string | null>(null);
  const map = useRef<MapHandle | null>(null);
  const cancelling = useRef(false);
  const mine = proposals.find((p) => p.proposedBy === 'PASSENGER' && p.status === 'PENDING');
  const waiting = !!mine;
  const toVisit = () => navigate('..', { relative: 'path', replace: true });
  useEffect(() => {
    if (mine) setSentId(mine.id);
    if (sentId && !cancelling.current && proposals.some((p) => p.id === sentId && p.status !== 'PENDING')) toVisit();
  }, [mine, proposals, sentId, navigate]);

  const pick = points.length ? nearest(points, center) : null;
  const target = waiting ? facility.accessPoints.find((p) => p.id === mine.accessPointId) : pick?.point;
  const canSend = !busy && !waiting && (pick?.snapped ? pick.point.id !== current.id : distanceM(pointPosition(current), center) > SNAP_RADIUS_M);
  const markers = useMemo<MapMarker[]>(() => [
    ...points.filter((a) => a.id !== current.id).map((a) => ({ pos: a.pos, kind: 'ap' as const, label: a.name })),
    { pos: pointPosition(current), kind: 'meet', label: '지금' },
    ...(waiting && target ? [{ pos: pointPosition(target), kind: 'proposed' as const, label: '제안' }] : []),
    ...(me ? [{ pos: me, kind: 'me' as const }] : []),
  ], [points, current, me, waiting, target?.id]);
  const send = () => { if (canSend) void mutate((s) => api.propose(token, s.visit.version, pick?.snapped ? pick.point.id : { lat: center[0], lng: center[1] })); };
  const cancel = async () => {
    if (!mine) return;
    cancelling.current = true;
    if (await mutate((s) => api.cancel(token, s.visit.version, mine.id))) setSentId(null);
    cancelling.current = false;
  };

  return <main className="screen">
    <header className="topbar"><button className="icon-btn" onClick={toVisit} aria-label="뒤로"><img src={back} alt="" /></button><h1>만날 곳 바꾸기</h1></header>
    <KakaoMap center={pointPosition(current)} zoom={17} height={430} markers={markers} onMoveEnd={setCenter} fitTo={waiting && target ? [pointPosition(current), pointPosition(target)] : undefined} mapRef={map}>
      <p className="hint">{waiting ? '운전자에게 보냈어요' : '지도를 움직여 핀을 원하는 곳에 놓아 주세요'}</p>
      {!waiting && <div className="target" aria-hidden><span className="target-tag">여기로 바꿀까요?</span><img src={pinTarget} alt="" /><span className="target-shadow" /></div>}
      <button className="locate" disabled={!me} aria-label="내 위치로" onClick={() => me && map.current?.setView(me, 17)}><img src={locate} alt="" /></button>
    </KakaoMap>
    <section className="sheet">
      <p className="data-notice">예시 시설·승하차 지점 데이터</p>
      {waiting ? <>
        <p className="waiting-title">운전자 응답 기다리는 중</p>
        <p className="sub">{current.name} → {target?.name} · 수락 전까지는 {current.name} 그대로예요</p>
        <button className="btn-cancel" disabled={busy} onClick={cancel}>제안 취소</button>
      </> : <>
        <div className="point-options" aria-label="변경할 장소">{points.map((point) => <button key={point.id} disabled={point.id === current.id || busy} aria-pressed={pick?.snapped && pick.point.id === point.id} onClick={() => { setCenter(point.pos); map.current?.setView(point.pos, 17); }}>{point.name}{point.id === current.id ? ' (지금)' : ''}</button>)}</div>
        <div className="target-info"><div>
          <p className="target-name">{pick?.snapped ? pick.point.name : '지도에서 고른 위치'}</p>
          <p className="target-note">{pick?.snapped ? pick.point.description : `확인되지 않은 위치예요. 차를 세우기 어려울 수 있어요${pick ? ` · 가장 가까운 승하차 지점: ${pick.point.name} (${Math.round(pick.distanceM)}m)` : ''}`}</p>
        </div><p className="sub">만날 곳에서 {Math.round(distanceM(pointPosition(current), center))}m</p></div>
        <button className="btn-primary" disabled={!canSend} onClick={send}>{busy ? '보내는 중…' : '여기로 조정하기'}</button>
      </>}
    </section>
  </main>;
}
