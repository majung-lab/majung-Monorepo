import type { FastifyInstance } from 'fastify';
import type { VisitRepository } from '../modules/visit/visit.repository.ts';

// REST and sockets use the same capability check and snapshot; clients also poll after disconnect.
export async function realtime(app: FastifyInstance, { visits }: {visits:VisitRepository}) {
  app.get<{Params:{token:string}}>('/ws/visits/:token', {
    websocket:true,
    preValidation:async req=>{visits.snapshot(req.params.token);},
  }, (socket,req)=>{
    const send=()=>{
      try {if(socket.readyState===1) socket.send(JSON.stringify(visits.snapshot(req.params.token)));}
      catch {socket.close(1008,'Visit unavailable');}
    };
    const unsubscribe=visits.subscribe(req.params.token,send);
    socket.on('close',unsubscribe);
    socket.on('error',unsubscribe);
    send();
  });
}
