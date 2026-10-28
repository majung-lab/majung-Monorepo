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

## 규칙
- API를 바꿀 땐 `contracts/openapi.yaml` → `packages/shared` → 차량 앱 `data/remote/dto` 순서로 맞춘다.
- Pleos SDK는 `pleos/navi/NaviClient` 뒤에 숨긴다. SDK 받기 전엔 `FakeNaviClient`.
- 주행 여부는 `CarUxRestrictionsManager`로만 판단한다 (`core/uxrestriction`).
