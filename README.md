# 마중 (Majung)

차량 앱 · 모바일 웹 · 방문 서버를 한 저장소에서 관리하는 모노레포.

```
majung/
├── apps/
│   ├── vehicle/      Pleos 차량 앱 — Kotlin + Compose, AAOS (Gradle 단독 빌드)
│   ├── web/          운전자·동승자 모바일 웹 — Vite + React + TS
│   └── server/       방문 서버 — Fastify + node:sqlite + WebSocket
├── packages/shared/  웹·서버 공용 타입 (Visit, MeetingProposal …)
├── contracts/        openapi.yaml — 세 앱의 API 계약 원본
├── data/facilities/  시설 데이터 JSON (출처·확인일 포함)
└── docs/             기획서·API·DB 설계·결정 기록
```

## 실행

요구: Node 22.5+ (node:sqlite), JDK 17, Android SDK 35

```bash
npm install
npm run dev:server        # http://localhost:4000  (GET /health)
npm run dev:web           # http://localhost:5173/v/<token>  — /api, /ws 는 서버로 프록시
npm test -w @majung/server
```

차량 앱: Android Studio에서 `apps/vehicle` 폴더를 열거나
```bash
cd apps/vehicle && ./gradlew assembleDebug
```
에뮬레이터에서 호스트 서버는 `10.0.2.2:4000` (`BuildConfig.SERVER_URL`).

## 더미 데이터로 시연 (서버 없이)
지도는 OpenStreetMap(웹 Leaflet · 차량 osmdroid), 좌표는 스타필드 하남 대략값이다.
카카오맵 키가 생기면 `apps/web/src/features/map/LeafletMap.tsx`, `apps/vehicle/.../ui/theme/OsmMap.kt`만 바꾼다.

- 동승자 웹: `http://localhost:5173/v/demo?reset&speed=20`
  - 차가 경로를 따라 움직이고, 35% 지점에서 운전자 제안(W02), 도착 8초 뒤 종료(W03)
  - 위치 바꾸기(W04)는 5초 뒤 운전자 수락. `?driver=reject` 면 거절
- 차량 앱: C06에서 가족 상태가 바뀌고 25초 뒤 가족 제안(C11), C10 제안은 5초 뒤 수락(C12)
  - 휴대폰으로 QR을 찍으려면 PC LAN IP로 빌드: `./gradlew assembleDebug -PwebUrl=http://192.168.0.12:5173`
- 차량 화면 스냅샷(Figma 대조용): `./gradlew testDebugUnitTest -Psnapshots` → `app/build/outputs/roborazzi/`

## 규칙
- API를 바꿀 땐 `contracts/openapi.yaml` → `packages/shared` → 차량 앱 `data/remote/dto` 순서로 맞춘다.
- Pleos SDK는 `pleos/navi/NaviClient` 뒤에 숨긴다. SDK 받기 전엔 `FakeNaviClient`.
- 주행 여부는 `CarUxRestrictionsManager`로만 판단한다 (`core/uxrestriction`).
