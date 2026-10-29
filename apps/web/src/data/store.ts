// 방문 상태 + 시뮬레이션. 서버·웹소켓 붙기 전까지 앱 안에서만 돈다.
// ponytail: 탭 하나 기준(sessionStorage). 서버 붙으면 subscribeVisit 응답으로 교체.
import { useEffect, useState, useSyncExternalStore } from 'react';
import { CAR_ROUTE, CAR_SPEED_KMH, DRIVER_PROPOSAL, apById, type LatLng } from './dummy';
import { pointAlong, routeLengthM } from './geo';

export type ProposalStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED';
export interface Proposal { toId: string; fromId: string; reason?: string; status: ProposalStatus; at: number }

export interface VisitState {
  startedAt: number;
  /** 시뮬레이션 배속 (시연용 ?reset&speed=20) */
  speed: number;
  meetId: string;
  ready: boolean;
  /** 운전자가 보낸 제안 (W02) */
  driverProposal: Proposal | null;
  /** 내가 보낸 제안 (W04 → W05) */
  myProposal: Proposal | null;
  /** W01 위에 띄울 결과 알림 (W05) */
  banner: { kind: 'accepted' | 'rejected'; placeId: string } | null;
}

/** 시뮬레이션 타이밍 (ms) */
export const SIM = {
  driverProposalAt: 0.35, // 차가 경로 35% 지점일 때 운전자 제안 도착
  driverAnswerMs: 5000, // 내 제안에 운전자가 답하는 시간
  boardMs: 8000, // 도착 후 탑승 완료까지
};

const KEY = 'majung.visit';
const fresh = (speed = 1): VisitState => ({
  startedAt: Date.now(),
  speed,
  meetId: 'north',
  ready: false,
  driverProposal: null,
  myProposal: null,
  banner: null,
});

function load(): VisitState {
  try {
    const raw = sessionStorage.getItem(KEY);
    if (raw) return JSON.parse(raw) as VisitState;
  } catch { /* 저장소 막힘 → 새로 시작 */ }
  return fresh();
}

let state = load();
const listeners = new Set<() => void>();

export function update(fn: (s: VisitState) => VisitState) {
  state = fn(state);
  try { sessionStorage.setItem(KEY, JSON.stringify(state)); } catch { /* 무시 */ }
  listeners.forEach((l) => l());
}

export const resetVisit = (speed = 1) => update(() => fresh(speed));

export function useVisit(): VisitState {
  return useSyncExternalStore(
    (l) => { listeners.add(l); return () => listeners.delete(l); },
    () => state,
  );
}

/** 1초마다 다시 그리기 */
export function useNow(intervalMs = 1000) {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), intervalMs);
    return () => clearInterval(id);
  }, [intervalMs]);
  return now;
}

/** 지금 만날 곳으로 가는 차 경로 (만날 곳이 바뀌면 끝점만 바꾼다) */
export const routeTo = (meetId: string): LatLng[] => [...CAR_ROUTE.slice(0, -1), apById(meetId).pos];

export interface CarSim {
  pos: LatLng;
  rest: LatLng[];
  progress: number;
  remainingM: number;
  etaMin: number;
  etaAt: string;
  arrivedAt: number | null;
}

export function carAt(s: VisitState, now: number): CarSim {
  const route = routeTo(s.meetId);
  const total = routeLengthM(route);
  const mps = (CAR_SPEED_KMH * 1000) / 3600;
  const travelled = ((now - s.startedAt) / 1000) * mps * (s.speed || 1);
  const progress = Math.min(travelled / total, 1);
  const { pos, rest } = pointAlong(route, progress);
  const remainingM = total * (1 - progress);
  const etaMs = (remainingM / mps) * 1000; // ETA는 실제 속도 기준으로 보여 준다
  const at = new Date(now + etaMs);
  return {
    pos,
    rest,
    progress,
    remainingM,
    etaMin: Math.ceil(etaMs / 60000),
    etaAt: `${String(at.getHours()).padStart(2, '0')}:${String(at.getMinutes()).padStart(2, '0')}`,
    arrivedAt: progress >= 1 ? s.startedAt + (total / mps / (s.speed || 1)) * 1000 : null,
  };
}

/** 시뮬레이션 이벤트: 운전자 제안 도착, 내 제안에 대한 운전자 응답 */
export function stepSimulation(s: VisitState, car: CarSim, now: number) {
  if (!s.driverProposal && !s.myProposal && car.progress >= SIM.driverProposalAt && s.meetId !== DRIVER_PROPOSAL.toId) {
    update((v) => ({
      ...v,
      driverProposal: { fromId: v.meetId, toId: DRIVER_PROPOSAL.toId, reason: DRIVER_PROPOSAL.reason, status: 'PENDING', at: now },
    }));
  }
  const mine = s.myProposal;
  if (mine?.status === 'PENDING' && now - mine.at >= SIM.driverAnswerMs) {
    // ponytail: 운전자는 항상 수락. 거절 시연은 ?driver=reject
    const reject = new URLSearchParams(location.search).get('driver') === 'reject';
    update((v) => ({
      ...v,
      meetId: reject ? v.meetId : mine.toId,
      myProposal: { ...mine, status: reject ? 'REJECTED' : 'ACCEPTED' },
      banner: { kind: reject ? 'rejected' : 'accepted', placeId: reject ? v.meetId : mine.toId },
    }));
  }
}


/** 앱 최상단에 한 번 둔다: 어느 화면에 있든 시뮬레이션 이벤트가 흐르게 */
export function Simulation() {
  const visit = useVisit();
  const now = useNow();
  useEffect(() => { stepSimulation(visit, carAt(visit, now), now); }, [visit, now]);
  return null;
}
