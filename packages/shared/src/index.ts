// The API maps the canonical majung-db lowercase schema to these client enums.
export type Role = 'DRIVER' | 'PASSENGER';
export type VisitMode = 'PICKUP' | 'PARK' | 'DROPOFF';
export type VisitPhase = 'PREPARING' | 'MOVING' | 'ARRIVED' | 'COMPLETED' | 'CANCELLED';
export type ProposalStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'EXPIRED' | 'CANCELLED';
export type NaviStatus = 'NONE' | 'REQUESTED' | 'GUIDING' | 'FAILED';
/** 링크 안 엶 → 링크 엶 → 준비됐어요 → 로비로 나가는 중 → 로비 도착 */
export type PassengerReadiness = 'UNSEEN' | 'SEEN' | 'READY' | 'HEADING_OUT' | 'AT_LOBBY';
export interface Visit {
  id: string;
  facilityId: string;
  mode: VisitMode;
  phase: VisitPhase;
  confirmedAccessPointId: string;
  version: number;
  passengerReadiness: PassengerReadiness;
  updatedAt: string;
  expiresAt: string;
  parkingFloor: string | null;
  completedAt: string | null;
}
export interface AccessPoint {
  id: string;
  name: string;
  lat: number;
  lng: number;
  description: string;
  kind: 'PICKUP' | 'PARKING_ENTRANCE';
  allowed: boolean;
  waitSpot: { name: string; floor: string | null; description: string } | null;
  walkMinutes: number | null;
  covered: boolean;
  /** 이 지점까지 동선이 있는 '사람이 기다리는 곳'들. 가까운 순 */
  waitSpots: WaitSpotOption[];
}
/** 동승자가 고를 수 있는 대기 지점 (로비, 휴게실 등)과 차 타는 곳까지의 동선 */
export interface WaitSpotOption {
  id: string;
  name: string;
  floor: string | null;
  description: string;
  walkMinutes: number;
  covered: boolean;
}
/** 시설 위치의 앞으로 몇 시간 날씨. source DEMO = 키 없이 시연용 */
export interface Weather {
  condition: 'CLEAR' | 'CLOUDY' | 'RAIN' | 'SNOW';
  tempC: number | null;
  rainChance: number | null;
  /** 지금 비·눈이 오거나 6시간 안에 올 것 같음 → 지붕 있는 곳을 먼저 추천 */
  rainSoon: boolean;
  /** 화면에 그대로 띄우는 한 줄. 예: "15시부터 비 · 강수확률 70%" */
  summary: string;
  checkedAt: string;
  source: 'KMA' | 'DEMO';
}
export interface RankedAccessPoint {
  accessPointId: string;
  recommended: boolean;
  /** 추천·주의 이유 한 줄. null이면 지점 설명을 그대로 쓴다 */
  reason: string | null;
  /** 동승자 추천에서만: 고른 대기 지점·이동 조건으로는 갈 수 없는 이유. 있으면 추천 대상이 아니다 */
  excludedReason?: string | null;
  /** 동승자 추천에서만: 고른 대기 지점에서 이 지점까지 걷는 시간 */
  walkMinutes?: number | null;
}
export interface FacilityWeather {
  /** 날씨를 못 받으면 null (키 없음, 기상청 응답 실패) */
  weather: Weather | null;
  /** 요청한 모드에서 쓸 수 있는 지점만, 추천 순서대로 */
  ranking: RankedAccessPoint[];
}
export interface Facility {
  id: string;
  name: string;
  address: string;
  lat: number;
  lng: number;
  accessPoints: AccessPoint[];
  /** 시설 안에서 사람이 기다리는 곳 전부 (동승자가 '지금 여기 있어요'로 고른다) */
  waitSpots: FacilityWaitSpot[];
  dataStatus: 'EXAMPLE';
}
export interface FacilityWaitSpot { id: string; name: string; floor: string | null; description: string }
/** 동승자 이동 조건. 대기 지점에서 승하차 구역까지의 동선을 고를 때 쓴다 */
export interface MoveConditions { stroller: boolean; luggage: boolean; avoidStairs: boolean; indoorOnly: boolean }
/** 동승자가 고른 대기 지점과, 거기서 지금 만날 곳까지의 동선. 동선 정보가 없으면 walkMinutes가 null */
export interface PassengerWaitSpot extends FacilityWaitSpot { walkMinutes: number | null; covered: boolean }
export interface MeetingProposal {
  id: string;
  visitId: string;
  proposedBy: Role;
  accessPointId: string;
  fromAccessPointId: string;
  baseVersion: number;
  status: ProposalStatus;
  reason?: string;
  /** CANCELLED인 이유. NAV_ENDED: 운전자가 내비 안내를 꺼서 기다리던 제안이 닫힘 */
  cancelReason?: 'NAV_ENDED';
  createdAt: string;
  expiresAt: string;
}
export interface VehicleStatus {
  lat: number | null;
  lng: number | null;
  etaSec: number | null;
  /** ended: 운전자가 내비 안내를 끔. 이때는 남은 시간을 모르고 차 위치만 계속 온다 */
  navResult: 'set' | 'failed' | 'ended' | null;
  /** 안내를 끈 시각. 다시 안내하거나 만날 곳이 바뀌면 null */
  navEndedAt: string | null;
  /** 이동 중 처음 받은 도착 예정 시각. 지금 예상이 이보다 늦으면 늦어짐을 알린다. 만날 곳이 바뀌면 다시 정한다 */
  plannedArrivalAt: string | null;
  /** 안내를 끈 뒤 다시 안내를 시작한 시각. 다시 끄거나 만날 곳이 바뀌면 null */
  navResumedAt: string | null;
  recordedAt: string;
}
export interface PassengerLocation { lat: number; lng: number; recordedAt: string }
export interface PlaceRating { rating: number; tags: string[]; comment: string | null }
export interface VisitSnapshot {
  visit: Visit;
  role: Role;
  facility: Facility;
  proposals: MeetingProposal[];
  vehicle: VehicleStatus | null;
  passengerLocation: PassengerLocation | null;
  /** 동승자가 '지금 여기 있어요'로 고른 곳. 만날 곳이 바뀌어도 남는다 */
  passengerWaitSpot: PassengerWaitSpot | null;
  passengerConditions: MoveConditions;
  rating: PlaceRating | null;
}
export interface CreateVisitRequest {
  requestId: string;
  facilityId: string;
  mode: VisitMode;
  accessPointId: string;
  /** Unguessable device-generated UUID; also authorizes access to device history. */
  driverDeviceId: string;
}
export interface VisitSession { visit: Visit; driverToken: string; passengerToken: string }
export interface MutationRequest { requestId: string; version: number }
export interface UpdateVisitRequest extends MutationRequest {
  /** Driver-only direct change for PARK/DROPOFF; PICKUP uses proposals. */
  accessPointId?: string;
  phase?: VisitPhase;
  passengerReadiness?: PassengerReadiness;
  passengerLocation?: { lat: number; lng: number } | null;
  /** Passenger-only. 시설의 waitSpots 중 하나, null이면 지움 */
  passengerWaitSpotId?: string | null;
  /** Passenger-only */
  passengerConditions?: MoveConditions;
  parkingFloor?: string | null;
  vehicle?: { lat: number | null; lng: number | null; etaSec: number | null; navResult?: 'set' | 'failed' | 'ended' };
}
/** Exactly one of accessPointId (registered point) or location (free map pin). */
export interface CreateProposalRequest extends MutationRequest { accessPointId?: string; location?: { lat: number; lng: number }; reason?: string }
export interface RespondProposalRequest extends MutationRequest { accept: boolean }
export interface RateVisitRequest extends MutationRequest { rating: number; tags: string[]; comment?: string }
export interface HistoryEntry {
  visit: Visit;
  facility: Facility;
  rating: PlaceRating | null;
  /** 방문을 만들 때의 날씨 */
  weather: Weather | null;
}
/** 운전자가 동승자 링크를 문자로 보낸다. 받는 번호는 저장하지 않는다 */
export interface CreateInviteRequest { requestId: string; phone: string }
export interface InviteResult { sentTo: string }
