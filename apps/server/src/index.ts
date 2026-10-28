import { openDb } from './db/index.ts';
import { buildApp } from './app.ts';

const db = openDb(process.env.DB_PATH ?? './majung.sqlite');
const app = await buildApp(db);
await app.listen({ port: Number(process.env.PORT ?? 4000), host: '0.0.0.0' });
