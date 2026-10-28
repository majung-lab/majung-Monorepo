import websocket from '@fastify/websocket';
import type { FastifyInstance } from 'fastify';

// 방문 상태 구독. 끊기면 클라이언트가 5초 간격 GET /visits/:id 로 대체한다.
export async function realtime(app: FastifyInstance) {
  await app.register(websocket);
  app.get('/ws/visits/:id', { websocket: true }, (socket) => {
    socket.send(JSON.stringify({ type: 'hello' }));
  });
}
