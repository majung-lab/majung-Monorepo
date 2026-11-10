# 마중 공모전 시연 영상 제작 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 심사위원용 3분 안팎 가로형 시연 영상 `docs/demos/contest-20261115/마중_공모전_시연.mp4`를 만든다.

**Architecture:**
- 시연용 DB에 신청서 A·B·C 시나리오 시설을 넣는다.
- Pleos 에뮬레이터의 차량 앱(adb)과 헤드리스 Chrome의 가족 웹(Playwright)을 한 시계로 조작하며 구간별로 녹화한다.
- 페르소나 일러스트, 사업성 정리 화면, TTS 내레이션, 자막과 함께 ffmpeg로 합성한다.
- 지난 작업 `docs/demos/weather-20261110/`(record.mjs, compose.py, tts.py)을 고쳐 쓴다.

**Tech Stack:** Node 24(+tsx), playwright-core 1.48.2(시스템 Chrome), adb / Android emulator(`Pleos_Car`), ffmpeg 6(`/Users/1uv0cean/Desktop/fwdlink/fwd-link/promo/node_modules/ffmpeg-static/ffmpeg`, libass), Python 3, BizRouter API(TTS·전사·이미지).

**Spec:** `docs/superpowers/specs/2026-11-10-demo-video-design.md`

## Global Constraints

- 출력: 1920×1080, 30fps, H.264 / AAC, 1080p 12Mbps 이상, `-preset slow`, faststart, 음량 약 −16 LUFS, 배경 음악 없음.
- 내레이션: `google/gemini-3.8-flash-tts`, voice `Aoede`. 요청에는 `model`, `input`, `voice`만 넣는다(다른 필드는 400). 전사는 `openai/gpt-transcribe`, `language: ko`.
- BizRouter 키는 `/Users/1uv0cean/Desktop/fwdlink/fwd-link/.env.local`의 `BIZ_ROUTER_API_KEY`를 메모리로만 읽는다. 파일·로그·영상에 남기지 않는다.
- 화면에 나오면 안 되는 것:
  - 연락처 목록, API 키
  - 앱 오류 안내: "다른 기기에서 상태가 바뀌었어요", "서버에 연결하지 못했어요", "할 수 없어요", "내비에 넣지 못했어요"
- 반드시 표시할 것:
  - 시연 모드 날씨를 쓰면 "날씨는 시연 모드"
  - 예시 시설 데이터 표시(앱이 이미 표시)
  - 내비 장면의 "Pleos 에뮬레이터 화면 · 에뮬레이터에서는 지도 바탕이 표시되지 않아요"
- 구현 안 된 것은 말하지 않는다: 문자·카카오톡 공유, 마중 화면의 ETA, 실내 위치 추적. 공유는 QR 스캔만 보여 준다.
- 사업성 숫자는 신청서 그대로 쓴다.
  - 운전자 MAU: 파일럿 50~100명 → 시설 10곳 약 3,000명 → 전국 약 169,000명
  - 3단계 근거: Pleos Connect 2,000만 대 × 국내 17.3% × 12세 이하 자녀 가구 12.2% × 월 1회 이상 이용 40%(가정)
  - 30일 재이용 30% 목표
  - 확장: 병원·공항·문화시설
- `apps/server/src/db/majung-db/`의 원본 SQL은 고치지 않는다. 시연 데이터는 시연용 DB에만 넣는다.
- 에뮬레이터: `Pleos_Car`, `-gpu host`, 앱은 user 10. 촬영 전에 내비 앱을 한 번 연다.
- 모든 녹화 산출물과 스크립트는 `docs/demos/contest-20261115/`에 둔다. `docs/demos/`는 git에 올리지 않는 폴더라 커밋 단계가 없다. 코드 변경이 생기면 그때만 커밋한다.

## Review Focus

1. **촬영 시점의 실제 날씨에 비가 없음**
   - 기대: C04 추천이 A가 아니게 되어 이야기가 무너지면 안 된다. `WEATHER_DEMO=rain`으로 찍고 "날씨는 시연 모드"를 표시한다.
   - 확인: Task 1 점검 스크립트가 비 조건에서 A 1순위를 확인하고, Task 4 녹화 스크립트는 시작 전에 날씨 출처를 기록한다.
