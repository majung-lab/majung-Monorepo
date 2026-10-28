// 웹·서버가 같이 쓰는 타입. 차량 앱(Kotlin)은 contracts/openapi.yaml 기준으로 DTO를 맞춘다.
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
  /** 현재 확정 장소. 변경 제안과 별도로 저장한다. */
  confirmedAccessPointId: string;
  /** 만날 곳이 바뀔 때마다 +1. 옛 버전 기준 요청은 서버가 거부한다. */
  version: number;
  passengerReadiness: PassengerReadiness;
  updatedAt: string;
  expiresAt: string;
}

export interface MeetingProposal {
  id: string;
  visitId: string;
  proposedBy: Role;
  accessPointId: string;
  baseVersion: number;
  status: ProposalStatus;
  createdAt: string;
  expiresAt: string;
}

export interface CreateVisitRequest {
  requestId: string;
  facilityId: string;
  mode: VisitMode;
  accessPointId: string;
}
