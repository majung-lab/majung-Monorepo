import type { PassengerReadiness, VisitSnapshot } from '@majung/shared';

export class ApiError extends Error {
  constructor(public status: number, message: string) { super(message); }
}

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

const path = (token: string) => `/visits/${encodeURIComponent(token)}`;
const mutation = (url: string, method: string, version: number, data: object = {}) => request(url, {
  method, body: JSON.stringify({ requestId: [...crypto.getRandomValues(new Uint8Array(16))].map((byte) => byte.toString(16).padStart(2, '0')).join(''), version, ...data }),
});

export const api = {
  getVisit: (token: string) => request(path(token)),
  readiness: (token: string, version: number, passengerReadiness: PassengerReadiness) => mutation(path(token), 'PATCH', version, { passengerReadiness }),
  location: (token: string, version: number, lat: number, lng: number) => mutation(path(token), 'PATCH', version, { passengerLocation: { lat, lng } }),
  stopLocation: (token: string, version: number) => mutation(path(token), 'PATCH', version, { passengerLocation: null }),
  propose: (token: string, version: number, accessPointId: string) => mutation(`${path(token)}/proposals`, 'POST', version, { accessPointId }),
  respond: (token: string, version: number, id: string, accept: boolean) => mutation(`${path(token)}/proposals/${encodeURIComponent(id)}/respond`, 'POST', version, { accept }),
  cancel: (token: string, version: number, id: string) => mutation(`${path(token)}/proposals/${encodeURIComponent(id)}`, 'DELETE', version),
};
