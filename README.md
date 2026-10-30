# 마중 (Majung)

차량 앱 · 동승자 모바일 웹 · 방문 서버가 **같은 SQLite 방문 상태**를 공유하는 모노레포다. 차량에서 방문을 생성하고 QR을 열면 동승자의 준비 상태, 실제 공유 위치, 장소 변경 제안·수락, 방문 완료와 평가가 서버에 저장된다. 앱 안의 자동 운행·자동 수락 시뮬레이션은 사용하지 않는다.

```
apps/vehicle/       Kotlin + Compose / AAOS 차량 앱
apps/web/           Vite + React 동승자 웹
apps/server/        Fastify + node:sqlite + WebSocket 방문 서버
packages/shared/    웹·서버 API 타입
contracts/          openapi.yaml (차량 DTO 포함 API 계약)
```

## 실행

요구 환경: Node **22.13+** (Vite 7 및 플래그 없는 `node:sqlite`; 검증 환경 24.6.0), npm, JDK 17, Android SDK 35.

저장소 루트에서 설치 후, 서버와 웹을 각각 별도 터미널에서 실행한다.

```bash
npm ci
npm run dev:server
npm run dev:web
```

- 서버: `http://localhost:4000/health`
- 동승자 웹: 차량에서 생성한 `http://localhost:5173/v/<passengerToken>` 링크. `/v/demo`는 실제 방문이 아니므로 동작하지 않는다.
- 웹 개발 서버는 `/api`와 `/ws`를 localhost:4000으로 전달한다. 두 서버를 함께 실행해야 한다.
- 기본 DB는 npm 서버 워크스페이스 기준 `apps/server/data/majung.sqlite`이다. 재시작해도 유지되며 Git에 포함되지 않는다.
- 다른 경로·기존 canonical DB를 사용할 때는 절대 경로를 지정한다.

```bash
DB_PATH=/absolute/path/majung.sqlite PORT=4000 npm run dev:server
```

차량 앱은 Android Studio에서 `apps/vehicle`을 열거나 다음과 같이 빌드한다.

```bash
cd apps/vehicle
./gradlew assembleDebug
```

기본 에뮬레이터 서버 주소는 `http://10.0.2.2:4000`이다. 실제 휴대폰으로 QR을 열려면 PC와 휴대폰을 같은 네트워크에 연결하고, PC의 실제 LAN IP로 웹 주소를 빌드한다. 실제 차량 장치의 서버 주소도 LAN IP로 지정한다.

```bash
./gradlew assembleDebug -PwebUrl=http://192.168.0.12:5173 -PserverUrl=http://192.168.0.12:4000
```

위 IP는 예시이므로 해당 PC의 주소로 바꾼다. 휴대폰의 `localhost`는 PC가 아니다. 브라우저 GPS 공유는 HTTPS 또는 브라우저가 허용하는 보안 컨텍스트가 필요하므로, 일반 LAN HTTP에서는 위치 공유가 거절될 수 있다. 위치 권한 없이도 준비 상태·제안·수락은 동작한다.

## DB 원본과 보존

[majung-lab/majung-db](https://github.com/majung-lab/majung-db)의 커밋 `67ad83e1cef5a3c706920a9a3adc7c425007f30c`를 통합했다. GitHub 저장소는 SQL 원본이며 실행 중인 원격 DB 서비스가 아니다. 서버는 원본 13개 테이블·뷰·트리거와 시설 시드를 [vendored SQL](apps/server/src/db/majung-db/UPSTREAM.md)에서 초기화하고, 앱의 데이터는 위 SQLite 파일에 저장한다.

- 연결마다 foreign keys를 켜고 WAL·busy timeout을 사용한다.
- 방문을 먼저 생성한 뒤 후보를 만들고 확정 후보를 연결한다.
- 쓰기는 트랜잭션, `WHERE version = ?`, requestId 재시도 처리를 거친다. 동시 변경은 409를 반환한다.
- 로컬 API 확장 테이블 3개는 중복 요청 응답, 제안 전 장소, 선택적인 위치·주차 층만 저장한다. 원본 SQL 파일은 수정하지 않았다.
- 시설이 비어 있을 때만 원본 예시 쇼핑몰·미술관을 넣는다. 기존 시설·방문은 교체하거나 삭제하지 않는다. 데모 방문·시나리오는 운영 DB에 넣지 않는다.
- 이전 모노레포의 단순 `phase` 기반 DB는 자동으로 덮어쓰지 않는다. 해당 DB가 지정되면 명시적 오류로 중단한다. 기존 파일을 보관하고 새 `DB_PATH`를 사용한다. 이전 더미 스키마에서 canonical 스키마로의 데이터 이관 도구는 포함하지 않는다.

실행 중 DB를 백업할 때는 SQLite backup 명령을 사용한다. 사용 중인 DB 파일 하나만 복사하면 WAL의 최신 변경을 놓칠 수 있다.

```bash
sqlite3 /absolute/path/majung.sqlite ".backup '/absolute/path/majung-backup.sqlite'"
```

파일에는 방문 링크·위치가 있으므로 private 데이터로 다룬다. 앱은 차량/동승자 역할별 임의 토큰을 나눠 권한을 검사한다. 차량의 임의 device ID는 방문 기록 조회 권한이므로 공유하면 안 된다. 서버는 토큰이 들어가는 URL을 요청 로그에 남기지 않는다. 링크는 24시간 후 만료되며, 저장된 방문 기록은 차량 device ID로 계속 조회할 수 있다. 평가는 방문 완료 후 링크 만료 전까지 1회 저장한다.

## 검증

```bash
npm run typecheck
npm run build
npm test -w @majung/server
npm test -w @majung/web
cd apps/vehicle
./gradlew testDebugUnitTest assembleDebug
```

서버 테스트는 양쪽 역할의 준비·GPS·제안·수락·완료·평가, 서버 재시작 후 저장, 권한·입력 검증·버전 충돌·중복 요청, WebSocket 방송, 주차·내려주기 직접 장소 변경, 만료 제안, 기존 canonical DB 확장과 레거시 DB 보존을 확인한다.

## 실제 서비스에 남은 외부 조건

현재 원본 DB의 시설명·좌표·승하차 정보는 **예시 데이터**다. 앱에도 이를 표시한다. 실제 시설로 운영하려면 승하차 허용 구역·대기 장소·연결 동선을 현장 확인해 교체해야 한다.

Pleos 내비게이션 SDK가 제공되지 않아 `FakeNaviClient`를 유지한다. 실제 차량 내비 경로 설정·도착 센서 연동·주행 제한은 SDK와 차량에서 추가 검증해야 한다. GPS는 장치가 실제로 제공한 위치만 공유하며, 없으면 위치 미공유 상태로 표시한다. 운전자가 이동 시작·도착·탑승 완료를 명시적으로 진행한다.

현재 실행 구성은 로컬/신뢰할 수 있는 네트워크 개발용이다. 공용 배포는 HTTPS 웹·서버와 영속 DB 볼륨이 필요하다. 외부 배포나 원격 DB 호스팅을 생성하지 않았다.
