import type { FastifyInstance } from 'fastify';
import type { CreateProposalRequest, CreateVisitRequest, MutationRequest, RateVisitRequest, RespondProposalRequest, UpdateVisitRequest } from '@majung/shared';
import type { VisitRepository } from './visit.repository.ts';
import type { PlaceNameLookup } from './place-name.ts';
import type { WeatherLookup } from '../weather/weather.ts';

const text = { type:'string', minLength:1, maxLength:128 };
const id = { type:'string', pattern:'^[1-9][0-9]*$', maxLength:16 };
const requestFields = {requestId:text,version:{type:'integer',minimum:1}};
const object = (properties:Record<string,unknown>, required:string[]) => ({type:'object',additionalProperties:false,properties,required});
const mutation = (properties:Record<string,unknown> = {},required:string[] = []) => object({...requestFields,...properties},['requestId','version',...required]);
const lat = {type:'number',minimum:-90,maximum:90};
const lng = {type:'number',minimum:-180,maximum:180};
const nullable = (schema:object) => ({anyOf:[schema,{type:'null'}]});
type TokenParams = {token:string};
type ProposalParams = TokenParams & {id:string};

export async function visitRoutes(app: FastifyInstance, { visits, placeName, weather }: { visits: VisitRepository; placeName: PlaceNameLookup; weather: WeatherLookup }) {
  app.get('/facilities',async()=>visits.facilities());
  app.post<{Body:CreateVisitRequest}>('/visits',{schema:{body:object({requestId:text,facilityId:id,mode:{enum:['PICKUP','PARK','DROPOFF']},accessPointId:id,driverDeviceId:{type:'string',minLength:24,maxLength:128}},['requestId','facilityId','mode','accessPointId','driverDeviceId'])}},async(req,reply)=>{
    // 날씨는 후보 순위와 기록에만 쓰므로, 못 받아도 방문은 만든다
    const f=visits.facilities().find(x=>x.id===req.body.facilityId);
    return reply.code(201).send(visits.create(req.body,f?await weather(f.lat,f.lng):null));
  });
  app.get<{Params:TokenParams}>('/visits/:token',async req=>visits.snapshot(req.params.token));
  app.patch<{Params:TokenParams;Body:UpdateVisitRequest}>('/visits/:token',{schema:{body:{...mutation({
    accessPointId:id,phase:{enum:['PREPARING','MOVING','ARRIVED','COMPLETED','CANCELLED']},passengerReadiness:{enum:['UNSEEN','SEEN','READY','HEADING_OUT','AT_LOBBY']},
    parkingFloor:nullable({type:'string',minLength:1,maxLength:32}),
    passengerLocation:nullable(object({lat,lng},['lat','lng'])),
    passengerWaitSpotId:nullable(id),
    passengerConditions:object({stroller:{type:'boolean'},luggage:{type:'boolean'},avoidStairs:{type:'boolean'},indoorOnly:{type:'boolean'}},['stroller','luggage','avoidStairs','indoorOnly']),
    vehicle:object({lat:nullable(lat),lng:nullable(lng),etaSec:nullable({type:'integer',minimum:0,maximum:86400}),navResult:{enum:['set','failed']}},['lat','lng','etaSec']),
  }),minProperties:3}}},async req=>visits.update(req.params.token,req.body));
  app.post<{Params:TokenParams;Body:CreateProposalRequest}>('/visits/:token/proposals',{schema:{body:{...mutation({accessPointId:id,location:object({lat,lng},['lat','lng']),reason:{type:'string',maxLength:500}}),oneOf:[{required:['accessPointId']},{required:['location']}]}}},async req=>{
    visits.snapshot(req.params.token);
    return visits.propose(req.params.token,req.body,req.body.location?await placeName(req.body.location.lat,req.body.location.lng):null);
  });
  app.post<{Params:ProposalParams;Body:RespondProposalRequest}>('/visits/:token/proposals/:id/respond',{schema:{body:mutation({accept:{type:'boolean'}},['accept'])}},async req=>visits.respond(req.params.token,req.params.id,req.body));
  app.delete<{Params:ProposalParams;Body:MutationRequest}>('/visits/:token/proposals/:id',{schema:{body:mutation()}},async req=>visits.respond(req.params.token,req.params.id,req.body,true));
  app.post<{Params:TokenParams;Body:RateVisitRequest}>('/visits/:token/rating',{schema:{body:mutation({rating:{type:'integer',minimum:1,maximum:5},tags:{type:'array',maxItems:10,items:{type:'string',maxLength:80}},comment:{type:'string',maxLength:1000}},['rating','tags'])}},async req=>visits.rate(req.params.token,req.body));
  app.get<{Querystring:{driverDeviceId:string}}>('/history',{schema:{querystring:object({driverDeviceId:{type:'string',minLength:24,maxLength:128}},['driverDeviceId'])}},async req=>visits.history(req.query.driverDeviceId));
}
