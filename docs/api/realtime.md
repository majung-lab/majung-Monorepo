# 실시간 업데이트 (WebSocket)

## WS /ws/visits/{token}

방문 상태가 바뀔 때마다 최신 [VisitSnapshot](models.md#visitsnapshot)을 받는 WebSocket입니다. 상대방이 준비 상태를 바꾸거나 제안을 보내면 바로 화면에 반영할 때 씁니다.

이 엔드포인트는 OpenAPI의 `paths`에는 없고 [openapi.yaml](../../contracts/openapi.yaml) 맨 아래 `x-websocket` 항목에 설명돼 있습니다. 그래서 Swagger 화면에는 나오지 않습니다.

**접속 주소**

| 환경 | 주소 |
|---|---|
| 서버에 직접 | `ws://localhost:4000/ws/visits/<token>` |
| 웹 개발 서버를 거칠 때 | `ws://localhost:5173/ws/visits/<token>` (Vite가 4000번으로 전달) |

`token`은 REST API와 같은 `driverToken` 또는 `passengerToken`입니다.

## 동작

1. **접속하자마자** 현재 상태를 담은 메시지가 하나 옵니다.
2. 이후 **그 방문에 변경이 성공할 때마다** 메시지가 옵니다. 내가 보낸 변경도 포함됩니다.
3. 메시지는 `VisitSnapshot` JSON 객체 **그 자체**입니다. `{ type, data }` 같은 감싸는 형식이 없습니다.
4. 메시지의 `role`은 **접속한 토큰의 역할**입니다. 운전자 소켓은 `DRIVER`, 동승자 소켓은 `PASSENGER` 기준 스냅샷을 받습니다.
5. 클라이언트가 서버로 보내는 메시지는 없습니다. 상태 변경은 REST API로 합니다.

```
클라이언트                         서버
   │──── WS 접속 /ws/visits/T ────▶│
   │◀──── VisitSnapshot (v1) ──────│  접속 직후
   │                                │  ← 상대방이 PATCH 성공
   │◀──── VisitSnapshot (v2) ──────│
   │                                │  ← 내가 POST proposals 성공
   │◀──── VisitSnapshot (v3) ──────│
```

## 접속 실패와 끊김

| 상황 | 결과 |
|---|---|
| 없는 토큰 | WebSocket으로 전환되지 않고 HTTP 404 `{"code":"VISIT_NOT_FOUND",...}` 응답 |
| 만료된 토큰 | WebSocket으로 전환되지 않고 HTTP 410 `{"code":"LINK_EXPIRED",...}` 응답 |
| 연결 중 링크가 만료되는 등 스냅샷을 만들 수 없게 됨 | 서버가 close code `1008`, reason `Visit unavailable`로 연결을 닫음 |

## 클라이언트 구현 시 지켜야 할 것

- **WebSocket만 믿으면 안 됩니다.** 연결이 끊긴 사이의 변경은 다시 보내 주지 않습니다. 그래서 계약상 클라이언트는 WebSocket과 별도로 **`GET /api/visits/{token}`을 5초마다 호출**해서 상태를 맞춰야 합니다.
- 제안이 10분 지나 만료되는 것은 "변경"이 아니라 시간이 지나서 생기는 일이라 WebSocket 메시지가 오지 않습니다. 이것도 주기적 조회로 확인합니다.
- 같은 `requestId`로 재시도한 요청도 메시지를 한 번 더 보냅니다. 그래서 **같은 version의 메시지가 중복으로 올 수 있습니다.** 받은 메시지의 `visit.version`이 지금 갖고 있는 것과 같거나 작으면 무시해도 됩니다.
- 연결이 끊겨도 서버는 제안을 자동 수락하거나 방문을 자동 완료하지 않습니다.

## 예시 (브라우저 / Node 22+)

```js
const ws = new WebSocket(`ws://localhost:4000/ws/visits/${token}`);
ws.onmessage = (event) => {
  const snapshot = JSON.parse(event.data); // VisitSnapshot
  console.log(snapshot.role, snapshot.visit.version, snapshot.visit.phase);
};
ws.onclose = (event) => console.log('closed', event.code, event.reason);
```

실제 구현은 웹의 [visitSocket.ts](../../apps/web/src/realtime/visitSocket.ts)를 참고하세요.
