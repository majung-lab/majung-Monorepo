# 장소 변경 제안

만날 장소를 바꾸고 싶을 때 한쪽이 **제안**하고 다른 쪽이 **수락**하면 확정 장소가 바뀝니다. 픽업(`PICKUP`) 방문은 장소를 이 방법으로만 바꿀 수 있습니다. 주차·내려주기 방문은 운전자가 [직접 바꿀](visits.md#accesspointid-장소-직접-변경) 수도 있고, 제안을 써도 됩니다.

## 흐름과 규칙

```
제안(POST)  ──▶  PENDING ──┬─ 상대가 수락 ──▶ ACCEPTED  (확정 장소 변경)
                           ├─ 상대가 거절 ──▶ REJECTED
                           ├─ 제안자가 취소 ─▶ EXPIRED
                           ├─ 10분 경과 ────▶ EXPIRED
                           └─ 방문 종료 ────▶ EXPIRED
```

| 규칙 | 내용 |
|---|---|
| 누가 제안 | 운전자, 동승자 모두 가능 |
| 동시 제안 | 방문 하나에 `PENDING` 제안은 **하나만**. 있으면 409 `PROPOSAL_PENDING` |
| 누가 응답 | **제안하지 않은 쪽**만 수락·거절 가능 |
| 누가 취소 | **제안한 쪽**만 취소 가능 |
| 유효 시간 | 제안 후 **10분**. 지나면 `EXPIRED`로 보이고 응답할 수 없습니다 |
| 제안할 수 있는 장소 | 방문 생성 때 후보로 등록된 장소 중 현재 확정 장소가 아닌 곳 |

- 제안, 응답, 취소도 모두 변경 요청이라 `requestId`, `version`이 필요하고, 성공하면 `visit.version`이 1 올라갑니다.
- 제안 목록은 [VisitSnapshot](models.md#visitsnapshot)의 `proposals`에 최신순으로 들어 있습니다.
- 10분이 지난 제안은 조회할 때 바로 `EXPIRED`로 보입니다.

## POST /api/visits/{token}/proposals

장소 변경을 제안합니다.

**요청 본문**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `requestId` | string | ✓ | 1~128자 | |
| `version` | integer | ✓ | | 마지막으로 본 `visit.version` |
| `accessPointId` | string | ✓ | 숫자 문자열 | 바꾸고 싶은 장소 ID |
| `reason` | string | | 최대 500자 | 제안 이유. 상대 화면에 보여 줄 수 있습니다 |

```json
{
  "requestId": "req-p-prop",
  "version": 3,
  "accessPointId": "2",
  "reason": "비가 와서 지붕 있는 북문이 좋아요"
}
```

**응답 200:** [VisitSnapshot](models.md#visitsnapshot). `proposals` 맨 앞에 새 제안이 들어 있습니다. 확정 장소는 아직 바뀌지 않습니다.

```json
{
  "visit": { "confirmedAccessPointId": "1", "version": 4, "...": "..." },
  "proposals": [
    {
      "id": "1",
      "visitId": "83e65daf-a17f-488c-955b-b83a8b6c2964",
      "proposedBy": "PASSENGER",
      "accessPointId": "2",
      "fromAccessPointId": "1",
      "baseVersion": 3,
      "status": "PENDING",
      "reason": "비가 와서 지붕 있는 북문이 좋아요",
      "createdAt": "2026-10-30T08:37:04.741Z",
      "expiresAt": "2026-10-30T08:47:04.741Z"
    }
  ],
  "...": "..."
}
```

**에러**

| 상태 | code | 원인 |
|---|---|---|
| 400 | `INVALID_REQUEST` | 형식 오류 |
| 400 | `SAME_ACCESS_POINT` | 지금 확정된 장소와 같음 |
| 400 | `INVALID_ACCESS_POINT` | 이 방문의 후보가 아닌 장소 (예: 승하차 금지 구역) |
| 404 | `VISIT_NOT_FOUND` | 없는 토큰 |
| 409 | `VERSION_CONFLICT` | `version`이 최신이 아님 |
| 409 | `PROPOSAL_PENDING` | 답을 기다리는 제안이 이미 있음 |
| 409 | `VISIT_ENDED` | 이미 종료된 방문 |
| 409 | `REQUEST_REUSED` | 이미 성공한 `requestId`를 다른 내용으로 사용 |
| 410 | `LINK_EXPIRED` | 만료된 토큰 |

---

## POST /api/visits/{token}/proposals/{id}/respond

받은 제안을 수락하거나 거절합니다. **제안하지 않은 쪽의 토큰**으로 호출해야 합니다.

**경로 파라미터**

| 이름 | 설명 |
|---|---|
| `token` | 응답하는 사람의 토큰 |
| `id` | 제안 ID (`proposals[].id`) |

**요청 본문**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `requestId` | string | ✓ | 1~128자 |
| `version` | integer | ✓ | 마지막으로 본 `visit.version` |
| `accept` | boolean | ✓ | `true` 수락, `false` 거절 |

```json
{ "requestId": "req-d-resp", "version": 4, "accept": true }
```

**응답 200:** [VisitSnapshot](models.md#visitsnapshot)

수락하면 제안 상태가 `ACCEPTED`가 되고, **`visit.confirmedAccessPointId`가 제안한 장소로 바뀝니다.** 거절하면 `REJECTED`가 되고 장소는 그대로입니다.

```json
{
  "visit": { "confirmedAccessPointId": "2", "version": 5, "...": "..." },
  "proposals": [
    { "id": "1", "proposedBy": "PASSENGER", "accessPointId": "2", "fromAccessPointId": "1", "status": "ACCEPTED", "...": "..." }
  ],
  "...": "..."
}
```

**에러**

| 상태 | code | 원인 |
|---|---|---|
| 400 | `INVALID_REQUEST` | 형식 오류 (`accept` 누락 등) |
| 403 | `FORBIDDEN` | 자기가 낸 제안에 응답 |
| 404 | `VISIT_NOT_FOUND` | 없는 토큰 |
| 404 | `PROPOSAL_NOT_FOUND` | 이 방문에 없는 제안 ID |
| 409 | `VERSION_CONFLICT` | `version`이 최신이 아님 |
| 409 | `PROPOSAL_CLOSED` | 이미 수락·거절·만료된 제안 |
| 409 | `VISIT_ENDED` | 이미 종료된 방문 |
| 409 | `REQUEST_REUSED` | 이미 성공한 `requestId`를 다른 내용으로 사용 |
| 410 | `LINK_EXPIRED` | 만료된 토큰 |

---

## DELETE /api/visits/{token}/proposals/{id}

내가 낸 제안을 취소합니다. **제안한 쪽의 토큰**으로 호출해야 합니다. 취소된 제안은 `EXPIRED` 상태가 됩니다(별도의 `CANCELLED` 상태는 없습니다).

**요청 본문**: `DELETE`지만 본문이 필요합니다. `Content-Type: application/json`도 함께 보내세요.

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `requestId` | string | ✓ | 1~128자 |
| `version` | integer | ✓ | 마지막으로 본 `visit.version` |

```json
{ "requestId": "req-d-cancel2", "version": 6 }
```

**응답 200:** [VisitSnapshot](models.md#visitsnapshot). 해당 제안의 `status`가 `EXPIRED`로 바뀌어 있습니다.

**에러**

| 상태 | code | 원인 |
|---|---|---|
| 400 | `INVALID_REQUEST` | 본문 누락, 형식 오류 |
| 403 | `FORBIDDEN` | 남이 낸 제안을 취소 |
| 404 | `VISIT_NOT_FOUND` | 없는 토큰 |
| 404 | `PROPOSAL_NOT_FOUND` | 이 방문에 없는 제안 ID |
| 409 | `VERSION_CONFLICT` | `version`이 최신이 아님 |
| 409 | `PROPOSAL_CLOSED` | 이미 수락·거절·만료된 제안 |
| 409 | `VISIT_ENDED` | 이미 종료된 방문 |
| 409 | `REQUEST_REUSED` | 이미 성공한 `requestId`를 다른 내용으로 사용 |
| 410 | `LINK_EXPIRED` | 만료된 토큰 |
