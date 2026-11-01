import { useEffect, useRef, useState, type ReactNode, type RefObject } from 'react';
import type { LatLng } from '../../data/geo';
import pinMeet from '../../assets/map/w01/pin-meet.svg';
import pinCar from '../../assets/map/w01/pin-car.svg';
import pinMe from '../../assets/map/w01/pin-me.svg';
import pinNew from '../../assets/map/w02/pin-new.svg';
import apDot from '../../assets/map/w04/access-point.svg';

export type MarkerKind = 'meet' | 'proposed' | 'car' | 'me' | 'ap';
export interface MapMarker { pos: LatLng; kind: MarkerKind; label?: string }
export interface MapLine { points: LatLng[]; dashed?: boolean }
export interface MapHandle {
  setView: (pos: LatLng, zoom: number) => void;
  zoomIn: () => void;
  zoomOut: () => void;
  fit: (points: LatLng[]) => void;
}

// [에셋, 크기, 기준점(핀 끝)] — 크기는 Figma 에셋 그대로
const ICONS: Record<MarkerKind, [string, [number, number], [number, number]]> = {
  meet: [pinMeet, [32, 46], [16, 46]],
  proposed: [pinNew, [32, 46], [16, 46]],
  car: [pinCar, [36, 36], [18, 18]],
  me: [pinMe, [32, 32], [16, 16]],
  ap: [apDot, [18, 18], [9, 9]],
};

// 카카오맵 레벨은 작을수록 가깝다
const CLOSEST_FIT_LEVEL = 3;
const SINGLE_POINT_ZOOM = 16;
const WHEEL_STEP_MS = 250;
const PINCH_STEP = 1.3;
const toLevel = (zoom: number) => Math.min(14, Math.max(1, 20 - zoom));

declare global {
  interface Window { kakao?: any }
}

let sdk: Promise<any> | null = null;

