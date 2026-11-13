#!/usr/bin/env node
// 차량 앱 ↔ 서버 ↔ 동승자 흐름 자동 확인 (Pleos 에뮬레이터 + 디버그 빌드)
//
// 손으로 하던 확인을 한 번에 돌린다:
//   1. 앱이 만든 픽업 방문에 동승자가 준비·나오는 중·도착을 누르면 차량이 소리로 알린다
//   2. 출발하면 내비 안내가 시작되고 서버에 '안내 중'이 남는다
//   3. 운전자가 안내를 끄면(NAVI_TEST ended) 동승자 화면은 남은 시간을 지우고, 기다리던 제안도 닫힌다
//   4. 운전자가 '말로 알리기'로 "10분 늦어"라고 하면 동승자에게 'LATE 10분'이 간다
//
// 준비: 서버 실행(npm run dev:server), 에뮬레이터 실행, 디버그 앱 설치(./gradlew assembleDebug → adb install -r --user 10 ...)
// 실행: npm run e2e:vehicle          (환경 변수: API=http://localhost:4000/api  ANDROID_USER=10  ADB=adb 경로)
// 주의: 앱에 진행 중인 방문이 있으면 덮어쓴다. 테스트로 만든 방문은 끝나면 취소한다.

import { execFileSync } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { existsSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const API = process.env.API ?? 'http://localhost:4000/api';
const USER = process.env.ANDROID_USER ?? '10';
const PKG = 'com.majung.vehicle';
const ADB = process.env.ADB ?? findAdb();

function findAdb() {
  const sdk = process.env.ANDROID_HOME ?? process.env.ANDROID_SDK_ROOT ?? (process.env.LOCALAPPDATA && join(process.env.LOCALAPPDATA, 'Android', 'Sdk'));
  const exe = sdk && join(sdk, 'platform-tools', process.platform === 'win32' ? 'adb.exe' : 'adb');
  return exe && existsSync(exe) ? exe : 'adb';
}
const adb = (...args) => execFileSync(ADB, args, { encoding: 'utf8', stdio: 'pipe' });
const shell = (cmd) => adb('shell', cmd);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const broadcast = (action, extras) => shell(`am broadcast --user ${USER} -a ${PKG}.${action} ${extras}`);

async function api(method, path, body) {
  const res = await fetch(API + path, { method, headers: body ? { 'content-type': 'application/json' } : {}, body: body ? JSON.stringify(body) : undefined });
  const text = await res.text();
  if (!res.ok) throw new Error(`${method} ${path} → ${res.status} ${text}`);
  return JSON.parse(text);
}
/** 조건이 맞을 때까지 기다린다. 맞으면 마지막 값을, 아니면 오류 */
async function until(what, fn, timeoutMs = 20_000, everyMs = 500) {
  const end = Date.now() + timeoutMs;
  let last;
  while (Date.now() < end) {
    last = await fn();
    if (last) return last;
    await sleep(everyMs);
  }
  throw new Error(`${what}: ${timeoutMs / 1000}초 안에 안 됨`);
}
const logcat = () => adb('logcat', '-d');
const count = (text, needle) => text.split(needle).length - 1;

const results = [];
async function step(name, fn) {
  const started = Date.now();
  try { await fn(); results.push([true, name]); console.log(`✔ ${name} (${((Date.now() - started) / 1000).toFixed(1)}s)`); }
  catch (e) { results.push([false, name]); console.log(`✖ ${name}\n   ${e.message}`); throw e; }
}

let session; let tmp;
const passenger = () => `/visits/${session.passengerToken}`;
const driver = () => `/visits/${session.driverToken}`;
const snap = () => api('GET', passenger());
async function patchAs(path, fields) {
  const { visit } = await api('GET', path);
  return api('PATCH', path, { requestId: randomUUID(), version: visit.version, ...fields });
}

try {
  await step('준비: 서버와 에뮬레이터, 디버그 앱', async () => {
    await api('GET', '/facilities');
    if (!adb('devices').includes('\tdevice')) throw new Error('연결된 에뮬레이터가 없어요');
    if (!shell(`pm list packages --user ${USER} ${PKG}`).includes(PKG)) throw new Error(`${PKG}가 user ${USER}에 없어요`);
    shell(`pm grant --user ${USER} ${PKG} android.permission.RECORD_AUDIO`);
  });

  await step('앱이 픽업 방문을 만든다', async () => {
    const prefs = shell(`run-as ${PKG} --user ${USER} cat shared_prefs/visit.xml`);
    const device = prefs.match(/name="device">([^<]+)</)?.[1];
    if (!device) throw new Error('앱 기기 ID를 못 읽었어요. 앱을 한 번 켠 뒤 다시 해 주세요');
    const facility = (await api('GET', '/facilities'))[0];
    const point = facility.accessPoints.find((p) => p.allowed && p.kind === 'PICKUP');
    const body = { requestId: randomUUID(), facilityId: facility.id, mode: 'PICKUP', accessPointId: point.id, driverDeviceId: device };
    // 앱은 켤 때 저장해 둔 생성 요청(pendingBody)을 다시 보낸다. 같은 requestId라 서버는 같은 방문을 돌려준다
    const xml = `<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n    <string name="device">${device}</string>\n    <string name="pendingBody">${JSON.stringify(body).replaceAll('"', '&quot;')}</string>\n</map>\n`;
    tmp = mkdtempSync(join(tmpdir(), 'majung-e2e-'));
    writeFileSync(join(tmp, 'visit.xml'), xml);
    shell(`am force-stop --user ${USER} ${PKG}`);
    adb('push', join(tmp, 'visit.xml'), '/data/local/tmp/majung-visit.xml');
    shell(`run-as ${PKG} --user ${USER} sh -c 'cat /data/local/tmp/majung-visit.xml > shared_prefs/visit.xml'`);
    shell(`am start --user ${USER} -n ${PKG}/.MainActivity`);
    await until('앱이 방문을 서버에 만들기', () => shell(`run-as ${PKG} --user ${USER} cat shared_prefs/visit.xml`).includes('name="driver"'));
    session = await api('POST', '/visits', body);
  });

  await step('동승자가 준비·나오는 중·도착을 누르면 차량이 한 번씩 소리로 알린다', async () => {
    adb('logcat', '-c');
    await patchAs(passenger(), { passengerWaitSpotId: '1' });
    for (const readiness of ['READY', 'HEADING_OUT', 'AT_LOBBY']) { await patchAs(passenger(), { passengerReadiness: readiness }); await sleep(5_000); }
    await until('세 번째 안내가 끝나기', () => count(logcat(), 'cmd(UpdateTtsDone)') >= 3, 15_000);
    const spoken = count(logcat(), 'cmd(UpdateTtsStart)');
    if (spoken !== 3) throw new Error(`소리 안내 ${spoken}번 (3번이어야 함)`);
  });

  await step('출발하면 내비 안내가 시작된다', async () => {
    await patchAs(driver(), { phase: 'MOVING' });
    await until('서버에 안내 중(set)', async () => (await snap()).vehicle?.navResult === 'set', 45_000, 1_000);
  });

  await step('안내를 끄면 동승자에게 남은 시간을 지우고 기다리던 제안도 닫는다', async () => {
    const s = await snap();
    const other = s.facility.accessPoints.find((p) => p.allowed && p.kind === 'PICKUP' && p.id !== s.visit.confirmedAccessPointId);
    await api('POST', `${passenger()}/proposals`, { requestId: randomUUID(), version: s.visit.version, accessPointId: other.id });
    await sleep(6_000); // 안내 시작 직후(5초)의 종료는 앱이 무시한다
    broadcast('NAVI_TEST', '--es event ended');
    const ended = await until('동승자 화면에 안내 종료', async () => { const n = await snap(); return n.vehicle?.navResult === 'ended' && n; }, 20_000);
    if (ended.vehicle.etaSec !== null) throw new Error(`남은 시간이 남아 있어요: ${ended.vehicle.etaSec}`);
    const p = ended.proposals[0];
    if (p.status !== 'CANCELLED' || p.cancelReason !== 'NAV_ENDED') throw new Error(`제안 상태 ${p.status}/${p.cancelReason}`);
  });

  await step("'말로 알리기'로 \"10분 늦어\"라고 하면 동승자에게 간다", async () => {
    adb('logcat', '-c');
    broadcast('VOICE_TEST', '--es start message');
    await until('묻고 듣기 시작', () => logcat().includes('ReadyToStartRecognition'), 20_000);
    broadcast('VOICE_TEST', `--es text "'10분 늦어'"`);
    const m = await until('동승자에게 알림', async () => (await snap()).driverMessage, 10_000);
    if (m.kind !== 'LATE' || m.minutes !== 10) throw new Error(`받은 알림 ${JSON.stringify(m)}`);
  });
} catch {
  // 실패한 단계는 위에 찍었다. 정리만 하고 끝낸다
} finally {
  if (session) await patchAs(driver(), { phase: 'CANCELLED' }).catch(() => {});
  if (tmp) rmSync(tmp, { recursive: true, force: true });
  const failed = results.filter(([ok]) => !ok).length;
  console.log(`\n${results.length - failed}/${results.length} 통과${session ? ' · 테스트 방문은 취소했어요' : ''}`);
  process.exitCode = failed ? 1 : 0;
}
