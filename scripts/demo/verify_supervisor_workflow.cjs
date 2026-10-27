// Dedicated opt-in fixtures. Never operates on the ordinary demo student's bookings or targets.
if (!process.argv.includes('--live')) { console.log('Use --live to verify the supervisor/student workflow with Edge and the isolated demo. No model calls or external mail.'); process.exit(0); }
const {chromium,request}=require(process.env.WBA_PLAYWRIGHT_MODULE || 'playwright');
const {spawnSync}=require('node:child_process');
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const docker=process.env.WBA_DOCKER_EXE || 'docker',base='http://127.0.0.1:5178';
const output=path.resolve(__dirname,'../../.local/wba-demo/browser');fs.mkdirSync(output,{recursive:true});
const ids={student:'980000000001',other:'980000000002',mentor:'980000000003',foreignMentor:'980000000004'};
const slots=Array.from({length:6},(_,i)=>String(980000000101+i));
const records=Array.from({length:7},(_,i)=>String(980000001001+i));
const createdUsers=[],createdSlots=[],checks=[],pageErrors=[];
let client,browser,studentToken,mentorToken,baseline,report;
function sql(statement) {
  const result=spawnSync(docker,['exec','-i','wba-ai-demo-mysql-1','sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -u wba_demo -N -B unsw_wba'],{input:statement,encoding:'utf8',timeout:15000});
  if(result.status!==0) throw new Error('Supervisor fixture SQL check failed.');return result.stdout.trim();
}
const count=q=>Number(sql(q));
const ok=body=>{assert.equal(body.code,2000,body.msg || body.message);return body.data;};
async function api(endpoint,data,token=studentToken,method='get') {return (await client[method](base+'/api'+endpoint,{data,headers:{Authorization:`Bearer ${token}`}})).json();}
async function login(email) {
  for(let n=0;n<4;n++) {const body=await(await client.post(base+'/api/general/user/login',{data:{email,password:'WbaDemo!2026'}})).json();if(body.code!==5011)return ok(body).token;await new Promise(resolve=>setTimeout(resolve,1100));}
  throw new Error('Login remained rate limited.');
}
async function uiLogin(page,email,role) {
  await new Promise(resolve=>setTimeout(resolve,1100));await page.goto(base);await page.getByLabel('Email address').fill(email);await page.getByLabel('Password',{exact:true}).fill('WbaDemo!2026');
  const [response]=await Promise.all([page.waitForResponse(r=>r.url().includes('/general/user/login')),page.getByRole('button',{name:'Sign in to WBA Platform',exact:true}).click()]);
  const token=ok(await response.json()).token;await page.waitForURL(`**/home/${role}/dashboard`);return token;
}
const snapshot=()=>JSON.stringify([
  sql(`SELECT id,creator,assessment_slot_id,start_time,end_time,status,score,feedback,completed_time,is_delete,version FROM assessment_booking WHERE creator NOT IN (${ids.student},${ids.other}) ORDER BY id;`),
  sql(`SELECT student_id,start_date,deadline,goals_json,version FROM student_assessment_plan WHERE student_id NOT IN (${ids.student},${ids.other}) ORDER BY student_id;`),
]);
function cleanup() {
  for(const owner of createdUsers.filter(id=>id===ids.student || id===ids.other)) sql(`DELETE FROM assessment_booking WHERE creator=${owner}; DELETE FROM student_assessment_plan WHERE student_id=${owner};`);
  if(createdSlots.length)sql(`DELETE FROM assessment_slot WHERE id IN (${createdSlots.join(',')});`);
  for(const id of createdUsers)sql(`DELETE FROM message WHERE creator=${id} OR receiver=${id}; DELETE FROM user_settings WHERE user_id=${id}; DELETE FROM user WHERE id=${id};`);
}
const plan=async()=>ok(await api('/student/assessmentPlan?summary=true'));
const notes=booking=>{assert.match(String(booking),/^\d+$/);return count(`SELECT COUNT(*) FROM message WHERE content REGEXP 'Reference: ${booking}([^0-9]|$)';`);};
const card=(page,id)=>page.getByRole('article',{name:`Assessment booking ${id}`,exact:true});
async function confirmDialog(page,kind) {const dialog=page.getByRole('dialog',{name:kind,exact:true});await dialog.waitFor();assert.equal(await dialog.getByRole('button',{name:/^Confirm /}).isDisabled(),true);await dialog.getByRole('checkbox').check();return dialog;}
async function dismissNotices(page) {
  // Exercise the normal close control: hovering an overlapping toast pauses Sonner's dismissal timer.
  for(let i=0;i<12;i++) {const close=page.locator('[data-sonner-toast][data-front="true"] [data-close-button]').first();if(!await close.isVisible())break;await close.click();await page.waitForTimeout(200);}
}
(async()=>{
  assert.equal(count(`SELECT COUNT(*) FROM user WHERE id IN (${Object.values(ids).join(',')}) OR email LIKE 'workflow-%@wba.test';`),0);
  assert.equal(count(`SELECT COUNT(*) FROM assessment_slot WHERE id IN (${slots.join(',')});`),0);
  assert.equal(count(`SELECT COUNT(*) FROM assessment_booking WHERE id IN (${records.join(',')});`),0);
  assert.equal(count(`SELECT COUNT(*) FROM student_assessment_plan WHERE student_id IN (${ids.student},${ids.other});`),0);baseline=snapshot();
  for(const [kind,id] of Object.entries(ids)) {sql(`INSERT INTO user (id,role_id,email,password,name,is_enable,is_canceled,is_delete,create_time) SELECT ${id},${kind.includes('Mentor')||kind==='mentor'?3:4},'workflow-${kind}@wba.test',password,'Workflow ${kind}',1,0,0,NOW() FROM user WHERE id=900000001;`);createdUsers.push(id);}
  client=await request.newContext();studentToken=await login('workflow-student@wba.test');
  const initial=await plan();assert.match(initial.generatedAt,/^\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}:\d{2}/);
  const now=initial.generatedAt.slice(0,19).replace('T',' '),shift=days=>{const date=new Date(initial.today+'T00:00:00Z');date.setUTCDate(date.getUTCDate()+days);return date.toISOString().slice(0,10);};
  const mini=sql("SELECT id FROM wba_type WHERE name='Mini-CEX' AND is_enable=1 AND is_delete=0;");assert.match(mini,/^\d+$/);
  for(let i=0;i<slots.length;i++) {const day=i+3,owner=i===5?ids.foreignMentor:ids.mentor;sql(`INSERT INTO assessment_slot (id,start_time,end_time,repeat_type,rrule,repeat_start_time,repeat_end_time,next_available_time,hospital,department,location,type,status,creator,create_time,is_enable,is_delete,version) VALUES (${slots[i]},DATE_ADD('${now}',INTERVAL ${day} DAY),DATE_ADD('${now}',INTERVAL ${day*1440+30} MINUTE),${i===4?1:0},${i===4?"'FREQ=WEEKLY;INTERVAL=1'":'NULL'},'${shift(0)}','${shift(28)}',DATE_ADD('${now}',INTERVAL ${day} DAY),'Prince of Wales Hospital','General Medicine','Workflow E2E Only','Mini-CEX',0,${owner},NOW(),1,0,17);`);createdSlots.push(slots[i]);}
  const selection=slot=>{const row=sql(`SELECT DATE_FORMAT(start_time,'%Y-%m-%d %H:%i:%s'),DATE_FORMAT(end_time,'%Y-%m-%d %H:%i:%s') FROM assessment_slot WHERE id=${slot};`).split('\t');return{assessmentSlotId:slot,slotVersion:17,startTime:row[0],endTime:row[1],requestKey:'workflow_'+slot};};
  const future=ok(await api('/student/assessmentBooking/bookings',selection(slots[0]),studentToken,'post'));
  const futureId=String(future.bookingId);assert.equal(notes(futureId),2);
  // Ended confirmed, future pending, future confirmed, recurring pending, another mentor's request, recurring confirmed on a different date.
  const specs=[[0,1,-1440,1,ids.student],[1,2,5*1440,0,ids.student],[2,3,6*1440,1,ids.student],[3,4,10*1440,0,ids.student],[4,5,8*1440,0,ids.other],[5,4,17*1440,1,ids.other]];
  for(const [index,slot,minutes,status,owner] of specs)sql(`INSERT INTO assessment_booking (id,creator,assessment_slot_id,start_time,end_time,status,create_time,is_delete,version) VALUES (${records[index]},${owner},${slots[slot]},DATE_ADD('${now}',INTERVAL ${minutes} MINUTE),DATE_ADD('${now}',INTERVAL ${minutes+30} MINUTE),${status},NOW(),0,0);`);
  sql(`UPDATE assessment_slot SET status=1 WHERE id IN (${slots[1]},${slots[2]},${slots[3]},${slots[5]});`);
  ok(await api('/student/assessmentPlan',{startDate:shift(-7),deadline:shift(28),expectedVersion:0,goals:[{typeId:mini,target:5}]},studentToken,'post'));
  browser=await chromium.launch({headless:true,channel:'msedge'});
  const mentorContext=await browser.newContext({viewport:{width:1440,height:1100},timezoneId:'America/Los_Angeles'}),studentContext=await browser.newContext({viewport:{width:1440,height:1100},timezoneId:'Asia/Shanghai'});
  const mentorPage=await mentorContext.newPage(),studentPage=await studentContext.newPage();
  for(const page of [mentorPage,studentPage]) {page.setDefaultTimeout(30000);page.on('pageerror',error=>pageErrors.push(error.message));}
  studentToken=await uiLogin(studentPage,'workflow-student@wba.test','student');await studentPage.goto(base+'/home/student/assessment-plan');await studentPage.getByRole('heading',{name:'Your next actions',exact:true}).waitFor();
  mentorToken=await uiLogin(mentorPage,'workflow-mentor@wba.test','supervisor');await mentorPage.goto(base+'/home/supervisor/upcoming-sessions');await card(mentorPage,futureId).waitFor();
  await dismissNotices(mentorPage);await card(mentorPage,futureId).getByRole('button',{name:'Approve',exact:true}).click({trial:true});
  assert.equal(await card(mentorPage,records[2]).getByRole('button',{name:'Complete',exact:true}).isDisabled(),true);
  const before=await plan();assert.equal(before.completed,0);
  await card(mentorPage,futureId).getByRole('button',{name:'Approve',exact:true}).click();let dialog=await confirmDialog(mentorPage,'Approve booking');await dialog.getByRole('button',{name:'Back',exact:true}).click();assert.equal(count(`SELECT status FROM assessment_booking WHERE id=${futureId};`),0);
  await card(mentorPage,futureId).getByRole('button',{name:'Approve',exact:true}).click();dialog=await confirmDialog(mentorPage,'Approve booking');
  let approvedReceipt;
  await mentorPage.route('**/api/supervisor/assessmentSlot/approveSlot?*',async route=>{const response=await route.fetch();const body=await response.json();if(body.code!==2000){await route.fulfill({response});return;}approvedReceipt=body.data;await route.abort('connectionreset');});
  await dialog.getByRole('button',{name:'Confirm approve',exact:true}).click();await dialog.getByRole('alert').waitFor();assert.equal(approvedReceipt.status,1);
  await mentorPage.unroute('**/api/supervisor/assessmentSlot/approveSlot?*');
  const [retried]=await Promise.all([mentorPage.waitForResponse(r=>r.url().includes('/approveSlot?')),dialog.getByRole('button',{name:'Confirm approve',exact:true}).click()]);assert.equal(ok(await retried.json()).replayed,true);
  await mentorPage.getByText('Booking approved',{exact:true}).waitFor();assert.equal(notes(futureId),4);assert.equal(count(`SELECT version FROM assessment_booking WHERE id=${futureId};`),1);
  await dismissNotices(studentPage);await studentPage.getByRole('button',{name:'Refresh',exact:true}).click();await studentPage.getByRole('region',{name:'Assessment follow-ups'}).getByText('Confirmed — scheduled later',{exact:true}).first().waitFor();
  let current=await plan();assert.equal(current.completed,0);assert.equal(current.pending,before.pending-1);assert.equal(current.confirmed,before.confirmed+1);
  checks.push('student requests a real future slot; supervisor sees actual occurrence details and booking version rather than template version; preview-back makes no change; lost approval response retries once with one decision and one pair of notices; student plan changes Pending to Confirmed without completing it');
  // Only an ended confirmed booking can be completed. An early completion API request is also rejected.
  assert.notEqual((await api('/supervisor/assessmentSlot/markAssessmentCompleted',{bookingId:futureId,version:1,score:80,feedback:'Too early'},mentorToken,'post')).code,2000);
  await card(mentorPage,records[0]).getByRole('button',{name:'Complete',exact:true}).click();dialog=await confirmDialog(mentorPage,'Complete assessment');
  await dialog.getByLabel('Score (0–100)',{exact:true}).fill('80');await dialog.getByLabel('Feedback',{exact:true}).fill('Observed assessment completed successfully.');
  let completeReceipt;
  await mentorPage.route('**/api/supervisor/assessmentSlot/markAssessmentCompleted',async route=>{const response=await route.fetch();const body=await response.json();if(body.code!==2000){await route.fulfill({response});return;}completeReceipt=body.data;await route.abort('connectionreset');});
  await dialog.getByRole('button',{name:'Confirm complete',exact:true}).click();await dialog.getByRole('alert').waitFor();assert.equal(completeReceipt.status,2);
  await mentorPage.unroute('**/api/supervisor/assessmentSlot/markAssessmentCompleted');
  const [completedRetry]=await Promise.all([mentorPage.waitForResponse(r=>r.url().endsWith('/markAssessmentCompleted')),dialog.getByRole('button',{name:'Confirm complete',exact:true}).click()]);assert.equal(ok(await completedRetry.json()).replayed,true);
  await mentorPage.getByText('Assessment completed',{exact:true}).waitFor();const completedAt=sql(`SELECT completed_time FROM assessment_booking WHERE id=${records[0]};`);assert.equal(notes(records[0]),2);
  await dismissNotices(studentPage);await studentPage.getByRole('button',{name:'Refresh',exact:true}).click();current=await plan();assert.equal(current.completed,1);assert.equal(current.followUps.some(i=>i.bookingId===records[0]),false);
  await dismissNotices(mentorPage);
  await mentorPage.waitForFunction(()=>document.documentElement.scrollWidth<=innerWidth);
  await mentorPage.screenshot({path:`${output}/supervisor-workflow-desktop.png`,animations:'disabled'});await mentorPage.setViewportSize({width:390,height:1000});await mentorPage.waitForFunction(()=>document.documentElement.scrollWidth<=innerWidth);await mentorPage.screenshot({path:`${output}/supervisor-workflow-mobile.png`,animations:'disabled'});await mentorPage.setViewportSize({width:1440,height:1100});
  checks.push('future completion is disabled and rejected server-side; an ended confirmed assessment is explicitly scored and completed; response loss/retry saves one result, one completion date and two notices; student completion count increases once and the follow-up disappears');
  await mentorPage.getByRole('link',{name:'View Assessment History',exact:true}).click();await mentorPage.getByRole('button',{name:'Edit',exact:true}).click();dialog=mentorPage.getByRole('dialog',{name:'Edit assessment result'});await dialog.waitFor();
  await dialog.getByLabel('Score',{exact:true}).fill('90');await dialog.getByLabel('Feedback',{exact:true}).fill('Corrected score after reviewing the rubric.');
  const [corrected]=await Promise.all([mentorPage.waitForResponse(r=>r.url().endsWith('/updateAssessmentResult')),dialog.getByRole('button',{name:'Save Changes',exact:true}).click()]);assert.equal(ok(await corrected.json()).status,2);
  await dialog.waitFor({state:'hidden'});assert.equal(sql(`SELECT completed_time FROM assessment_booking WHERE id=${records[0]};`),completedAt);assert.equal(count(`SELECT score FROM assessment_booking WHERE id=${records[0]};`),90);assert.equal((await plan()).completed,1);
  assert.notEqual((await api('/supervisor/assessmentSlot/updateAssessmentResult',{bookingId:records[0],version:1,score:95,feedback:'Stale edit'},mentorToken,'post')).code,2000);
  await studentPage.goto(base+'/home/student/assessment-history');await studentPage.getByText('Corrected score after reviewing the rubric.',{exact:true}).waitFor();
  checks.push('supervisor corrects score and feedback through a separate endpoint; original completion date and student progress are preserved, stale edits are rejected, and the corrected feedback appears in student history');
  await mentorPage.goto(base+'/home/supervisor/upcoming-sessions');await card(mentorPage,records[1]).waitFor();await dismissNotices(mentorPage);await card(mentorPage,records[1]).getByRole('button',{name:'Decline',exact:true}).click();dialog=await confirmDialog(mentorPage,'Decline booking');
  await dialog.getByRole('radio',{name:'Keep this session closed',exact:true}).check();await dialog.getByRole('button',{name:'Confirm decline',exact:true}).click();await mentorPage.getByText('Booking declined',{exact:true}).waitFor();assert.equal(count(`SELECT status FROM assessment_booking WHERE id=${records[1]};`),3);assert.equal(count(`SELECT is_enable FROM assessment_slot WHERE id=${slots[2]};`),0);
  await dismissNotices(mentorPage);await card(mentorPage,records[2]).getByRole('button',{name:'Cancel',exact:true}).click();dialog=await confirmDialog(mentorPage,'Cancel confirmed session');assert.equal(await dialog.getByRole('radio',{name:'Keep this session closed',exact:true}).isChecked(),true);await dialog.getByRole('button',{name:'Confirm cancel',exact:true}).click();await mentorPage.getByText('Booking cancelled',{exact:true}).waitFor();assert.equal(count(`SELECT status FROM assessment_booking WHERE id=${records[2]};`),4);assert.equal(count(`SELECT is_enable FROM assessment_slot WHERE id=${slots[3]};`),0);
  const recurringBefore=sql(`SELECT status,is_enable FROM assessment_slot WHERE id=${slots[4]};`);
  await dismissNotices(mentorPage);await card(mentorPage,records[3]).getByRole('button',{name:'Decline',exact:true}).click();dialog=await confirmDialog(mentorPage,'Decline booking');assert.equal(await dialog.getByRole('radio').count(),0);await dialog.getByRole('button',{name:'Confirm decline',exact:true}).click();await mentorPage.getByText('Booking declined',{exact:true}).waitFor();assert.equal(sql(`SELECT status,is_enable FROM assessment_slot WHERE id=${slots[4]};`),recurringBefore);assert.equal(count(`SELECT status FROM assessment_booking WHERE id=${records[5]};`),1);
  const resolved=await plan();assert.equal(resolved.completed,1);assert.equal(resolved.confirmed,1);assert.equal(resolved.pending,0);assert.equal(resolved.toRequest,3);
  await studentPage.goto(base+'/home/student/assessment-plan');await studentPage.getByRole('region',{name:'Plan summary'}).getByText('1/5',{exact:true}).waitFor();
  assert.equal(await studentPage.getByRole('region',{name:'Assessment follow-ups'}).getByRole('article').count(),1);
  checks.push('decline and supervisor cancellation retain historical records and honor the reviewed one-off availability setting; cancellation defaults to closed; resolving a recurring request preserves other occurrences and the series enabled setting');
  const foreign=await login('workflow-foreignMentor@wba.test');
  assert.notEqual((await api(`/supervisor/assessmentSlot/approveSlot?id=${slots[0]}&bookingId=${futureId}&version=1`,undefined,foreign,'post')).code,2000);
  assert.notEqual((await api(`/supervisor/assessmentSlot/declineSlot?id=${slots[5]}&bookingId=${futureId}&version=1&isEnable=true`,undefined,mentorToken,'post')).code,2000);
  assert.notEqual((await api('/supervisor/assessmentSlot/markAssessmentCompleted',{bookingId:futureId,version:1,score:80,feedback:'Student attempt'},studentToken,'post')).code,2000);
  assert.equal(count(`SELECT status FROM assessment_booking WHERE id=${futureId};`),1);assert.equal(notes(futureId),4);assert.deepEqual(pageErrors,[]);
  checks.push('another supervisor, a forged slot/booking pair, and a student cannot perform these decisions; failed authorization does not change the booking or create notices; desktop/mobile and cross-timezone pages have no script errors');
  report={date:'2026-10-26',completedAt:new Date().toISOString(),scope:'real Edge + Spring + isolated MySQL/Redis; no model or external mail',checks,pageErrors,githubPushes:0,originalDeploymentModified:false};
})().catch(error=>{console.error(error.stack);process.exitCode=1;}).finally(async()=>{
  try {cleanup();if(baseline!==undefined)assert.equal(snapshot(),baseline,'Existing bookings or targets changed.');if(report){assert.equal(count(`SELECT COUNT(*) FROM user WHERE id IN (${Object.values(ids).join(',')});`),0);assert.equal(count(`SELECT COUNT(*) FROM assessment_slot WHERE id IN (${slots.join(',')});`),0);report.existingDataPreserved=true;report.fixtureCleanupVerified=true;fs.writeFileSync(`${output}/supervisor-workflow-live-report.json`,JSON.stringify(report,null,2));console.log(JSON.stringify({passed:true,checks,pageErrors,existingDataPreserved:true}));}}
  catch(error){console.error('Workflow fixture cleanup verification failed:',error.message);process.exitCode=1;}if(client)await client.dispose();if(browser)await browser.close();
});
