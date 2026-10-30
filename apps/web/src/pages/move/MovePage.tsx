// W04 위치 바꾸기 (동승자) — 서버에 제안 → 실제 운전자 응답 대기
import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type L from 'leaflet';
import LeafletMap, { type MapMarker } from '../../features/map/LeafletMap';
import { distanceM, nearest, type LatLng } from '../../data/geo';
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
  const map = useRef<L.Map | null>(null);
  const mine = proposals.find((p) => p.proposedBy === 'PASSENGER' && p.status === 'PENDING');
  const waiting = !!mine;
  const toVisit = () => navigate('..', { relative: 'path', replace: true });
  useEffect(() => {
    if (mine) setSentId(mine.id);
    if (sentId && proposals.some((p) => p.id === sentId && p.status !== 'PENDING')) toVisit();
  }, [mine, proposals, sentId, navigate]);

  const pick = points.length ? nearest(points, center) : null;
  const target = waiting ? facility.accessPoints.find((p) => p.id === mine.accessPointId) : pick?.point;
  const canSend = !busy && !waiting && pick?.snapped && pick.point.id !== current.id;
  const markers = useMemo<MapMarker[]>(() => [
    ...points.filter((a) => a.id !== current.id).map((a) => ({ pos: a.pos, kind: 'ap' as const, label: a.name })),
    { pos: pointPosition(current), kind: 'meet', label: '지금' },
    ...(me ? [{ pos: me, kind: 'me' as const }] : []),
  ], [points, current, me]);
  const send = () => { if (canSend && pick) void mutate((s) => api.propose(token, s.visit.version, pick.point.id)); };
  const cancel = async () => { if (mine && await mutate((s) => api.cancel(token, s.visit.version, mine.id))) toVisit(); };

  return <main className="screen">
    <header className="topbar"><button className="icon-btn" onClick={toVisit} aria-label="뒤로"><img src={back} alt="" /></button><h1>만날 곳 바꾸기</h1></header>
    <LeafletMap center={pointPosition(current)} zoom={17} height={430} markers={markers} onMoveEnd={waiting ? undefined : setCenter} mapRef={map}>
      <p className="hint">{waiting ? '운전자에게 보냈어요' : '장소를 고르거나 지도를 움직여 주세요'}</p>
      <div className="target" aria-hidden><span className="target-tag">{waiting ? '응답 기다리는 중' : '여기로 바꿀까요?'}</span><img src={pinTarget} alt="" /><span className="target-shadow" /></div>
      <button className="locate" disabled={!me} aria-label="내 위치로" onClick={() => me && map.current?.setView(me, 17)}><img src={locate} alt="" /></button>
    </LeafletMap>
    <section className="sheet">
      <p className="data-notice">예시 시설·승하차 지점 데이터</p>
      {waiting ? <>
        <p className="waiting-title">운전자 응답 기다리는 중</p>
        <p className="sub">{current.name} → {target?.name} · 수락 전까지는 {current.name} 그대로예요</p>
        <button className="btn-cancel" disabled={busy} onClick={cancel}>제안 취소</button>
      </> : <>
        <div className="point-options" aria-label="변경할 장소">{points.map((point) => <button key={point.id} disabled={point.id === current.id || busy} aria-pressed={pick?.snapped && pick.point.id === point.id} onClick={() => { setCenter(point.pos); map.current?.setView(point.pos, 17); }}>{point.name}{point.id === current.id ? ' (지금)' : ''}</button>)}</div>
        <div className="target-info"><div>
          <p className="target-name">{pick?.snapped ? target?.name : '지도에서 고른 위치'}</p>
          <p className="target-note">{!pick ? '변경 가능한 승하차 지점이 없어요.' : pick.snapped ? target?.description : `가장 가까운 승하차 지점: ${pick.point.name} (${Math.round(pick.distanceM)}m) · 그쪽으로 옮겨 주세요`}</p>
        </div><p className="sub">만날 곳에서 {Math.round(distanceM(pointPosition(current), center))}m</p></div>
        <button className="btn-primary" disabled={!canSend} onClick={send}>{busy ? '보내는 중…' : '여기로 조정하기'}</button>
      </>}
    </section>
  </main>;
}
