import type { CSSProperties, ReactNode } from 'react';

// ponytail: Figma 목업 지도를 레이어 그대로 깐다. 지도 SDK 붙일 때 이 컴포넌트만 교체.
// [에셋 경로, inset(top right bottom left), stroke 여백]
export type MapLayer = [string, string, string?];

export interface MapScene {
  background: string;
  /** 지도 그림 영역 (보이는 영역보다 클 수 있다) */
  canvas: CSSProperties;
  layers: MapLayer[];
  label: { text: string; inset: string; style?: CSSProperties };
}

const ASSETS = import.meta.glob<string>('../../assets/map/**/*.svg', { eager: true, import: 'default' });
const asset = (path: string) => ASSETS[`../../assets/map/${path}`];

export default function MockMap({ scene, children }: { scene: MapScene; children?: ReactNode }) {
  return (
    <div className="map" style={{ background: scene.background }}>
      <div className="map-layer" style={scene.canvas}>
        {scene.layers.map(([src, inset, bleed = '0'], i) => (
          <div key={i} className="map-layer" style={{ inset }}>
            <div className="map-layer" style={{ inset: bleed }}>
              <img alt="" src={asset(src)} style={{ display: 'block', width: '100%', height: '100%' }} />
            </div>
          </div>
        ))}
        <p className="map-label" style={{ inset: scene.label.inset, ...scene.label.style }}>{scene.label.text}</p>
      </div>
      {children}
    </div>
  );
}
