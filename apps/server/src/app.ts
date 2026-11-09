import Fastify, { LogController, type FastifyError } from 'fastify';
import websocket from '@fastify/websocket';
import type { Db } from './db/index.ts';
import { HttpError } from './common/errors.ts';
import { visitRoutes } from './modules/visit/visit.routes.ts';
import { createVisitRepository } from './modules/visit/visit.repository.ts';
import { realtime } from './realtime/index.ts';
import { kakaoPlaceName, type PlaceNameLookup } from './modules/visit/place-name.ts';
import { inviteRoutes } from './modules/notification/invite.routes.ts';
import { smsFromEnv, type SmsSender } from './modules/notification/sms.ts';

export interface AppOptions {
  sms?: SmsSender;
  /** 문자에 담는 동승자 웹 주소. 휴대폰에서 열려야 하므로 실제로는 PC의 LAN IP나 배포 주소 */
  passengerWebUrl?: string;
}

export async function buildApp(db: Db, placeName: PlaceNameLookup = kakaoPlaceName(), options: AppOptions = {}) {
  // URLs contain bearer tokens and history device IDs, so omit automatic URL request logging.
  const app = Fastify({ logger: true, logController:new LogController({disableRequestLogging:true}), ajv:{customOptions:{removeAdditional:false,coerceTypes:false}} });
  app.setErrorHandler<FastifyError>((err,_req,reply)=>{
    if(err instanceof HttpError) return reply.code(err.statusCode).send({code:err.code,message:err.message});
    if(err.validation) return reply.code(400).send({code:'INVALID_REQUEST',message:'요청 내용을 확인해 주세요'});
    app.log.error({name:err.name,code:err.code},'Request failed');
    return reply.code(500).send({code:'INTERNAL_ERROR',message:'요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요'});
  });
  const visits=createVisitRepository(db);
  app.get('/health',async()=>({ok:true,database:'majung-db',facilityData:'EXAMPLE'}));
  await app.register(websocket);
  await app.register(realtime,{visits});
  await app.register(visitRoutes,{prefix:'/api',visits,placeName});
  await app.register(inviteRoutes,{prefix:'/api',visits,sms:options.sms??smsFromEnv(),passengerWebUrl:options.passengerWebUrl??process.env.PASSENGER_WEB_URL??'http://localhost:5173'});
  return app;
}