function loadKakao() {
  const key = import.meta.env.VITE_KAKAO_MAP_KEY;
  if (!key) return Promise.reject(new Error('VITE_KAKAO_MAP_KEY가 없어요'));
  sdk ??= new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${encodeURIComponent(key)}&autoload=false`;
    // index.html은 토큰 URL을 숨기려고 no-referrer. 카카오 도메인 확인에는 origin만 보낸다.
    script.referrerPolicy = 'origin';
    script.onload = () => window.kakao ? window.kakao.maps.load(() => resolve(window.kakao.maps)) : reject(new Error('카카오맵을 불러오지 못했어요'));
    script.onerror = () => { sdk = null; reject(new Error('카카오맵을 불러오지 못했어요')); };
    document.head.append(script);
  });
  return sdk;
}

/** 카카오 기본 확대는 손가락·휠 위치가 기준이라 끄고, 레벨만 바꿔 가운데를 유지한다 */
function keepCenterOnZoom(host: HTMLElement, instance: any) {
  instance.setZoomable(false);
  let lastWheel = 0;
  let pinch = 0;
  const step = (delta: number) => instance.setLevel(instance.getLevel() + delta);
  const spread = (e: TouchEvent) => Math.hypot(e.touches[0].clientX - e.touches[1].clientX, e.touches[0].clientY - e.touches[1].clientY);
  const wheel = (e: WheelEvent) => {
    e.preventDefault();
    if (e.timeStamp - lastWheel < WHEEL_STEP_MS) return;
    lastWheel = e.timeStamp;
    step(e.deltaY > 0 ? 1 : -1);
  };
  const touchStart = (e: TouchEvent) => {
    if (e.touches.length !== 2) return;
    pinch = spread(e);
    e.stopPropagation();
  };
  const touchMove = (e: TouchEvent) => {
    if (e.touches.length !== 2 || !pinch) return;
    e.preventDefault();
    e.stopPropagation();
    const ratio = spread(e) / pinch;
    if (ratio < PINCH_STEP && ratio > 1 / PINCH_STEP) return;
    step(ratio > 1 ? -1 : 1);
    pinch = spread(e);
  };
  const touchEnd = (e: TouchEvent) => { if (e.touches.length < 2) pinch = 0; };
  host.addEventListener('wheel', wheel, { passive: false });
  host.addEventListener('touchstart', touchStart, { capture: true });
  host.addEventListener('touchmove', touchMove, { capture: true, passive: false });
  host.addEventListener('touchend', touchEnd, { capture: true });
  return () => {
    host.removeEventListener('wheel', wheel);
    host.removeEventListener('touchstart', touchStart, { capture: true });
    host.removeEventListener('touchmove', touchMove, { capture: true });
    host.removeEventListener('touchend', touchEnd, { capture: true });
  };
}

function pin(m: MapMarker) {
  const [src, size] = ICONS[m.kind];
  const content = document.createElement('div');
  content.className = `pin pin-${m.kind}`;
  const img = document.createElement('img');
  img.src = src; img.width = size[0]; img.height = size[1]; img.alt = '';
  content.append(img);
  if (m.label) {
    const label = document.createElement('span');
    label.className = 'pin-label'; label.textContent = m.label;
    content.append(label);
  }
  return content;
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
  /** 손가락·휠 위치가 아니라 지도 가운데를 기준으로 확대한다 (W04 가운데 핀) */
  zoomAtCenter?: boolean;
  mapRef?: RefObject<MapHandle | null>;
  children?: ReactNode;
}

export default function KakaoMap({ center, zoom = 16, height = 470, markers = [], lines = [], onMoveEnd, fitTo, zoomAtCenter, mapRef, children }: Props) {
  const el = useRef<HTMLDivElement>(null);
  const [map, setMap] = useState<{ kakao: any; instance: any } | null>(null);
  const [failed, setFailed] = useState(false);
  const moveEnd = useRef(onMoveEnd);
  moveEnd.current = onMoveEnd;

  useEffect(() => {
    let stopped = false;
    let cleanup = () => {};
    loadKakao().then((kakao) => {
      if (stopped) return;
      const host = el.current!;
      const instance = new kakao.Map(host, { center: new kakao.LatLng(center[0], center[1]), level: toLevel(zoom) });
      const handle: MapHandle = {
        setView: (pos, nextZoom) => { instance.setLevel(toLevel(nextZoom)); instance.setCenter(new kakao.LatLng(pos[0], pos[1])); },
        zoomIn: () => instance.setLevel(instance.getLevel() - 1),
        zoomOut: () => instance.setLevel(instance.getLevel() + 1),
        // 위 칩·알림 띠와 아래 보기 버튼을 피해서 맞춘다
        fit: (points) => {
          if (points.length === 1) { handle.setView(points[0], SINGLE_POINT_ZOOM); return; }
          const bounds = new kakao.LatLngBounds();
          for (const p of points) bounds.extend(new kakao.LatLng(p[0], p[1]));
          instance.setBounds(bounds, 96, 40, 80, 40);
          if (instance.getLevel() < CLOSEST_FIT_LEVEL) instance.setLevel(CLOSEST_FIT_LEVEL);
        },
      };
      if (fitTo?.length) handle.fit(fitTo);
      const idle = () => {
        const c = instance.getCenter();
        moveEnd.current?.([c.getLat(), c.getLng()]);
      };
      kakao.event.addListener(instance, 'idle', idle);
      const stopCenterZoom = zoomAtCenter ? keepCenterOnZoom(host, instance) : () => {};
      if (mapRef) mapRef.current = handle;
      setMap({ kakao, instance });
      cleanup = () => {
        kakao.event.removeListener(instance, 'idle', idle);
        stopCenterZoom();
        if (mapRef) mapRef.current = null;
        host.replaceChildren();
      };
    }).catch(() => { if (!stopped) setFailed(true); });
    return () => { stopped = true; cleanup(); };
    // 가운데·확대는 처음 한 번만. 이후 이동은 mapRef로
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 마커·선은 바뀔 때마다 다시 그린다 (몇 개뿐이라 충분)
  useEffect(() => {
    if (!map) return;
    const { kakao, instance } = map;
    const drawn = [
      ...lines.map((l) => new kakao.Polyline({
        map: instance,
        path: l.points.map((p) => new kakao.LatLng(p[0], p[1])),
        ...(l.dashed
          ? { strokeColor: '#d9480f', strokeWeight: 3, strokeStyle: 'dashed' }
          : { strokeColor: '#f5a524', strokeWeight: 6, strokeStyle: 'solid' }),
      })),
      ...markers.map((m) => {
        const [, size, anchor] = ICONS[m.kind];
        return new kakao.CustomOverlay({
          map: instance,
          position: new kakao.LatLng(m.pos[0], m.pos[1]),
          content: pin(m),
          xAnchor: anchor[0] / size[0],
          yAnchor: anchor[1] / size[1],
        });
      }),
    ];
    return () => drawn.forEach((item) => item.setMap(null));
  }, [map, markers, lines]);

  return (
    <div className="map" style={{ height }}>
      <div ref={el} className="map-host" />
      {failed && <p className="map-error" role="alert">지도를 불러오지 못했어요</p>}
      {children}
    </div>
  );
}
