import type { MapScene } from './MockMap';

// Figma 목업 지도 장면들. 값은 Figma inset 그대로.

const NOWRAP = { whiteSpace: 'nowrap' } as const;
const BIG_LABEL = { ...NOWRAP, fontSize: 18, fontWeight: 700 } as const;

/** W01: 내 위치 · 차 위치 · 만날 곳 + 경로 */
export const visitScene: MapScene = {
  background: '#e9ede6',
  canvas: { inset: 0 },
  layers: [
    ['w01/base.svg', '0'],
    ['w01/building.svg', '12.77% 10.26% 55.32% 38.46%'],
    ['w01/park.svg', '63.83% 66.67% 19.15% 5.13%'],
    ['w01/road-h.svg', '51.06% 0 48.94% 0', '-9px 0'],
    ['w01/road-v.svg', '0 71.79% 0 28.21%', '0 -8px'],
    ['w01/road-diag.svg', '51.06% 7.69% 2.13% 28.21%', '-2.05% -1.59%'],
    ['w01/route.svg', '45.11% 35.9% 29.79% 28.21%', '-2.54% -2.14%'],
    ['w01/pin-meet.svg', '35.74% 31.79% 54.47% 60%'],
    ['w01/pin-car.svg', '66.38% 67.18% 25.96% 23.59%', '-5.56%'],
    ['w01/pin-me.svg', '30.64% 36.41% 62.55% 55.38%'],
  ],
  labels: [{ text: '스타필드 하남', inset: '26.81% 25.13% 69.36% 53.85%' }],
};

/** W05 바뀜: 만날 곳 핀이 서문 쪽으로 이동 */
export const visitMovedScene: MapScene = {
  ...visitScene,
  layers: visitScene.layers.map((l) =>
    l[0] === 'w01/pin-meet.svg' ? ['w01/pin-meet.svg', '50.64% 60% 39.57% 31.79%'] : l,
  ),
};

/** W02: 지금 만날 곳(검정 핀) → 제안된 곳(주황 핀) */
export const proposalScene: MapScene = {
  background: '#eef0ea',
  canvas: { left: -206, top: -106, width: 1000, height: 900 },
  layers: [
    ['w02/base.svg', '0'],
    ['w02/road-h.svg', '18.89% 0 81.11% 0', '-14px 0'],
    ['w02/road-v.svg', '0 80% 0 20%', '0 -13px'],
    ['w02/road-v.svg', '0 18% 0 82%', '0 -13px'],
    ['w02/road-loop.svg', '18.89% 18% 52.22% 20%', '0 0 -2.69% 0'],
    ['w02/building.svg', '27.78% 30% 33.33% 30%', '-0.29% -0.25%'],
    ['w02/me-halo.svg', '50.22% 75.2% 45.78% 21.2%'],
    ['w02/me-dot.svg', '51.33% 76.2% 46.89% 22.2%', '-12.5%'],
    ['w02/dash.svg', '27.78% 50% 52.22% 30%', '-0.62% -0.5%'],
    ['w02/pin-now.svg', '42.44% 68.4% 52.22% 28.4%'],
    ['w02/pin-hole.svg', '43.56% 69.4% 55.11% 29.4%'],
    ['w02/tag-now.svg', '38.89% 65% 58.22% 25%'],
    ['w02/pin-new.svg', '22.44% 48.4% 72.22% 48.4%'],
    ['w02/pin-hole.svg', '23.56% 49.4% 75.11% 49.4%'],
    ['w02/tag-new.svg', '18.89% 45% 78.22% 45%'],
  ],
  labels: [{ text: '스타필드 하남', inset: '46% 44.8% 51.56% 44.8%', style: BIG_LABEL }],
};

/** W04: 출입구들 + 지금 만날 곳. 가운데 고정 핀은 화면 쪽에서 그린다 */
export const adjustScene: MapScene = {
  background: '#eef0ea',
  height: 576,
  canvas: { left: 0, top: 0, width: 1000, height: 900 },
  layers: [
    ['w04/base.svg', '0'],
    ['w04/park.svg', '71.11% 76% 6.67% 4%'],
    ['w04/road-h-top.svg', '18.89% 0 81.11% 0', '-14px 0'],
    ['w04/road-h-bottom.svg', '80% 0 20% 0', '-12px 0'],
    ['w04/road-v.svg', '0 80% 0 20%', '0 -13px'],
    ['w04/road-v.svg', '0 18% 0 82%', '0 -13px'],
    ['w04/road-loop.svg', '18.89% 18% 20% 20%'],
    ['w04/building.svg', '27.78% 30% 33.33% 30%', '-0.29% -0.25%'],
    ['w04/parking.svg', '66.67% 3% 23.33% 86%'],
    ['w04/access-point.svg', '26.89% 49.2% 71.33% 49.2%', '-6.25%'],
    ['w04/access-point.svg', '46.89% 69.2% 51.33% 29.2%', '-6.25%'],
    ['w04/access-point.svg', '46.89% 29.2% 51.33% 69.2%', '-6.25%'],
    ['w04/access-point.svg', '65.78% 37.2% 32.44% 61.2%', '-6.25%'],
    ['w02/me-halo.svg', '50.22% 75.2% 45.78% 21.2%'],
    ['w02/me-dot.svg', '51.33% 76.2% 46.89% 22.2%', '-12.5%'],
    ['w04/pin-now.svg', '22.44% 48.4% 72.22% 48.4%'],
    ['w02/pin-hole.svg', '23.56% 49.4% 75.11% 49.4%'],
    ['w04/tag-now.svg', '18.89% 45.6% 78.22% 45.6%'],
  ],
  labels: [
    { text: '스타필드 하남', inset: '46% 44.8% 51.56% 44.8%', style: BIG_LABEL },
    { text: 'P', inset: '70.67% 7.8% 27.11% 91%', style: { fontSize: 16, fontWeight: 700 } },
    { text: '북문 로비', inset: '30.22% 47.2% 67.78% 47.2%', style: NOWRAP },
    { text: '서문 로비', inset: '50.67% 62.6% 47.33% 31.8%', style: NOWRAP },
    { text: '동문 로비', inset: '50.67% 31.6% 47.33% 62.8%', style: NOWRAP },
    { text: 'B1 승하차장', inset: '62.89% 34.4% 35.11% 58.4%', style: NOWRAP },
    { text: '지금', inset: '19.33% 48.6% 78.67% 48.8%', style: { ...NOWRAP, fontWeight: 700, color: '#fff' } },
  ],
};
