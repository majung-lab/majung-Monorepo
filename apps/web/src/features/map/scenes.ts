import type { MapScene } from './MockMap';

// Figma 목업 지도 장면들. 값은 Figma inset 그대로.

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
  label: { text: '스타필드 하남', inset: '26.81% 25.13% 69.36% 53.85%' },
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
  label: {
    text: '스타필드 하남',
    inset: '46% 44.8% 51.56% 44.8%',
    style: { fontSize: 18, fontWeight: 700, whiteSpace: 'nowrap' },
  },
};
