# Upstream database

Source: https://github.com/majung-lab/majung-db
Commit: `67ad83e1cef5a3c706920a9a3adc7c425007f30c` (2026-10-30).

These three SQL files are unchanged upstream originals. Runtime uses Node's built-in SQLite adapter instead of better-sqlite3; the database structure is identical. `../schema.sql` adds API request receipts, proposal origin snapshots and optional UI state without changing upstream tables or existing data.

Seeds contain **example facilities and temporary coordinates**, not verified places. Demo visits and scenario executions are intentionally not imported into application databases.
