import { createHash, randomBytes, randomUUID } from 'node:crypto';
import type { AccessPoint, CreateProposalRequest, CreateVisitRequest, Facility, HistoryEntry, MeetingProposal, MutationRequest, RateVisitRequest, RespondProposalRequest, Role, UpdateVisitRequest, Visit, VisitSession, VisitSnapshot } from '@majung/shared';
import type { Db } from '../../db/index.ts';
import { HttpError } from '../../common/errors.ts';

const LINK_TTL_MS = 24 * 60 * 60 * 1000;
const PROPOSAL_TTL_MS = 10 * 60 * 1000;
const phaseFromDb = { created: 'PREPARING', moving: 'MOVING', arrived: 'ARRIVED', boarded: 'COMPLETED', ended: 'COMPLETED', cancelled: 'CANCELLED' } as const;
const modeFromDb = { pickup: 'PICKUP', dropoff: 'DROPOFF', parking: 'PARK' } as const;
const modeToDb = { PICKUP: 'pickup', DROPOFF: 'dropoff', PARK: 'parking' } as const;
const readinessFromDb = { none: 'UNSEEN', preparing: 'SEEN', moving_to_lobby: 'HEADING_OUT', at_lobby: 'READY' } as const;
const readinessToDb = { UNSEEN: 'none', SEEN: 'preparing', HEADING_OUT: 'moving_to_lobby', READY: 'at_lobby' } as const;
type VisitRow = {id:string; facility_id:number;mode:keyof typeof modeFromDb;status:keyof typeof phaseFromDb;confirmed_candidate_id:number|null;parking_candidate_id:number|null;driver_device_id:string;version:number;updated_at:string;expires_at:string;last_lat:number|null;last_lng:number|null;last_eta_sec:number|null;last_nav_result:'set'|'failed'|null;last_vehicle_at:string|null};
type Actor = {id:number;visit_id:string;role:'driver'|'passenger';token:string};
type Candidate = {id:number;access_point_id:number;purpose:'meet'|'parking'};
type ProposalRow = {id:number;visit_id:string;candidate_id:number;proposer_id:number;role:'driver'|'passenger';access_point_id:number;from_access_point_id:number;base_version:number;status:'pending'|'accepted'|'rejected'|'expired';reason_text:string|null;created_at:string};
const iso = () => new Date().toISOString();
const normalizeTime = (value:string) => value.includes('T') ? value : value.replace(' ', 'T') + 'Z';
const error = (status:number,code:string,message:string):never => {throw new HttpError(status,code,message);};

