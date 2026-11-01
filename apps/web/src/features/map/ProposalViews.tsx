import { useState, type RefObject } from 'react';
import type { LatLng } from '../../data/geo';
import type { MapHandle } from './KakaoMap';
import pinMeet from '../../assets/map/w01/pin-meet.svg';
import pinNew from '../../assets/map/w02/pin-new.svg';

type View = 'from' | 'to' | 'all';

/** 위치 변경 제안 중 기존 목적지·제안 목적지·둘 다를 골라 본다 */
export default function ProposalViews({ map, from, to }: { map: RefObject<MapHandle | null>; from: LatLng; to: LatLng }) {
  const [view, setView] = useState<View>('all');
  const choose = (next: View) => {
    const m = map.current;
    if (!m) return;
    setView(next);
    if (next === 'all') m.fit([from, to]);
    else m.setView(next === 'from' ? from : to, 17);
  };
  return <div className="map-views">
    <button aria-pressed={view === 'from'} onClick={() => choose('from')}><img src={pinMeet} alt="" width={10} height={14} />기존 목적지</button>
    <button aria-pressed={view === 'to'} onClick={() => choose('to')}><img src={pinNew} alt="" width={10} height={14} />변경제안 목적지</button>
    <button aria-pressed={view === 'all'} onClick={() => choose('all')}>전체</button>
  </div>;
}
