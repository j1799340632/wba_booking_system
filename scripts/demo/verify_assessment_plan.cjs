// Opt-in verification with collision-checked accounts. Never changes the ordinary demo student's goals or bookings.
if (!process.argv.includes('--live')) {
  console.log('Use --live to test assessment planning with real Edge, DeepSeek, and the isolated demo. Dedicated fixtures are cleaned afterward.');
  process.exit(0);
}
const {chromium,request}=require(process.env.WBA_PLAYWRIGHT_MODULE || 'playwright');
const {spawnSync}=require('node:child_process');
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const docker=process.env.WBA_DOCKER_EXE || 'docker';
const output=path.resolve(__dirname,'../../.local/wba-demo/browser');fs.mkdirSync(output,{recursive:true});
const users={student:'950000000001',other:'950000000002',mentor:'950000000003',secondMentor:'950000000004'};
const slotIds=Array.from({length:9},(_,i)=>String(950000000101+i));
const bookingIds=Array.from({length:7},(_,i)=>String(950000001001+i));
const createdUsers=[],createdSlots=[],checks=[],pageErrors=[];
let browser,client,token,baseline,report;
function sql(statement) {
  const r=spawnSync(docker,['exec','-i','wba-ai-demo-mysql-1','sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -u wba_demo -N -B unsw_wba'],{input:statement,encoding:'utf8',timeout:15000});
  if(r.status!==0) throw new Error('Isolated planning SQL verification failed.');
  return r.stdout.trim();
}
const count=q=>Number(sql(q));
const ok=body=>{assert.equal(body.code,2000,body.msg || body.message);return body.data;};
async function api(endpoint,data,auth=token,method='post') {
  return (await client[method]('http://127.0.0.1:5178/api'+endpoint,{data,headers:{Authorization:`Bearer ${auth}`}})).json();
}
async function login(email) {
  for(let n=0;n<3;n++) {
    const body=await (await client.post('http://127.0.0.1:5178/api/general/user/login',{data:{email,password:'WbaDemo!2026'}})).json();
    if(body.code!==5011) return ok(body).token;
    await new Promise(resolve=>setTimeout(resolve,1100));
  }
  throw new Error('Login remained rate limited.');
}
const snapshot=()=>JSON.stringify([
  sql(`SELECT id,creator,assessment_slot_id,status,is_delete,version FROM assessment_booking WHERE creator NOT IN (${users.student},${users.other}) ORDER BY id;`),
  sql(`SELECT student_id,start_date,deadline,goals_json,version FROM student_assessment_plan WHERE student_id NOT IN (${users.student},${users.other}) ORDER BY student_id;`),
]);
function cleanup() {
  for(const owner of createdUsers.filter(id=>id===users.student || id===users.other)) {
    const ids=sql(`SELECT id FROM assessment_booking WHERE creator=${owner};`).split('\n').filter(Boolean);
    for(const id of ids) {assert.match(id,/^\d+$/);sql(`DELETE FROM message WHERE (creator=${owner} OR receiver=${owner}) AND content LIKE '%Reference: ${id}%'; DELETE FROM assessment_booking WHERE id=${id} AND creator=${owner};`);}
    sql(`DELETE FROM student_assessment_plan WHERE student_id=${owner};`);
  }
  if(createdSlots.length) sql(`DELETE FROM assessment_slot WHERE id IN (${createdSlots.join(',')});`);
  for(const id of createdUsers) sql(`DELETE FROM user_settings WHERE user_id=${id}; DELETE FROM user WHERE id=${id};`);
}
(async()=>{
  assert.equal(count(`SELECT COUNT(*) FROM user WHERE id IN (${Object.values(users).join(',')});`),0);
  assert.equal(count(`SELECT COUNT(*) FROM assessment_slot WHERE id IN (${slotIds.join(',')});`),0);
  assert.equal(count(`SELECT COUNT(*) FROM assessment_booking WHERE id IN (${bookingIds.join(',')});`),0);
  baseline=snapshot();
  const mini=sql("SELECT id FROM wba_type WHERE name='Mini-CEX' AND is_enable=1 AND is_delete=0;");
  const dop=sql("SELECT id FROM wba_type WHERE name='DOP' AND is_enable=1 AND is_delete=0;");
  assert.match(mini,/^\d+$/); assert.match(dop,/^\d+$/);
  for(const [kind,id] of Object.entries(users)) {
    const role=['student','other'].includes(kind)?4:3;
    const name=kind==='mentor'?'Dr Planning Alpha':kind==='secondMentor'?'Dr Planning Beta':'Planning '+kind;
    sql(`INSERT INTO user (id,role_id,email,password,name,is_enable,is_canceled,is_delete,create_time) SELECT ${id},${role},'planning-${kind}@wba.test',password,'${name}',1,0,0,NOW() FROM user WHERE id=900000001;`);createdUsers.push(id);
  }
  const specs=[[-2,'Mini-CEX',users.mentor], [3,'Mini-CEX',users.mentor], [4,'DOP',users.secondMentor], [5,'Mini-CEX',users.secondMentor], [6,'DOP',users.mentor], [7,'Mini-CEX',users.mentor], [-3,'Mini-CEX',users.secondMentor], [-1,'Mini-CEX',users.mentor], [-4,'Mini-CEX',users.secondMentor]];
  for(let i=0;i<specs.length;i++) {
    const [day,type,mentor]=specs[i];
    sql(`INSERT INTO assessment_slot (id,start_time,end_time,repeat_type,next_available_time,hospital,department,location,type,status,creator,create_time,is_enable,is_delete,version) VALUES (${slotIds[i]},TIMESTAMP(DATE_ADD(CURDATE(),INTERVAL ${day} DAY),'13:00:00'),TIMESTAMP(DATE_ADD(CURDATE(),INTERVAL ${day} DAY),'13:30:00'),0,TIMESTAMP(DATE_ADD(CURDATE(),INTERVAL ${day} DAY),'13:00:00'),'Prince of Wales Hospital','General Medicine','Planning E2E Only','${type}',0,${mentor},NOW(),1,0,0);`);createdSlots.push(slotIds[i]);
  }
  const booked=[[0,2,users.student,'DATE_SUB(NOW(),INTERVAL 1 DAY)'],[1,1,users.student,'NULL'],[2,0,users.student,'NULL'],[5,4,users.student,'NULL'],[6,2,users.other,'DATE_SUB(NOW(),INTERVAL 1 DAY)'],[7,0,users.student,'NULL'],[8,2,users.student,'NULL']];
  for(let i=0;i<booked.length;i++) {
    const [slot,status,owner,completed]=booked[i];
    sql(`INSERT INTO assessment_booking (id,creator,assessment_slot_id,start_time,end_time,status,completed_time,create_time,is_delete,version) SELECT ${bookingIds[i]},${owner},id,start_time,end_time,${status},${completed},NOW(),0,0 FROM assessment_slot WHERE id=${slotIds[slot]};`);
    if(status===0 || status===1) sql(`UPDATE assessment_slot SET status=1 WHERE id=${slotIds[slot]};`);
  }
  sql(`UPDATE assessment_slot SET is_delete=1 WHERE id=${slotIds[0]};`);
  client=await request.newContext(); browser=await chromium.launch({headless:true,channel:'msedge'});
  const page=await browser.newPage({viewport:{width:1440,height:1120},timezoneId:'Asia/Shanghai'});page.setDefaultTimeout(35000);
  page.on('pageerror',e=>pageErrors.push(e.message));
  await page.goto('http://127.0.0.1:5178/');
  await page.getByLabel('Email address').fill('planning-student@wba.test');await page.getByLabel('Password',{exact:true}).fill('WbaDemo!2026');
  const [logged]=await Promise.all([page.waitForResponse(r=>r.url().endsWith('/general/user/login')),page.getByRole('button',{name:'Sign in to WBA Platform',exact:true}).click()]);token=ok(await logged.json()).token;
  await page.waitForURL('**/home/student/dashboard');await page.goto('http://127.0.0.1:5178/home/student/bookSlot');
  const assistant=page.getByRole('region',{name:'AI booking assistant'});
  await page.locator('#booking-assistant-message').fill('帮我做评估规划');
  const [planned]=await Promise.all([page.waitForResponse(r=>r.url().endsWith('/bookingAssistant/search')),assistant.getByRole('button',{name:'Send request',exact:true}).click()]);
  const initial=ok(await planned.json());assert.equal(initial.status,'PLAN');assert.equal(initial.assessmentPlan.configured,false);
  await assistant.getByRole('link',{name:'Open assessment plan',exact:true}).click();
  await page.getByRole('heading',{name:'Set your assessment targets',exact:true}).waitFor();
  assert.equal(await page.getByLabel('Target count 1',{exact:true}).inputValue(),'');
  const start=sql('SELECT DATE_SUB(CURDATE(),INTERVAL 7 DAY);'),deadline=sql('SELECT DATE_ADD(CURDATE(),INTERVAL 28 DAY);');
  await page.getByLabel('Placement start',{exact:true}).fill(start);await page.getByLabel('Deadline, inclusive',{exact:true}).fill(deadline);
  await page.getByLabel('Assessment type 1',{exact:true}).selectOption(mini);await page.getByLabel('Target count 1',{exact:true}).fill('3');
  await page.getByRole('button',{name:'Add assessment type',exact:true}).click();
  await page.getByLabel('Assessment type 2',{exact:true}).selectOption(dop);await page.getByLabel('Target count 2',{exact:true}).fill('2');
  const payload={startDate:start,deadline,expectedVersion:0,goals:[{typeId:mini,target:3},{typeId:dop,target:2}]};
  const before=count(`SELECT COUNT(*) FROM assessment_booking WHERE creator=${users.student};`);
  const [saved]=await Promise.all([page.waitForResponse(r=>r.url().endsWith('/student/assessmentPlan') && r.request().method()==='POST'),page.getByRole('button',{name:'Save targets',exact:true}).click()]);assert.equal(ok(await saved.json()),1);
  await page.getByRole('heading',{name:'Your assessment gaps',exact:true}).waitFor();
  let plan=ok(await api('/student/assessmentPlan',undefined,token,'get'));
  assert.equal(plan.totalTarget,5);assert.equal(plan.completed,1);assert.equal(plan.confirmed,1);assert.equal(plan.pending,1);assert.equal(plan.remaining,4);assert.equal(plan.toRequest,2);assert.equal(plan.uncountedRecords,1);
  assert.equal(plan.goals.find(g=>g.type==='Mini-CEX').missed,1);
  assert.equal(plan.weeks.flatMap(w=>w.goals).reduce((sum,g)=>sum+g.toRequest,0),2);
  assert.equal(count(`SELECT COUNT(*) FROM assessment_booking WHERE creator=${users.student};`),before);
  await page.reload();await page.getByRole('heading',{name:'Your assessment gaps',exact:true}).waitFor();
  await page.waitForFunction(()=>document.documentElement.scrollWidth<=innerWidth);
  await page.screenshot({path:`${output}/assessment-plan-desktop.png`,fullPage:true,animations:'disabled'});
  await page.setViewportSize({width:390,height:1000});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  await page.screenshot({path:`${output}/assessment-plan-mobile.png`,fullPage:true});await page.setViewportSize({width:1440,height:1120});
  checks.push('real model opens personal planning without inventing targets; saved goals survive reload; exact completion/confirmed/pending/gap counts, missing-date warning and weekly totals are correct');
  await page.goto('http://127.0.0.1:5178/home/student/dashboard');await page.getByText('1/3',{exact:true}).waitFor();await page.getByText('0/2',{exact:true}).waitFor();
  await page.getByRole('button',{name:'View plan',exact:true}).click();await page.getByRole('heading',{name:'Your assessment gaps',exact:true}).waitFor();
  const card=page.locator('.assessment-plan-session').filter({hasText:'Mini-CEX'}).first();
  await card.getByRole('button',{name:'Review session',exact:true}).click();
  let dialog=page.getByRole('dialog',{name:'Review assessment booking'});await dialog.waitFor();
  assert.equal(await dialog.getByRole('button',{name:'Confirm booking request',exact:true}).isDisabled(),true);
  assert.equal(count(`SELECT COUNT(*) FROM assessment_booking WHERE creator=${users.student};`),before);
  await dialog.getByRole('button',{name:'Back',exact:true}).click();
  await card.getByRole('button',{name:'Review session',exact:true}).click();dialog=page.getByRole('dialog',{name:'Review assessment booking'});await dialog.waitFor();
  let receipt;
  await page.route('**/api/student/bookingAssistant/confirm',async route=>{receipt=ok(await (await route.fetch()).json());await route.abort('connectionreset');});
  await dialog.getByRole('checkbox').check();await dialog.getByRole('button',{name:'Confirm booking request',exact:true}).click();await dialog.getByRole('alert').waitFor();assert.ok(receipt.bookingId);
  await page.unroute('**/api/student/bookingAssistant/confirm');
  const [retry]=await Promise.all([page.waitForResponse(r=>r.url().endsWith('/bookingAssistant/confirm')),dialog.getByRole('button',{name:'Confirm booking request',exact:true}).click()]);
  assert.equal(ok(await retry.json()).bookingId,receipt.bookingId);
  await page.getByText('Booking request saved — awaiting supervisor approval',{exact:true}).waitFor();
  plan=ok(await api('/student/assessmentPlan',undefined,token,'get'));assert.equal(plan.completed,1);assert.equal(plan.pending,2);assert.equal(plan.toRequest,1);assert.equal(plan.remaining,4);
  assert.equal(plan.goals.find(g=>g.type==='Mini-CEX').candidates.length,0);
  assert.equal(count(`SELECT COUNT(*) FROM assessment_booking WHERE creator=${users.student};`),before+1);
  assert.equal(count(`SELECT COUNT(*) FROM message WHERE content LIKE '%Reference: ${receipt.bookingId}%';`),2);
  await page.screenshot({path:`${output}/assessment-plan-booking-receipt.png`,fullPage:true});
  checks.push('dashboard uses saved targets instead of ten; recommendation preview makes no booking; lost response retry creates one Pending booking and refreshes provisional coverage without inflating completed progress');
  assert.equal(ok(await api('/student/assessmentPlan',payload)),1);
  const changed={...payload,goals:[{typeId:mini,target:4},{typeId:dop,target:2}]};
  assert.notEqual((await api('/student/assessmentPlan',changed)).code,2000);
  assert.equal(ok(await api('/student/assessmentPlan',{...changed,expectedVersion:1})),2);
  assert.equal(count(`SELECT COUNT(*) FROM assessment_booking WHERE creator=${users.student};`),before+1);
  const other=await login('planning-other@wba.test');assert.equal(ok(await api('/student/assessmentPlan',undefined,other,'get')).configured,false);
  const mentor=await login('planning-mentor@wba.test');assert.notEqual((await api('/student/assessmentPlan',undefined,mentor,'get')).code,2000);assert.notEqual((await api('/student/assessmentPlan',payload,mentor)).code,2000);
  checks.push('identical save retry replays version, stale different edit is rejected, deliberate target edit never books sessions, goals stay private to each student and supervisor role cannot read or write student plans');
  const yesterday=sql('SELECT DATE_SUB(CURDATE(),INTERVAL 1 DAY);');ok(await api('/student/assessmentPlan',{...changed,expectedVersion:2,deadline:yesterday}));
  plan=ok(await api('/student/assessmentPlan',undefined,token,'get'));assert.equal(plan.status,'OVERDUE');assert.equal(plan.weeks.length,0);assert.ok(plan.goals.every(g=>g.candidates.length===0));
  checks.push('expired planning deadline shows overdue gaps and no future pacing or recommendations; existing bookings remain unchanged');
  assert.deepEqual(pageErrors,[]);
  report={date:'2026-10-26',completedAt:new Date().toISOString(),scope:'real Edge + DeepSeek routing + Spring + isolated MySQL/Redis',checks,pageErrors,githubPushes:0,originalDeploymentModified:false};
})().catch(error=>{console.error(error.stack);process.exitCode=1;}).finally(async()=>{
  try{
    cleanup();if(baseline!==undefined)assert.equal(snapshot(),baseline,'Pre-existing bookings or targets changed.');
    if(report){report.existingDataPreserved=true;report.fixtureCleanupVerified=true;fs.writeFileSync(`${output}/assessment-plan-live-report.json`,JSON.stringify(report,null,2));console.log(JSON.stringify({passed:true,checks,pageErrors,existingDataPreserved:true}));}
  }catch(error){console.error('Planning fixture cleanup verification failed:',error.message);process.exitCode=1;}
  if(client)await client.dispose();if(browser)await browser.close();
});