export function createVisitRepository(db: Db) {
  const listeners = new Map<string, Set<() => void>>();
  const one = <T>(sql:string,...params:(string|number|null)[]) => db.prepare(sql).get(...params) as T|undefined;
  const all = <T>(sql:string,...params:(string|number|null)[]) => db.prepare(sql).all(...params) as T[];
  const run = (sql:string,...params:(string|number|null)[]) => db.prepare(sql).run(...params);
  const row = (id:string) => one<VisitRow>('SELECT * FROM visit WHERE id=?',id) ?? error(404,'VISIT_NOT_FOUND','방문을 찾을 수 없어요');
  function actor(token:string, allowExpired = false): Actor {
    const participant = one<Actor>('SELECT id,visit_id,role,token FROM participant WHERE token=?',token);
    if (!participant) return error(404,'VISIT_NOT_FOUND','방문 링크를 확인해 주세요');
    if (!allowExpired && Date.parse(normalizeTime(row(participant.visit_id).expires_at)) <= Date.now()) return error(410,'LINK_EXPIRED','방문 링크가 만료됐어요');
    return participant;
  }
  function connection(apId:number) {
    return one<{id:number;wait_spot_id:number;name:string;floor:string|null;spot_description:string|null;walk_minutes:number;covered:number;description:string|null}>(
      `SELECT cn.*, ws.name, ws.floor, ws.description AS spot_description FROM connection cn
       JOIN access_point ap ON ap.id=cn.access_point_id
       JOIN wait_spot ws ON ws.id=cn.wait_spot_id AND ws.facility_id=ap.facility_id AND ws.active=1
       WHERE cn.access_point_id=? ORDER BY cn.walk_minutes,cn.id LIMIT 1`,apId);
  }
  function facilities():Facility[] {
    return all<{id:number;name:string;address:string|null;lat:number;lng:number}>('SELECT * FROM facility ORDER BY id').map(f => ({
      id:String(f.id),name:f.name,address:f.address??'',lat:f.lat,lng:f.lng,dataStatus:'EXAMPLE',
      accessPoints:all<{id:number;name:string;lat:number;lng:number;type:string;dropoff_allowed:number;vehicle_notes:string|null}>('SELECT * FROM access_point WHERE facility_id=? AND active=1 ORDER BY id',f.id).map(ap => {
        const cn=connection(ap.id);
        return {id:String(ap.id),name:ap.name,lat:ap.lat,lng:ap.lng,kind:ap.type==='parking'?'PARKING_ENTRANCE':'PICKUP',allowed:!!ap.dropoff_allowed && (ap.type==='parking'||!!cn),
          description:[cn?.name,cn?.spot_description,cn?.description,ap.vehicle_notes].filter(Boolean).join(' · '),
          waitSpot:cn?{name:cn.name,floor:cn.floor,description:cn.spot_description??''}:null,walkMinutes:cn?.walk_minutes??null,covered:!!cn?.covered} satisfies AccessPoint;
      }),
    }));
  }
  const facility = (id:number) => facilities().find(f=>f.id===String(id)) ?? error(404,'FACILITY_NOT_FOUND','시설을 찾을 수 없어요');
  function toVisit(v:VisitRow):Visit {
    const passenger = one<{status:keyof typeof readinessFromDb}>('SELECT status FROM participant WHERE visit_id=? AND role=\'passenger\'',v.id);
    const candidate=one<Candidate>('SELECT * FROM candidate WHERE id=?',v.mode==='parking'?v.parking_candidate_id:v.confirmed_candidate_id);
    const extra=one<{parking_floor:string|null}>('SELECT parking_floor FROM api_visit_state WHERE visit_id=?',v.id);
    const completed=one<{created_at:string}>("SELECT created_at FROM stage_event WHERE visit_id=? AND stage='ended' ORDER BY id DESC LIMIT 1",v.id);
    return {id:v.id,facilityId:String(v.facility_id),mode:modeFromDb[v.mode],phase:phaseFromDb[v.status],confirmedAccessPointId:String(candidate?.access_point_id??''),version:v.version,passengerReadiness:readinessFromDb[passenger?.status??'none'],updatedAt:normalizeTime(v.updated_at),expiresAt:normalizeTime(v.expires_at),parkingFloor:extra?.parking_floor??null,completedAt:completed?normalizeTime(completed.created_at):null};
  }
  function rating(visitId:string) {
    const result=one<{rating:number;tags_json:string|null;comment:string|null}>('SELECT * FROM place_rating WHERE visit_id=? ORDER BY id DESC LIMIT 1',visitId);
    return result?{rating:result.rating,tags:JSON.parse(result.tags_json??'[]') as string[],comment:result.comment}:null;
  }
  function snapshot(token:string):VisitSnapshot {
    const a=actor(token); const v=row(a.visit_id);
    const extra=one<{passenger_lat:number|null;passenger_lng:number|null;passenger_at:string|null}>('SELECT * FROM api_visit_state WHERE visit_id=?',v.id);
    const proposals=all<ProposalRow>(`SELECT pr.*, p.role, c.access_point_id, ctx.from_access_point_id FROM proposal pr
      JOIN participant p ON p.id=pr.proposer_id JOIN candidate c ON c.id=pr.candidate_id
      LEFT JOIN api_proposal_context ctx ON ctx.proposal_id=pr.id WHERE pr.visit_id=? ORDER BY pr.id DESC`,v.id).map(p=>({
        id:String(p.id),visitId:p.visit_id,proposedBy:p.role.toUpperCase() as Role,accessPointId:String(p.access_point_id),fromAccessPointId:String(p.from_access_point_id??toVisit(v).confirmedAccessPointId),baseVersion:p.base_version,
        status:(p.status==='pending'&&Date.now()>=Date.parse(normalizeTime(p.created_at))+PROPOSAL_TTL_MS?'EXPIRED':p.status.toUpperCase()) as MeetingProposal['status'],
        ...(p.reason_text?{reason:p.reason_text}:{}),createdAt:normalizeTime(p.created_at),expiresAt:new Date(Date.parse(normalizeTime(p.created_at))+PROPOSAL_TTL_MS).toISOString(),
      }));
    return {visit:toVisit(v),role:a.role.toUpperCase() as Role,facility:facility(v.facility_id),proposals,
      vehicle:v.last_vehicle_at?{lat:v.last_lat,lng:v.last_lng,etaSec:v.last_eta_sec,navResult:v.last_nav_result,recordedAt:normalizeTime(v.last_vehicle_at)}:null,
      passengerLocation:extra?.passenger_lat!=null&&extra.passenger_lng!=null&&extra.passenger_at?{lat:extra.passenger_lat,lng:extra.passenger_lng,recordedAt:extra.passenger_at}:null,rating:rating(v.id)};
  }
  function request<T>(actorKey:string,action:string,input:{requestId:string},fn:()=>T):T {
    const canonical=JSON.stringify(input,(_key,value)=>value&&typeof value==='object'&&!Array.isArray(value)
      ? Object.fromEntries(Object.entries(value).sort(([a],[b])=>a.localeCompare(b))) : value);
    const hash=createHash('sha256').update(canonical).digest('hex');
    const cached=one<{actor_key:string;action:string;payload_hash:string;response_json:string}>('SELECT * FROM api_request WHERE request_id=?',input.requestId);
    if(cached) {
      if(cached.actor_key!==actorKey||cached.action!==action||cached.payload_hash!==hash) return error(409,'REQUEST_REUSED','같은 요청 번호를 다른 작업에 사용할 수 없어요');
      return JSON.parse(cached.response_json) as T;
    }
    db.exec('BEGIN IMMEDIATE');
    try {
      const result=fn();
      run('INSERT INTO api_request VALUES (?,?,?,?,?,?)',input.requestId,actorKey,action,hash,JSON.stringify(result),iso());
      db.exec('COMMIT'); return result;
    } catch(e) {db.exec('ROLLBACK');throw e;}
  }
  function publish(id:string) {listeners.get(id)?.forEach(fn=>fn());}
  function create(input:CreateVisitRequest):VisitSession {
    return request(input.driverDeviceId,'create',input,()=>{
      const f=facility(Number(input.facilityId)); const selected=f.accessPoints.find(ap=>ap.id===input.accessPointId&&ap.allowed);
      if(!selected || selected.kind!==(input.mode==='PARK'?'PARKING_ENTRANCE':'PICKUP')) return error(400,'INVALID_ACCESS_POINT','이 방문에 사용할 수 없는 장소예요');
      const id=randomUUID(); const now=iso(); const expires=new Date(Date.now()+LINK_TTL_MS).toISOString();
      run('INSERT INTO visit (id,facility_id,mode,driver_device_id,expires_at,created_at,updated_at) VALUES (?,?,?,?,?,?,?)',id,Number(input.facilityId),modeToDb[input.mode],input.driverDeviceId,expires,now,now);
      run('INSERT INTO api_visit_state (visit_id) VALUES (?)',id);
      let selectedCandidate:number|null=null; let rank=0;
      for(const ap of f.accessPoints.filter(ap=>ap.allowed&&ap.kind===(input.mode==='PARK'?'PARKING_ENTRANCE':'PICKUP'))) {
        const cn=connection(Number(ap.id));
        const result=run('INSERT INTO candidate (visit_id,purpose,access_point_id,wait_spot_id,connection_id,rank,reasons_json) VALUES (?,?,?,?,?,?,?)',id,input.mode==='PARK'?'parking':'meet',Number(ap.id),cn?.wait_spot_id??null,cn?.id??null,++rank,JSON.stringify([ap.description]));
        if(ap.id===input.accessPointId) selectedCandidate=Number(result.lastInsertRowid);
      }
      run(`UPDATE visit SET ${input.mode==='PARK'?'parking_candidate_id':'confirmed_candidate_id'}=? WHERE id=? AND version=1`,selectedCandidate,id);
      const driverToken=randomBytes(32).toString('base64url'); const passengerToken=randomBytes(32).toString('base64url');
      run("INSERT INTO participant (visit_id,role,token) VALUES (?,'driver',?),(?,'passenger',?)",id,driverToken,id,passengerToken);
      return {visit:toVisit(row(id)),driverToken,passengerToken};
    });
  }
  function mutate<T extends MutationRequest>(token:string,action:string,input:T,fn:(a:Actor,v:VisitRow)=>void):VisitSnapshot {
    const a=actor(token);
    request(token,action,input,()=>{
      const v=row(a.visit_id);
      if(v.version!==input.version) return error(409,'VERSION_CONFLICT','다른 사람이 방문을 변경했어요. 최신 상태를 확인해 주세요');
      run("UPDATE proposal SET status='expired',responded_at=? WHERE visit_id=? AND status='pending' AND julianday(created_at)<=julianday(?)-?",iso(),v.id,iso(),PROPOSAL_TTL_MS/86400000);
      fn(a,v);
      const updated=run('UPDATE visit SET version=version+1,updated_at=? WHERE id=? AND version=?',iso(),v.id,input.version);
      if(updated.changes!==1) return error(409,'VERSION_CONFLICT','방문 상태가 바뀌었어요');
      return {visitId:v.id};
    });
    publish(a.visit_id);return snapshot(token);
  }
  const requireRole=(a:Actor,role:Actor['role'])=>{if(a.role!==role) error(403,'FORBIDDEN','이 작업을 할 권한이 없어요');};
  const requireActive=(v:VisitRow)=>{if(['ended','boarded','cancelled'].includes(v.status)) error(409,'VISIT_ENDED','이미 종료된 방문이에요');};
  function update(token:string,input:UpdateVisitRequest):VisitSnapshot {
    return mutate(token,'update',input,(a,v)=>{
      requireActive(v);
      if(input.phase!==undefined||input.vehicle!==undefined||input.parkingFloor!==undefined||input.accessPointId!==undefined) requireRole(a,'driver');
      if(input.passengerReadiness!==undefined||input.passengerLocation!==undefined) requireRole(a,'passenger');
      if(input.accessPointId!==undefined) {
        if(v.mode==='pickup') return error(403,'MUTUAL_CONSENT_REQUIRED','픽업 장소는 상대방이 제안을 수락해야 바뀌어요');
        const candidate=one<Candidate>('SELECT * FROM candidate WHERE visit_id=? AND access_point_id=? AND purpose=? AND excluded_reason IS NULL',v.id,Number(input.accessPointId),v.mode==='parking'?'parking':'meet');
        if(!candidate) return error(400,'INVALID_ACCESS_POINT','이 방문에 사용할 수 없는 장소예요');
        run(`UPDATE visit SET ${v.mode==='parking'?'parking_candidate_id':'confirmed_candidate_id'}=? WHERE id=? AND version=?`,candidate.id,v.id,v.version);
      }
      if(input.phase!==undefined) {
        const current=phaseFromDb[v.status];
        const next:Record<string,string[]>={PREPARING:['MOVING','CANCELLED'],MOVING:['ARRIVED','CANCELLED'],ARRIVED:['COMPLETED','CANCELLED']};
        if(!next[current]?.includes(input.phase)) return error(409,'INVALID_PHASE','방문 단계 순서가 올바르지 않아요');
        const status={PREPARING:'created',MOVING:'moving',ARRIVED:'arrived',COMPLETED:'ended',CANCELLED:'cancelled'}[input.phase];
        const stage={PREPARING:'created',MOVING:'departed',ARRIVED:'arrived',COMPLETED:'ended',CANCELLED:'cancelled'}[input.phase];
        run('UPDATE visit SET status=?,current_stage=? WHERE id=? AND version=?',status!,stage!,v.id,v.version);
        run("INSERT INTO stage_event (visit_id,stage,actor_id,source,request_id) VALUES (?,?,?,'manual',?)",v.id,stage!,a.id,input.requestId);
        if(input.phase==='COMPLETED') run('INSERT INTO destination_history (driver_device_id,facility_id,mode,last_candidate_id) VALUES (?,?,?,?)',v.driver_device_id,v.facility_id,v.mode,v.mode==='parking'?v.parking_candidate_id:v.confirmed_candidate_id);
        if(['COMPLETED','CANCELLED'].includes(input.phase)) run("UPDATE proposal SET status='expired',responded_at=? WHERE visit_id=? AND status='pending'",iso(),v.id);
      }
      if(input.passengerReadiness!==undefined) run('UPDATE participant SET status=?,last_seen_at=? WHERE id=?',readinessToDb[input.passengerReadiness],iso(),a.id);
      if(input.passengerLocation!==undefined) run('UPDATE api_visit_state SET passenger_lat=?,passenger_lng=?,passenger_at=? WHERE visit_id=?',input.passengerLocation?.lat??null,input.passengerLocation?.lng??null,input.passengerLocation?iso():null,v.id);
      if(input.parkingFloor!==undefined) run('UPDATE api_visit_state SET parking_floor=? WHERE visit_id=?',input.parkingFloor,v.id);
      if(input.vehicle) {
        const pos=input.vehicle;
        if((pos.lat===null)!==(pos.lng===null)) return error(400,'INVALID_POSITION','위도와 경도를 함께 보내 주세요');
        run('INSERT INTO vehicle_update (visit_id,lat,lng,eta_sec,nav_result,nav_target_access_point_id) VALUES (?,?,?,?,?,?)',v.id,pos.lat,pos.lng,pos.etaSec,pos.navResult??null,Number(toVisit(v).confirmedAccessPointId));
        run('UPDATE visit SET last_lat=?,last_lng=?,last_eta_sec=?,last_nav_result=?,last_vehicle_at=? WHERE id=? AND version=?',pos.lat,pos.lng,pos.etaSec,pos.navResult??null,iso(),v.id,v.version);
      }
    });
  }
  function propose(token:string,input:CreateProposalRequest) {
    return mutate(token,'propose',input,(a,v)=>{
      requireActive(v);
      if(one('SELECT id FROM proposal WHERE visit_id=? AND status=\'pending\'',v.id)) return error(409,'PROPOSAL_PENDING','진행 중인 제안에 먼저 답해 주세요');
      if(toVisit(v).confirmedAccessPointId===input.accessPointId) return error(400,'SAME_ACCESS_POINT','현재 장소와 같아요');
      const candidate=one<Candidate>('SELECT * FROM candidate WHERE visit_id=? AND access_point_id=? AND purpose=? AND excluded_reason IS NULL',v.id,Number(input.accessPointId),v.mode==='parking'?'parking':'meet');
      if(!candidate) return error(400,'INVALID_ACCESS_POINT','이 방문에 사용할 수 없는 장소예요');
      const result=run("INSERT INTO proposal (visit_id,candidate_id,proposer_id,reason,reason_text,base_version,request_id,created_at) VALUES (?,?,?,'other',?,?,?,?)",v.id,candidate.id,a.id,input.reason??null,v.version,input.requestId,iso());
      run('INSERT INTO api_proposal_context VALUES (?,?)',Number(result.lastInsertRowid),Number(toVisit(v).confirmedAccessPointId));
    });
  }
  function respond(token:string,id:string,input:RespondProposalRequest|MutationRequest,cancel=false) {
    return mutate(token,`${cancel?'cancel':'respond'}:${id}`,input,(a,v)=>{
      requireActive(v);
      const p=one<ProposalRow>('SELECT * FROM proposal WHERE id=? AND visit_id=?',Number(id),v.id);
      if(!p) return error(404,'PROPOSAL_NOT_FOUND','제안을 찾을 수 없어요');
      if(cancel?(p.proposer_id!==a.id):(p.proposer_id===a.id)) return error(403,'FORBIDDEN','이 제안에 답할 권한이 없어요');
      if(p.status!=='pending') return error(409,'PROPOSAL_CLOSED','이미 답했거나 만료된 제안이에요');
      const accept='accept' in input&&input.accept;
      run('UPDATE proposal SET status=?,responder_id=?,responded_via=\'touch\',responded_at=? WHERE id=?',cancel?'expired':accept?'accepted':'rejected',a.id,iso(),p.id);
      if(accept) run(`UPDATE visit SET ${v.mode==='parking'?'parking_candidate_id':'confirmed_candidate_id'}=? WHERE id=? AND version=?`,p.candidate_id,v.id,v.version);
    });
  }
  function rate(token:string,input:RateVisitRequest) {
    return mutate(token,'rate',input,(a,v)=>{
      requireRole(a,'driver');
      if(phaseFromDb[v.status]!=='COMPLETED') return error(409,'VISIT_NOT_COMPLETED','방문을 완료한 뒤 평가해 주세요');
      if(rating(v.id)) return error(409,'ALREADY_RATED','이미 평가한 방문이에요');
      run('INSERT INTO place_rating (visit_id,candidate_id,rating,tags_json,comment) VALUES (?,?,?,?,?)',v.id,(v.mode==='parking'?v.parking_candidate_id:v.confirmed_candidate_id)!,input.rating,JSON.stringify(input.tags),input.comment??null);
    });
  }
  function history(driverDeviceId:string):HistoryEntry[] {
    return all<VisitRow>('SELECT * FROM visit WHERE driver_device_id=? ORDER BY created_at DESC LIMIT 100',driverDeviceId).map(v=>({visit:toVisit(v),facility:facility(v.facility_id),rating:rating(v.id)}));
  }
  return {facilities,create,snapshot,update,propose,respond,rate,history,
    subscribe(token:string,listener:()=>void) {
      const a=actor(token);const set=listeners.get(a.visit_id)??new Set<()=>void>();set.add(listener);listeners.set(a.visit_id,set);
      return ()=>{set.delete(listener);if(!set.size) listeners.delete(a.visit_id);};
    },
  };
}
export type VisitRepository = ReturnType<typeof createVisitRepository>;
