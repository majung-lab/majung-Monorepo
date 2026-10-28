import type { Visit, VisitMode } from '@majung/shared';
import type { Db } from '../../db/index.ts';

type VisitRow = {
  id: string;
  facility_id: string;
  mode: VisitMode;
  phase: Visit['phase'];
  confirmed_access_point_id: string;
  version: number;
  passenger_readiness: Visit['passengerReadiness'];
  updated_at: string;
  expires_at: string;
};

const toVisit = (r: VisitRow): Visit => ({
  id: r.id,
  facilityId: r.facility_id,
  mode: r.mode,
  phase: r.phase,
  confirmedAccessPointId: r.confirmed_access_point_id,
  version: r.version,
  passengerReadiness: r.passenger_readiness,
  updatedAt: r.updated_at,
  expiresAt: r.expires_at,
});

export function createVisitRepository(db: Db) {
  return {
    insert(v: Visit) {
      db.prepare(
        `INSERT INTO visit (id, facility_id, mode, phase, confirmed_access_point_id, version,
                            passenger_readiness, updated_at, expires_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      ).run(v.id, v.facilityId, v.mode, v.phase, v.confirmedAccessPointId, v.version,
        v.passengerReadiness, v.updatedAt, v.expiresAt);
    },
    findById(id: string): Visit | undefined {
      const row = db.prepare('SELECT * FROM visit WHERE id = ?').get(id) as VisitRow | undefined;
      return row && toVisit(row);
    },
  };
}
