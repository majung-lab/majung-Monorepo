import type { FastifyInstance } from 'fastify';
import type { CreateProposalRequest, CreateVisitRequest, MutationRequest, RateVisitRequest, RespondProposalRequest, UpdateVisitRequest } from '@majung/shared';
import type { VisitRepository } from './visit.repository.ts';

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

export async function visitRoutes(app: FastifyInstance, { visits }: { visits: VisitRepository }) {
  app.get('/facilities',async()=>visits.facilities());
  app.post<{Body:CreateVisitRequest}>('/visits',{schema:{body:object({requestId:text,facilityId:id,mode:{enum:['PICKUP','PARK','DROPOFF']},accessPointId:id,driverDeviceId:{type:'string',minLength:24,maxLength:128}},['requestId','facilityId','mode','accessPointId','driverDeviceId'])}},async(req,reply)=>reply.code(201).send(visits.create(req.body)));
  app.get<{Params:TokenParams}>('/visits/:token',async req=>visits.snapshot(req.params.token));
  app.patch<{Params:TokenParams;Body:UpdateVisitRequest}>('/visits/:token',{schema:{body:{...mutation({
    accessPointId:id,phase:{enum:['PREPARING','MOVING','ARRIVED','COMPLETED','CANCELLED']},passengerReadiness:{enum:['UNSEEN','SEEN','READY','HEADING_OUT']},
    parkingFloor:nullable({type:'string',minLength:1,maxLength:32}),
    passengerLocation:nullable(object({lat,lng},['lat','lng'])),
    vehicle:object({lat:nullable(lat),lng:nullable(lng),etaSec:nullable({type:'integer',minimum:0,maximum:86400}),navResult:{enum:['set','failed']}},['lat','lng','etaSec']),
  }),minProperties:3}}},async req=>visits.update(req.params.token,req.body));
  app.post<{Params:TokenParams;Body:CreateProposalRequest}>('/visits/:token/proposals',{schema:{body:mutation({accessPointId:id,reason:{type:'string',maxLength:500}},['accessPointId'])}},async req=>visits.propose(req.params.token,req.body));
  app.post<{Params:ProposalParams;Body:RespondProposalRequest}>('/visits/:token/proposals/:id/respond',{schema:{body:mutation({accept:{type:'boolean'}},['accept'])}},async req=>visits.respond(req.params.token,req.params.id,req.body));
  app.delete<{Params:ProposalParams;Body:MutationRequest}>('/visits/:token/proposals/:id',{schema:{body:mutation()}},async req=>visits.respond(req.params.token,req.params.id,req.body,true));
  app.post<{Params:TokenParams;Body:RateVisitRequest}>('/visits/:token/rating',{schema:{body:mutation({rating:{type:'integer',minimum:1,maximum:5},tags:{type:'array',maxItems:10,items:{type:'string',maxLength:80}},comment:{type:'string',maxLength:1000}},['rating','tags'])}},async req=>visits.rate(req.params.token,req.body));
  app.get<{Querystring:{driverDeviceId:string}}>('/history',{schema:{querystring:object({driverDeviceId:{type:'string',minLength:24,maxLength:128}},['driverDeviceId'])}},async req=>visits.history(req.query.driverDeviceId));
}
