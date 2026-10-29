import type { AccessPoint, LatLng } from './dummy';

const R = 6371000;
const rad = (d: number) => (d * Math.PI) / 180;

/** 두 좌표 사이 거리 (m), haversine */
export function distanceM([lat1, lng1]: LatLng, [lat2, lng2]: LatLng): number {
  const a = Math.sin(rad(lat2 - lat1) / 2) ** 2 + Math.cos(rad(lat1)) * Math.cos(rad(lat2)) * Math.sin(rad(lng2 - lng1) / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(a));
}

export function routeLengthM(route: LatLng[]): number {
  let sum = 0;
  for (let i = 1; i < route.length; i++) sum += distanceM(route[i - 1], route[i]);
  return sum;
}

/** 경로 위 진행률(0~1) 지점과, 지나온 경로 / 남은 경로 */
export function pointAlong(route: LatLng[], t: number): { pos: LatLng; rest: LatLng[] } {
  const total = routeLengthM(route);
  let left = Math.min(Math.max(t, 0), 1) * total;
  for (let i = 1; i < route.length; i++) {
    const seg = distanceM(route[i - 1], route[i]);
    if (left < seg) {
      const k = seg ? left / seg : 0;
      const [a, b] = [route[i - 1], route[i]];
      const pos: LatLng = [a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k];
      return { pos, rest: [pos, ...route.slice(i)] };
    }
    left -= seg;
  }
  const end = route[route.length - 1];
  return { pos: end, rest: [end] };
}

/** 확인된 승하차 지점 반경 (m). 밖이면 "지도에서 고른 위치" */
export const SNAP_RADIUS_M = 60;

export function nearest(points: AccessPoint[], at: LatLng) {
  let best = points[0];
  let bestD = Infinity;
  for (const p of points) {
    const d = distanceM(p.pos, at);
    if (d < bestD) [best, bestD] = [p, d];
  }
  return { point: best, distanceM: bestD, snapped: bestD <= SNAP_RADIUS_M };
}

// 자체 점검: node --experimental-strip-types src/data/geo.ts
if (typeof process !== 'undefined' && process.argv[1]?.endsWith('geo.ts')) {
  const d = distanceM([37.5455, 127.2214], [37.5455, 127.2266]);
  console.assert(Math.abs(d - 458) < 10, `서문↔동문 약 458m, 실제 ${d}`);
  const half = pointAlong([[0, 0], [0, 1]], 0.5).pos;
  console.assert(Math.abs(half[1] - 0.5) < 1e-9, 'pointAlong 중간');
  console.assert(pointAlong([[0, 0], [0, 1]], 1).rest.length === 1, '끝에선 남은 경로 1점');
  console.log('geo ok');
}
