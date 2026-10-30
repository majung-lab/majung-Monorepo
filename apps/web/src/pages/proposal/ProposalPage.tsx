// W02 운전자 제안 받기. 만료 여부와 응답 결과는 서버가 결정한다.
import { useMemo } from 'react';
import LeafletMap, { type MapMarker } from '../../features/map/LeafletMap';
import { pointPosition, useNow, useVisit } from '../../data/store';
import { api } from '../../api/client';
import type { LatLng } from '../../data/geo';
import arrowRight from '../../assets/icons/arrow-right.svg';

const short = (name: string) => name.split(' ')[0];
export default function ProposalPage() {
  const { snapshot, token, busy, mutate } = useVisit();
  const now = useNow();
  const p = snapshot.proposals.find((p) => p.proposedBy === 'DRIVER' && p.status === 'PENDING');
  const points = snapshot.facility.accessPoints;
  const from = points.find((a) => a.id === (p?.fromAccessPointId ?? snapshot.visit.confirmedAccessPointId))!;
  const to = points.find((a) => a.id === (p?.accessPointId ?? snapshot.visit.confirmedAccessPointId))!;
  const fromPos = pointPosition(from), toPos = pointPosition(to);
  const markers = useMemo<MapMarker[]>(() => [
    { pos: fromPos, kind: 'meet', label: '지금' },
    { pos: toPos, kind: 'proposed', label: '제안' },
  ], [from, to]);
  const center: LatLng = [(from.lat + to.lat) / 2, (from.lng + to.lng) / 2];
  if (!p) return null;
  const leftMin = Math.max(0, Math.ceil((Date.parse(p.expiresAt) - now) / 60_000));
  const answer = (accept: boolean) => void mutate((s) => api.respond(token, s.visit.version, p.id, accept));
  return <main className="screen">
    <LeafletMap center={center} zoom={17} markers={markers} fitTo={[fromPos, toPos]}><span className="chip chip-brand">마중</span></LeafletMap>
    <section className="sheet sheet-dialog" role="dialog" aria-labelledby="proposal-title">
      <h1 id="proposal-title" className="dialog-title">운전자가 만날 곳을 바꾸자고 해요</h1>
      <div className="change"><span className="change-from">{from.name}</span><img src={arrowRight} alt="→" style={{ margin: '0 4px' }} /><span className="change-to">{to.name}</span></div>
      {p.reason && <p className="reason">{p.reason}</p>}
      <p className="place-desc">{to.description}</p>
      <p className="note">{leftMin > 0 ? `${leftMin}분 안에 답하지 않으면 ${from.name} 그대로예요` : '제안 유효시간이 끝났어요. 서버 확인 중이에요.'}</p>
      <p className="data-notice">예시 시설·승하차 지점 데이터</p>
      <div className="actions">
        <button className="btn-secondary" disabled={busy || leftMin === 0} onClick={() => answer(false)}>{short(from.name)} 그대로</button>
        <button className="btn-primary" disabled={busy || leftMin === 0} onClick={() => answer(true)}>{short(to.name)} 좋아요</button>
      </div>
    </section>
  </main>;
}
