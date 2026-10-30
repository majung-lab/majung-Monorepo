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
  parking_floor TEXT
);
CREATE TABLE IF NOT EXISTS api_proposal_context (
  proposal_id INTEGER PRIMARY KEY REFERENCES proposal(id) ON DELETE CASCADE,
  from_access_point_id INTEGER NOT NULL REFERENCES access_point(id)
);

INSERT OR IGNORE INTO api_visit_state (visit_id) SELECT id FROM visit;
