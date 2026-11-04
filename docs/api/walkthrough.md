# 따라 해 보기: 픽업 방문 하나 진행하기

curl로 픽업 방문을 처음부터 끝까지 진행해 봅니다. 차량 앱과 동승자 웹 없이 서버만 켜 두고 터미널에서 따라 할 수 있습니다.

진행 순서:

1. 운전자: 방문 생성
2. 동승자: 링크 열고 로비로 이동 중이라고 알림
3. 운전자: 출발
4. 동승자: 북문으로 장소 변경 제안
5. 운전자: 제안 수락
6. 운전자: 도착 → 완료
7. 운전자: 평가
8. 운전자: 방문 기록 조회

## 준비

서버를 켜 둔 상태에서 **다른 터미널**을 열고 시작합니다.

```bash
B=http://localhost:4000
DEV=7f3c2a9e-1b4d-4e8a-9c6f-0a1b2c3d4e5f
J='content-type: application/json'
```

- `B`: 서버 주소를 변수에 저장합니다. 이후 명령에서 `$B`로 씁니다.
- `DEV`: 차량 기기 ID입니다. 24자 이상이면 아무 값이나 됩니다.
- `J`: JSON 요청 헤더를 매번 쓰지 않으려고 변수에 넣어 둡니다.

이 변수들은 **지금 터미널에만** 남습니다. 터미널을 새로 열면 다시 실행하세요.

curl 옵션 설명:

| 옵션 | 뜻 |
|---|---|
| `-X PATCH` | HTTP 메서드 지정. 없으면 GET입니다 (`-d`가 있으면 POST) |
| `-H "$J"` | 요청 헤더 추가. 여기서는 본문이 JSON이라고 알려 줍니다 |
| `-d '{...}'` | 요청 본문 |
| `-s` | 진행 표시줄을 숨깁니다 |

## 1. 방문 생성 (운전자)

```bash
R=$(curl -s -H "$J" -d "{\"requestId\":\"wt-1\",\"facilityId\":\"1\",\"mode\":\"PICKUP\",\"accessPointId\":\"1\",\"driverDeviceId\":\"$DEV\"}" $B/api/visits)
echo "$R"
DT=$(node -p "JSON.parse(process.argv[1]).driverToken" "$R")
PT=$(node -p "JSON.parse(process.argv[1]).passengerToken" "$R")
```

- 첫 줄: 예시 쇼핑몰(시설 1)의 서문 승하차 구역(장소 1)으로 픽업 방문을 만들고, 응답을 `R` 변수에 저장합니다. `$(...)`는 괄호 안 명령의 출력을 값으로 쓰겠다는 뜻입니다.
- `echo "$R"`: 응답을 화면에 출력합니다. `visit.version`이 `1`, `phase`가 `PREPARING`입니다.
- `node -p "..."`: Node로 JSON에서 토큰을 꺼내 `DT`(운전자 토큰), `PT`(동승자 토큰)에 저장합니다.

> 이 예시는 `requestId`를 `wt-1`, `wt-2`처럼 씁니다. 같은 DB에서 다시 따라 하면 `REQUEST_REUSED`가 날 수 있으니, 그때는 `wt2-1`처럼 바꿔서 하세요. 실제 앱에서는 UUID를 씁니다.

## 2. 동승자가 링크를 열고 이동 시작

```bash
curl -s -X PATCH -H "$J" -d '{"requestId":"wt-2","version":1,"passengerReadiness":"HEADING_OUT","passengerLocation":{"lat":37.5405,"lng":127.2193}}' $B/api/visits/$PT
```

- `$PT`(동승자 토큰)로 호출했으니 동승자 역할입니다.
- 준비 상태를 `HEADING_OUT`(로비로 이동 중)으로 바꾸고 현재 위치를 보냅니다.
- 응답의 `visit.version`이 `2`로 올라갑니다.

## 3. 운전자 출발

```bash
curl -s -X PATCH -H "$J" -d '{"requestId":"wt-3","version":2,"phase":"MOVING","vehicle":{"lat":37.53,"lng":127.2,"etaSec":420,"navResult":"set"}}' $B/api/visits/$DT
```

- `$DT`(운전자 토큰)로 단계를 `MOVING`으로 바꾸고 차량 위치와 도착 예상 시간(420초)을 보냅니다.
- version은 `3`이 됩니다.

