import { DatabaseSync } from 'node:sqlite';
import { chmodSync, mkdirSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';

export type Db = DatabaseSync;
const sql = (file: string) => readFileSync(new URL(file, import.meta.url), 'utf8');

export function openDb(path: string): Db {
  const isFile = path !== ':memory:';
  if (isFile) mkdirSync(dirname(resolve(path)), { recursive: true });
  const db = new DatabaseSync(path);
  try {
    if (isFile) chmodSync(path, 0o600);
    db.exec('PRAGMA foreign_keys = ON; PRAGMA busy_timeout = 5000;');
    const columns = db.prepare('PRAGMA table_info(visit)').all() as { name: string }[];
    if (columns.length && !columns.some((column) => column.name === 'status')) {
      throw new Error('Legacy Majung database: preserved without changes. Back it up and use a new DB_PATH for the canonical majung-db schema.');
    }
    db.exec(sql('./majung-db/01_schema.sql'));
    db.exec('BEGIN IMMEDIATE');
    try {
      if (Number(db.prepare('SELECT COUNT(*) AS count FROM facility').get()?.count) === 0) {
        db.exec(sql('./majung-db/02_seed_facility.sql'));
        db.exec(sql('./majung-db/05_seed_scenarios.sql'));
      }
      db.exec(sql('./schema.sql'));
      db.exec('COMMIT');
    } catch (error) { db.exec('ROLLBACK'); throw error; }
    return db;
  } catch (error) { db.close(); throw error; }
}
