// W04 위치 바꾸기 (동승자) — 핀 조정 → 응답 대기
import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type L from 'leaflet';
import LeafletMap, { type MapMarker } from '../../features/map/LeafletMap';
import { ACCESS_POINTS, apById, type LatLng } from '../../data/dummy';
import { distanceM, nearest } from '../../data/geo';
import { update, useVisit } from '../../data/store';
import { useMyPosition } from '../../data/useMyPosition';
import back from '../../assets/icons/back.svg';
import pinTarget from '../../assets/icons/pin-target.svg';
import locate from '../../assets/icons/locate.svg';

export default function MovePage() {
  const navigate = useNavigate();
  const visit = useVisit();
  const me = useMyPosition();
  const current = apById(visit.meetId);
  const [center, setCenter] = useState<LatLng>(current.pos);
  const map = useRef<L.Map | null>(null);
  const mine = visit.myProposal;
  const waiting = mine?.status === 'PENDING';
  const toVisit = () => navigate('..', { relative: 'path', replace: true });

  // 운전자가 답하면 W01로 (결과는 W01 위 알림 W05)
  const openedAt = useRef(Date.now());
  useEffect(() => {
    if (mine && mine.status !== 'PENDING' && mine.at >= openedAt.current - 60_000) toVisit();
  });

  const pick = nearest(ACCESS_POINTS, center);
  // 확인된 지점에 붙고, 지금 만날 곳이 아닐 때만 보내기 가능
  const canSend = pick.snapped && pick.point.id !== current.id;
  const target = waiting ? apById(mine.toId) : pick.point;

  const markers = useMemo<MapMarker[]>(() => [
    ...ACCESS_POINTS.filter((a) => a.id !== current.id).map((a) => ({ pos: a.pos, kind: 'ap' as const, label: a.name })),
    { pos: current.pos, kind: 'meet', label: '지금' },
    { pos: me, kind: 'me' },
  ], [current, me]);

  const send = () => {
    update((v) => ({ ...v, myProposal: { fromId: v.meetId, toId: pick.point.id, status: 'PENDING', at: Date.now() } }));
  };
  const cancel = () => {
    update((v) => ({ ...v, myProposal: null }));
    toVisit();
  };

  return (
    <main className="screen">
      <header className="topbar">
        <button className="icon-btn" onClick={waiting ? cancel : toVisit} aria-label="뒤로"><img src={back} alt="" /></button>
        <h1>만날 곳 바꾸기</h1>
      </header>

      <LeafletMap center={current.pos} zoom={17} height={576} markers={markers} onMoveEnd={waiting ? undefined : setCenter} mapRef={map}>
        <p className="hint">{waiting ? '운전자에게 보냈어요' : '지도를 움직여 핀을 맞춰 주세요'}</p>
        <div className="target" aria-hidden>
          <span className="target-tag">{waiting ? '응답 기다리는 중' : '여기로 바꿀까요?'}</span>
          <img src={pinTarget} alt="" />
          <span className="target-shadow" />
        </div>
        <button className="locate" aria-label="내 위치로" onClick={() => map.current?.setView(me, 17)}>
          <img src={locate} alt="" />
        </button>
      </LeafletMap>

      <section className="sheet">
        {waiting ? (
          <>
            <p className="waiting-title">운전자 응답 기다리는 중</p>
            <p className="sub">
              {current.name} → {target.name} · 수락 전까지는 {current.name} 그대로예요
            </p>
            <button className="btn-cancel" onClick={cancel}>제안 취소</button>
          </>
        ) : (
          <>
            <div className="target-info">
              <div>
                <p className="target-name">{pick.snapped ? target.name : '지도에서 고른 위치'}</p>
                <p className="target-note">
                  {pick.snapped
                    ? `${target.desc} · 확인된 승하차 지점`
                    : `가장 가까운 승하차 지점: ${pick.point.name} (${Math.round(pick.distanceM)}m) · 그쪽으로 옮겨 주세요`}
                </p>
              </div>
              <p className="sub">지금 위치에서 {Math.round(distanceM(current.pos, center))}m</p>
            </div>
            <button className="btn-primary" disabled={!canSend} onClick={send}>
              여기로 조정하기
            </button>
          </>
        )}
      </section>
    </main>
  );
}
