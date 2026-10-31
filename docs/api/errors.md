# 에러

## 응답 형식

에러는 HTTP 상태 코드와 함께 아래 형식으로 돌아옵니다.

```json
{ "code": "VERSION_CONFLICT", "message": "다른 사람이 방문을 변경했어요. 최신 상태를 확인해 주세요" }
```

| 필드 | 설명 |
|---|---|
| `code` | 에러 종류. **클라이언트 분기 처리는 이 값으로 하세요.** |
| `message` | 사용자에게 그대로 보여 줄 수 있는 한국어 문장. 바뀔 수 있으니 분기 조건으로 쓰지 마세요. |

**예외:** 존재하지 않는 경로를 호출하면 서버 프레임워크(Fastify) 기본 형식으로 404가 옵니다. 주로 URL 오타일 때입니다.

```json
{ "message": "Route POST:/api/visits/abc not found", "error": "Not Found", "statusCode": 404 }
```

## 에러 코드 목록

### 400 Bad Request: 요청 내용이 잘못됨

| code | 언제 | message |
|---|---|---|
| `INVALID_REQUEST` | 필수 값 누락, 타입·길이·범위 위반, 정의되지 않은 필드 포함, PATCH에 바꿀 필드가 하나도 없음 | 요청 내용을 확인해 주세요 |
| `INVALID_ACCESS_POINT` | 이 방문(또는 모드)에 쓸 수 없는 장소 ID | 이 방문에 사용할 수 없는 장소예요 |
| `INVALID_POSITION` | 차량 위치의 `lat`, `lng` 중 하나만 `null` | 위도와 경도를 함께 보내 주세요 |
| `SAME_ACCESS_POINT` | 지금 확정된 장소와 같은 장소를 제안 | 현재 장소와 같아요 |

`INVALID_REQUEST`는 어떤 필드가 잘못됐는지 알려 주지 않습니다. [models.md](models.md)와 각 API 문서의 제약 조건을 확인하세요.

### 403 Forbidden: 이 역할로는 할 수 없음

| code | 언제 | message |
|---|---|---|
| `FORBIDDEN` | 운전자 전용 필드를 동승자가 보냄(또는 그 반대), 동승자가 평가 시도 | 이 작업을 할 권한이 없어요 |
| `FORBIDDEN` | 내가 낸 제안에 내가 응답, 남이 낸 제안을 취소 | 이 제안에 답할 권한이 없어요 |
| `MUTUAL_CONSENT_REQUIRED` | 픽업(`PICKUP`) 방문에서 장소를 직접 바꾸려고 함 | 픽업 장소는 상대방이 제안을 수락해야 바뀌어요 |

### 404 Not Found: 대상이 없음

| code | 언제 | message |
|---|---|---|
| `VISIT_NOT_FOUND` | 토큰이 틀림 | 방문 링크를 확인해 주세요 |
| `FACILITY_NOT_FOUND` | 방문 생성 시 없는 시설 ID | 시설을 찾을 수 없어요 |
| `PROPOSAL_NOT_FOUND` | 이 방문에 없는 제안 ID | 제안을 찾을 수 없어요 |

### 409 Conflict: 현재 상태와 맞지 않음

| code | 언제 | message |
|---|---|---|
| `VERSION_CONFLICT` | 보낸 `version`이 최신이 아님 | 다른 사람이 방문을 변경했어요. 최신 상태를 확인해 주세요 |
| `VERSION_CONFLICT` | 처리 도중 다른 요청이 먼저 방문을 바꿈 (드묾) | 방문 상태가 바뀌었어요 |
| `REQUEST_REUSED` | 이미 성공한 `requestId`를 다른 내용으로 다시 사용 | 같은 요청 번호를 다른 작업에 사용할 수 없어요 |
| `INVALID_PHASE` | 허용되지 않는 단계 이동 (예: `PREPARING`에서 바로 `COMPLETED`) | 방문 단계 순서가 올바르지 않아요 |
| `VISIT_ENDED` | 이미 `COMPLETED`·`CANCELLED`된 방문을 변경하거나 제안 | 이미 종료된 방문이에요 |
| `PROPOSAL_PENDING` | 답을 기다리는 제안이 이미 있는데 새 제안 | 진행 중인 제안에 먼저 답해 주세요 |
| `PROPOSAL_CLOSED` | 이미 수락·거절·만료된 제안에 응답하거나 취소 | 이미 답했거나 만료된 제안이에요 |
| `VISIT_NOT_COMPLETED` | 완료되지 않은 방문을 평가 (취소된 방문 포함) | 방문을 완료한 뒤 평가해 주세요 |
| `ALREADY_RATED` | 이미 평가한 방문을 다시 평가 | 이미 평가한 방문이에요 |

### 410 Gone: 링크 만료

| code | 언제 | message |
|---|---|---|
| `LINK_EXPIRED` | 생성 후 24시간이 지난 토큰 사용 | 방문 링크가 만료됐어요 |

### 500 Internal Server Error

| code | 언제 | message |
|---|---|---|
| `INTERNAL_ERROR` | 서버 내부 오류. 서버 로그를 확인하세요. | 요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요 |

## 클라이언트 대응 가이드

| 상황 | 권장 동작 |
|---|---|
| 네트워크 오류·타임아웃 (응답 없음) | **같은 `requestId`와 같은 본문**으로 재시도 |
| `VERSION_CONFLICT` | 최신 상태를 다시 받아 화면 갱신 → 사용자가 다시 결정 → **새 `requestId`** 로 전송 |
| `PROPOSAL_PENDING`, `PROPOSAL_CLOSED` | 최신 상태를 다시 받아 제안 목록 갱신 |
| `VISIT_ENDED` | 종료 화면으로 이동 |
| `LINK_EXPIRED` | 만료 안내 화면 표시. 재시도해도 결과는 같습니다 |
| `VISIT_NOT_FOUND` | 잘못된 링크 안내 |
| 400, 403 | 클라이언트 버그일 가능성이 높습니다. 재시도하지 말고 로그를 남기세요 |
| 500 | 잠시 후 같은 `requestId`로 재시도 |
