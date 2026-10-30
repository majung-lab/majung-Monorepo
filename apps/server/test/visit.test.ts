import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { openDb } from '../src/db/index.ts';
import { buildApp } from '../src/app.ts';

const body = { requestId: 'create-1', facilityId: '1', mode: 'PICKUP', accessPointId: '2', driverDeviceId: 'device-with-unguessable-random-identifier' };

// Removing persisted canonical candidates, role checks or version checks breaks these flows.
test('canonical DB connects both roles, persists accepted proposals, completion, rating and history', async () => {
  const directory = mkdtempSync(join(tmpdir(), 'majung-test-'));
  const path = join(directory, 'majung.sqlite');
  let db = openDb(path);
  let app = await buildApp(db);
  const call = async (method: 'GET' | 'POST' | 'PATCH' | 'DELETE', url: string, payload?: object) => {
    const response = await app.inject({ method, url, payload });
    assert.ok(response.statusCode < 300, `${method} ${url}: ${response.body}`);
    return response.json();
  };
  try {
    const facilities = await call('GET', '/api/facilities');
    assert.equal(facilities[0].dataStatus, 'EXAMPLE');
    const session = await call('POST', '/api/visits', body);
    assert.ok(session.driverToken && session.passengerToken);
    assert.notEqual(session.driverToken, session.passengerToken);
    assert.deepEqual(await call('POST', '/api/visits', body), session);
    assert.equal(db.prepare('SELECT COUNT(*) AS n FROM candidate').get()?.n, 2);
    const driver = `/api/visits/${session.driverToken}`;
    const passenger = `/api/visits/${session.passengerToken}`;
    let snapshot = await call('GET', passenger);
    assert.equal(snapshot.role, 'PASSENGER');
    snapshot = await call('PATCH', passenger, {requestId:'ready',version:snapshot.visit.version,passengerReadiness:'READY',passengerLocation:{lat:37.54,lng:127.22}});
    assert.equal(snapshot.visit.passengerReadiness, 'READY');
    assert.equal(snapshot.passengerLocation.lat, 37.54);
    snapshot = await call('PATCH', driver, {requestId:'moving',version:snapshot.visit.version,phase:'MOVING',vehicle:{lat:37.53,lng:127.21,etaSec:240,navResult:'set'}});
    snapshot = await call('POST', passenger+'/proposals', {requestId:'proposal',version:snapshot.visit.version,accessPointId:'1',reason:'서문이 가까워요'});
    const proposal = snapshot.proposals[0];
    assert.equal(proposal.fromAccessPointId,'2');
    const accept = {requestId:'accept',version:snapshot.visit.version,accept:true};
    snapshot = await call('POST', driver+`/proposals/${proposal.id}/respond`, accept);
    assert.equal(snapshot.visit.confirmedAccessPointId,'1');
    assert.equal(snapshot.proposals[0].fromAccessPointId,'2');
    const acceptedVersion = snapshot.visit.version;
    snapshot = await call('POST', driver+`/proposals/${proposal.id}/respond`, accept);
    assert.equal(snapshot.visit.version,acceptedVersion);
    snapshot = await call('PATCH',driver,{requestId:'arrived',version:snapshot.visit.version,phase:'ARRIVED'});
    snapshot = await call('PATCH',driver,{requestId:'complete',version:snapshot.visit.version,phase:'COMPLETED'});
    snapshot = await call('POST',driver+'/rating',{requestId:'rate',version:snapshot.visit.version,rating:4,tags:['찾기 쉬움'],comment:'편리해요'});
    assert.equal(snapshot.rating.rating,4);
    assert.equal(db.prepare('SELECT COUNT(*) AS n FROM destination_history').get()?.n,1);
    await app.close(); db.close();
    db = openDb(path); app = await buildApp(db);
    snapshot = await call('GET',passenger);
    assert.equal(snapshot.visit.phase,'COMPLETED');
    assert.equal(snapshot.rating.comment,'편리해요');
    const history = await call('GET','/api/history?driverDeviceId='+body.driverDeviceId);
    assert.equal(history.length,1);
    assert.equal(history[0].rating.rating,4);
  } finally { await app.close(); db.close(); rmSync(directory,{recursive:true,force:true}); }
});

