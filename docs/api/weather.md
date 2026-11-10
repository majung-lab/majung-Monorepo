# 날씨와 추천

만날 장소를 고를 때 쓰는 추천 API 두 개입니다. 둘 다 [FacilityWeather](models.md#facilityweather)(날씨 + 추천 순서)를 돌려줍니다.

| API | 쓰는 곳 | 추천 기준 |
|---|---|---|
| [GET /api/facilities/{id}/weather](#get-apifacilitiesidweather) | 차량 앱의 장소 선택 화면 (방문 만들기 전) | 날씨, 이 운전자의 지난 평가, 걷는 시간 |
| [GET /api/visits/{token}/recommendation](#get-apivisitstokenrecommendation) | 동승자 웹 (방문 진행 중) | 동승자가 고른 대기 장소, 이동 조건, 날씨 |

추천은 참고용입니다. 확정 장소를 바꾸지 않습니다. 바꾸려면 [제안](proposals.md)이나 [직접 변경](visits.md#accesspointid-장소-직접-변경)을 쓰세요.

## 날씨 출처

서버 환경 변수에 따라 정해집니다.

| 설정 | `weather` 값 |
|---|---|
| `KMA_SERVICE_KEY` 있음 | 기상청 단기예보(공공데이터포털). `source: "KMA"`. 같은 격자·발표 시각은 30분 동안 캐시합니다 |
| `KMA_SERVICE_KEY` 없고 `WEATHER_DEMO=rain` | 시연용 "곧 비" 고정 값. `source: "DEMO"` |
| `KMA_SERVICE_KEY` 없고 `WEATHER_DEMO=clear` | 시연용 "맑음" 고정 값. `source: "DEMO"` |
| 둘 다 없음 | 항상 `null` |

- 기상청 호출이 실패하거나 3초 안에 응답이 없으면 `weather`는 `null`입니다. 에러가 나지 않고, 날씨 없이 추천합니다.
- `DEMO` 값은 실제 날씨가 아닙니다. 화면에 띄울 때는 시연용이라는 것을 구분해 주세요.

---

## GET /api/facilities/{id}/weather

시설의 날씨와, 방문 모드에서 고를 수 있는 장소의 추천 순서를 조회합니다. 시설 목록처럼 공개 데이터라 토큰이 필요 없습니다.

**경로 파라미터**

| 이름 | 설명 |
|---|---|
| `id` | 시설 ID (숫자 문자열) |

**쿼리 파라미터**

| 이름 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `mode` | enum | | `PICKUP` / `DROPOFF` / `PARK` | 방문 모드. 없으면 `PICKUP`으로 봅니다. `PARK`이면 주차장 입구, 그 외에는 승하차 구역만 나옵니다 |
| `driverDeviceId` | string | | 24~128자 | 있으면 이 기기의 지난 평가를 순서에 반영합니다 |

**추천 순서**

1. `weather.rainSoon`이 `true`면 지붕 있는 동선(`covered: true`)이 먼저
2. 이 운전자가 이 시설에서 평균 4점 이상 준 장소가 먼저, 2점 이하는 뒤로 (3점은 영향 없음)
3. 대표 대기 장소에서 걷는 시간(`walkMinutes`)이 짧은 순

날씨나 평가를 모르면 그 단계는 건너뜁니다. 1번이 2번보다 앞서므로, 지난번에 별로였던 곳이라도 비가 오면 지붕이 있으면 먼저 나옵니다. 첫 번째 장소가 `recommended: true`입니다. `allowed: false`인 장소는 목록에 없습니다.

```bash
curl "http://localhost:4000/api/facilities/1/weather?mode=PICKUP&driverDeviceId=7f3c2a9e-1b4d-4e8a-9c6f-0a1b2c3d4e5f"
```

**응답 200:** [FacilityWeather](models.md#facilityweather)

```json
{
  "weather": {
    "condition": "CLOUDY",
    "tempC": 14,
    "rainChance": 70,
    "rainSoon": true,
    "summary": "15시부터 비 · 강수확률 70%",
    "checkedAt": "2026-11-10T05:15:37.659Z",
    "source": "DEMO"
  },
  "ranking": [
    { "accessPointId": "2", "recommended": true, "reason": "비 예보라 지붕 아래에서 타고 내려요" },
    { "accessPointId": "1", "recommended": false, "reason": "지붕이 없어 비를 맞을 수 있어요" }
  ]
}
```

`reason`에 나올 수 있는 문장:

| 문장 | 언제 |
|---|---|
| 비 예보라 지붕 아래에서 타고 내려요 | 비 예보, 지붕 있음, 1순위 (눈이면 "눈 예보라…") |
| 지붕 있는 동선이에요 | 비 예보, 지붕 있음, 2순위 이하 |
| 지붕이 없어 비를 맞을 수 있어요 | 비 예보, 지붕 없음 (눈이면 "눈을") |
| 지난번에 좋았던 곳이에요 | 지난 평가 4점 이상, 1순위 |
| 지난번 평가가 좋지 않았어요 | 지난 평가 2점 이하 |
| 기다리는 곳에서 가장 가까워요 | 1순위이고 걷는 시간이 가장 짧음 |
| 지난번 별로였던 곳 대신 추천해요 | 1순위이고, 목록에 평가가 나빴던 곳이 있음 |
| `null` | 위에 해당 없음. 장소 `description`을 보여 주면 됩니다 |

**에러**

| 상태 | code | 원인 |
|---|---|---|
| 400 | `INVALID_REQUEST` | `id` 형식 오류, 정의되지 않은 `mode`, `driverDeviceId` 길이 위반, 정의되지 않은 쿼리 파라미터 |
| 404 | `FACILITY_NOT_FOUND` | 없는 시설 ID |

### 평가가 다음 추천에 쓰이는 방법

[평가](ratings.md)를 남기면 같은 기기(`driverDeviceId`)의 다음 추천이 바뀝니다.

- 이 API에 `driverDeviceId`를 넣어 부를 때
- 같은 기기로 [방문을 만들 때](visits.md#post-apivisits)의 후보 순위

장소별 평균 점수로 판단하고, 다른 기기의 평가는 쓰지 않습니다.

---

## GET /api/visits/{token}/recommendation

동승자가 고른 대기 장소와 이동 조건, 날씨로 만날 장소를 다시 추천합니다. 동승자 웹이 씁니다.

**경로 파라미터**

| 이름 | 설명 |
|---|---|
| `token` | 방문 토큰. 동승자 웹에서 쓰지만 운전자 토큰으로도 같은 결과를 받습니다 |

**추천 방법**

동승자가 아직 [대기 장소](visits.md#passengerwaitspotid-동승자가-기다리는-곳)를 고르지 않았으면 [시설 날씨 추천](#get-apifacilitiesidweather)과 같은 규칙(지난 평가 제외)으로 순서를 정하고, `excludedReason`·`walkMinutes` 필드는 없습니다.

대기 장소를 골랐으면 그 대기 장소에서 각 장소까지의 동선으로 판단합니다.

1. 갈 수 없는 장소는 `excludedReason`을 붙여 맨 뒤로 보냅니다. 추천 대상이 아닙니다.
2. 나머지는 아래 순서로 정렬합니다.
   1. 비 예보(`rainSoon`)이거나 `indoorOnly`면 지붕 있는 동선이 먼저
   2. `luggage`면 계단 없는 동선이 먼저
   3. 걷는 시간이 짧은 순
3. 제외되지 않은 첫 장소가 `recommended: true`입니다. 모두 제외되면 추천 장소가 없습니다.

| `excludedReason` | 언제 |
|---|---|
| 기다리는 곳에서 가는 동선 정보가 없어요 | 고른 대기 장소에서 이 장소까지 동선 데이터가 없음 |
| 유모차로 갈 수 없는 동선이에요 | `stroller`인데 유모차로 갈 수 없는 동선 |
| 계단이 있어 유모차로는 돌아가야 해요 | `stroller`인데 계단이 있음 |
| 가는 길에 계단이 있어요 | `avoidStairs`인데 계단이 있음 |

제외되지 않은 장소의 `reason`은 "계단 없이, 비 맞지 않고 걸어서 4분"처럼 동선을 한 줄로 설명합니다. 비 예보에 지붕이 없으면 " · 지붕이 없어 비를 맞을 수 있어요", 실내만 원하는데 지붕이 없으면 " · 실외 구간이 있어요"가 붙습니다. 제외된 장소의 `reason`은 `null`입니다.

```bash
curl http://localhost:4000/api/visits/<passengerToken>/recommendation
```

**응답 200:** [FacilityWeather](models.md#facilityweather)

예: 동승자가 "2층 가족휴게실"(대기 장소 3)에 있고 유모차가 있을 때. 서문(장소 1)은 계단이 있어 빠지고, 북문(장소 2)을 추천합니다.

```json
{
  "weather": { "condition": "CLOUDY", "rainSoon": true, "summary": "15시부터 비 · 강수확률 70%", "source": "DEMO", "...": "..." },
  "ranking": [
    {
      "accessPointId": "2",
      "recommended": true,
      "reason": "계단 없이, 비 맞지 않고 걸어서 4분",
      "excludedReason": null,
      "walkMinutes": 4
    },
    {
      "accessPointId": "1",
      "recommended": false,
      "reason": null,
      "excludedReason": "계단이 있어 유모차로는 돌아가야 해요",
      "walkMinutes": 6
    }
  ]
}
```

**에러**

| 상태 | code | 원인 |
|---|---|---|
| 404 | `VISIT_NOT_FOUND` | 없는 토큰 |
| 410 | `LINK_EXPIRED` | 만료된 토큰 |

이 API는 상태를 바꾸지 않으므로 `requestId`, `version`이 없고, 종료된 방문에서도 호출할 수 있습니다.
