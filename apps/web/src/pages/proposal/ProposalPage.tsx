// W02 운전자 제안 받기
import { useEffect, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import LeafletMap, { type MapMarker } from '../../features/map/LeafletMap';
import { apById } from '../../data/dummy';
import { update, useNow, useVisit } from '../../data/store';
import arrowRight from '../../assets/icons/arrow-right.svg';

const TIMEOUT_MS = 10 * 60 * 1000;
const short = (name: string) => name.split(' ')[0];

export default function ProposalPage() {
  const navigate = useNavigate();
  const visit = useVisit();
  const now = useNow();
  const p = visit.driverProposal;
  const toVisit = () => navigate('..', { relative: 'path', replace: true });

  // 답했거나 제안이 없으면 W01. 10분 무응답이면 그대로
  useEffect(() => {
    if (!p || p.status !== 'PENDING') toVisit();
    else if (now - p.at >= TIMEOUT_MS) answer(false);
  });

  const from = apById(p?.fromId ?? visit.meetId);
  const to = apById(p?.toId ?? visit.meetId);
  const markers = useMemo<MapMarker[]>(() => [
    { pos: from.pos, kind: 'meet', label: '지금' },
    { pos: to.pos, kind: 'proposed', label: '제안' },
  ], [from, to]);
  const lines = useMemo(() => [{ points: [from.pos, to.pos], dashed: true }], [from, to]);
  const center: [number, number] = [(from.pos[0] + to.pos[0]) / 2, (from.pos[1] + to.pos[1]) / 2];

  function answer(accept: boolean) {
    update((v) => v.driverProposal
      ? { ...v, meetId: accept ? v.driverProposal.toId : v.meetId, driverProposal: { ...v.driverProposal, status: accept ? 'ACCEPTED' : 'REJECTED' } }
      : v);
  }

  if (!p) return null;
  const leftMin = Math.max(0, Math.ceil((TIMEOUT_MS - (now - p.at)) / 60000));

  return (
    <main className="screen">
      <LeafletMap center={center} zoom={17} markers={markers} lines={lines} fitTo={[from.pos, to.pos]}>
        <span className="chip chip-brand">마중</span>
      </LeafletMap>

      <section className="sheet sheet-dialog" role="dialog" aria-labelledby="proposal-title">
        <h1 id="proposal-title" className="dialog-title">운전자가 만날 곳을 바꾸자고 해요</h1>
        <div className="change">
          <span className="change-from">{from.name}</span>
          <img src={arrowRight} alt="→" style={{ margin: '0 4px' }} />
          <span className="change-to">{to.name}</span>
        </div>
        {p.reason && <p className="reason">{p.reason}</p>}
        <p className="note">{leftMin}분 안에 답하지 않으면 {from.name} 그대로예요</p>

        <div className="actions">
          <button className="btn-secondary" onClick={() => answer(false)}>{short(from.name)} 그대로</button>
          <button className="btn-primary" onClick={() => answer(true)}>{short(to.name)} 좋아요</button>
        </div>
      </section>
    </main>
  );
}
