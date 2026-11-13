import type { FacilityWeather, MoveConditions, PassengerReadiness, VisitSnapshot } from '@majung/shared';

export class ApiError extends Error {
  constructor(public status: number, message: string) { super(message); }
}

const path = (token: string) => `/visits/${encodeURIComponent(token)}`;

async function request(path: string, init?: RequestInit): Promise<VisitSnapshot> {
  const res = await fetch(`/api${path}`, {
    cache: 'no-store',
    ...init,
    headers: { 'Content-Type': 'application/json' },
    signal: AbortSignal.timeout(15_000),
  });
  if (!res.ok) {
    const body = await res.json().catch(() => null);
    throw new ApiError(res.status, body?.message || '서버에 연결하지 못했어요. 다시 시도해 주세요.');
  }
  return res.json();
}

const mutation = (url: string, method: string, version: number, data: object = {}) => request(url, {
  method, body: JSON.stringify({ requestId: [...crypto.getRandomValues(new Uint8Array(16))].map((byte) => byte.toString(16).padStart(2, '0')).join(''), version, ...data }),
});

/** 날씨와, 고른 대기 지점·이동 조건으로 다시 매긴 만날 곳 순서. 보조 정보라 실패하면 null (화면에서 숨긴다) */
async function recommendation(token: string): Promise<FacilityWeather | null> {
  try {
    const res = await fetch(`/api${path(token)}/recommendation`, { cache: 'no-store', signal: AbortSignal.timeout(10_000) });
    return res.ok ? await res.json() : null;
  } catch { return null; }
}

export const api = {
  recommendation,
  getVisit: (token: string) => request(path(token)),
  readiness: (token: string, version: number, passengerReadiness: PassengerReadiness) => mutation(path(token), 'PATCH', version, { passengerReadiness }),
  location: (token: string, version: number, lat: number, lng: number) => mutation(path(token), 'PATCH', version, { passengerLocation: { lat, lng } }),
  stopLocation: (token: string, version: number) => mutation(path(token), 'PATCH', version, { passengerLocation: null }),
  /** 지금 기다리는 곳. null이면 지운다 */
  waitSpot: (token: string, version: number, id: string | null) => mutation(path(token), 'PATCH', version, { passengerWaitSpotId: id }),
  conditions: (token: string, version: number, passengerConditions: MoveConditions) => mutation(path(token), 'PATCH', version, { passengerConditions }),
  propose: (token: string, version: number, target: string | { lat: number; lng: number }, reason?: string) => mutation(`${path(token)}/proposals`, 'POST', version, { ...(typeof target === 'string' ? { accessPointId: target } : { location: target }), ...(reason ? { reason } : {}) }),
  respond: (token: string, version: number, id: string, accept: boolean) => mutation(`${path(token)}/proposals/${encodeURIComponent(id)}/respond`, 'POST', version, { accept }),
  /** 탑승 뒤 '오는 길에 지붕이 있었나요?' 답. 방문마다 한 번 */
  coverage: (token: string, version: number, covered: boolean) => mutation(`${path(token)}/coverage`, 'POST', version, { covered }),
  cancel: (token: string, version: number, id: string) => mutation(`${path(token)}/proposals/${encodeURIComponent(id)}`, 'DELETE', version),
};
