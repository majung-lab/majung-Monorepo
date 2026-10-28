-- 기획서 10장 최소 엔터티 기준
CREATE TABLE IF NOT EXISTS facility (
  id           TEXT PRIMARY KEY,
  name         TEXT NOT NULL,
  address      TEXT NOT NULL,
  source       TEXT NOT NULL,      -- 정보 출처
  verified_at  TEXT NOT NULL       -- 확인일
);

CREATE TABLE IF NOT EXISTS access_point (
  id            TEXT PRIMARY KEY,
  facility_id   TEXT NOT NULL REFERENCES facility(id),
  kind          TEXT NOT NULL CHECK (kind IN ('PICKUP', 'PARKING_ENTRANCE')),
  name          TEXT NOT NULL,     -- "북문 로비" 같이 차량·웹 공통 명칭
  vehicle_lat   REAL NOT NULL,     -- 차량 접근 좌표 (건물 중심 좌표 금지)
  vehicle_lng   REAL NOT NULL,
  wait_spot     TEXT,              -- 사람이 기다릴 위치 설명
  step_free     INTEGER NOT NULL DEFAULT 0,
  covered       INTEGER NOT NULL DEFAULT 0,
  indoor_linked INTEGER NOT NULL DEFAULT 0,
  visible       INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS visit (
  id                         TEXT PRIMARY KEY,
  facility_id                TEXT NOT NULL REFERENCES facility(id),
  mode                       TEXT NOT NULL CHECK (mode IN ('PICKUP', 'PARK', 'DROPOFF')),
  phase                      TEXT NOT NULL,
  confirmed_access_point_id  TEXT NOT NULL REFERENCES access_point(id),
  version                    INTEGER NOT NULL DEFAULT 1,
  passenger_readiness        TEXT NOT NULL DEFAULT 'UNSEEN',
  updated_at                 TEXT NOT NULL,
  expires_at                 TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS participant (
  token       TEXT PRIMARY KEY,    -- 방문별 권한 링크 토큰 (운전자/동승자 분리)
  visit_id    TEXT NOT NULL REFERENCES visit(id),
  role        TEXT NOT NULL CHECK (role IN ('DRIVER', 'PASSENGER')),
  revoked_at  TEXT
);

CREATE TABLE IF NOT EXISTS meeting_proposal (
  id               TEXT PRIMARY KEY,
  visit_id         TEXT NOT NULL REFERENCES visit(id),
  proposed_by      TEXT NOT NULL CHECK (proposed_by IN ('DRIVER', 'PASSENGER')),
  access_point_id  TEXT NOT NULL REFERENCES access_point(id),
  base_version     INTEGER NOT NULL,
  status           TEXT NOT NULL DEFAULT 'PENDING',
  created_at       TEXT NOT NULL,
  expires_at       TEXT NOT NULL
);
-- 한 방문에 진행 중 제안은 하나
CREATE UNIQUE INDEX IF NOT EXISTS one_pending_proposal
  ON meeting_proposal(visit_id) WHERE status = 'PENDING';

CREATE TABLE IF NOT EXISTS navigation_status (
  visit_id         TEXT PRIMARY KEY REFERENCES visit(id),
  access_point_id  TEXT NOT NULL,
  version          INTEGER NOT NULL,
  status           TEXT NOT NULL,  -- NONE / REQUESTED / GUIDING / FAILED
  updated_at       TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS visit_event (
  request_id  TEXT PRIMARY KEY,    -- 재시도해도 한 번만 반영
  visit_id    TEXT NOT NULL,
  actor       TEXT NOT NULL,
  action      TEXT NOT NULL,
  payload     TEXT,
  created_at  TEXT NOT NULL
);
