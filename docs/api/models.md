# 데이터 모델

API 응답에 나오는 데이터 구조입니다. TypeScript 정의는 [packages/shared/src/index.ts](../../packages/shared/src/index.ts)에 있습니다.

- **필수** 표시가 없는 필드는 응답에서 빠질 수 있습니다.
- `| null`이 붙은 필드는 항상 응답에 들어 있지만 값이 `null`일 수 있습니다.

## API별 응답 구조

| 리소스 | API | 성공 응답 |
|---|---|---|
| 시설 | [GET /health](facilities.md#get-health) | `{ ok, database, facilityData }` (해당 문서 참고) |
| | [GET /api/facilities](facilities.md#get-apifacilities) | [Facility](#facility)[] |
| 방문 | [POST /api/visits](visits.md#post-apivisits) | [VisitSession](#visitsession) |
| | [GET /api/visits/{token}](visits.md#get-apivisitstoken) | [VisitSnapshot](#visitsnapshot) |
| | [PATCH /api/visits/{token}](visits.md#patch-apivisitstoken) | [VisitSnapshot](#visitsnapshot) |
| 제안 | [POST /api/visits/{token}/proposals](proposals.md#post-apivisitstokenproposals) | [VisitSnapshot](#visitsnapshot) |
| | [POST /api/visits/{token}/proposals/{id}/respond](proposals.md#post-apivisitstokenproposalsidrespond) | [VisitSnapshot](#visitsnapshot) |
| | [DELETE /api/visits/{token}/proposals/{id}](proposals.md#delete-apivisitstokenproposalsid) | [VisitSnapshot](#visitsnapshot) |
| 평가 | [POST /api/visits/{token}/rating](ratings.md#post-apivisitstokenrating) | [VisitSnapshot](#visitsnapshot) |
| 기록 | [GET /api/history](history.md#get-apihistory) | [HistoryEntry](#historyentry)[] |
| 실시간 | [WS /ws/visits/{token}](realtime.md#ws-wsvisitstoken) | [VisitSnapshot](#visitsnapshot) (메시지마다) |

에러 응답은 모든 API가 같은 형식을 씁니다. → [errors.md](errors.md#응답-형식)

## Enum 값

### `mode`: 방문 종류

| 값 | 의미 | 쓸 수 있는 장소 종류(`AccessPoint.kind`) |
|---|---|---|
| `PICKUP` | 동승자를 태우러 감 | `PICKUP` |
| `DROPOFF` | 동승자를 내려 줌 | `PICKUP` |
| `PARK` | 주차 후 시설에서 만남 | `PARKING_ENTRANCE` |

### `phase`: 진행 단계

| 값 | 의미 |
|---|---|
| `PREPARING` | 방문 생성됨, 아직 출발 전 |
| `MOVING` | 차량 이동 중 |
| `ARRIVED` | 차량이 장소에 도착 |
| `COMPLETED` | 방문 완료 (종료 상태) |
| `CANCELLED` | 방문 취소 (종료 상태) |

단계 이동 규칙은 [visits.md](visits.md#진행-단계-phase-규칙)를 참고하세요.

### `passengerReadiness`: 동승자 준비 상태

| 값 | 의미 |
|---|---|
| `UNSEEN` | 동승자가 아직 링크를 보지 않음 (초기값) |
| `SEEN` | 링크를 봤고 준비 중 |
| `HEADING_OUT` | 대기 장소(로비)로 이동 중 |
| `READY` | 대기 장소에 도착해 기다리는 중 |

### `role`: 역할

`DRIVER`(운전자), `PASSENGER`(동승자)

### 제안 `status`

| 값 | 의미 |
|---|---|
| `PENDING` | 상대방 응답을 기다리는 중 |
| `ACCEPTED` | 수락됨. 확정 장소가 제안 장소로 바뀜 |
| `REJECTED` | 거절됨 |
| `EXPIRED` | 10분이 지났거나, 제안한 사람이 취소했거나, 방문이 종료됨 |

## Visit

방문 하나의 기본 정보입니다.

**포함되는 곳:** [VisitSession](#visitsession).visit, [VisitSnapshot](#visitsnapshot).visit, [HistoryEntry](#historyentry).visit

**관련 API:** [POST /api/visits](visits.md#post-apivisits), [GET](visits.md#get-apivisitstoken)·[PATCH /api/visits/{token}](visits.md#patch-apivisitstoken), [GET /api/history](history.md#get-apihistory)

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | string, 필수 | 방문 UUID. 이것만으로는 권한이 없습니다 |
| `facilityId` | string, 필수 | 시설 ID |
| `mode` | enum, 필수 | `PICKUP` / `DROPOFF` / `PARK` |
| `phase` | enum, 필수 | 진행 단계 |
| `confirmedAccessPointId` | string, 필수 | 지금 확정된 장소 ID. `PARK`이면 주차장 입구 |
| `version` | integer, 필수 | 1부터 시작, 변경이 성공할 때마다 +1. 변경 요청에 그대로 담아 보냅니다 |
| `passengerReadiness` | enum, 필수 | 동승자 준비 상태 |
| `updatedAt` | string(시각), 필수 | 마지막 변경 시각 |
| `expiresAt` | string(시각), 필수 | 토큰 만료 시각 (생성 + 24시간) |
| `parkingFloor` | string \| null, 필수 | 운전자가 입력한 주차 층 (예: `"B1"`) |
| `completedAt` | string(시각) \| null, 필수 | `COMPLETED`가 된 시각 |

```json
{
  "id": "83e65daf-a17f-488c-955b-b83a8b6c2964",
  "facilityId": "1",
  "mode": "PICKUP",
  "phase": "MOVING",
  "confirmedAccessPointId": "2",
  "version": 5,
  "passengerReadiness": "HEADING_OUT",
  "updatedAt": "2026-10-30T08:37:04.788Z",
  "expiresAt": "2026-10-31T08:36:29.692Z",
  "parkingFloor": null,
  "completedAt": null
}
```

## Facility

시설(쇼핑몰, 미술관 등)과 그 시설의 장소 목록입니다.

**포함되는 곳:** [VisitSnapshot](#visitsnapshot).facility, [HistoryEntry](#historyentry).facility

**관련 API:** [GET /api/facilities](facilities.md#get-apifacilities) (배열로 직접 반환), [GET /api/visits/{token}](visits.md#get-apivisitstoken), [GET /api/history](history.md#get-apihistory)

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | string, 필수 | 시설 ID |
| `name` | string, 필수 | 시설 이름 |
| `address` | string, 필수 | 주소 (없으면 빈 문자열) |
| `lat`, `lng` | number, 필수 | 시설 대표 좌표 |
| `accessPoints` | [AccessPoint](#accesspoint)[], 필수 | 차량이 서거나 들어가는 장소 목록 (운영 중인 것만) |
| `dataStatus` | `"EXAMPLE"`, 필수 | 지금은 항상 `EXAMPLE`. 검증되지 않은 예시 데이터라는 뜻입니다 |

## AccessPoint

차량이 정차하거나 진입하는 장소(승하차 구역, 주차장 입구)입니다. 방문의 "장소"는 모두 이 ID로 가리킵니다.

**포함되는 곳:** [Facility](#facility).accessPoints

**관련 API:** [GET /api/facilities](facilities.md#get-apifacilities). 이 ID를 요청에 쓰는 API는 [POST /api/visits](visits.md#post-apivisits), [PATCH /api/visits/{token}](visits.md#accesspointid-장소-직접-변경), [POST /api/visits/{token}/proposals](proposals.md#post-apivisitstokenproposals)입니다

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | string, 필수 | 장소 ID |
| `name` | string, 필수 | 장소 이름 (예: "북문 승하차 구역") |
| `lat`, `lng` | number, 필수 | 장소 좌표 |
| `kind` | enum, 필수 | `PICKUP`(승하차 구역) / `PARKING_ENTRANCE`(주차장 입구) |
| `allowed` | boolean, 필수 | 방문 장소로 고를 수 있는지. `false`면 승하차 금지 구역이거나 연결된 대기 장소가 없는 곳입니다 |
| `description` | string, 필수 | 대기 장소, 이동 경로, 차량 안내를 ` · `로 이어 붙인 설명 |
| `waitSpot` | object \| null, 필수 | 동승자가 기다리는 실내 장소. `{ name, floor (string\|null), description }` |
| `walkMinutes` | integer \| null, 필수 | 대기 장소에서 이 장소까지 걸어서 걸리는 시간(분) |
| `covered` | boolean, 필수 | 이동 경로에 지붕이 있는지 (비 올 때 참고) |

```json
{
  "id": "2",
  "name": "북문 승하차 구역",
  "lat": 37.541,
  "lng": 127.22,
  "kind": "PICKUP",
  "allowed": true,
  "description": "1층 북문 로비 · 북문 안쪽, 비 안 맞음 · 북문 로비 → 북문 구역, 지붕 연결 · 지붕 있음, 엘리베이터 바로 옆",
  "waitSpot": { "name": "1층 북문 로비", "floor": "1F", "description": "북문 안쪽, 비 안 맞음" },
  "walkMinutes": 1,
  "covered": true
}
```

## MeetingProposal

장소 변경 제안입니다. 자세한 규칙은 [proposals.md](proposals.md#흐름과-규칙)를 참고하세요.

**포함되는 곳:** [VisitSnapshot](#visitsnapshot).proposals

**관련 API:** [제안](proposals.md#post-apivisitstokenproposals), [수락·거절](proposals.md#post-apivisitstokenproposalsidrespond), [취소](proposals.md#delete-apivisitstokenproposalsid)

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | string, 필수 | 제안 ID |
| `visitId` | string, 필수 | 방문 UUID |
| `proposedBy` | enum, 필수 | 제안한 역할 `DRIVER` / `PASSENGER` |
| `accessPointId` | string, 필수 | 바꾸자고 제안한 장소 |
| `fromAccessPointId` | string, 필수 | 제안할 당시 확정돼 있던 장소 |
| `baseVersion` | integer, 필수 | 제안할 당시의 방문 version |
| `status` | enum, 필수 | `PENDING` / `ACCEPTED` / `REJECTED` / `EXPIRED` |
| `reason` | string | 제안 이유. 보내지 않았으면 필드가 없습니다 |
| `createdAt` | string(시각), 필수 | 제안 시각 |
| `expiresAt` | string(시각), 필수 | 응답 마감 시각 (제안 + 10분) |

## PlaceRating

방문 평가입니다.

**포함되는 곳:** [VisitSnapshot](#visitsnapshot).rating, [HistoryEntry](#historyentry).rating

**관련 API:** [POST /api/visits/{token}/rating](ratings.md#post-apivisitstokenrating) (저장), [GET /api/history](history.md#get-apihistory) (조회)

| 필드 | 타입 | 설명 |
|---|---|---|
| `rating` | integer, 필수 | 1~5 |
| `tags` | string[], 필수 | 태그 목록 (빈 배열 가능) |
| `comment` | string \| null, 필수 | 코멘트 |

## VisitSnapshot

방문 조회, 변경 API와 WebSocket이 돌려주는 **방문 전체 상태**입니다.

**반환하는 API:**
- 방문: [GET /api/visits/{token}](visits.md#get-apivisitstoken), [PATCH /api/visits/{token}](visits.md#patch-apivisitstoken)
- 제안: [제안](proposals.md#post-apivisitstokenproposals), [수락·거절](proposals.md#post-apivisitstokenproposalsidrespond), [취소](proposals.md#delete-apivisitstokenproposalsid)
- 평가: [POST /api/visits/{token}/rating](ratings.md#post-apivisitstokenrating)
- 실시간: [WS /ws/visits/{token}](realtime.md#ws-wsvisitstoken)

| 필드 | 타입 | 설명 |
|---|---|---|
| `visit` | [Visit](#visit), 필수 | 방문 정보 |
| `role` | enum, 필수 | 이 응답을 받는 사람의 역할 (호출한 토큰 기준) |
| `facility` | [Facility](#facility), 필수 | 방문 대상 시설 전체 정보 |
| `proposals` | [MeetingProposal](#meetingproposal)[], 필수 | 이 방문의 모든 제안, **최신순** |
| `vehicle` | object \| null, 필수 | 차량의 마지막 위치. 한 번도 보내지 않았으면 `null` |
| `passengerLocation` | object \| null, 필수 | 동승자의 마지막 위치. 공유하지 않으면 `null` |
| `rating` | [PlaceRating](#placerating) \| null, 필수 | 평가. 아직 없으면 `null` |

`vehicle` 객체:

| 필드 | 타입 | 설명 |
|---|---|---|
| `lat`, `lng` | number \| null | 차량 좌표. GPS가 없으면 둘 다 `null` |
| `etaSec` | integer \| null | 도착 예상까지 남은 시간(초) |
| `navResult` | `"set"` \| `"failed"` \| null | 내비게이션 목적지 설정 결과 |
| `recordedAt` | string(시각) | 서버가 받은 시각 |

`passengerLocation` 객체: `{ lat, lng, recordedAt }`

```json
{
  "visit": { "id": "83e65daf-...", "phase": "MOVING", "version": 3, "...": "..." },
  "role": "DRIVER",
  "facility": { "id": "1", "name": "예시 쇼핑몰", "accessPoints": [ "..." ], "...": "..." },
  "proposals": [],
  "vehicle": { "lat": 37.53, "lng": 127.2, "etaSec": 420, "navResult": "set", "recordedAt": "2026-10-30T08:36:44.524Z" },
  "passengerLocation": { "lat": 37.5405, "lng": 127.2193, "recordedAt": "2026-10-30T08:36:44.469Z" },
  "rating": null
}
```

## VisitSession

방문 생성 응답입니다.

**반환하는 API:** [POST /api/visits](visits.md#post-apivisits)

| 필드 | 타입 | 설명 |
|---|---|---|
| `visit` | [Visit](#visit), 필수 | 만들어진 방문 |
| `driverToken` | string, 필수 | 운전자 토큰. 차량 앱에만 보관 |
| `passengerToken` | string, 필수 | 동승자 토큰. QR 링크에 넣어 전달 |

## HistoryEntry

방문 기록 한 건입니다. 토큰과 동승자 위치는 들어 있지 않습니다.

**반환하는 API:** [GET /api/history](history.md#get-apihistory) (배열)

| 필드 | 타입 | 설명 |
|---|---|---|
| `visit` | [Visit](#visit), 필수 | 방문 정보 |
| `facility` | [Facility](#facility), 필수 | 시설 정보 |
| `rating` | [PlaceRating](#placerating) \| null, 필수 | 평가 |
