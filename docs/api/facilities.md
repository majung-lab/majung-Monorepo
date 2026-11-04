# 시설과 헬스체크

## GET /health

서버가 켜져 있고 DB 초기화가 끝났는지 확인합니다. 인증이 필요 없습니다.

**응답 200**

```json
{ "ok": true, "database": "majung-db", "facilityData": "EXAMPLE" }
```

| 필드 | 설명 |
|---|---|
| `ok` | 항상 `true`. 응답이 왔다면 서버가 정상입니다 |
| `database` | 사용하는 DB 스키마 이름 |
| `facilityData` | 시설 데이터 상태. 지금은 예시 데이터라 `EXAMPLE` |

```bash
curl http://localhost:4000/health
```

## GET /api/facilities

전체 시설과 각 시설의 장소(AccessPoint) 목록을 조회합니다. 차량 앱이 방문을 만들기 전에 시설과 장소를 고를 때 씁니다. 인증이 필요 없습니다.

**요청:** 파라미터 없음

**응답 200:** [Facility](models.md#facility) 배열 (ID 오름차순)

```json
[
  {
    "id": "1",
    "name": "예시 쇼핑몰",
    "address": "경기도 ○○시 ○○로 1",
    "lat": 37.54,
    "lng": 127.22,
    "dataStatus": "EXAMPLE",
    "accessPoints": [
      {
        "id": "1",
        "name": "서문 승하차 구역",
        "lat": 37.5402,
        "lng": 127.219,
        "kind": "PICKUP",
        "allowed": true,
        "description": "1층 서문 로비 · 서문 안쪽, 의자 있음 · 서문 로비 → 서문 구역, 야외 20m · 우회전 진입, 정차 3분",
        "waitSpot": { "name": "1층 서문 로비", "floor": "1F", "description": "서문 안쪽, 의자 있음" },
        "walkMinutes": 2,
        "covered": false
      },
      {
        "id": "3",
        "name": "동문 출입구",
        "lat": 37.5401,
        "lng": 127.2215,
        "kind": "PICKUP",
        "allowed": false,
        "description": "승하차 금지 구역 (후보 제외용)",
        "waitSpot": null,
        "walkMinutes": null,
        "covered": false
      }
    ]
  }
]
```

예시에서는 일부 장소만 보여 줍니다.

**참고**

- 운영 중인 장소(`active`)만 나옵니다.
- `allowed: false`인 장소는 화면에 보여 줄 수는 있지만 방문 장소로 고를 수 없습니다. 고르면 `INVALID_ACCESS_POINT` 에러가 납니다.
- 방문 모드에 따라 고를 수 있는 장소 종류가 다릅니다. `PICKUP`·`DROPOFF`는 `kind: "PICKUP"`, `PARK`는 `kind: "PARKING_ENTRANCE"`만 고를 수 있습니다.
- 기본으로 들어 있는 시설은 "예시 쇼핑몰"(ID 1, 장소 1~4)과 "예시 미술관"(ID 2, 장소 5~6)입니다. DB의 시설 테이블이 비어 있을 때만 이 예시 데이터가 들어갑니다.

```bash
curl http://localhost:4000/api/facilities
```
