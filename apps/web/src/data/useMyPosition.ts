import { useEffect, useState } from 'react';
import { FACILITY, MY_POS, type LatLng } from './dummy';
import { distanceM } from './geo';

/** 시설 근처(2km)에 있을 때만 실제 위치, 아니면 더미 위치 (개발 PC에서도 시연되게) */
const NEAR_M = 2000;

export function useMyPosition(): LatLng {
  const [pos, setPos] = useState<LatLng>(MY_POS);
  useEffect(() => {
    if (!('geolocation' in navigator)) return;
    const id = navigator.geolocation.watchPosition(
      ({ coords }) => {
        const p: LatLng = [coords.latitude, coords.longitude];
        if (distanceM(p, FACILITY.pos) <= NEAR_M) setPos(p);
      },
      () => { /* 권한 거절 → 더미 위치 유지 */ },
    );
    return () => navigator.geolocation.clearWatch(id);
  }, []);
  return pos;
}