2. **에뮬레이터를 새로 켠 직후 첫 내비 요청 실패(`navi not ready`)**
   - 기대: 녹화 중 "내비에 넣지 못했어요"가 뜨지 않는다.
   - 확인: Task 4가 시작 전에 내비 앱을 열고 20초 기다리며, 구간 끝마다 오류 문구를 검사한다.
3. **가족 제안과 차량 위치 전송의 버전 충돌**
   - 기대: 운전자 화면에 충돌 경고가 뜨지 않는다(커밋 `1d84957`에서 수정됨).
   - 확인: Task 4의 구간별 오류 검사.
4. **QR·화면 주소에 든 동승자 토큰 노출**
   - 기대: 영상 공개 시점에 그 링크가 쓸모없어야 한다.
   - 확인: Task 6이 녹화 방문을 끝내고(COMPLETED/CANCELLED) 시연용 DB를 지운다.
5. **휴대폰 화면 작은 글자 판독**
   - 기대: 추천 이유와 계단 경고가 1080p에서 읽힌다.
   - 확인: Task 5가 추천 카드 구간에 휴대폰 화면 확대 컷을 넣고 프레임으로 확인한다.

---

### Task 0: 에뮬레이터 가능 여부 확인 (주행 상태·위치·도착 감지)

영상 구성이 바뀌는 위험부터 확인한다. 결과는 `docs/demos/contest-20261115/NOTES.md`에 적는다.

**Files:**
- Create: `docs/demos/contest-20261115/NOTES.md`

**Interfaces:**
- Produces: `NOTES.md`에 다음 세 줄. Task 4가 이 결과로 구간을 고른다.
  - `driving: supported|unsupported` (+ 쓴 명령)
  - `geo: <lat>,<lng>`
  - `arrival: supported|unsupported`

- [ ] **Step 1: 주행 상태 만들기 시도.** 기어를 D로, 속도를 30km/h로 주입한다. 그 뒤 `adb shell dumpsys car_service`의 UX restrictions가 바뀌는지, 마중 앱 이동 화면에서 "위치 바꾸기"·"방문 취소"가 사라지는지 본다. 다음 명령을 차례로 시도한다.
  - `adb -s emulator-5554 shell cmd car_service inject-vhal-event 0x11400400 8` (GEAR_SELECTION=DRIVE)
  - `adb -s emulator-5554 shell cmd car_service inject-vhal-event 0x11600207 8.3` (PERF_VEHICLE_SPEED, m/s)
  - 안 되면 `adb emu` 차량 데이터 명령이나 Pleos 계기판을 확인한다.
  - 기대: 마중 화면에서 주행 중 숨김 버튼이 사라진다.
- [ ] **Step 2: 정차로 되돌리기** (속도 0, 기어 P). 버튼이 돌아오는지 확인한다.
- [ ] **Step 3: 차량 위치를 시설 근처로 옮긴다.** `adb emu geo fix <lng> <lat>`. 내비 출발 위치가 바뀌는지(`수정구 시흥동` 표시가 사라지는지) 본다. 바뀌지 않으면 그대로 두고 적는다.
- [ ] **Step 4: 도착 감지 시험.** 경로 안내 중 목적지 좌표로 `geo fix`를 주고, 마중이 자동으로 도착 화면으로 가는지 본다.
- [ ] **Step 5: 결과를 `NOTES.md`에 적는다.** Global Constraints에 영향이 있으면(예: 주행 상태 불가) 사용자에게 한 줄로 알린다.

### Task 1: 시연용 시설 데이터 (A·B·C)

**Files:**
- Create: `docs/demos/contest-20261115/demo-facility.sql`
- Create: `docs/demos/contest-20261115/make-demo-db.sh`
- Create: `docs/demos/contest-20261115/check-demo-db.mjs`

