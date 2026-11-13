-- Non-destructive API additions to the vendored majung-db schema.
CREATE TABLE IF NOT EXISTS api_request (
  request_id TEXT PRIMARY KEY,
  actor_key TEXT NOT NULL,
  action TEXT NOT NULL,
  payload_hash TEXT NOT NULL,
  response_json TEXT NOT NULL,
  created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS api_visit_state (
  visit_id TEXT PRIMARY KEY REFERENCES visit(id) ON DELETE CASCADE,
  passenger_lat REAL,
  passenger_lng REAL,
  passenger_at TEXT,
  parking_floor TEXT,
  passenger_wait_spot_id INTEGER REFERENCES wait_spot(id),
  nav_ended_at TEXT,
  planned_arrival_at TEXT,
  nav_resumed_at TEXT
);
CREATE TABLE IF NOT EXISTS api_proposal_context (
  proposal_id INTEGER PRIMARY KEY REFERENCES proposal(id) ON DELETE CASCADE,
  from_access_point_id INTEGER NOT NULL REFERENCES access_point(id)
);
-- 탑승 뒤 동승자에게 묻는 '오는 길에 지붕이 있었나요?'. 방문마다 한 표. 표가 모이면 시드 값보다 앞선다
CREATE TABLE IF NOT EXISTS api_coverage_vote (
  visit_id TEXT PRIMARY KEY REFERENCES visit(id) ON DELETE CASCADE,
  connection_id INTEGER NOT NULL REFERENCES connection(id) ON DELETE CASCADE,
  covered INTEGER NOT NULL CHECK (covered IN (0, 1)),
  created_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS api_coverage_vote_connection ON api_coverage_vote (connection_id);

INSERT OR IGNORE INTO api_visit_state (visit_id) SELECT id FROM visit;