**일부러 틀려 보기:** 위 명령을 `version` 그대로 한 번 더 실행하면 `REQUEST_REUSED`가 아니라 **같은 결과**가 옵니다. 같은 `requestId`와 같은 본문은 재시도로 취급하기 때문입니다. `requestId`만 `wt-3b`로 바꿔 실행하면 version이 오래돼서 `VERSION_CONFLICT`가 납니다.

## 4. 동승자가 장소 변경 제안

```bash
curl -s -H "$J" -d '{"requestId":"wt-4","version":3,"accessPointId":"2","reason":"비가 와서 지붕 있는 북문이 좋아요"}' $B/api/visits/$PT/proposals
```

- 북문 승하차 구역(장소 2)으로 바꾸자고 제안합니다.
- 응답의 `proposals[0]`에 `"id": "1"`, `"status": "PENDING"`인 제안이 생깁니다. 확정 장소(`confirmedAccessPointId`)는 아직 `"1"`입니다.
- 제안 ID가 `1`이 아니라면 아래 명령의 `/proposals/1/`을 그 값으로 바꾸세요.

## 5. 운전자가 제안 수락

```bash
curl -s -H "$J" -d '{"requestId":"wt-5","version":4,"accept":true}' $B/api/visits/$DT/proposals/1/respond
```

- 제안을 받은 운전자가 수락합니다. 동승자 토큰으로 하면 자기 제안이라 `FORBIDDEN`이 납니다.
- 응답에서 `confirmedAccessPointId`가 `"2"`로, 제안 상태가 `ACCEPTED`로 바뀝니다.

## 6. 도착, 완료

```bash
curl -s -X PATCH -H "$J" -d '{"requestId":"wt-6","version":5,"phase":"ARRIVED"}' $B/api/visits/$DT
curl -s -X PATCH -H "$J" -d '{"requestId":"wt-7","version":6,"phase":"COMPLETED"}' $B/api/visits/$DT
```

- 단계는 순서대로만 바꿀 수 있어서 `ARRIVED`를 거쳐 `COMPLETED`로 갑니다.
- 완료 후에는 `visit.completedAt`이 채워지고, 이 방문은 더 이상 바꿀 수 없습니다.

## 7. 평가

```bash
curl -s -H "$J" -d '{"requestId":"wt-8","version":7,"rating":4,"tags":["지붕 있음","대기 편함"],"comment":"북문이 비 피하기 좋았어요"}' $B/api/visits/$DT/rating
```

- 운전자만, 완료된 방문에 한 번만 평가할 수 있습니다. 응답의 `rating`에 저장된 값이 나옵니다.

## 8. 방문 기록 조회

```bash
curl -s "$B/api/history?driverDeviceId=$DEV"
```

- 이 기기로 만든 방문 목록이 최신순으로 나옵니다. 방금 진행한 방문이 `COMPLETED` 상태와 평가와 함께 들어 있습니다.
- URL에 `?`가 들어가서 따옴표로 감쌌습니다.

## 실시간 메시지도 같이 보기

위 과정을 진행하는 동안 WebSocket 메시지를 보고 싶다면, 1단계 후 **또 다른 터미널**에서 아래를 실행해 두세요. `<DT>`에는 `echo $DT`로 확인한 운전자 토큰을 넣습니다.

```bash
node -e 'const ws=new WebSocket(`ws://localhost:4000/ws/visits/${process.argv[1]}`);ws.onmessage=e=>{const s=JSON.parse(e.data);console.log(s.visit.version,s.visit.phase,s.visit.passengerReadiness)}' <DT>
```

- Node(22 이상)에 내장된 WebSocket으로 접속해서 메시지가 올 때마다 version, 단계, 동승자 준비 상태를 한 줄씩 출력합니다.
- 2~7단계를 실행할 때마다 한 줄씩 늘어납니다. 끝내려면 `Ctrl + C`를 누르세요.

## 응답을 보기 좋게 출력하기

응답 JSON이 한 줄로 길게 나와서 읽기 어렵다면 명령 끝에 아래를 붙이세요.

```bash
... | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>console.log(JSON.stringify(JSON.parse(s),null,2)))'
```

- `|`: 앞 명령(curl)의 출력을 뒤 명령의 입력으로 넘깁니다.
- 뒤 명령은 받은 JSON을 들여쓰기해서 다시 출력합니다.
