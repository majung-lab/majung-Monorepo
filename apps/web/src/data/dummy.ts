// 더미 데이터: 서버·시설 데이터 붙기 전까지 앱 안에서만 쓴다. 좌표는 대략값(실측 아님).
export type LatLng = [number, number];

export interface AccessPoint {
  id: string;
  name: string;
  desc: string;
  pos: LatLng;
  verified: boolean;
}

export const FACILITY = {
  id: 'starfield-hanam',
  name: '스타필드 하남',
  pos: [37.5455, 127.224] as LatLng,
};

export const ACCESS_POINTS: AccessPoint[] = [
  { id: 'north', name: '북문 로비', desc: '엘리베이터 앞 벤치 · 지붕 있음', pos: [37.5468, 127.2239], verified: true },
  { id: 'west', name: '서문 로비', desc: '서문 회전문 앞', pos: [37.5455, 127.2214], verified: true },
  { id: 'east', name: '동문 로비', desc: '동문 택시 승강장 옆', pos: [37.5455, 127.2266], verified: true },
  { id: 'b1', name: 'B1 승하차장', desc: '지하 1층 승하차 전용 구역', pos: [37.5446, 127.225], verified: true },
];

export const apById = (id: string) => ACCESS_POINTS.find((a) => a.id === id) ?? ACCESS_POINTS[0];

/** 차가 오는 길 (출발 → 북문). 시뮬레이션은 이 선을 따라 움직인다 */
export const CAR_ROUTE: LatLng[] = [
  [37.5205, 127.1985],
  [37.5262, 127.2051],
  [37.5331, 127.2102],
  [37.5398, 127.2148],
  [37.5441, 127.2196],
  [37.5468, 127.2239],
];

/** 위치 권한이 없을 때 쓰는 내 위치 (북문 근처 매장 안) */
export const MY_POS: LatLng = [37.5461, 127.2228];

/** 시뮬레이션 평균 속도 (km/h) */
export const CAR_SPEED_KMH = 24;

/** 운전자가 보내는 제안 (W02) */
export const DRIVER_PROPOSAL = {
  toId: 'west',
  reason: '북문 앞이 막혀서, 차 세우기 쉬운 서문으로 바꾸고 싶어요',
};
