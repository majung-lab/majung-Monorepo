# 방문 평가

## POST /api/visits/{token}/rating

완료된 방문의 장소를 평가합니다. **운전자 토큰으로만** 호출할 수 있습니다.

| 규칙 | 내용 |
|---|---|
| 누가 | 운전자만. 동승자는 403 `FORBIDDEN` |
| 언제 | `phase`가 `COMPLETED`일 때만. 취소된 방문은 평가할 수 없습니다 |
| 몇 번 | 방문당 **한 번** |
| 기한 | 링크 만료(생성 후 24시간) 전까지. 만료 후에는 410 `LINK_EXPIRED` |
| 평가 대상 | 방문의 최종 확정 장소 |

**요청 본문**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `requestId` | string | ✓ | 1~128자 | |
| `version` | integer | ✓ | | 마지막으로 본 `visit.version` |
| `rating` | integer | ✓ | 1~5 | 점수 |
| `tags` | string[] | ✓ | 최대 10개, 각 80자 이하 | 태그. 없으면 빈 배열 `[]` |
| `comment` | string | | 최대 1000자 | 코멘트 |

```json
{
  "requestId": "req-d-rate",
  "version": 9,
  "rating": 4,
  "tags": ["지붕 있음", "대기 편함"],
  "comment": "북문이 비 피하기 좋았어요"
}
```

**응답 200:** [VisitSnapshot](models.md#visitsnapshot). `rating`에 저장된 평가가 들어 있습니다.

```json
{
  "visit": { "phase": "COMPLETED", "version": 10, "...": "..." },
  "rating": {
    "rating": 4,
    "tags": ["지붕 있음", "대기 편함"],
    "comment": "북문이 비 피하기 좋았어요"
  },
  "...": "..."
}
```

저장된 평가는 링크가 만료된 뒤에도 [방문 기록](history.md)에서 볼 수 있습니다.

**에러**

| 상태 | code | 원인 |
|---|---|---|
| 400 | `INVALID_REQUEST` | 형식 오류 (점수 범위 밖, `tags` 누락 등) |
| 403 | `FORBIDDEN` | 동승자 토큰으로 호출 |
| 404 | `VISIT_NOT_FOUND` | 없는 토큰 |
| 409 | `VERSION_CONFLICT` | `version`이 최신이 아님 |
| 409 | `VISIT_NOT_COMPLETED` | 아직 완료되지 않았거나 취소된 방문 |
| 409 | `ALREADY_RATED` | 이미 평가함 |
| 409 | `REQUEST_REUSED` | 이미 성공한 `requestId`를 다른 내용으로 사용 |
| 410 | `LINK_EXPIRED` | 만료된 토큰 |