test('role authority, validation, optimistic concurrency and request replay are enforced', async () => {
  const db = openDb(':memory:'); const app = await buildApp(db);
  try {
    const created = await app.inject({method:'POST',url:'/api/visits',payload:body});
    assert.equal(created.statusCode,201);
    const session=created.json(); const d=`/api/visits/${session.driverToken}`; const p=`/api/visits/${session.passengerToken}`;
    const check = async (status:number,method:'POST'|'PATCH'|'GET'|'DELETE',url:string,payload?:object) => {
      const r=await app.inject({method,url,payload}); assert.equal(r.statusCode,status,r.body); return r.json();
    };
    await check(404,'GET','/api/visits/'+session.visit.id);
    await check(403,'PATCH',p,{requestId:'forbidden',version:1,phase:'MOVING'});
    await check(403,'PATCH',d,{requestId:'driver-ready',version:1,passengerReadiness:'READY'});
    await check(409,'PATCH',d,{requestId:'skip',version:1,phase:'COMPLETED'});
    await check(400,'PATCH',d,{requestId:'bad-pos',version:1,vehicle:{lat:91,lng:127,etaSec:0}});
    await check(400,'POST',p+'/proposals',{requestId:'wrong',version:1,accessPointId:'5'});
    await check(400,'POST',p+'/proposals',{requestId:'forbidden-place',version:1,accessPointId:'3'});
    const pending=await check(200,'POST',p+'/proposals',{requestId:'pending',version:1,accessPointId:'1'});
    await check(403,'POST',p+`/proposals/${pending.proposals[0].id}/respond`,{requestId:'self',version:2,accept:true});
    await check(409,'PATCH',d,{requestId:'stale',version:1,phase:'MOVING'});
    await check(409,'POST',p+'/proposals',{requestId:'second',version:2,accessPointId:'4'});
    await check(409,'POST','/api/visits',{...body,accessPointId:'1'});
    const cancelled=await check(200,'DELETE',p+`/proposals/${pending.proposals[0].id}`,{requestId:'cancel',version:2});
    assert.equal(cancelled.proposals[0].status,'EXPIRED');
    await check(403,'POST',p+'/rating',{requestId:'rating',version:3,rating:4,tags:[]});
    db.prepare("UPDATE visit SET expires_at='2000-01-01T00:00:00Z'").run();
    await check(410,'GET',d);
    const history=await check(200,'GET','/api/history?driverDeviceId='+body.driverDeviceId);
    assert.equal(history.length,1);
  } finally {await app.close();db.close();}
});

test('parking, dropoff, completion time and live role-specific socket snapshots work', {timeout:10000}, async () => {
  const db=openDb(':memory:'); const app=await buildApp(db);
  try {
    const session=(await app.inject({method:'POST',url:'/api/visits',payload:{...body,requestId:'park-create',mode:'PARK',accessPointId:'4'}})).json();
    assert.ok(session.driverToken);
    const driver=`/api/visits/${session.driverToken}`;
    let resolveInitial!:(value:unknown)=>void;
    const initial=new Promise(resolve=>{resolveInitial=resolve;});
    const ws=await app.injectWS(`/ws/visits/${session.passengerToken}`,{}, {onInit:socket=>{socket.once('message',(data:{toString():string})=>resolveInitial(JSON.parse(data.toString())));}});
    const first=await initial as {role:string;visit:{phase:string}};
    assert.equal(first.role,'PASSENGER');
    const live=new Promise<{visit:{phase:string}}>(resolve=>ws.once('message',(data:{toString():string})=>resolve(JSON.parse(data.toString()))));
    let snapshot=(await app.inject({method:'PATCH',url:driver,payload:{requestId:'park-moving',version:1,phase:'MOVING'}})).json();
    assert.equal((await live).visit.phase,'MOVING');
    snapshot=(await app.inject({method:'PATCH',url:driver,payload:{requestId:'park-arrived',version:snapshot.visit.version,phase:'ARRIVED',parkingFloor:'B2'}})).json();
    snapshot=(await app.inject({method:'PATCH',url:driver,payload:{requestId:'park-ended',version:snapshot.visit.version,phase:'COMPLETED'}})).json();
    assert.equal(snapshot.visit.parkingFloor,'B2');
    assert.ok(snapshot.visit.completedAt);
    const time=snapshot.visit.completedAt;
    snapshot=(await app.inject({method:'POST',url:driver+'/rating',payload:{requestId:'park-rating',version:snapshot.visit.version,rating:5,tags:[]}})).json();
    assert.equal(snapshot.visit.completedAt,time);
    ws.terminate();
    assert.equal((await app.inject({method:'GET',url:'/ws/visits/invalid'})).statusCode,404);
    const dropoff=(await app.inject({method:'POST',url:'/api/visits',payload:{...body,requestId:'dropoff-create',mode:'DROPOFF'}})).json();
    assert.equal(dropoff.visit.mode,'DROPOFF');
  } finally {await app.close();db.close();}
});

test('legacy databases are preserved and request object ordering does not affect retries', async () => {
  const {DatabaseSync}=await import('node:sqlite');
  const directory=mkdtempSync(join(tmpdir(),'majung-legacy-')); const path=join(directory,'legacy.sqlite');
  const legacy=new DatabaseSync(path); legacy.exec("CREATE TABLE visit (id TEXT,phase TEXT); INSERT INTO visit VALUES ('keep','MOVING')"); legacy.close();
  try {
    assert.throws(()=>openDb(path),/Legacy Majung database/);
    const check=new DatabaseSync(path);
    assert.equal(check.prepare('SELECT id FROM visit').get()?.id,'keep'); check.close();
  } finally {rmSync(directory,{recursive:true,force:true});}
  const db=openDb(':memory:');const app=await buildApp(db);
  try {
    const created=await app.inject({method:'POST',url:'/api/visits',payload:body});
    const reordered=await app.inject({method:'POST',url:'/api/visits',payload:{driverDeviceId:body.driverDeviceId,accessPointId:body.accessPointId,mode:body.mode,facilityId:body.facilityId,requestId:body.requestId}});
    assert.equal(reordered.statusCode,201,reordered.body);
    assert.deepEqual(reordered.json(),created.json());
  }finally{await app.close();db.close();}
});

