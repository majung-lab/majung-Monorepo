import Fastify from 'fastify';
import type { Db } from './db/index.ts';
import { HttpError } from './common/errors.ts';
import { visitRoutes } from './modules/visit/visit.routes.ts';
import { realtime } from './realtime/index.ts';

export async function buildApp(db: Db) {
  const app = Fastify({ logger: true });

  app.setErrorHandler((err, _req, reply) => {
    if (err instanceof HttpError) {
      return reply.code(err.statusCode).send({ code: err.code, message: err.message });
    }
    reply.send(err);
  });

  app.get('/health', async () => ({ ok: true }));
  await app.register(realtime);
  await app.register(visitRoutes, { prefix: '/api', db });
  return app;
}
