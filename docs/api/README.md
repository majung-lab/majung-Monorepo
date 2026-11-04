# 마중 API 문서

차량 앱과 동승자 웹이 함께 쓰는 방문(visit) 서버 API입니다. 서버 코드는 [apps/server](../../apps/server)에 있습니다.

- 기계용 명세(OpenAPI): [contracts/openapi.yaml](../../contracts/openapi.yaml)
- 공용 타입(TypeScript): [packages/shared/src/index.ts](../../packages/shared/src/index.ts)
- 구현: [visit.routes.ts](../../apps/server/src/modules/visit/visit.routes.ts)(입력 검사), [visit.repository.ts](../../apps/server/src/modules/visit/visit.repository.ts)(규칙·DB 처리), [realtime/index.ts](../../apps/server/src/realtime/index.ts)(WebSocket)

> API를 바꿀 때는 코드, `openapi.yaml`, `packages/shared`, 이 문서를 함께 고쳐 주세요.

## 문서 구성

| 파일 | 내용 |
|---|---|
| [conventions.md](conventions.md) | 모든 API에 공통으로 적용되는 규칙: 주소, 토큰 권한, `requestId`·`version`, 시간 형식 |
| [errors.md](errors.md) | 에러 응답 형식과 에러 코드 전체 목록, 클라이언트 대응 방법 |
| [models.md](models.md) | 응답에 나오는 데이터 구조와 enum 값 |
| [facilities.md](facilities.md) | 시설 목록 조회, 헬스체크 |
| [visits.md](visits.md) | 방문 생성, 조회, 상태 변경 |
| [proposals.md](proposals.md) | 만날 장소 변경 제안, 수락·거절, 취소 |
| [ratings.md](ratings.md) | 방문 평가 |
| [history.md](history.md) | 차량의 방문 기록 조회 |
| [realtime.md](realtime.md) | WebSocket 실시간 업데이트 |
| [walkthrough.md](walkthrough.md) | curl로 픽업 방문 하나를 처음부터 끝까지 진행해 보는 예시 |

## 엔드포인트 목록

| Method | 경로 | 호출하는 쪽 | 설명 | 문서 |
|---|---|---|---|---|
| GET | `/health` | - | 서버·DB 준비 상태 | [facilities.md](facilities.md#get-health) |
| GET | `/api/facilities` | 차량 | 시설과 출입구(장소) 목록 | [facilities.md](facilities.md#get-apifacilities) |
| POST | `/api/visits` | 차량 | 방문 생성, 운전자·동승자 토큰 발급 | [visits.md](visits.md#post-apivisits) |
| GET | `/api/visits/{token}` | 차량, 웹 | 방문 현재 상태 조회 | [visits.md](visits.md#get-apivisitstoken) |
| PATCH | `/api/visits/{token}` | 차량, 웹 | 진행 단계·위치·준비 상태 등 변경 | [visits.md](visits.md#patch-apivisitstoken) |
| POST | `/api/visits/{token}/proposals` | 차량, 웹 | 장소 변경 제안 | [proposals.md](proposals.md#post-apivisitstokenproposals) |
| POST | `/api/visits/{token}/proposals/{id}/respond` | 제안 받은 쪽 | 제안 수락·거절 | [proposals.md](proposals.md#post-apivisitstokenproposalsidrespond) |
| DELETE | `/api/visits/{token}/proposals/{id}` | 제안한 쪽 | 제안 취소 | [proposals.md](proposals.md#delete-apivisitstokenproposalsid) |
| POST | `/api/visits/{token}/rating` | 차량 | 완료된 방문 평가 | [ratings.md](ratings.md) |
| GET | `/api/history?driverDeviceId=` | 차량 | 이 기기의 방문 기록 | [history.md](history.md) |
| WS | `/ws/visits/{token}` | 차량, 웹 | 상태가 바뀔 때마다 최신 상태 수신 | [realtime.md](realtime.md) |

## 핵심 개념 요약

- **방문(visit)** 하나에 **운전자**와 **동승자** 두 참가자가 있습니다. 방문을 만들면 역할별 토큰이 하나씩 발급되고, URL의 `{token}`이 곧 권한입니다. → [conventions.md](conventions.md#토큰과-권한)
- 방문은 `PREPARING → MOVING → ARRIVED → COMPLETED` 순서로 진행되고, 끝나기 전에는 언제든 `CANCELLED`가 될 수 있습니다. → [visits.md](visits.md#진행-단계-phase-규칙)
- 상태를 바꾸는 요청에는 항상 `requestId`(중복 방지)와 `version`(동시 수정 충돌 방지)을 보냅니다. → [conventions.md](conventions.md#변경-요청-규칙-requestid와-version)
- 픽업(`PICKUP`) 장소는 한쪽이 마음대로 바꿀 수 없고, 제안하고 상대가 수락해야 바뀝니다. → [proposals.md](proposals.md)
- 시설 데이터는 모두 **예시 데이터**(`dataStatus: "EXAMPLE"`)이고 좌표도 임시 값입니다.

## 로컬에서 실행

```bash
npm ci                 # 저장소 루트에서, 처음 한 번
npm run dev:server     # http://localhost:4000
curl localhost:4000/health
```

DB 파일은 처음 실행할 때 `apps/server/data/majung.sqlite`에 자동으로 만들어집니다. 자세한 내용은 루트 [README.md](../../README.md)를 참고하세요.