**Interfaces:**
- Produces:
  - `make-demo-db.sh <db경로>`: 빈 DB를 서버 스키마로 만들고 `demo-facility.sql`을 적용한다.
  - 시설 id 1의 지점 id는 다음과 같다. Task 4가 이 id를 쓴다.
    - 승하차 지점: A=1, B=2, C=3
    - 대기 지점: 1층 정문 로비=1, 1층 북측 로비=2, 2층 가족휴게실=3

데이터 값(작성자 결정):
- 시설 1 이름: `예시 복합쇼핑몰`. 좌표는 기존 예시 시설 좌표를 유지한다.
- 시설 2(예시 미술관)는 그대로 둔다.

| access_point | name | dropoff_allowed | has_roof | elevator_nearby | vehicle_notes |
|---|---|---|---|---|---|
| 1 | A구역 정문 승하차장 | 1 | 1 | 0 | 정문 캐노피 아래, 정차 3분 |
| 2 | B구역 북측 승하차장 | 1 | 1 | 1 | 엘리베이터 홀 바로 앞, 정차 5분 |
| 3 | C구역 동문 출입구 | 0 | 0 | 0 | 승하차 금지 구역 |

| connection | from wait_spot | → ap | walk_minutes | stroller_ok | no_stairs | covered | description |
|---|---|---|---|---|---|---|---|
| 1 | 1층 정문 로비(1) | A(1) | 1 | 1 | 1 | 1 | 정문 로비 → 캐노피 아래 승하차장 |
| 2 | 2층 가족휴게실(3) | A(1) | 6 | 1 | 0 | 0 | 가족휴게실 → 계단 → 정문, 유모차는 야외로 우회 |
| 3 | 1층 북측 로비(2) | B(2) | 2 | 1 | 1 | 1 | 북측 로비 → 승하차장, 실내 연결 |
| 4 | 2층 가족휴게실(3) | B(2) | 3 | 1 | 1 | 1 | 가족휴게실 → 엘리베이터 → 북측 로비, 실내 연결 |

- [ ] **Step 1: `check-demo-db.mjs`를 먼저 쓴다.** 서버 4100에 다음을 assert한다.
  - `GET /api/facilities/1/weather?mode=PICKUP`(WEATHER_DEMO=rain): `ranking[0].accessPointId === '1'`, reason에 `지붕` 포함, `ranking`에 `'3'` 없음
  - `POST /api/visits`(accessPointId '1') → 동승자 PATCH `passengerWaitSpotId '3'`, `passengerConditions {stroller:true,…false}` → `GET /api/visits/{p}/recommendation`
    - `ranking[0]` = `{accessPointId:'2', recommended:true}`, reason `계단 없이, 비 맞지 않고 걸어서 3분`
    - A의 `excludedReason === '계단이 있어 유모차로는 돌아가야 해요'`
  - 맑음 서버 4101(WEATHER_DEMO=clear)에서도 C04 1순위가 `'1'`
- [ ] **Step 2: 실행해 실패를 확인한다**(데이터가 아직 예시 시설이라 reason·id가 다름).
  `node check-demo-db.mjs` → FAIL
- [ ] **Step 3: `demo-facility.sql`, `make-demo-db.sh`를 쓴다.**
  - SQL은 `DELETE FROM connection/wait_spot/access_point WHERE facility_id=1`(connection은 해당 access_point 기준) 후 위 표대로 INSERT하고, 시설 이름을 UPDATE한다.
  - 셸은 `DB_PATH=$1` 서버를 잠깐 띄워 스키마·시드를 만든 뒤 내리고, `sqlite3 $1 < demo-facility.sql`을 적용한다.
- [ ] **Step 4: 두 서버(4100 rain, 4101 clear)를 띄워 `node check-demo-db.mjs` → 모두 통과.**

### Task 2: 내레이션 대본과 TTS → 사용자 확인 ①

**Files:**
- Create: `docs/demos/contest-20261115/narration.json` (형식은 `weather-20261110/narration.json`과 같음: `[{id,title,lines[]}]`)
- Create: `docs/demos/contest-20261115/tts.py`, `check_tts.py` (weather-20261110의 같은 파일을 경로만 바꿔 복사)

