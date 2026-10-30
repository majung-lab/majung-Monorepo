import { useEffect, useRef, type ReactNode, type RefObject } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import type { LatLng } from '../../data/geo';
import pinMeet from '../../assets/map/w01/pin-meet.svg';
import pinCar from '../../assets/map/w01/pin-car.svg';
import pinMe from '../../assets/map/w01/pin-me.svg';
import pinNew from '../../assets/map/w02/pin-new.svg';
import apDot from '../../assets/map/w04/access-point.svg';

// ponytail: OpenStreetMap(Leaflet). 카카오맵 키가 생기면 이 파일만 교체한다.

export type MarkerKind = 'meet' | 'proposed' | 'car' | 'me' | 'ap';
export interface MapMarker { pos: LatLng; kind: MarkerKind; label?: string }
export interface MapLine { points: LatLng[]; dashed?: boolean }

// [에셋, 크기, 기준점(핀 끝)] — 크기는 Figma 에셋 그대로
const ICONS: Record<MarkerKind, [string, [number, number], [number, number]]> = {
  meet: [pinMeet, [32, 46], [16, 46]],
  proposed: [pinNew, [32, 46], [16, 46]],
  car: [pinCar, [36, 36], [18, 18]],
  me: [pinMe, [32, 32], [16, 16]],
  ap: [apDot, [18, 18], [9, 9]],
};

/** 위 칩·알림 띠와 아래 보기 버튼을 피해서 맞춘다 */
export function fitAll(m: L.Map, pts: LatLng[]) {
  m.fitBounds(pts, { paddingTopLeft: [40, 96], paddingBottomRight: [40, 80], maxZoom: 17 });
}

function icon(m: MapMarker) {
  const [src, size, anchor] = ICONS[m.kind];
  const content = document.createElement('div');
  const img = document.createElement('img');
  img.src = src; img.width = size[0]; img.height = size[1]; img.alt = '';
  content.append(img);
  if (m.label) {
    const label = document.createElement('span');
    label.className = 'pin-label'; label.textContent = m.label;
    content.append(label);
  }
  return L.divIcon({
    className: `pin pin-${m.kind}`,
    html: content,
    iconSize: size,
    iconAnchor: anchor,
  });
}

interface Props {
  center: LatLng;
  zoom?: number;
  height?: number;
  markers?: MapMarker[];
  lines?: MapLine[];
  /** 지도를 다 움직였을 때 가운데 좌표 (W04 핀 조정) */
  onMoveEnd?: (center: LatLng) => void;
  /** 처음 열 때 이 점들이 다 보이게 맞춘다 */
  fitTo?: LatLng[];
  mapRef?: RefObject<L.Map | null>;
  children?: ReactNode;
}

export default function LeafletMap({ center, zoom = 16, height = 470, markers = [], lines = [], onMoveEnd, fitTo, mapRef, children }: Props) {
  const el = useRef<HTMLDivElement>(null);
  const map = useRef<L.Map | null>(null);
  const layer = useRef<L.LayerGroup | null>(null);
  const moveEnd = useRef(onMoveEnd);
  moveEnd.current = onMoveEnd;

  useEffect(() => {
    // Leaflet's delayed zoom callback can run after a route has removed the map.
    const m = L.map(el.current!, { zoomControl: false, attributionControl: true, zoomAnimation: false }).setView(center, zoom);
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '© OpenStreetMap',
    }).addTo(m);
    if (fitTo?.length) fitAll(m, fitTo);
    layer.current = L.layerGroup().addTo(m);
    m.on('moveend', () => {
      const c = m.getCenter();
      moveEnd.current?.([c.lat, c.lng]);
    });
    map.current = m;
    if (mapRef) mapRef.current = m;
    return () => { m.remove(); map.current = null; };
    // 가운데·확대는 처음 한 번만. 이후 이동은 mapRef로
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 마커·선은 바뀔 때마다 다시 그린다 (몇 개뿐이라 충분)
  useEffect(() => {
    const g = layer.current;
    if (!g) return;
    g.clearLayers();
    for (const l of lines) {
      L.polyline(l.points, l.dashed
        ? { color: '#d9480f', weight: 3, dashArray: '6 6' }
        : { color: '#f5a524', weight: 6, lineCap: 'round' }).addTo(g);
    }
    for (const m of markers) L.marker(m.pos, { icon: icon(m), interactive: false, keyboard: false }).addTo(g);
  }, [markers, lines]);

  return (
    <div className="map" style={{ height }}>
      <div ref={el} className="leaflet-host" />
      {children}
    </div>
  );
}
