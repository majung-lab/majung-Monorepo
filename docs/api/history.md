# 방문 기록

## GET /api/history

차량 기기 하나의 방문 기록을 조회합니다. 차량 앱의 기록 화면에서 씁니다.

토큰 대신 `driverDeviceId`로 조회하기 때문에 **링크가 만료된 방문도 볼 수 있습니다.** 그래서 `driverDeviceId`는 토큰만큼 비밀로 다뤄야 합니다.

**쿼리 파라미터**

| 이름 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `driverDeviceId` | string | ✓ | 24~128자 | 방문을 만들 때 보낸 기기 ID |

```bash
curl "http://localhost:4000/api/history?driverDeviceId=7f3c2a9e-1b4d-4e8a-9c6f-0a1b2c3d4e5f"
```

**응답 200:** [HistoryEntry](models.md#historyentry) 배열

- 최근에 만든 방문부터 **최대 100개**를 돌려줍니다.
- 진행 중, 완료, 취소, 만료된 방문이 모두 포함됩니다.
- 토큰과 동승자 위치는 들어 있지 않습니다.
- 기록이 없는 기기 ID면 빈 배열 `[]`을 돌려줍니다. 에러가 나지 않습니다.

```json
[
  {
    "visit": {
      "id": "83e65daf-a17f-488c-955b-b83a8b6c2964",
      "facilityId": "1",
      "mode": "PICKUP",
      "phase": "COMPLETED",
      "confirmedAccessPointId": "2",
      "version": 10,
      "passengerReadiness": "HEADING_OUT",
      "updatedAt": "2026-10-30T08:37:04.899Z",
      "expiresAt": "2026-10-31T08:36:29.692Z",
      "parkingFloor": null,
      "completedAt": "2026-10-30T08:37:04Z"
    },
    "facility": { "id": "1", "name": "예시 쇼핑몰", "accessPoints": [ "..." ], "...": "..." },
    "rating": {
      "rating": 4,
      "tags": ["지붕 있음", "대기 편함"],
      "comment": "북문이 비 피하기 좋았어요"
    }
  }
]
```

**에러**

| 상태 | code | 원인 |
|---|---|---|
| 400 | `INVALID_REQUEST` | `driverDeviceId` 누락, 길이 제약(24~128자) 위반 |