**Interfaces:**
- Produces: 구간 id `intro, persona, s1…s8, business, outro`. 각 줄 음성 파일은 `tts/<id>-<n>.wav`. Task 4·5가 이 id와 줄 순서를 쓴다.

구간과 내용(대본 문장은 빌더가 쓴다):

| id | 내용 | 심사 기준 |
|---|---|---|
| persona | 가족 소개, 입구 엇갈림 문제 | 창의성 |
| s1 | 정차 중 목적지·픽업하기, 비 예보라 A 추천 | 차량특화(정차 맥락)·창의성 |
| s2 | 방문 시작, QR을 찍어 앱 설치 없이 연결 | 사업성(진입 장벽)·창의성 |
| s3 | 가족이 2층 가족휴게실·유모차 선택, A는 계단, B 추천, 제안 | 창의성 |
| s4 | 출발, 내장 내비 목적지 자동 설정·안내 시작, 내비 위 배너, 주행 중 제안은 정차 후, 정차 후 수락, 내비 목적지 B로 변경 | 기술 활용성·차량특화성 |
| s5 | 앱을 다시 켜도 진행 중인 방문으로 돌아옴 | 차량특화성 |
| s6 | 가족 "지금 나갈게요", 운전자 화면에 보임 | 차량특화성 |
| s7 | 도착·탑승 완료·평가·기록에 날씨와 평가 | 사업성(데이터) |
| s8 | 다음 방문: 다시 가기 / 지난 평가 반영 | 사업성(재이용) |
| business | MAU 단계, 30일 재이용 30%, 확장 | 사업성 |

- [ ] **Step 1: 대본을 쓴다.**
  - 한 줄은 TTS 7초 이내로 쓰고, 화면에 실제로 보이는 문구만 언급한다.
  - Global Constraints의 "구현 안 된 것"은 말하지 않는다.
- [ ] **Step 2: 대본을 사용자에게 보여 주고 승인받는다(확인 ①).** 승인 전에는 TTS를 만들지 않는다.
- [ ] **Step 3: `python3 tts.py` → `python3 check_tts.py`.** 모든 줄 일치율 0.9 이상이 기대값이다. 미달 줄은 문장을 고쳐 다시 만든다.

### Task 3: 페르소나 일러스트 → 사용자 확인 ②

**Files:**
- Create: `docs/demos/contest-20261115/personas/gen.py`, `personas/*.png`

**Interfaces:**
- Produces: `personas/family.png`(소개 카드), `problem.png`(엇갈린 입구), `driver.png`(운전석), `waiting.png`(유모차와 2층 가족휴게실), `next-weekend.png`. 모두 16:9, 1920×1080 이상.

