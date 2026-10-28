import { randomUUID } from 'node:crypto';
import type { FastifyInstance } from 'fastify';
import type { CreateVisitRequest, Visit } from '@majung/shared';
import type { Db } from '../../db/index.ts';
import { HttpError } from '../../common/errors.ts';
import { createVisitRepository } from './visit.repository.ts';

const LINK_TTL_MS = 24 * 60 * 60 * 1000;

export async function visitRoutes(app: FastifyInstance, { db }: { db: Db }) {
  const visits = createVisitRepository(db);

  app.post<{ Body: CreateVisitRequest }>('/visits', async (req, reply) => {
    const now = new Date();
    const visit: Visit = {
      id: randomUUID(),
      facilityId: req.body.facilityId,
      mode: req.body.mode,
      phase: 'PREPARING',
      confirmedAccessPointId: req.body.accessPointId,
      version: 1,
      passengerReadiness: 'UNSEEN',
      updatedAt: now.toISOString(),
      expiresAt: new Date(now.getTime() + LINK_TTL_MS).toISOString(),
    };
    visits.insert(visit);
    return reply.code(201).send(visit);
  });

  app.get<{ Params: { id: string } }>('/visits/:id', async (req) => {
    const visit = visits.findById(req.params.id);
    if (!visit) throw new HttpError(404, 'VISIT_NOT_FOUND', '방문을 찾을 수 없어요');
    return visit;
  });
}
