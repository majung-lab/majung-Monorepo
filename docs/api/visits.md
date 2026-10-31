# 방문

방문 생성, 조회, 상태 변경 API입니다. 공통 규칙(토큰, `requestId`, `version`)은 [conventions.md](conventions.md)를 먼저 읽어 주세요.

## POST /api/visits

새 방문을 만들고 운전자·동승자 토큰을 발급합니다. **차량 앱**이 호출합니다.

방문을 만들면 서버가 이 모드에서 고를 수 있는 장소들을 모두 후보로 등록하고, 요청한 장소를 확정 장소로 정합니다.

**요청 본문**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `requestId` | string | ✓ | 1~128자 | 중복 방지용 고유 값 ([규칙](conventions.md#requestid-같은-요청이-두-번-처리되지-않게-하기)) |
| `facilityId` | string | ✓ | 숫자 문자열 | 방문할 시설 ID |
| `mode` | enum | ✓ | `PICKUP` / `DROPOFF` / `PARK` | 방문 종류 |
| `accessPointId` | string | ✓ | 숫자 문자열 | 처음 확정할 장소 ID |
| `driverDeviceId` | string | ✓ | 24~128자 | 차량 기기 고유 ID. 방문 기록 조회에 씁니다 |

`accessPointId`는 해당 시설의 장소 중 `allowed: true`이고, 모드에 맞는 종류여야 합니다. `PICKUP`·`DROPOFF`는 `kind: "PICKUP"`, `PARK`는 `kind: "PARKING_ENTRANCE"`입니다.

```json
{
  "requestId": "req-create-1",
  "facilityId": "1",
  "mode": "PICKUP",
  "accessPointId": "1",
  "driverDeviceId": "7f3c2a9e-1b4d-4e8a-9c6f-0a1b2c3d4e5f"
}
```

**응답 201:** [VisitSession](models.md#visitsession)

```json
{
  "visit": {
    "id": "83e65daf-a17f-488c-955b-b83a8b6c2964",
    "facilityId": "1",
    "mode": "PICKUP",
    "phase": "PREPARING",
    "confirmedAccessPointId": "1",
    "version": 1,
    "passengerReadiness": "UNSEEN",
    "updatedAt": "2026-10-30T08:36:29Z",
    "expiresAt": "2026-10-31T08:36:29.692Z",
    "parkingFloor": null,
    "completedAt": null
  },
  "driverToken": "E-PqegqQCRhu_q-QueqfuS7bBCzZ_kfr38dSMsMau2M",
  "passengerToken": "ilVVNObQ8m39KRUYqnfyy-Ch0bnsiLbjf70UMvfGeS0"
}
```

- 같은 `requestId`와 같은 본문으로 다시 보내면 새로 만들지 않고 **처음 만든 방문과 토큰을 그대로** 201로 돌려줍니다.
- `passengerToken`은 QR 링크(`<웹 주소>/v/<passengerToken>`)로 동승자에게 전달하고, `driverToken`은 차량 앱에만 보관하세요.

**에러**

| 상태 | code | 원인 |
|---|---|---|
| 400 | `INVALID_REQUEST` | 필수 값 누락, 형식 오류, 정의되지 않은 필드 |
| 400 | `INVALID_ACCESS_POINT` | 시설에 없는 장소, `allowed: false`인 장소, 모드와 맞지 않는 장소 종류 |
| 404 | `FACILITY_NOT_FOUND` | 없는 시설 ID |
| 409 | `REQUEST_REUSED` | 이미 성공한 `requestId`를 다른 내용으로 사용 |

---

## GET /api/visits/{token}

방문의 현재 상태 전체를 조회합니다. 차량 앱과 동승자 웹이 모두 씁니다.

**경로 파라미터**

| 이름 | 설명 |
|---|---|
| `token` | `driverToken` 또는 `passengerToken`. 어느 토큰이냐에 따라 응답의 `role`이 달라집니다 |

**응답 200:** [VisitSnapshot](models.md#visitsnapshot)

```json
{
  "visit": { "id": "83e65daf-...", "mode": "PICKUP", "phase": "PREPARING", "version": 1, "...": "..." },
  "role": "PASSENGER",
  "facility": { "id": "1", "name": "예시 쇼핑몰", "accessPoints": [ "..." ], "...": "..." },
  "proposals": [],
  "vehicle": null,
  "passengerLocation": null,
  "rating": null
}
```

**에러**

| 상태 | code | 원인 |
|---|---|---|
| 404 | `VISIT_NOT_FOUND` | 없는 토큰 |
| 410 | `LINK_EXPIRED` | 만료된 토큰 (생성 후 24시간 경과) |

[WebSocket](realtime.md) 연결이 끊겼을 때를 대비해, 클라이언트는 이 API를 **5초마다 호출해서** 상태를 맞추도록 계약에 정해져 있습니다.

---

## PATCH /api/visits/{token}

방문 상태를 바꿉니다. 바꾸고 싶은 필드만 보내면 되고, 여러 필드를 한 번에 보낼 수도 있습니다.

**필드마다 바꿀 수 있는 역할이 정해져 있습니다.** 한 요청에 운전자 필드와 동승자 필드를 섞어 보내면 403 `FORBIDDEN`이 납니다.

**요청 본문**

| 필드 | 타입 | 필수 | 바꿀 수 있는 역할 | 설명 |
|---|---|---|---|---|
| `requestId` | string | ✓ | - | 1~128자 |
| `version` | integer | ✓ | - | 마지막으로 본 `visit.version` |
| `phase` | enum | | 운전자 | 진행 단계. [아래 규칙](#진행-단계-phase-규칙) 참고 |
| `vehicle` | object | | 운전자 | 차량 위치·도착 예상 시간. [아래 설명](#vehicle-차량-위치) 참고 |
| `parkingFloor` | string \| null | | 운전자 | 주차한 층 (1~32자). `null`이면 지움 |
| `accessPointId` | string | | 운전자 | 확정 장소를 직접 변경. **`PARK`·`DROPOFF` 방문만 가능** |
| `passengerReadiness` | enum | | 동승자 | `UNSEEN` / `SEEN` / `HEADING_OUT` / `READY` |
| `passengerLocation` | object \| null | | 동승자 | `{ "lat": number, "lng": number }`. `null`이면 위치 공유 중단 |

- `requestId`, `version` 외에 **바꿀 필드가 최소 하나는 있어야** 합니다. 없으면 400 `INVALID_REQUEST`입니다.
- 이미 종료된(`COMPLETED`, `CANCELLED`) 방문은 바꿀 수 없습니다. → 409 `VISIT_ENDED`

### 진행 단계 (`phase`) 규칙

```
PREPARING ──▶ MOVING ──▶ ARRIVED ──▶ COMPLETED
    │            │           │
    └────────────┴───────────┴──────▶ CANCELLED
```

| 현재 단계 | 바꿀 수 있는 단계 |
|---|---|
| `PREPARING` | `MOVING`, `CANCELLED` |
| `MOVING` | `ARRIVED`, `CANCELLED` |
| `ARRIVED` | `COMPLETED`, `CANCELLED` |
| `COMPLETED`, `CANCELLED` | 없음 (종료) |

- 단계를 건너뛰거나 되돌리면 409 `INVALID_PHASE`입니다.
- `COMPLETED`가 되면 `visit.completedAt`이 기록되고, 이 방문이 기기의 [방문 기록](history.md)에 목적지 기록으로 남습니다.
- `COMPLETED`나 `CANCELLED`가 되면 답을 기다리던 제안은 모두 `EXPIRED`가 됩니다.

### `vehicle`: 차량 위치

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `lat` | number \| null | ✓ | 위도 |
| `lng` | number \| null | ✓ | 경도 |
| `etaSec` | integer \| null | ✓ | 도착까지 남은 시간(초), 0~86400 |
| `navResult` | `"set"` \| `"failed"` | | 내비게이션 목적지 설정 결과 |

- GPS를 쓸 수 없으면 `lat`, `lng`를 **둘 다 `null`** 로 보내세요. 하나만 `null`이면 400 `INVALID_POSITION`입니다. 가짜 좌표를 만들어 보낼 필요는 없습니다.
- 보낼 때마다 위치 이력이 쌓이고, 응답의 `vehicle`에는 마지막 값이 나옵니다.

### `accessPointId`: 장소 직접 변경

| 방문 모드 | 운전자가 직접 변경 |
|---|---|
| `PARK` | 가능 (주차장 입구 중에서) |
| `DROPOFF` | 가능 (승하차 구역 중에서) |
| `PICKUP` | **불가.** 403 `MUTUAL_CONSENT_REQUIRED`. [제안](proposals.md)을 보내고 동승자가 수락해야 합니다 |

방문을 만들 때 후보로 등록된 장소만 고를 수 있습니다. 그 외 장소는 400 `INVALID_ACCESS_POINT`입니다.

### 요청 예시

동승자가 준비 상태와 위치를 보냄:

```json
{
  "requestId": "req-p-1",
  "version": 1,
  "passengerReadiness": "HEADING_OUT",
  "passengerLocation": { "lat": 37.5405, "lng": 127.2193 }
}
```

운전자가 출발하면서 차량 위치를 보냄:

```json
{
  "requestId": "req-d-1",
  "version": 2,
  "phase": "MOVING",
  "vehicle": { "lat": 37.53, "lng": 127.2, "etaSec": 420, "navResult": "set" }
}
```

주차 방문에서 운전자가 장소와 주차 층을 바꿈:

```json
{ "requestId": "req-park-2", "version": 1, "accessPointId": "6", "parkingFloor": "B1" }
```

**응답 200:** 변경이 반영된 [VisitSnapshot](models.md#visitsnapshot). `visit.version`이 1 올라가 있습니다.

```json
{
  "visit": { "phase": "MOVING", "version": 3, "passengerReadiness": "HEADING_OUT", "...": "..." },
  "role": "DRIVER",
  "proposals": [],
  "vehicle": { "lat": 37.53, "lng": 127.2, "etaSec": 420, "navResult": "set", "recordedAt": "2026-10-30T08:36:44.524Z" },
  "passengerLocation": { "lat": 37.5405, "lng": 127.2193, "recordedAt": "2026-10-30T08:36:44.469Z" },
  "rating": null,
  "facility": { "...": "..." }
}
```

**에러**

| 상태 | code | 원인 |
|---|---|---|
| 400 | `INVALID_REQUEST` | 형식 오류, 바꿀 필드 없음, 정의되지 않은 필드 |
| 400 | `INVALID_POSITION` | `vehicle.lat`, `vehicle.lng` 중 하나만 `null` |
| 400 | `INVALID_ACCESS_POINT` | 이 방문의 후보가 아닌 장소 |
| 403 | `FORBIDDEN` | 내 역할이 바꿀 수 없는 필드 포함 |
| 403 | `MUTUAL_CONSENT_REQUIRED` | `PICKUP` 방문에서 `accessPointId` 변경 |
| 404 | `VISIT_NOT_FOUND` | 없는 토큰 |
| 409 | `VERSION_CONFLICT` | `version`이 최신이 아님 |
| 409 | `REQUEST_REUSED` | 이미 성공한 `requestId`를 다른 내용으로 사용 |
| 409 | `INVALID_PHASE` | 허용되지 않는 단계 이동 |
| 409 | `VISIT_ENDED` | 이미 종료된 방문 |
| 410 | `LINK_EXPIRED` | 만료된 토큰 |