- [ ] **Step 1: BizRouter의 이미지 생성 모델을 확인한다.** `GET https://api.bizrouter.ai/v1/models`. 이미지 생성 엔드포인트와 요청 형식은 응답과 공식 문서로 정한다. 쓴 모델과 엔드포인트를 `NOTES.md`에 적는다.
- [ ] **Step 2: 일러스트를 생성한다.**
  - 스타일: 따뜻한 플랫 일러스트, 주황 강조(#F5A623), 어두운 남색 배경과 어울리는 톤.
  - 인물: 같은 가족(운전자 1, 동승자 1, 영유아 1, 유모차)을 모든 장에 일관되게 둔다. 실존 인물·브랜드 로고·읽을 수 있는 글자는 넣지 않는다.
  - 프롬프트는 `gen.py`에 남긴다.
- [ ] **Step 3: 이미지를 사용자에게 보여 주고 고르게 한다(확인 ②).** 고친 요청이 있으면 다시 생성한다.

### Task 4: 녹화 (Pleos 에뮬레이터 + 가족 웹)

**Files:**
- Create: `docs/demos/contest-20261115/record.mjs` (`weather-20261110/record.mjs`에서 출발)
- Output: `rec/<id>-car.webm`, `rec/web.webm`, `rec/marks.json`

**Interfaces:**
- Consumes:
  - Task 0 `NOTES.md`(driving, geo, arrival)
  - Task 1의 지점 id와 `make-demo-db.sh`
  - Task 2의 `narration.json`과 `tts/*.wav` 길이
- Produces: `rec/marks.json`, 형식 `{webStart, weatherSource, sections:[{id,start,end,lines:[{key,at}]}]}`(시각은 ms). Task 5가 쓴다.

바꿀 점(weather-20261110/record.mjs 대비):
- adb 대상: `-s emulator-5554`. 앱 실행은 `am start --user 10 -n com.majung.vehicle/.MainActivity`. 차량 녹화는 `adb emu screenrecord start|stop`(2560×1440). 회전 처리는 하지 않는다. 받은 프레임 방향을 확인하고 필요하면 Task 5에서 돌린다.
- 시작 준비:
  - `make-demo-db.sh`로 새 DB를 만들고 서버 4000을 띄운다. 날씨는 실제 예보에 비가 있으면 KMA, 없으면 `WEATHER_DEMO=rain`(키 줄 없이)으로 하고, `marks.weatherSource`에 기록한다.
  - 웹은 5173(카카오 도메인 등록됨)을 쓴다.
  - 앱은 `pm clear` 후 권한을 다시 준다. `README.md` Pleos 절의 POST_NOTIFICATIONS, 위치·연락처, SYSTEM_ALERT_WINDOW appops가 대상이다.
  - 내비 앱을 열고 20초 기다린 뒤 마중으로 돌아온다.
  - Task 0의 geo를 적용한다.
- QR: 차량 앱 빌드의 `-PwebUrl`은 `http://10.0.2.2:5173`(기본값)이다. Playwright는 QR 노드의 content-desc에서 토큰을 꺼내 `http://localhost:5173/v/<token>`을 연다.
- 구간(각 구간 끝에 오류 문구 검사, 있으면 throw):
  - s1: 홈 → 목적지 입력 → 예시 복합쇼핑몰 → 픽업하기 → C04(A 추천 확인 후 내레이션)
  - s2: 다음 → 링크 전달 QR(3초 유지) → 웹 열기
  - s3: 웹에서 2층 가족휴게실 → 유모차 → 추천 카드(B) → "여기로 바꾸자고 하기"는 **s4 안에서** 누른다(주행 중 도착시키기 위해)
  - s4: 픽업 장소로 출발 → 내비 안내 시작 확인("내비 안내 중") → 내비 보기(배너 노출 3초) → 배너 눌러 복귀 → driving=supported면 주행 상태 주입 → 웹에서 제안 → 차량 "정차하면 수락·거절할 수 있어요" 확인 → 정차 → "목적지 바꾸기" → 내비 재안내 확인
  - s5: `am force-stop --user 10 com.majung.vehicle` → 다시 실행 → 이동 화면 복귀 확인
  - s6: 웹 "준비됐어요" → "지금 나갈게요" → 차량 가족 카드·내비 위 카드 확인
  - s7: 도착했어요(arrival=supported면 geo fix로 자동 도착) → 동승자 → 탑승 완료 → 좋았어요 + 태그 → 평가 완료 → 목적지 기록
  - s8: 기록에서 "다시 가기" → 링크 전달 화면(지난 평가 반영은 C04에서 보이게, 필요하면 홈 → 목적지 → 픽업하기 경로로)
- driving=unsupported면 s4의 주행 단계는 건너뛰고, `marks`에 `drivingSkipped: true`를 남긴다(Task 5가 자막·정지 화면으로 대체).

- [ ] **Step 1: 리허설 녹화** `node record.mjs <playwright-core/index.mjs> --dry`. 녹화 없이 전 구간 조작과 오류 검사만 한다. 기대: `done` 출력, throw 없음.
- [ ] **Step 2: 본 녹화** `node record.mjs <playwright-core/index.mjs>`. 기대: 구간별 webm 파일과 `marks.json`이 생긴다.
- [ ] **Step 3: 확인.**
  - 구간마다 1/3fps 타일 시트를 만들어 본다. 오류 문구, 연락처 노출, 세로 레이아웃이 없어야 한다.
  - 차량 녹화 길이가 구간 길이 + 0.5~1초인지 확인한다.

### Task 5: 합성 → 사용자 확인 ③

**Files:**
- Create: `docs/demos/contest-20261115/compose.py` (`weather-20261110/compose.py`에서 출발)

**Interfaces:**
- Consumes: Task 4 `rec/`, Task 2 `tts/`·`narration.json`, Task 3 `personas/*.png`
- Produces: `마중_공모전_시연.mp4`, `build/timeline.json`

바꿀 점:
- 레이아웃: 차량 2560×1440(16:9, 계기판 포함)을 왼쪽에 1280×720 크기로 두고, 휴대폰 390×844를 오른쪽에 둔다. s3·s6 추천 카드 구간에는 휴대폰 화면 확대 컷(2배)을 2~3초 넣는다.
- 페르소나: 일러스트에 Ken Burns(zoompan, 5~7초)를 주고 이름·상황 자막을 붙인다. s2(QR 찍기)와 s6 사이에 짧은 페르소나 컷을 넣는다.
- business 화면: Global Constraints의 숫자·근거를 그대로 표·텍스트로 넣는다(ASS).
- 고정 하단 문구:
  - "예시 시설 데이터"
  - weatherSource가 DEMO면 "날씨는 시연 모드"
  - 내비 장면이 나오는 구간에 "Pleos 에뮬레이터 화면 · 에뮬레이터에서는 지도 바탕이 표시되지 않아요"
  - drivingSkipped면 해당 장면 자막 "주행 중에는 제안이 정차 후로 미뤄져요"와 정지 화면
- 배속: 내레이션 없는 2.4초 초과 구간은 2배속과 `2×` 표시(기존 로직 유지).
- 인코딩: `-c:v libx264 -preset slow -b:v 12M -maxrate 16M -bufsize 24M -pix_fmt yuv420p -c:a aac -b:a 192k -movflags +faststart`.

- [ ] **Step 1: `python3 compose.py` 실행.** 기대: 완성 파일과 총 길이 출력(160~200초).
- [ ] **Step 2: 기술 검증.**
  - ffmpeg 전체 디코딩에 오류가 없다.
  - 1920×1080, 30fps, 비트레이트 12M 근처, `ebur128` I ≈ −16 LUFS.
- [ ] **Step 3: 내용 검증.**
  - 2초 간격 프레임 전수 확인: 연락처, 오류 문구, 키가 없고 표시 문구가 있다.
  - 완성본 오디오 전사가 대본과 일치율 0.95 이상이다.
  - 사업성 숫자를 신청서와 대조한다.
- [ ] **Step 4: 사용자에게 완성본을 보여 준다(확인 ③).** 수정 요청을 반영해 Step 1~3을 반복한다.

### Task 6: 정리와 전달

**Files:**
- Create: `docs/demos/contest-20261115/README.md` (weather-20261110/README.md 형식: 결과물, 타임라인, 만든 방법, BizRouter 사용, 다시 만들기, 검증, 알려진 화면 차이)

- [ ] **Step 1: 녹화에 쓴 방문을 모두 끝낸다.** 진행 중이면 CANCELLED로 끝내고, 시연용 DB(`rec/demo.sqlite*`)를 지운다. 기대: 서버 로그에 남은 토큰 링크를 열면 404 또는 410.
- [ ] **Step 2: 개발 서버(4000)를 원래 설정으로 되돌린다.** `npm run dev:server`, `apps/server/.env`의 KMA 키를 쓴다. 에뮬레이터는 끄지 않고 둔다.
- [ ] **Step 3: README를 쓴다.** 키와 개인정보는 넣지 않는다.
- [ ] **Step 4: 사용자에게 영상 경로와 남은 일(유튜브 업로드, 신청서 "구현 제외 범위" 문구 수정 권고)을 보고한다.**
