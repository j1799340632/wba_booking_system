// Opt-in destructive reset of THIS TOOL'S synthetic showcase only; preserves unrelated data.
if (!process.argv.includes('--live')) {
  console.log('Use --live for showcase setup/idempotency/cleanup safety and a real three-role Edge workflow. One model request; leaves a fresh showcase ready. Requires no existing showcase.');
  process.exit(0);
}
const {chromium, request} = require(process.env.WBA_PLAYWRIGHT_MODULE || 'playwright');
const {spawnSync} = require('node:child_process');
const fs = require('node:fs'), path = require('node:path'), assert = require('node:assert/strict');
const {createHash} = require('node:crypto');
const root = path.resolve(__dirname, '../..'), runtime = path.join(root, '.local/wba-demo');
const manifestPath = path.join(runtime, 'showcase/manifest.json');
const output = path.join(runtime, 'browser'), base = 'http://127.0.0.1:5178';
const docker = process.env.WBA_DOCKER_EXE || 'docker', python = process.env.WBA_PYTHON_EXE || 'python';
const student = '995000000001', mentor = '995000000002', admin = '995000000003';
const foreignUser = '995000009001', foreignBooking = '995000009002';
const checks = [], pageErrors = [];
let client, browser, token, baseline, existingEvents, foreignCreated = false, report;
const pages = [];
function sql(statement) {
  const result = spawnSync(docker, ['exec', '-i', 'wba-ai-demo-mysql-1', 'sh', '-c',
    'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --default-character-set=utf8mb4 -u wba_demo -N -B unsw_wba'],
    {input: statement, encoding: 'utf8', timeout: 20000});
  if (result.status !== 0) throw new Error('Isolated showcase SQL verification failed.');
  return result.stdout.trim();
}
function command(operation, failure = false) {
  const result = spawnSync(python, ['scripts/demo_showcase.py', operation], {cwd: root, encoding: 'utf8', timeout: 60000});
  if (failure) { assert.notEqual(result.status, 0); return result.stderr; }
  assert.equal(result.status, 0, result.stderr);
  return JSON.parse(result.stdout);
}
const ok = body => { assert.equal(body.code, 2000, body.msg || body.message); return body.data; };
const snapshot = () => JSON.stringify([
  sql(`SELECT id,creator,assessment_slot_id,start_time,end_time,status,score,feedback,completed_time,is_delete,version FROM assessment_booking WHERE creator NOT IN (${student},${foreignUser}) ORDER BY id;`),
  sql(`SELECT student_id,start_date,deadline,goals_json,version FROM student_assessment_plan WHERE student_id<>${student} ORDER BY student_id;`),
]);
async function plan() { return ok(await (await client.get(base + '/api/student/assessmentPlan?summary=true', {headers: {Authorization: `Bearer ${token}`}})).json()); }
async function login(page, email, role) {
  await new Promise(resolve => setTimeout(resolve, 1100));
  await page.goto(base);
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Password', {exact: true}).fill('WbaDemo!2026');
  const [response] = await Promise.all([page.waitForResponse(r => r.url().endsWith('/general/user/login')),
    page.getByRole('button', {name: 'Sign in to WBA Platform', exact: true}).click()]);
  const auth = ok(await response.json()).token;
  await page.waitForURL(`**/home/${role}/dashboard`);
  return auth;
}
async function dismiss(page) {
  for (let n = 0; n < 12; n++) {
    const close = page.locator('[data-sonner-toast][data-front="true"] [data-close-button]').first();
    if (!await close.isVisible()) break;
    await close.click(); await page.waitForTimeout(200);
  }
}
(async () => {
  assert.equal(fs.existsSync(manifestPath), false, 'Existing showcase must be retained. Run cleanup explicitly before verifying a fresh showcase.');
  assert.equal(sql(`SELECT COUNT(*) FROM user WHERE id=${foreignUser};`), '0');
  assert.equal(sql(`SELECT COUNT(*) FROM assessment_booking WHERE id=${foreignBooking};`), '0');
  fs.mkdirSync(output, {recursive: true});
  baseline = snapshot(); existingEvents = sql('SELECT id FROM assistant_event ORDER BY id;').split('\n').filter(Boolean);
  const initial = command('setup');
  assert.equal(initial.status, 'ready'); assert.equal(initial.counts.users, 3); assert.equal(initial.counts.slots, 6);
  assert.equal(initial.counts.bookings, 2); assert.equal(initial.counts.plans, 1); assert.equal(initial.counts.events, 0);
  const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
  assert.deepEqual(command('setup').counts, initial.counts);
  assert.deepEqual(JSON.parse(fs.readFileSync(manifestPath, 'utf8')), manifest);
  assert.equal(command('status').refreshRecommended, false);
  const guide = fs.readFileSync(initial.guide, 'utf8');
  const question = guide.match(/```text\n([^\n]+)\n/)[1];
  checks.push('setup creates three public synthetic accounts, six Sydney-relative sessions, two clearly synthetic historical bookings and personal targets; repeated setup preserves the manifest and all records; no model calls or fake observations are seeded');

  // No ownership manifest means no adoption of already occupied identities or IDs.
  const held = path.join(runtime, 'showcase/manifest-verification-held.json');
  assert.equal(fs.existsSync(held), false);
  fs.renameSync(manifestPath, held);
  try { assert.match(command('setup', true), /reserved showcase ID/); }
  finally { fs.renameSync(held, manifestPath); }
  assert.deepEqual(command('status').counts, initial.counts);
  // A changed per-run marker must refuse the entire cleanup before any row is deleted.
  sql("UPDATE assessment_slot SET location='Foreign marker - verification only' WHERE id=995000000101;");
  try { assert.match(command('cleanup', true), /foreignSlots/); assert.equal(sql(`SELECT COUNT(*) FROM user WHERE id IN (${student},${mentor},${admin});`), '3'); }
  finally { sql(`UPDATE assessment_slot SET location='Showcase ${manifest.marker}' WHERE id=995000000101;`); }
  // Simulate a concurrent change AFTER the Python preflight. The in-transaction guard must still stop every delete.
  const raced = spawnSync(python, ['-c', `
import sys
sys.path.insert(0,'scripts')
import demo_showcase as s
original=s.snapshot
def interleave(value=None):
    counts=original(value)
    if value is not None:
        s.sql("UPDATE assessment_slot SET location='Changed after preflight' WHERE id=995000000101;")
    return counts
s.snapshot=interleave
try:
    s.cleanup()
except RuntimeError as error:
    print(str(error))
    sys.exit(7)
sys.exit(0)
`], {cwd: root, encoding: 'utf8', timeout: 60000});
  try {
    assert.equal(raced.status, 7, raced.stderr);
    assert.match(raced.stdout, /final safety check/);
    assert.equal(sql(`SELECT COUNT(*) FROM user WHERE id IN (${student},${mentor},${admin});`), '3');
    assert.equal(sql(`SELECT COUNT(*) FROM assessment_booking WHERE creator=${student};`), '2');
    assert.equal(sql(`SELECT COUNT(*) FROM student_assessment_plan WHERE student_id=${student};`), '1');
  } finally { sql(`UPDATE assessment_slot SET location='Showcase ${manifest.marker}' WHERE id=995000000101;`); }
  // Cross-account use of a showcase session must preserve the other student's booking.
  sql(`START TRANSACTION; INSERT INTO user(id,role_id,email,password,name,create_time) VALUES(${foreignUser},4,'showcase-boundary-check@wba.test','not-a-login','Showcase boundary verification',NOW()); INSERT INTO assessment_booking(id,creator,assessment_slot_id,start_time,end_time,status,create_time) SELECT ${foreignBooking},${foreignUser},id,start_time,end_time,0,NOW() FROM assessment_slot WHERE id=995000000101; COMMIT;`);
  foreignCreated = true;
  assert.match(command('cleanup', true), /foreignBookings/);
  assert.equal(sql(`SELECT COUNT(*) FROM assessment_booking WHERE id=${foreignBooking} AND creator=${foreignUser};`), '1');
  sql(`DELETE FROM assessment_booking WHERE id=${foreignBooking} AND creator=${foreignUser}; DELETE FROM user WHERE id=${foreignUser} AND email='showcase-boundary-check@wba.test';`);
  foreignCreated = false;
  assert.deepEqual(command('status').counts, initial.counts);
  checks.push('occupied identities cannot be adopted when the manifest is absent; changed slot markers block all cleanup, including a change injected after preflight that is caught inside the transaction; another student booking a showcase session blocks cleanup and preserves that booking; guard failures retain the original showcase');

  client = await request.newContext();
  browser = await chromium.launch({headless: true, channel: 'msedge'});
  for (const zone of ['Asia/Shanghai', 'America/Los_Angeles', 'Australia/Sydney']) {
    const context = await browser.newContext({viewport: {width: 1440, height: 1100}, timezoneId: zone});
    const page = await context.newPage(); page.setDefaultTimeout(35000); page.on('pageerror', error => pageErrors.push(error.message)); pages.push(page);
  }
  const [studentPage, mentorPage, adminPage] = pages;
  token = await login(studentPage, initial.accounts.student, 'student');
  await studentPage.goto(base + '/home/student/assessment-plan');
  await studentPage.getByRole('region', {name: 'Plan summary'}).getByText('1/5', {exact: true}).waitFor();
  const before = await plan(); assert.equal(before.completed, 1); assert.equal(before.remaining, 4); assert.equal(before.toRequest, 4);
  await studentPage.screenshot({path: path.join(output, 'showcase-plan-initial.png'), animations: 'disabled'});
  await studentPage.goto(base + '/home/student/bookSlot');
  await studentPage.locator('#booking-assistant-message').fill(question);
  const assistant = studentPage.getByRole('region', {name: 'AI booking assistant'});
  const [searchResponse] = await Promise.all([studentPage.waitForResponse(r => r.url().endsWith('/bookingAssistant/search')),
    assistant.getByRole('button', {name: 'Send request', exact: true}).click()]);
  const found = ok(await searchResponse.json()); assert.equal(found.status, 'READY');
  assert.equal(String(found.candidates[0].slotId), '995000000101');
  await assistant.getByRole('button', {name: 'Review session', exact: true}).first().click();
  let dialog = studentPage.getByRole('dialog', {name: 'Review booking request', exact: true});
  await dialog.waitFor(); assert.equal(await dialog.getByRole('button', {name: 'Confirm booking request', exact: true}).isDisabled(), true);
  assert.equal(sql(`SELECT COUNT(*) FROM assessment_booking WHERE creator=${student};`), '2');
  await dialog.getByRole('checkbox').check();
  const [saveResponse] = await Promise.all([studentPage.waitForResponse(r => r.url().endsWith('/bookingAssistant/confirm')),
    dialog.getByRole('button', {name: 'Confirm booking request', exact: true}).click()]);
  const receipt = ok(await saveResponse.json()); const bookingId = String(receipt.bookingId);
  assert.equal(sql(`SELECT status FROM assessment_booking WHERE id=${bookingId} AND creator=${student};`), '0');
  assert.equal(command('setup').counts.bookings, 3);
  assert.equal(sql(`SELECT status FROM assessment_booking WHERE id=${bookingId};`), '0');
  checks.push('generated query works with one real model request; real student UI shows 1/5 completed, requires explicit confirmation and saves one Pending booking; setup after that write preserves the new booking instead of resetting the demonstration');

  await login(mentorPage, initial.accounts.mentor, 'supervisor');
  await mentorPage.goto(base + '/home/supervisor/upcoming-sessions');
  const card = id => mentorPage.getByRole('article', {name: `Assessment booking ${id}`, exact: true});
  await dismiss(mentorPage); await card(bookingId).getByRole('button', {name: 'Approve', exact: true}).click();
  dialog = mentorPage.getByRole('dialog', {name: 'Approve booking', exact: true});
  await dialog.getByRole('checkbox').check(); await dialog.getByRole('button', {name: 'Confirm approve', exact: true}).click();
  await mentorPage.getByText('Booking approved', {exact: true}).waitFor();
  assert.equal((await plan()).completed, 1); assert.equal((await plan()).confirmed, 1);
  await studentPage.goto(base + '/home/student/assessment-plan');
  await studentPage.getByRole('region', {name: 'Plan summary'}).getByText('1/5', {exact: true}).waitFor();
  await dismiss(mentorPage); await card('995000001001').getByRole('button', {name: 'Complete', exact: true}).click();
  dialog = mentorPage.getByRole('dialog', {name: 'Complete assessment', exact: true});
  await dialog.getByLabel('Score (0–100)', {exact: true}).fill('85');
  await dialog.getByLabel('Feedback', {exact: true}).fill('Synthetic demo: observed assessment completed.');
  await dialog.getByRole('checkbox').check(); await dialog.getByRole('button', {name: 'Confirm complete', exact: true}).click();
  await mentorPage.getByText('Assessment completed', {exact: true}).waitFor();
  await dismiss(studentPage);
  const [refreshed] = await Promise.all([studentPage.waitForResponse(r => r.url().includes('/student/assessmentPlan?summary=false')),
    studentPage.getByRole('button', {name: 'Refresh', exact: true}).click()]);
  assert.equal(ok(await refreshed.json()).completed, 2);
  await studentPage.getByRole('region', {name: 'Plan summary'}).getByText('2/5', {exact: true}).waitFor();
  const after = await plan(); assert.equal(after.completed, 2); assert.equal(after.remaining, 3); assert.equal(after.confirmed, 1);
  await studentPage.screenshot({path: path.join(output, 'showcase-plan-after-completion.png'), animations: 'disabled'});
  checks.push('mentor approves the newly requested future session while completion stays 1; separately completes the clearly labelled historical confirmed fixture; student sees 2/5 completed and remaining 3, with future confirmation still provisional in different browser time zones');

  await login(adminPage, initial.accounts.admin, 'admin');
  await adminPage.getByRole('button', {name: 'AI Insights', exact: true}).click();
  await adminPage.getByRole('heading', {name: 'AI Insights', exact: true}).waitFor();
  await adminPage.getByRole('region', {name: 'Observed assistant metrics'}).waitFor();
  assert.equal(sql(`SELECT COUNT(*) FROM assistant_event WHERE actor_id=${student};`), '3');
  assert.equal(sql(`SELECT SUM(model_calls) FROM assistant_event WHERE actor_id=${student};`), '1');
  assert.equal(sql(`SELECT COUNT(*) FROM assistant_event WHERE actor_id=${student} AND stage='CONFIRM_BOOKING' AND outcome='SAVED';`), '1');
  await adminPage.screenshot({path: path.join(output, 'showcase-insights.png'), animations: 'disabled'});
  assert.deepEqual(pageErrors, []); assert.equal(snapshot(), baseline);
  // Close all account contexts before transactional cleanup; leave the user a fresh showcase.
  await browser.close(); browser = null; await client.dispose(); client = null;
  assert.equal(command('cleanup').status, 'cleaned'); assert.equal(command('status').status, 'not-configured');
  assert.equal(fs.existsSync(manifestPath), false);
  assert.equal(snapshot(), baseline);
  if (existingEvents.length) assert.equal(Number(sql(`SELECT COUNT(*) FROM assistant_event WHERE id IN (${existingEvents.join(',')});`)), existingEvents.length);
  const fresh = command('setup'); assert.deepEqual(fresh.counts, initial.counts);
  assert.notEqual(JSON.parse(fs.readFileSync(manifestPath, 'utf8')).marker, manifest.marker);
  assert.equal(snapshot(), baseline);
  checks.push('admin sees the real runtime report; only search/review/confirmation produce three observations and one model call; full cleanup removes only owned records and observations; existing bookings, goals and event IDs survive; new setup leaves a fresh ready showcase with 0 seeded events');
  report = {completedAt: new Date().toISOString(), scope: 'synthetic showcase; actual Edge, one model request, Spring, isolated MySQL/Redis',
    checks, pageErrors, providerCalls: 1, existingDataPreserved: true, baselineBookingPlanSha256: createHash('sha256').update(baseline).digest('hex'),
    leftFreshShowcase: true, counts: fresh.counts, githubPushes: 0};
  fs.mkdirSync(output, {recursive: true});
  fs.writeFileSync(path.join(output, 'showcase-live-report.json'), JSON.stringify(report, null, 2));
  console.log(JSON.stringify({passed: true, checks, pageErrors, leftFreshShowcase: true}));
})().catch(async error => {
  console.error(error.stack); process.exitCode = 1;
  for (let index = 0; index < pages.length; index++) {
    if (!pages[index].isClosed()) {
      await pages[index].screenshot({path: path.join(output, `showcase-failure-${index}.png`), animations: 'disabled'}).catch(() => {});
      console.error(JSON.stringify({page: index, url: pages[index].url(), pageErrors}));
    }
  }
}).finally(async () => {
  if (foreignCreated) {
    if (sql(`SELECT COUNT(*) FROM user WHERE id=${foreignUser} AND email='showcase-boundary-check@wba.test' AND name='Showcase boundary verification';`) === '1') {
      sql(`DELETE FROM assessment_booking WHERE id=${foreignBooking} AND creator=${foreignUser}; DELETE FROM user WHERE id=${foreignUser} AND email='showcase-boundary-check@wba.test';`);
    }
  }
  if (browser) await browser.close(); if (client) await client.dispose();
});
