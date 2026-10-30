// The API maps the canonical majung-db lowercase schema to these client enums.
export type Role = 'DRIVER' | 'PASSENGER';
export type VisitMode = 'PICKUP' | 'PARK' | 'DROPOFF';
export type VisitPhase = 'PREPARING' | 'MOVING' | 'ARRIVED' | 'COMPLETED' | 'CANCELLED';
export type ProposalStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'EXPIRED';
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
export interface CreateProposalRequest extends MutationRequest { accessPointId: string; reason?: string }
export interface RespondProposalRequest extends MutationRequest { accept: boolean }
export interface RateVisitRequest extends MutationRequest { rating: number; tags: string[]; comment?: string }
export interface HistoryEntry { visit: Visit; facility: Facility; rating: PlaceRating | null }
