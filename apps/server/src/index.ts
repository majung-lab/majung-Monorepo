import { openDb } from './db/index.ts';
import { buildApp } from './app.ts';

const db = openDb(process.env.DB_PATH ?? './data/majung.sqlite');
const app = await buildApp(db);
app.addHook('onClose',async()=>db.close());
for(const signal of ['SIGINT','SIGTERM'] as const) process.once(signal,()=>{void app.close();});
await app.listen({ port: Number(process.env.PORT ?? 4000), host: '0.0.0.0' });
