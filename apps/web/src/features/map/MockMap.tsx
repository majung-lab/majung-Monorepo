import type { ReactNode } from 'react';
import base from '../../assets/map/base.svg';
import building from '../../assets/map/building.svg';
import park from '../../assets/map/park.svg';
import roadH from '../../assets/map/road-h.svg';
import roadV from '../../assets/map/road-v.svg';
import roadDiag from '../../assets/map/road-diag.svg';
import route from '../../assets/map/route.svg';
import pinMeet from '../../assets/map/pin-meet.svg';
import pinCar from '../../assets/map/pin-car.svg';
import pinMe from '../../assets/map/pin-me.svg';

// ponytail: Figma 목업 지도(390×470)를 그대로 깐다. 지도 SDK 붙일 때 이 컴포넌트만 교체.
// [src, inset(top right bottom left), stroke 여백]
const LAYERS: [string, string, string?][] = [
  [base, '0'],
  [building, '12.77% 10.26% 55.32% 38.46%'],
  [park, '63.83% 66.67% 19.15% 5.13%'],
  [roadH, '51.06% 0 48.94% 0', '-9px 0'],
  [roadV, '0 71.79% 0 28.21%', '0 -8px'],
  [roadDiag, '51.06% 7.69% 2.13% 28.21%', '-2.05% -1.59%'],
  [route, '45.11% 35.9% 29.79% 28.21%', '-2.54% -2.14%'],
  [pinMeet, '35.74% 31.79% 54.47% 60%'],
  [pinCar, '66.38% 67.18% 25.96% 23.59%', '-5.56%'],
  [pinMe, '30.64% 36.41% 62.55% 55.38%'],
];

export default function MockMap({ children }: { children?: ReactNode }) {
  return (
    <div className="map">
      {LAYERS.map(([src, inset, bleed = '0']) => (
        <div key={src} className="map-layer" style={{ inset }}>
          <div className="map-layer" style={{ inset: bleed }}>
            <img alt="" src={src} style={{ display: 'block', width: '100%', height: '100%' }} />
          </div>
        </div>
      ))}
      <p className="map-label" style={{ inset: '26.81% 25.13% 69.36% 53.85%' }}>스타필드 하남</p>
      {children}
    </div>
  );
}
