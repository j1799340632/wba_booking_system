if (!process.argv.includes('--live')) {
  console.log('Use --live to verify the real model and isolated demo booking writes. Test fixtures are cleaned afterward.');
  process.exit(0);
}
const path = require('node:path');
const { chromium, request } = require(process.env.WBA_PLAYWRIGHT_MODULE || 'playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const { spawnSync } = require('node:child_process');
const crypto = require('node:crypto');
const output = path.resolve(__dirname, '../../.local/wba-demo/browser');
fs.mkdirSync(output, { recursive: true });
const docker = process.env.WBA_DOCKER_EXE || 'docker';
const runKey = 'e2e_' + crypto.randomUUID();
function sql(query) {
  const r = spawnSync(docker, ['exec', '-i', 'wba-ai-demo-mysql-1', 'sh', '-c', 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -u wba_demo -N -B unsw_wba'], { input: query, encoding: 'utf8', timeout: 10000 });
  if (r.status !== 0) throw new Error('Isolated demo SQL verification failed.');
  return r.stdout.trim();
}
function count(query) { return Number(sql(query)); }
let browser, client, token, secondToken, draftId, savedId;
const created = new Set();
const createdFixtureIds = [];
let createdFixtureUser = false;
const fixtureIds = ['930000000101', '930000000102', '930000000103', '930000000104', '930000000105', '930000000106'];
const failures = [], checks = [];
async function api(path, data, auth = token, method = 'post') {
  const response = await client[method]('http://127.0.0.1:5178/api' + path, { data, headers: { Authorization: `Bearer ${auth}` } });
  const body = await response.json();
  if (path.endsWith('/bookings') && body.code === 2000) created.add(String(body.data.bookingId));
  return body;
}
function cleanup() {
  for (const id of sql(`SELECT id FROM assessment_booking WHERE request_key LIKE '${runKey}%'`).split('\n').filter(Boolean)) created.add(id);
  if (savedId) created.add(String(savedId));
  for (const id of created) {
    assert.match(id, /^\d+$/);
    sql(`DELETE FROM message WHERE content LIKE '%Reference: ${id}.%' OR content LIKE '%Reference: ${id} is%' OR content LIKE '%Reference: ${id}. %'; DELETE FROM assessment_booking WHERE id=${id};`);
  }
  // Restore only the real demo candidate used by this test, only if no other active booking remains.
  if (savedId) sql('UPDATE assessment_slot s SET status=0 WHERE s.id=910000003 AND s.status=1 AND NOT EXISTS (SELECT 1 FROM assessment_booking b WHERE b.assessment_slot_id=s.id AND b.is_delete=0 AND b.status IN (0,1));');
  if (createdFixtureIds.length) sql(`DELETE FROM assessment_slot WHERE id IN (${createdFixtureIds.join(',')});`);
  if (createdFixtureUser) sql("DELETE FROM user_settings WHERE user_id=930000000001; DELETE FROM user WHERE id=930000000001 AND email='race-e2e@wba.test';");
}
(async () => {
  assert.equal(count('SELECT COUNT(*) FROM assessment_slot WHERE id IN (' + fixtureIds.join(',') + ');'), 0);
  assert.equal(count('SELECT COUNT(*) FROM user WHERE id=930000000001;'), 0);
  browser = await chromium.launch({ headless: true, channel: 'msedge' });
  const page = await browser.newPage({ viewport: { width: 1440, height: 1120 }, timezoneId: 'Asia/Shanghai' });
  page.setDefaultTimeout(35000); client = await request.newContext();
  page.on('pageerror', error => failures.push(error.message));
  await page.goto('http://127.0.0.1:5178/');
  await page.getByLabel('Email address').fill('student@wba.test');
  await page.getByLabel('Password', { exact: true }).fill('WbaDemo!2026');
  const [loginResponse] = await Promise.all([
    page.waitForResponse(r => r.url().endsWith('/general/user/login')),
    page.getByRole('button', { name: 'Sign in to WBA Platform', exact: true }).click(),
  ]);
  const login = await loginResponse.json(); assert.equal(login.code, 2000); token = login.data.token;
  await page.waitForURL('**/home/student/dashboard');
  await page.goto('http://127.0.0.1:5178/home/student/bookSlot');
  const region = page.getByRole('region', { name: 'AI booking assistant' });
  const originalTuesday = sql("SELECT DATE_ADD(next_monday, INTERVAL 1 DAY) FROM demo_metadata WHERE id=1;");
  await page.locator('#booking-assistant-message').fill(`帮我预约${originalTuesday}下午的 Mini-CEX，优先 Dr Chen，其他导师也可以`);
  const [searchResponse] = await Promise.all([
    page.waitForResponse(r => r.url().endsWith('/bookingAssistant/search')),
    region.getByRole('button', { name: 'Send request', exact: true }).click(),
  ]);
  const search = await searchResponse.json(); assert.equal(search.data.status, 'READY');
  assert.equal(search.data.candidates[0].slotId, '910000003');
  const beforeBooking = count('SELECT COUNT(*) FROM assessment_booking;');
  const [prepareResponse] = await Promise.all([
    page.waitForResponse(r => r.url().endsWith('/bookingAssistant/prepare')),
    region.getByRole('button', { name: 'Review session', exact: true }).first().click(),
  ]);
  const prepared = await prepareResponse.json(); assert.equal(prepared.code, 2000); draftId = prepared.data.draftId;
  const dialog = page.getByRole('dialog', { name: 'Review booking request' });
  await dialog.waitFor();
  assert.equal(await dialog.evaluate(element => getComputedStyle(element).color), 'rgb(23, 43, 58)');
  const confirmButton = dialog.getByRole('button', { name: 'Confirm booking request', exact: true });
  assert.equal(await confirmButton.isDisabled(), true);
  assert.equal(count('SELECT COUNT(*) FROM assessment_booking;'), beforeBooking);
  assert.notEqual((await api('/student/bookingAssistant/confirm', { draftId, confirmed: false })).code, 2000);
  checks.push('new booking language only searches; review performs no write; explicit checkbox and confirmed=true required');
  await page.screenshot({ path: `${output}/booking-agent-review-desktop.png`, fullPage: true });
  await page.setViewportSize({ width: 390, height: 1000 });
  await page.screenshot({ path: `${output}/booking-agent-review-mobile.png`, fullPage: true });
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
  await page.setViewportSize({ width: 1440, height: 1120 });

  // Forward a real confirmation, then deliberately lose its HTTP response.
  let dropped = false;
  await page.route('**/api/student/bookingAssistant/confirm', async route => {
    const response = await route.fetch();
    const body = await response.json();
    assert.equal(body.code, 2000); savedId = String(body.data.bookingId); created.add(savedId); dropped = true;
    await route.abort('connectionreset');
  });
  await dialog.getByRole('checkbox').check();
  await confirmButton.click();
  await dialog.getByRole('alert').waitFor();
  assert.equal(dropped, true);
  assert.equal(count(`SELECT COUNT(*) FROM assessment_booking WHERE request_key='ai_${draftId}';`), 1);
  assert.equal(count(`SELECT COUNT(*) FROM message WHERE content LIKE '%Reference: ${savedId}%';`), 2);
  await page.unroute('**/api/student/bookingAssistant/confirm');
  const [retryResponse] = await Promise.all([
    page.waitForResponse(r => r.url().endsWith('/bookingAssistant/confirm')),
    confirmButton.click(),
  ]);
  const retry = await retryResponse.json();
  assert.equal(String(retry.data.bookingId), savedId); assert.equal(retry.data.replayed, true); assert.equal(retry.data.status, 0);
  await region.getByText('Request saved — awaiting supervisor approval', { exact: true }).waitFor();
  assert.equal(count(`SELECT COUNT(*) FROM message WHERE content LIKE '%Reference: ${savedId}%';`), 2);
  const closeToast = page.getByRole('button', { name: 'Close toast' });
  if (await closeToast.count()) await closeToast.first().click({ timeout: 1000 }).catch(() => {});
  await page.screenshot({ path: `${output}/booking-agent-receipt.png`, fullPage: true });
  checks.push('lost response retry returns the same Pending booking; one row and exactly two durable notices');
  const myBookings = await api('/student/assessmentBooking/bookings/my?pageNum=1&pageQuantity=10', undefined, token, 'get');
  assert.ok(myBookings.data.records.some(row => String(row.bookingId) === savedId && row.status === 0));
  checks.push('My Bookings contains the actual requested occurrence and Pending status');

  sql("INSERT INTO user (id,role_id,email,password,name,is_enable,is_canceled,is_delete,create_time) SELECT 930000000001,4,'race-e2e@wba.test',password,'Demo Race Student',1,0,0,NOW() FROM user WHERE id=900000001;");
  createdFixtureUser = true;
  const raceLoginResponse = await client.post('http://127.0.0.1:5178/api/general/user/login', { data: { email: 'race-e2e@wba.test', password: 'WbaDemo!2026' } });
  const raceLogin = await raceLoginResponse.json(); assert.equal(raceLogin.code, 2000); secondToken = raceLogin.data.token;
  assert.notEqual((await api('/student/bookingAssistant/confirm', { draftId, confirmed: true }, secondToken)).code, 2000);
  checks.push('another student cannot reuse the review or access its idempotent receipt');

  const monday = sql('SELECT next_monday FROM demo_metadata WHERE id=1;');
  function stamp(hour, minute = 0) {
    const date = new Date(monday + 'T00:00:00Z'); date.setUTCDate(date.getUTCDate() + 1);
    return date.toISOString().slice(0, 10) + ' ' + String(hour).padStart(2, '0') + ':' + String(minute).padStart(2, '0') + ':00';
  }
  const specs = [[fixtureIds[0],900000002,18,0],[fixtureIds[1],900000002,18,15],[fixtureIds[2],900000003,18,15],
    [fixtureIds[3],900000002,19,0],[fixtureIds[4],900000002,19,0],[fixtureIds[5],900000003,20,0]];
  for (const [id,mentor,hour,minute] of specs) {
    sql(`INSERT INTO assessment_slot (id,start_time,end_time,repeat_type,next_available_time,hospital,department,location,type,status,creator,create_time,is_enable,is_delete,version) VALUES (${id},'${stamp(hour,minute)}',DATE_ADD('${stamp(hour,minute)}',INTERVAL 30 MINUTE),0,'${stamp(hour,minute)}','Prince of Wales Hospital','General Medicine','E2E Only','Mini-CEX',0,${mentor},NOW(),1,0,0);`);
    createdFixtureIds.push(id);
  }
  const payload = (id, start, suffix) => ({ assessmentSlotId: id, slotVersion: 0, startTime: start,
    endTime: sql(`SELECT end_time FROM assessment_slot WHERE id=${id};`), requestKey: runKey + suffix });
  const same = payload(fixtureIds[0], stamp(18), '_same');
  const parallel = await Promise.all(Array.from({ length: 4 }, () => api('/student/assessmentBooking/bookings', same)));
  assert.ok(parallel.every(item => item.code === 2000));
  assert.equal(new Set(parallel.map(item => item.data.bookingId)).size, 1);
  const identity = String(parallel[0].data.bookingId);
  assert.equal(count(`SELECT COUNT(*) FROM message WHERE content LIKE '%Reference: ${identity}%';`), 2);
  const changed = await api('/student/assessmentBooking/bookings', { ...same, endTime: stamp(19) });
  assert.notEqual(changed.code, 2000);
  checks.push('four parallel identical submissions create one booking; a reused key with changed payload is rejected');
  assert.notEqual((await api('/student/assessmentBooking/bookings', payload(fixtureIds[1],stamp(18,15),'_mentor'), secondToken)).code,2000);
  assert.notEqual((await api('/student/assessmentBooking/bookings', payload(fixtureIds[2],stamp(18,15),'_student'))).code,2000);
  checks.push('supervisor overlap across different templates and student overlap across different supervisors are rejected');
  const competing = await Promise.all([
    api('/student/assessmentBooking/bookings',payload(fixtureIds[3],stamp(19),'_race1')),
    api('/student/assessmentBooking/bookings',payload(fixtureIds[4],stamp(19),'_race2'),secondToken),
  ]);
  assert.equal(competing.filter(item => item.code === 2000).length,1);
  checks.push('real MySQL locks give one winner when two students choose overlapping templates of one supervisor');

  const selection = payload(fixtureIds[5],stamp(20),'_review'); delete selection.requestKey;
  const stale = await api('/student/bookingAssistant/prepare',selection); assert.equal(stale.code,2000);
  sql(`UPDATE assessment_slot SET location='Changed after review' WHERE id=${fixtureIds[5]};`);
  assert.notEqual((await api('/student/bookingAssistant/confirm',{draftId:stale.data.draftId,confirmed:true})).code,2000);
  sql(`UPDATE assessment_slot SET location='E2E Only' WHERE id=${fixtureIds[5]};`);
  const expires = await api('/student/bookingAssistant/prepare',selection); assert.equal(expires.code,2000);
  const redisKey = 'booking:review:900000001:' + expires.data.draftId;
  const deleted = spawnSync(docker, ['exec','wba-ai-demo-redis-1','sh','-c','REDISCLI_AUTH="$REDIS_PASSWORD" exec redis-cli DEL "$1"','sh',redisKey],{encoding:'utf8',timeout:10000});
  assert.equal(deleted.status,0);
  assert.notEqual((await api('/student/bookingAssistant/confirm',{draftId:expires.data.draftId,confirmed:true})).code,2000);
  assert.notEqual((await api('/student/bookingAssistant/prepare',{...selection,slotVersion:999})).code,2000);
  assert.notEqual((await api('/student/bookingAssistant/prepare',{...selection,startTime:stamp(20,1)})).code,2000);
  checks.push('expired reviews, silent location changes, stale versions and forged occurrence times fail without writes');
  assert.deepEqual(failures,[]);
  const report = {date:'2026-10-23',scope:'real browser + DeepSeek + Spring + isolated MySQL/Redis; controlled HTTP response loss',
    checks,pageErrors:failures,notificationFailureUnitTest:true,createdBookingRows:created.size,
    originalDeploymentModified:false,githubPushes:0,cleanup:'only exact test booking IDs, notices and dedicated fixtures'};
  fs.writeFileSync(`${output}/booking-agent-live-report.json`,JSON.stringify(report,null,2));
  console.log(JSON.stringify({passed:true,checks,pageErrors:failures,createdBookingRows:created.size}));
})().catch(error => { console.error(error.stack); process.exitCode=1; }).finally(async()=>{
  try { cleanup(); } catch(error) { console.error('Test cleanup failed:',error.message); process.exitCode=1; }
  if(client) await client.dispose(); if(browser) await browser.close();
});
