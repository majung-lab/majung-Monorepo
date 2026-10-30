import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { Outlet, useLocation, useNavigate, useParams } from 'react-router-dom';
import type { VisitSnapshot } from '@majung/shared';
import { api, ApiError } from '../api/client';
import { subscribeVisit } from '../realtime/visitSocket';
import { useMyPosition } from './useMyPosition';
import { distanceM, type LatLng } from './geo';

type Mutation = (snapshot: VisitSnapshot) => Promise<VisitSnapshot>;
interface Session {
  snapshot: VisitSnapshot;
  token: string;
  busy: boolean;
  position: LatLng | null;
  positionError: string | null;
  mutate: (action: Mutation) => Promise<boolean>;
}
const CONNECTION_ERROR = '연결이 끊겼어요. 인터넷 연결을 확인해 주세요.';
const Context = createContext<Session | null>(null);

/** A slow poll must never overwrite a newer mutation or websocket event. */
export function chooseSnapshot(current: VisitSnapshot | null, incoming: VisitSnapshot): VisitSnapshot {
  return current && (current.visit.id !== incoming.visit.id || current.visit.version > incoming.visit.version) ? current : incoming;
}

export function useVisit() {
  const session = useContext(Context);
  if (!session) throw new Error('방문 연결이 필요합니다.');
  return session;
}

export function useNow(intervalMs = 1000) {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), intervalMs);
    return () => clearInterval(id);
  }, [intervalMs]);
  return now;
}

export function VisitLoader() {
  const { token = '' } = useParams();
  return <VisitSession key={token} token={token} />;
}

function VisitSession({ token }: { token: string }) {
  const [snapshot, setSnapshot] = useState<VisitSnapshot | null>(null);
  const current = useRef<VisitSnapshot | null>(null);
  const alive = useRef(true);
  const working = useRef(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fatal, setFatal] = useState<ApiError | null>(null);
  const { position, error: positionError } = useMyPosition(snapshot?.role === 'PASSENGER' && !isEnded(snapshot));
  const tick = useNow(15_000);
  const sentPosition = useRef<{ pos: LatLng; at: number } | null>(null);
  const navigate = useNavigate();
  const location = useLocation();
  const base = `/v/${encodeURIComponent(token)}`;

  const receive = useCallback((incoming: VisitSnapshot) => {
    if (!alive.current || !incoming?.visit?.id || !Number.isFinite(incoming.visit.version)) return;
    current.current = chooseSnapshot(current.current, incoming);
    setSnapshot(current.current);
  }, []);
  const report = useCallback((cause: unknown) => {
    if (!alive.current) return;
    if (cause instanceof ApiError && [403, 404, 410].includes(cause.status)) setFatal(cause);
    else setError(cause instanceof ApiError && cause.status === 409
      ? '다른 기기에서 방문이 변경됐어요. 최신 상태를 확인한 뒤 다시 시도해 주세요.'
      : cause instanceof ApiError ? cause.message : CONNECTION_ERROR);
  }, []);
  const refresh = useCallback(async () => {
    try { receive(await api.getVisit(token)); if (alive.current) { setFatal(null); setError((previous) => previous === CONNECTION_ERROR ? null : previous); } }
    catch (cause) { report(cause); }
  }, [token, receive, report]);

  useEffect(() => {
    alive.current = true;
    void refresh();
    const unsubscribe = subscribeVisit(token, receive);
    const interval = setInterval(refresh, 5000);
    const foreground = () => { if (document.visibilityState === 'visible') void refresh(); };
    window.addEventListener('online', refresh);
    document.addEventListener('visibilitychange', foreground);
    return () => {
      alive.current = false;
      unsubscribe(); clearInterval(interval);
      window.removeEventListener('online', refresh);
      document.removeEventListener('visibilitychange', foreground);
    };
  }, [token, refresh, receive]);

  const mutate = useCallback(async (action: Mutation) => {
    if (working.current || !current.current) return false;
    working.current = true; setBusy(true); setError(null);
    try { receive(await action(current.current)); return true; }
    catch (cause) { report(cause); await refresh(); return false; }
    finally { working.current = false; if (alive.current) setBusy(false); }
  }, [receive, report, refresh]);

  useEffect(() => {
    if (snapshot?.role === 'PASSENGER' && snapshot.visit.passengerReadiness === 'UNSEEN' && !isEnded(snapshot)) {
      void mutate((s) => api.readiness(token, s.visit.version, 'SEEN'));
    }
  }, [snapshot, token, mutate]);

  useEffect(() => {
    if (positionError && snapshot?.passengerLocation && !isEnded(snapshot) && snapshot.role === 'PASSENGER') {
      void mutate((s) => api.stopLocation(token, s.visit.version)).then((ok) => { if (ok) sentPosition.current = null; });
    }
  }, [positionError, snapshot, token, mutate]);

  useEffect(() => {
    if (!position || !snapshot || isEnded(snapshot) || snapshot.role !== 'PASSENGER') return;
    const last = sentPosition.current;
    const elapsed = Date.now() - (last?.at ?? 0);
    if (last && (elapsed < 15_000 || (elapsed < 60_000 && distanceM(last.pos, position) < 10))) return;
    void mutate((s) => api.location(token, s.visit.version, ...position)).then((ok) => {
      if (ok) sentPosition.current = { pos: position, at: Date.now() };
    });
  }, [position, snapshot, token, mutate, tick]);

  useEffect(() => {
    if (!snapshot) return;
    const pendingDriver = snapshot.proposals.some((p) => p.proposedBy === 'DRIVER' && p.status === 'PENDING');
    const target = isEnded(snapshot) ? `${base}/ended` : pendingDriver ? `${base}/proposal`
      : location.pathname.endsWith('/ended') || location.pathname.endsWith('/proposal') ? base : null;
    if (target && location.pathname !== target) navigate(target, { replace: true });
  }, [snapshot, base, location.pathname, navigate]);

  if (fatal || !snapshot) return <main className="screen ended">
    <p className="ended-brand">마중</p>
    <h1 className="ended-title">{fatal?.status === 410 ? '방문 링크가 만료됐어요' : fatal ? '방문을 열 수 없어요' : '방문을 연결하고 있어요'}</h1>
    <p role={fatal || error ? 'alert' : 'status'}>{fatal?.message || error || '잠시만 기다려 주세요.'}</p>
    {(fatal || error) && <button className="btn-secondary" onClick={refresh}>다시 확인</button>}
  </main>;
  if (snapshot.role !== 'PASSENGER') return <main className="screen ended"><h1>동승자 링크가 필요해요</h1><p>차량 앱에서 동승자용 QR을 다시 열어 주세요.</p></main>;

  return <Context.Provider value={{ snapshot, token, busy, position, positionError, mutate }}>
    {error && <div className="connection-error" role="alert"><span>{error}</span><button onClick={() => { setError(null); void refresh(); }}>다시 확인</button></div>}
    <Outlet />
  </Context.Provider>;
}

export function isEnded(snapshot: VisitSnapshot | null) {
  return snapshot?.visit.phase === 'COMPLETED' || snapshot?.visit.phase === 'CANCELLED';
}

export const pointPosition = (point: { lat: number; lng: number }): LatLng => [point.lat, point.lng];
