export type LatLng = [number, number];
interface LocatedPoint { id: string; pos: LatLng }

const R = 6371000;
const rad = (d: number) => (d * Math.PI) / 180;

/** 두 좌표 사이 거리 (m), haversine */
export function distanceM([lat1, lng1]: LatLng, [lat2, lng2]: LatLng): number {
  const a = Math.sin(rad(lat2 - lat1) / 2) ** 2 + Math.cos(rad(lat1)) * Math.cos(rad(lat2)) * Math.sin(rad(lng2 - lng1) / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(a));
}

/** 등록된 승하차 지점 선택 반경 (m). 밖이면 "지도에서 고른 위치" */
export const SNAP_RADIUS_M = 60;

export function nearest<T extends LocatedPoint>(points: T[], at: LatLng) {
  let best = points[0];
  let bestD = Infinity;
  for (const p of points) {
    const d = distanceM(p.pos, at);
    if (d < bestD) [best, bestD] = [p, d];
  }
  return { point: best, distanceM: bestD, snapped: bestD <= SNAP_RADIUS_M };
}
