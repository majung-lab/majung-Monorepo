// The API maps the canonical majung-db lowercase schema to these client enums.
export type Role = 'DRIVER' | 'PASSENGER';
export type VisitMode = 'PICKUP' | 'PARK' | 'DROPOFF';
export type VisitPhase = 'PREPARING' | 'MOVING' | 'ARRIVED' | 'COMPLETED' | 'CANCELLED';
export type ProposalStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'EXPIRED' | 'CANCELLED';
export type NaviStatus = 'NONE' | 'REQUESTED' | 'GUIDING' | 'FAILED';
export type PassengerReadiness = 'UNSEEN' | 'SEEN' | 'READY' | 'HEADING_OUT';
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
  dataStatus: 'EXAMPLE';
}
export interface MeetingProposal {
  id: string;
  visitId: string;
  proposedBy: Role;
  accessPointId: string;
  fromAccessPointId: string;
  baseVersion: number;
  status: ProposalStatus;
  reason?: string;
  createdAt: string;
  expiresAt: string;
}
export interface VehicleStatus {
  lat: number | null;
  lng: number | null;
  etaSec: number | null;
  navResult: 'set' | 'failed' | null;
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
  parkingFloor?: string | null;
  vehicle?: { lat: number | null; lng: number | null; etaSec: number | null; navResult?: 'set' | 'failed' };
}
/** Exactly one of accessPointId (registered point) or location (free map pin). */
export interface CreateProposalRequest extends MutationRequest { accessPointId?: string; location?: { lat: number; lng: number }; reason?: string }
export interface RespondProposalRequest extends MutationRequest { accept: boolean }
export interface RateVisitRequest extends MutationRequest { rating: number; tags: string[]; comment?: string }
export interface HistoryEntry { visit: Visit; facility: Facility; rating: PlaceRating | null }
