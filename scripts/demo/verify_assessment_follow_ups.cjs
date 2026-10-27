// Opt-in integration check. Dedicated fixtures only; no emails, reminders, model calls, or ordinary demo data edits.
if (!process.argv.includes('--live')) {
  console.log('Use --live to verify assessment follow-ups with Edge and the isolated demo. Collision-checked fixtures are cleaned afterward.');
  process.exit(0);
}
const {chromium,request}=require(process.env.WBA_PLAYWRIGHT_MODULE || 'playwright');
const {spawnSync}=require('node:child_process');
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const docker=process.env.WBA_DOCKER_EXE || 'docker';
const output=path.resolve(__dirname,'../../.local/wba-demo/browser');fs.mkdirSync(output,{recursive:true});
const users={student:'970000000001',other:'970000000002',mentor:'970000000003'};
const slotIds=Array.from({length:15},(_,i)=>String(970000000101+i));
const bookingIds=Array.from({length:15},(_,i)=>String(970000001001+i));
const createdUsers=[],createdSlots=[],checks=[],pageErrors=[];
let client,browser,token,baseline,report;
function sql(statement) {
  const r=spawnSync(docker,['exec','-i','wba-ai-demo-mysql-1','sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -u wba_demo -N -B unsw_wba'],{input:statement,encoding:'utf8',timeout:15000});
  if(r.status!==0) throw new Error('Isolated follow-up SQL verification failed.');
  return r.stdout.trim();
}
const count=q=>Number(sql(q));
const ok=body=>{assert.equal(body.code,2000,body.msg || body.message);return body.data;};
async function api(endpoint,data,auth=token,method='get') {
  return (await client[method]('http://127.0.0.1:5178/api'+endpoint,{data,headers:{Authorization:`Bearer ${auth}`}})).json();
}
async function login(email) {
  for(let n=0;n<3;n++) {
    const body=await(await client.post('http://127.0.0.1:5178/api/general/user/login',{data:{email,password:'WbaDemo!2026'}})).json();
    if(body.code!==5011) return ok(body).token;
    await new Promise(resolve=>setTimeout(resolve,1100));
  }
  throw new Error('Login remained rate limited.');
}
const snapshot=()=>JSON.stringify([
  sql(`SELECT id,creator,assessment_slot_id,start_time,end_time,status,completed_time,is_delete,version FROM assessment_booking WHERE creator NOT IN (${users.student},${users.other}) ORDER BY id;`),
  sql(`SELECT student_id,start_date,deadline,goals_json,version FROM student_assessment_plan WHERE student_id NOT IN (${users.student},${users.other}) ORDER BY student_id;`),
]);
const ownSnapshot=()=>JSON.stringify([
  sql(`SELECT id,status,completed_time,version FROM assessment_booking WHERE creator IN (${users.student},${users.other}) ORDER BY id;`),
  sql(`SELECT student_id,start_date,deadline,goals_json,version FROM student_assessment_plan WHERE student_id IN (${users.student},${users.other}) ORDER BY student_id;`),
  sql(`SELECT COUNT(*) FROM message WHERE creator IN (${Object.values(users).join(',')}) OR receiver IN (${Object.values(users).join(',')});`),
]);
function cleanup() {
  for(const owner of createdUsers.filter(id=>id===users.student || id===users.other)) {
    sql(`DELETE FROM assessment_booking WHERE creator=${owner}; DELETE FROM student_assessment_plan WHERE student_id=${owner};`);
  }
  if(createdSlots.length) sql(`DELETE FROM assessment_slot WHERE id IN (${createdSlots.join(',')});`);
  for(const id of createdUsers) sql(`DELETE FROM user_settings WHERE user_id=${id}; DELETE FROM user WHERE id=${id};`);
}
(async()=>{
  assert.equal(count(`SELECT COUNT(*) FROM user WHERE id IN (${Object.values(users).join(',')}) OR email LIKE 'followup-%@wba.test';`),0);
  assert.equal(count(`SELECT COUNT(*) FROM assessment_slot WHERE id IN (${slotIds.join(',')});`),0);
  assert.equal(count(`SELECT COUNT(*) FROM assessment_booking WHERE id IN (${bookingIds.join(',')});`),0);
  assert.equal(count(`SELECT COUNT(*) FROM student_assessment_plan WHERE student_id IN (${users.student},${users.other});`),0);
  baseline=snapshot();
  for(const [kind,id] of Object.entries(users)) {
    sql(`INSERT INTO user (id,role_id,email,password,name,is_enable,is_canceled,is_delete,create_time) SELECT ${id},${kind==='mentor'?3:4},'followup-${kind}@wba.test',password,'${kind==='mentor'?'Dr Follow-up Alpha':'Follow-up '+kind}',1,0,0,NOW() FROM user WHERE id=900000001;`);
    createdUsers.push(id);
  }
  client=await request.newContext();token=await login('followup-student@wba.test');
  const initial=ok(await api('/student/assessmentPlan'));
  assert.equal(initial.configured,false);assert.equal(initial.followUpTotal,0);
  assert.match(initial.generatedAt,/^\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}:\d{2}/);
  const now=initial.generatedAt.slice(0,19).replace('T',' ');
  const shift=days=>{const d=new Date(initial.today+'T00:00:00Z');d.setUTCDate(d.getUTCDate()+days);return d.toISOString().slice(0,10);};
  const mini=sql("SELECT id FROM wba_type WHERE name='Mini-CEX' AND is_enable=1 AND is_delete=0;");assert.match(mini,/^\d+$/);
  // [minutes from server's Sydney wall clock, status, owner, type]. Templates deliberately use a different date.
  const specs=[[-2880,0],[-1440,1],[-10,1],[1440,1],[2160,0],[5760,1],[-4320,2],[720,0,users.other],[720,4],[720,3],[960,0,users.student,'DOP'],[12960,0],[2880,0],[2880,0],[-12960,0]];
  for(let i=0;i<specs.length;i++) {
    const [minutes,status,owner=users.student,type='Mini-CEX']=specs[i];
    sql(`INSERT INTO assessment_slot (id,start_time,end_time,repeat_type,next_available_time,hospital,department,location,type,status,creator,create_time,is_enable,is_delete,version) VALUES (${slotIds[i]},DATE_ADD('${now}',INTERVAL 5 DAY),DATE_ADD('${now}',INTERVAL 7230 MINUTE),0,DATE_ADD('${now}',INTERVAL 5 DAY),'Prince of Wales Hospital','General Medicine','Follow-up E2E Only','${type}',1,${users.mentor},NOW(),1,0,0);`);createdSlots.push(slotIds[i]);
    sql(`INSERT INTO assessment_booking (id,creator,assessment_slot_id,start_time,end_time,status,completed_time,create_time,is_delete,version) VALUES (${bookingIds[i]},${owner},${slotIds[i]},DATE_ADD('${now}',INTERVAL ${minutes} MINUTE),DATE_ADD('${now}',INTERVAL ${minutes+30} MINUTE),${status},${status===2?`DATE_SUB('${now}',INTERVAL 2 DAY)`:'NULL'},NOW(),0,0);`);
  }
  sql(`UPDATE assessment_slot SET is_delete=1 WHERE id=${slotIds[1]}; UPDATE assessment_booking SET status=2,completed_time=NULL WHERE id=${bookingIds[12]}; UPDATE assessment_booking SET is_delete=1 WHERE id=${bookingIds[13]};`);
  const goalPayload={startDate:shift(-7),deadline:shift(6),expectedVersion:0,goals:[{typeId:mini,target:12}]};
  assert.equal(ok(await api('/student/assessmentPlan',goalPayload,token,'post')),1);
  let plan=ok(await api('/student/assessmentPlan'));
  assert.equal(plan.daysToDeadline,6);assert.equal(plan.followUpTotal,6);assert.equal(plan.completed,1);assert.equal(plan.inProgress,1);assert.equal(plan.confirmed,2);assert.equal(plan.pending,1);assert.equal(plan.toRequest,7);assert.equal(plan.uncountedRecords,1);
  assert.deepEqual(plan.followUps.map(i=>i.bookingId),bookingIds.slice(0,6));
  assert.deepEqual(plan.followUps.map(i=>i.kind),['UNAPPROVED_SESSION','AWAITING_COMPLETION','IN_PROGRESS','UPCOMING','PENDING_APPROVAL','SCHEDULED']);
  assert.deepEqual(plan.followUpCounts,{needsAttention:2,awaitingApproval:1,inProgress:1,upcoming:1,scheduled:1});
  const readBaseline=ownSnapshot();
  browser=await chromium.launch({headless:true,channel:'msedge'});
  const page=await browser.newPage({viewport:{width:1440,height:1100},timezoneId:'America/Los_Angeles'});page.setDefaultTimeout(25000);page.on('pageerror',e=>pageErrors.push(e.message));
  // UI login is separate from the API context and exercises normal authentication.
  await new Promise(resolve=>setTimeout(resolve,1100));await page.goto('http://127.0.0.1:5178/');
  await page.getByLabel('Email address').fill('followup-student@wba.test');await page.getByLabel('Password',{exact:true}).fill('WbaDemo!2026');
  await page.getByRole('button',{name:'Sign in to WBA Platform',exact:true}).click();await page.waitForURL('**/home/student/dashboard');
  await page.getByText(/6 calendar day\(s\) until the deadline · 2 booking status check/).waitFor();
  await page.getByRole('button',{name:'View plan',exact:true}).click();
  let region=page.getByRole('region',{name:'Assessment follow-ups'});await region.getByRole('heading',{name:'Your next actions',exact:true}).waitFor();
  assert.equal(await region.getByRole('article').count(),4);
  await region.getByRole('button',{name:'Show all 6 actions',exact:true}).click();assert.equal(await region.getByRole('article').count(),6);
  assert.equal(await region.getByRole('article').first().innerText().then(t=>t.includes(bookingIds[0])),true);
  const expectedStart=plan.followUps[0].startTime;
  assert.ok((await region.getByRole('article').first().innerText()).includes(expectedStart.slice(11,16)), 'Browser timezone shifted the Sydney session time.');
  await page.waitForFunction(()=>document.documentElement.scrollWidth<=innerWidth);
  await region.evaluate(el=>el.scrollIntoView({block:'start'}));await page.screenshot({path:`${output}/assessment-follow-ups-desktop.png`,fullPage:true,animations:'disabled'});
  await page.setViewportSize({width:390,height:1000});await page.waitForFunction(()=>document.documentElement.scrollWidth<=innerWidth);
  await region.evaluate(el=>el.scrollIntoView({block:'start'}));await page.screenshot({path:`${output}/assessment-follow-ups-mobile.png`,fullPage:true,animations:'disabled'});await page.setViewportSize({width:1440,height:1100});
  checks.push('saved plan shows six correct owner-scoped actions using actual occurrence times; canceled, declined, deleted, out-of-period, unrelated-type and other-student records are excluded; deadline, ongoing coverage and Sydney times stay correct in a Los Angeles browser');
  await region.getByRole('article').nth(1).getByRole('link',{name:'View booking',exact:true}).click();
  await page.waitForURL(`**/home/student/my-bookings?booking=${bookingIds[1]}`);
  await page.locator('.my-bookings-focused-card').waitFor();assert.equal(await page.locator('.my-bookings-focused-card').getAttribute('aria-current'),'true');
  assert.ok((await page.locator('.my-bookings-focused-card').innerText()).includes(bookingIds[1]));
  assert.equal(await page.evaluate(()=>document.activeElement?.classList.contains('my-bookings-focused-card')),true);
  assert.equal(ownSnapshot(),readBaseline,'Reading actions or following a link changed records or sent a message.');
  checks.push('View booking opens and focuses the exact owned record even after its slot template was archived; reading, expanding and navigation do not change statuses, targets, completion or messages');
  // Simulate independent supervisor decisions using fixture-only SQL, then verify the student's explicit refresh.
  sql(`UPDATE assessment_booking SET status=1 WHERE id=${bookingIds[4]} AND creator=${users.student}; UPDATE assessment_booking SET status=2,completed_time='${now}' WHERE id=${bookingIds[1]} AND creator=${users.student}; UPDATE assessment_booking SET status=4 WHERE id=${bookingIds[0]} AND creator=${users.student};`);
  await page.goto('http://127.0.0.1:5178/home/student/assessment-plan');region=page.getByRole('region',{name:'Assessment follow-ups'});
  await region.getByRole('heading',{name:'Your next actions',exact:true}).waitFor();
  plan=ok(await api('/student/assessmentPlan'));assert.equal(plan.followUpTotal,4);assert.equal(plan.completed,2);assert.equal(plan.followUpCounts.needsAttention,0);assert.equal(plan.followUpCounts.awaitingApproval,0);assert.equal(plan.followUpCounts.upcoming,2);
  assert.equal(await region.getByRole('article').count(),4);assert.equal(await region.getByText('Scheduled end passed — check completion',{exact:true}).count(),0);
  sql(`UPDATE assessment_booking SET status=4 WHERE id=${bookingIds[5]} AND creator=${users.student};`);
  const [refreshed]=await Promise.all([page.waitForResponse(r=>new URL(r.url()).pathname.endsWith('/student/assessmentPlan')),page.getByRole('button',{name:'Refresh',exact:true}).click()]);
  assert.equal(ok(await refreshed.json()).followUpTotal,3);await region.getByRole('article').nth(2).waitFor();assert.equal(await region.getByRole('article').count(),3);
  checks.push('approval, completion and cancellation changes remove or reclassify actions on load and explicit refresh; completed counts change only after a real Completed status and completion date');
  // A URL reference never loads another student's booking.
  await page.goto(`http://127.0.0.1:5178/home/student/my-bookings?booking=${bookingIds[7]}`);
  await page.getByText(/This booking is no longer in your available records/).waitFor();assert.equal(await page.locator('.my-bookings-focused-card').count(),0);
  const other=await login('followup-other@wba.test');assert.equal(ok(await api('/student/assessmentPlan',undefined,other)).followUpTotal,0);
  const mentor=await login('followup-mentor@wba.test');assert.notEqual((await api('/student/assessmentPlan',undefined,mentor)).code,2000);
  checks.push('query-string references cannot disclose another student’s record; an unconfigured student gets no plan actions and supervisors cannot read student plans');
  const yesterday=shift(-1);ok(await api('/student/assessmentPlan',{...goalPayload,expectedVersion:1,deadline:yesterday},token,'post'));
  await page.goto('http://127.0.0.1:5178/home/student/assessment-plan');region=page.getByRole('region',{name:'Assessment follow-ups'});
  await region.getByText('Deadline passed 1 calendar day(s) ago',{exact:true}).waitFor();assert.equal(await region.getByRole('article').count(),0);
  assert.ok((await region.innerText()).includes('No pending or confirmed bookings need follow-up'));
  assert.equal(count(`SELECT COUNT(*) FROM message WHERE creator IN (${Object.values(users).join(',')}) OR receiver IN (${Object.values(users).join(',')});`),0);
  checks.push('past deadline shows remaining gap and date-review guidance, excludes future bookings outside the revised period, and sends no automatic reminder');
  assert.deepEqual(pageErrors,[]);
  report={date:'2026-10-26',completedAt:new Date().toISOString(),scope:'real Edge + Spring + isolated MySQL/Redis; no model calls or outgoing reminders',checks,pageErrors,githubPushes:0,originalDeploymentModified:false};
})().catch(error=>{console.error(error.stack);process.exitCode=1;}).finally(async()=>{
  try {
    cleanup();if(baseline!==undefined)assert.equal(snapshot(),baseline,'Pre-existing bookings or targets changed.');
    if(report){
      assert.equal(count(`SELECT COUNT(*) FROM user WHERE id IN (${Object.values(users).join(',')});`),0);
      assert.equal(count(`SELECT COUNT(*) FROM assessment_slot WHERE id IN (${slotIds.join(',')});`),0);
      report.existingDataPreserved=true;report.fixtureCleanupVerified=true;fs.writeFileSync(`${output}/assessment-follow-ups-live-report.json`,JSON.stringify(report,null,2));console.log(JSON.stringify({passed:true,checks,pageErrors,existingDataPreserved:true}));
    }
  }catch(error){console.error('Follow-up fixture cleanup verification failed:',error.message);process.exitCode=1;}
  if(client)await client.dispose();if(browser)await browser.close();
});
