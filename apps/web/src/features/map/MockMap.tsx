import { useRef, useState, type CSSProperties, type ReactNode } from 'react';

// ponytail: Figma 목업 지도를 레이어 그대로 깐다. 지도 SDK 붙일 때 이 컴포넌트만 교체.
// [에셋 경로, inset(top right bottom left), stroke 여백]
export type MapLayer = [string, string, string?];
export interface MapLabel { text: string; inset: string; style?: CSSProperties }

export interface MapScene {
  background: string;
  height?: number;
  /** 지도 그림 영역 (보이는 영역보다 클 수 있다) */
  canvas: CSSProperties;
  layers: MapLayer[];
  labels: MapLabel[];
}

const ASSETS = import.meta.glob<string>('../../assets/map/**/*.svg', { eager: true, import: 'default' });
const asset = (path: string) => ASSETS[`../../assets/map/${path}`];

interface Props {
  scene: MapScene;
  /** 끌어서 지도 이동 (W04 핀 조정) */
  onPan?: () => void;
  children?: ReactNode;
}

export default function MockMap({ scene, onPan, children }: Props) {
  const [pan, setPan] = useState({ x: 0, y: 0 });
  const drag = useRef<{ x: number; y: number } | null>(null);

  const panHandlers = onPan && {
    onPointerDown: (e: React.PointerEvent) => {
      e.currentTarget.setPointerCapture(e.pointerId);
      drag.current = { x: e.clientX - pan.x, y: e.clientY - pan.y };
    },
    onPointerMove: (e: React.PointerEvent) => {
      if (!drag.current) return;
      setPan({ x: e.clientX - drag.current.x, y: e.clientY - drag.current.y });
      onPan();
    },
    onPointerUp: () => { drag.current = null; },
  };

  return (
    <div
      className="map"
      style={{ background: scene.background, height: scene.height, touchAction: onPan ? 'none' : undefined }}
      {...panHandlers}
    >
      <div className="map-layer" style={{ ...scene.canvas, translate: `${pan.x}px ${pan.y}px` }}>
        {scene.layers.map(([src, inset, bleed = '0'], i) => (
          <div key={i} className="map-layer" style={{ inset }}>
            <div className="map-layer" style={{ inset: bleed }}>
              <img alt="" draggable={false} src={asset(src)} style={{ display: 'block', width: '100%', height: '100%' }} />
            </div>
          </div>
        ))}
        {scene.labels.map((l) => (
          <p key={l.text} className="map-label" style={{ inset: l.inset, ...l.style }}>{l.text}</p>
        ))}
      </div>
      {children}
    </div>
  );
}
