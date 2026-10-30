import { useEffect, useState } from 'react';
import type { LatLng } from './geo';

export function useMyPosition(enabled = true) {
  const [position, setPosition] = useState<LatLng | null>(null);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    if (!enabled) return;
    if (!('geolocation' in navigator)) { setError('이 기기에서는 위치를 공유할 수 없어요.'); return; }
    const id = navigator.geolocation.watchPosition(
      ({ coords }) => { setPosition([coords.latitude, coords.longitude]); setError(null); },
      (e) => { setPosition(null); setError(e.code === 1 ? '위치 권한이 꺼져 있어요. 준비 상태는 계속 보낼 수 있어요.' : '내 위치를 확인하지 못했어요.'); },
      { enableHighAccuracy: true, maximumAge: 10_000, timeout: 15_000 },
    );
    return () => navigator.geolocation.clearWatch(id);
  }, [enabled]);
  return { position, error };
}