test('an existing upstream canonical database gains persistent API UI state without replacing visits', async () => {
  const {DatabaseSync}=await import('node:sqlite');
  const {readFileSync}=await import('node:fs');
  const directory=mkdtempSync(join(tmpdir(),'majung-canonical-')); const path=join(directory,'canonical.sqlite');
  const original=new DatabaseSync(path);
  for(const file of ['../src/db/majung-db/01_schema.sql','../src/db/majung-db/02_seed_facility.sql','./fixtures/03_demo_visit.sql']) original.exec(readFileSync(new URL(file,import.meta.url),'utf8'));
  original.close();
  let db=openDb(path);let app=await buildApp(db);
  try {
    const updated=await app.inject({method:'PATCH',url:'/api/visits/tok-passenger-001',payload:{requestId:'legacy-location',version:1,passengerLocation:{lat:37.54,lng:127.22}}});
    assert.equal(updated.statusCode,200,updated.body);
    assert.equal(updated.json().passengerLocation?.lat,37.54);
    assert.equal(updated.json().visit.id,'visit-demo-001');
    await app.close();db.close(); db=openDb(path);app=await buildApp(db);
    const persisted=(await app.inject({method:'GET',url:'/api/visits/tok-passenger-001'})).json();
    assert.equal(persisted.passengerLocation.lat,37.54);
    assert.equal(db.prepare('SELECT COUNT(*) AS n FROM visit').get()?.n,1);
  }finally{await app.close();db.close();rmSync(directory,{recursive:true,force:true});}
});

test('expired proposals no longer block a new proposal and cannot be accepted', async () => {
  const db=openDb(':memory:'); const app=await buildApp(db);
  try {
    const session=(await app.inject({method:'POST',url:'/api/visits',payload:body})).json();
    const passenger=`/api/visits/${session.passengerToken}`;const driver=`/api/visits/${session.driverToken}`;
    const pending=(await app.inject({method:'POST',url:passenger+'/proposals',payload:{requestId:'old-proposal',version:1,accessPointId:'1'}})).json();
    db.prepare('UPDATE proposal SET created_at=?').run(new Date(Date.now()-11*60000).toISOString());
    const expired=(await app.inject({method:'GET',url:passenger})).json();
    assert.equal(expired.proposals[0].status,'EXPIRED');
    const answer=await app.inject({method:'POST',url:driver+`/proposals/${pending.proposals[0].id}/respond`,payload:{requestId:'expired-answer',version:2,accept:true}});
    assert.equal(answer.statusCode,409,answer.body);
    const next=await app.inject({method:'POST',url:passenger+'/proposals',payload:{requestId:'new-proposal',version:2,accessPointId:'1'}});
    assert.equal(next.statusCode,200,next.body);
    assert.equal(next.json().proposals.filter((p:{status:string})=>p.status==='PENDING').length,1);
  }finally{await app.close();db.close();}
});

test('driver can directly change a non-pickup destination while pickup still requires mutual consent', async () => {
  const db=openDb(':memory:'); const app=await buildApp(db);
  try {
    const parking=(await app.inject({method:'POST',url:'/api/visits',payload:{...body,requestId:'gallery',facilityId:'2',mode:'PARK',accessPointId:'5'}})).json();
    const changed=await app.inject({method:'PATCH',url:`/api/visits/${parking.driverToken}`,payload:{requestId:'direct-parking',version:1,accessPointId:'6'}});
    assert.equal(changed.statusCode,200,changed.body);
    assert.equal(changed.json().visit.confirmedAccessPointId,'6');
    const wrongFacility=await app.inject({method:'PATCH',url:`/api/visits/${parking.driverToken}`,payload:{requestId:'direct-wrong',version:2,accessPointId:'4'}});
    assert.equal(wrongFacility.statusCode,400,wrongFacility.body);
    const passenger=await app.inject({method:'PATCH',url:`/api/visits/${parking.passengerToken}`,payload:{requestId:'direct-passenger',version:2,accessPointId:'5'}});
    assert.equal(passenger.statusCode,403,passenger.body);
    const pickup=(await app.inject({method:'POST',url:'/api/visits',payload:body})).json();
    const forbidden=await app.inject({method:'PATCH',url:`/api/visits/${pickup.driverToken}`,payload:{requestId:'direct-pickup',version:1,accessPointId:'1'}});
    assert.equal(forbidden.statusCode,403,forbidden.body);
    const dropoff=(await app.inject({method:'POST',url:'/api/visits',payload:{...body,requestId:'dropoff-direct',mode:'DROPOFF'}})).json();
    const dropped=await app.inject({method:'PATCH',url:`/api/visits/${dropoff.driverToken}`,payload:{requestId:'direct-dropoff',version:1,accessPointId:'1'}});
    assert.equal(dropped.statusCode,200,dropped.body);
    assert.equal(dropped.json().visit.confirmedAccessPointId,'1');
  }finally{await app.close();db.close();}
});
