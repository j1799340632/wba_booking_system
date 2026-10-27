// Opt-in integration test. Uses only dedicated, collision-checked demo fixtures.
if (!process.argv.includes('--live')) {
  console.log('Use --live to verify booking management in the isolated local demo. Dedicated fixtures are cleaned afterward.');
  process.exit(0);
}
const {chromium,request}=require(process.env.WBA_PLAYWRIGHT_MODULE || 'playwright');
const {spawnSync}=require('node:child_process');
const fs=require('node:fs'), path=require('node:path'), assert=require('node:assert/strict'), crypto=require('node:crypto');
const docker=process.env.WBA_DOCKER_EXE || 'docker';
const output=path.resolve(__dirname,'../../.local/wba-demo/browser'); fs.mkdirSync(output,{recursive:true});
const runKey='mgt_'+crypto.randomUUID();
const ids={student:'940000000001',other:'940000000002',mentor:'940000000003',secondMentor:'940000000004'};
const slotIds=Array.from({length:6},(_,i)=>String(940000000101+i));
const madeUsers=[],madeSlots=[],checks=[],pageErrors=[];
let browser,client,token,otherToken,primaryOriginal,newBooking,baseline,report;
const originalBookings=()=>sql(`SELECT id,creator,assessment_slot_id,status,is_delete,version FROM assessment_booking WHERE creator NOT IN (${ids.student},${ids.other}) ORDER BY id;`);
function sql(statement) {
  const r=spawnSync(docker,['exec','-i','wba-ai-demo-mysql-1','sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -u wba_demo -N -B unsw_wba'],{input:statement,encoding:'utf8',timeout:15000});
  if(r.status!==0) throw new Error('Isolated demo SQL verification failed.');
  return r.stdout.trim();
}
const count=q=>Number(sql(q));
async function api(endpoint,data,auth=token,method='post') {
  const r=await client[method]('http://127.0.0.1:5178/api'+endpoint,{data,headers:{Authorization:`Bearer ${auth}`}});
  return r.json();
}
const ok=body=>{assert.equal(body.code,2000,body.msg || body.message);return body.data;};
const confirmBody=draft=>({draftId:draft.draftId,confirmed:true});
const selection=(booking,action,replacement)=>({bookingId:booking.bookingId,bookingVersion:booking.version,action,...(replacement?{replacement}:{})});
function payload(slot,suffix) {
  const [startTime,endTime]=sql(`SELECT start_time,end_time FROM assessment_slot WHERE id=${slot};`).split('\t');
  return {assessmentSlotId:slot,slotVersion:0,startTime,endTime,requestKey:runKey+suffix};
}
async function login(email) {
  for(let attempt=0;attempt<3;attempt++) {
    const result=await (await client.post('http://127.0.0.1:5178/api/general/user/login',{data:{email,password:'WbaDemo!2026'}})).json();
    if(result.code!==5011) return ok(result).token;
    await new Promise(resolve=>setTimeout(resolve,1100));
  }
  throw new Error('Demo login remained rate limited.');
}
function deleteDraft(owner,draft) {
  const r=spawnSync(docker,['exec','wba-ai-demo-redis-1','sh','-c','REDISCLI_AUTH="$REDIS_PASSWORD" exec redis-cli DEL "$1"','sh',`booking:management:${owner}:${draft.draftId}`],{encoding:'utf8',timeout:10000});
  assert.equal(r.status,0);
}
function cleanup() {
  // Only rows owned by a fixture user created successfully by this invocation can be removed.
  for(const owner of madeUsers.filter(id=>id===ids.student || id===ids.other)) {
    const bookingIds=sql(`SELECT id FROM assessment_booking WHERE creator=${owner};`).split('\n').filter(Boolean);
    for(const id of bookingIds) {
      assert.match(id,/^\d+$/);
      sql(`DELETE FROM message WHERE content LIKE '%Reference: ${id}%' AND (creator=${owner} OR receiver=${owner}); DELETE FROM assessment_booking WHERE id=${id} AND creator=${owner};`);
    }
  }
  if(madeSlots.length) sql(`DELETE FROM assessment_slot WHERE id IN (${madeSlots.join(',')});`);
  for(const id of madeUsers) sql(`DELETE FROM user_settings WHERE user_id=${id}; DELETE FROM user WHERE id=${id};`);
}
(async()=>{
  assert.equal(count(`SELECT COUNT(*) FROM user WHERE id IN (${Object.values(ids).join(',')});`),0);
  assert.equal(count(`SELECT COUNT(*) FROM assessment_slot WHERE id IN (${slotIds.join(',')});`),0);
  baseline=originalBookings();
  for(const [kind,id] of Object.entries(ids)) {
    const role=['student','other'].includes(kind)?4:3;
    const name=kind==='mentor'?'Dr Management Alpha':kind==='secondMentor'?'Dr Management Beta':'Management '+kind;
    sql(`INSERT INTO user (id,role_id,email,password,name,is_enable,is_canceled,is_delete,create_time) SELECT ${id},${role},'management-${kind}@wba.test',password,'${name}',1,0,0,NOW() FROM user WHERE id=900000001;`);
    madeUsers.push(id);
  }
  for(let i=0;i<slotIds.length;i++) {
    const mentor=i%2===0?ids.mentor:ids.secondMentor;
    sql(`INSERT INTO assessment_slot (id,start_time,end_time,repeat_type,next_available_time,hospital,department,location,type,status,creator,create_time,is_enable,is_delete,version) VALUES (${slotIds[i]},TIMESTAMP(DATE_ADD(CURDATE(),INTERVAL ${14+i} DAY),'14:00:00'),TIMESTAMP(DATE_ADD(CURDATE(),INTERVAL ${14+i} DAY),'14:30:00'),0,TIMESTAMP(DATE_ADD(CURDATE(),INTERVAL ${14+i} DAY),'14:00:00'),'Prince of Wales Hospital','General Medicine','Management E2E Only','Mini-CEX',0,${mentor},NOW(),1,0,0);`);
    madeSlots.push(slotIds[i]);
  }
  client=await request.newContext(); token=await login('management-student@wba.test'); otherToken=await login('management-other@wba.test');
  primaryOriginal=ok(await api('/student/assessmentBooking/bookings',payload(slotIds[0],'_original')));
  const privateOther=ok(await api('/student/assessmentBooking/bookings',payload(slotIds[5],'_private'),otherToken));
  browser=await chromium.launch({headless:true,channel:'msedge'});
  const page=await browser.newPage({viewport:{width:1440,height:1120},timezoneId:'Asia/Shanghai'});
  page.setDefaultTimeout(35000); page.on('pageerror',error=>pageErrors.push(error.message));
  await page.goto('http://127.0.0.1:5178/');
  await page.getByLabel('Email address').fill('management-student@wba.test');
  await page.getByLabel('Password',{exact:true}).fill('WbaDemo!2026');
  await new Promise(resolve=>setTimeout(resolve,1100)); // Respect the existing one-login-per-second rule.
  await page.getByRole('button',{name:'Sign in to WBA Platform',exact:true}).click();
  await page.waitForURL('**/home/student/dashboard');
  await page.goto('http://127.0.0.1:5178/home/student/bookSlot');
  const region=page.getByRole('region',{name:'AI booking assistant'});
  async function ask(text) {
    await page.locator('#booking-assistant-message').fill(text);
    const [response]=await Promise.all([page.waitForResponse(r=>r.url().endsWith('/bookingAssistant/search')),region.getByRole('button',{name:'Send request',exact:true}).click()]);
    return ok(await response.json());
  }
  const listed=await ask('查看我的预约'); assert.equal(listed.status,'BOOKINGS');
  assert.deepEqual(listed.bookings.map(x=>String(x.bookingId)),[String(primaryOriginal.bookingId)]);
  assert.ok(!listed.bookings.some(x=>String(x.bookingId)===String(privateOther.bookingId)));
  await region.getByRole('button',{name:'Review cancellation',exact:true}).click();
  let dialog=page.getByRole('dialog',{name:'Review booking changes'}); await dialog.waitFor();
  assert.equal(await dialog.getByRole('button',{name:'Confirm cancellation',exact:true}).isDisabled(),true);
  assert.equal(count(`SELECT status FROM assessment_booking WHERE id=${primaryOriginal.bookingId};`),0);
  await dialog.getByRole('button',{name:'Back',exact:true}).click();
  const stolen=ok(await api('/student/bookingAssistant/management/prepare',selection(primaryOriginal,'CANCEL')));
  assert.notEqual((await api('/student/bookingAssistant/management/confirm',confirmBody(stolen),otherToken)).code,2000);
  assert.notEqual((await api('/student/bookingAssistant/management/confirm',{...confirmBody(stolen),confirmed:false})).code,2000);
  checks.push('natural language list is owner-scoped; preview/close never changes data; checkbox, explicit confirmation and owner binding enforced');
  await region.getByRole('button',{name:'New search',exact:true}).click();
  const oldDate=payload(slotIds[0],'').startTime.slice(0,10),newDate=payload(slotIds[1],'').startTime.slice(0,10);
  const reschedule=await ask(`把我${oldDate}的Mini-CEX预约改到${newDate}下午，新的导师必须是Dr Management Beta`);
  assert.equal(reschedule.status,'BOOKINGS'); assert.equal(reschedule.bookingAction,'RESCHEDULE_BOOKING');
  assert.ok(reschedule.replacementConditions);
  const [replacementResponse]=await Promise.all([page.waitForResponse(r=>r.url().endsWith('/management/replacements')),region.getByRole('button',{name:'Reschedule',exact:true}).click()]);
  assert.equal(ok(await replacementResponse.json()).candidates[0].slotId,slotIds[1]);
  await region.getByRole('button',{name:'Review reschedule',exact:true}).click();
  dialog=page.getByRole('dialog',{name:'Review booking changes'}); await dialog.waitFor();
  assert.equal(await dialog.evaluate(el=>getComputedStyle(el).color),'rgb(23, 43, 58)');
  assert.equal(count(`SELECT COUNT(*) FROM assessment_booking WHERE creator=${ids.student};`),1);
  await page.screenshot({path:`${output}/management-reschedule-desktop.png`,fullPage:true});
  await page.setViewportSize({width:390,height:1000});
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  await page.screenshot({path:`${output}/management-reschedule-mobile.png`,fullPage:true});
  await page.setViewportSize({width:1440,height:1120});
  let droppedReceipt;
  await page.route('**/api/student/bookingAssistant/management/confirm',async route=>{
    droppedReceipt=ok(await (await route.fetch()).json()); newBooking=droppedReceipt.newBooking;
    await route.abort('connectionreset');
  });
  await dialog.getByRole('checkbox').check(); await dialog.getByRole('button',{name:'Confirm reschedule',exact:true}).click();
  await dialog.getByRole('alert').waitFor(); assert.ok(newBooking?.bookingId);
  await page.unroute('**/api/student/bookingAssistant/management/confirm');
  const [retried]=await Promise.all([page.waitForResponse(r=>r.url().endsWith('/management/confirm')),dialog.getByRole('button',{name:'Confirm reschedule',exact:true}).click()]);
  const retry=ok(await retried.json()); assert.equal(retry.replayed,true); assert.equal(retry.newBooking.bookingId,newBooking.bookingId);
  await region.getByText('Reschedule saved — replacement awaits supervisor approval',{exact:true}).waitFor();
  const mine=ok(await api('/student/assessmentBooking/bookings/my?pageNum=1&pageQuantity=-1',undefined,token,'get')).records;
  assert.equal(mine.find(x=>x.bookingId===primaryOriginal.bookingId).status,4); assert.equal(mine.find(x=>x.bookingId===newBooking.bookingId).status,0);
  assert.equal(count(`SELECT COUNT(*) FROM message WHERE creator=${ids.student};`),6);
  checks.push('real DeepSeek separates original and target dates; desktop/mobile review; lost response replay produces one Pending replacement and retains Cancelled original');
  await region.getByRole('button',{name:'New search',exact:true}).click();
  await ask('取消我的预约');
  const activeCard=region.getByRole('listitem').filter({hasText:String(newBooking.bookingId)});
  await activeCard.getByRole('button',{name:'Review cancellation',exact:true}).click();
  dialog=page.getByRole('dialog',{name:'Review booking changes'}); await dialog.waitFor();
  await dialog.getByRole('checkbox').check();
  await dialog.getByRole('button',{name:'Confirm cancellation',exact:true}).click();
  await region.getByText('Booking cancelled',{exact:true}).waitFor();
  assert.equal(count(`SELECT status FROM assessment_booking WHERE id=${newBooking.bookingId};`),4);
  await page.screenshot({path:`${output}/management-cancel-receipt.png`,fullPage:true});
  checks.push('natural language cancellation completes through a separate review and retains history');
  const conflictOriginal=ok(await api('/student/assessmentBooking/bookings',payload(slotIds[2],'_conflictOriginal')));
  const expires=ok(await api('/student/bookingAssistant/management/prepare',selection(conflictOriginal,'CANCEL'))); deleteDraft(ids.student,expires);
  assert.notEqual((await api('/student/bookingAssistant/management/confirm',confirmBody(expires))).code,2000);
  const stale=ok(await api('/student/bookingAssistant/management/prepare',selection(conflictOriginal,'CANCEL')));
  sql(`UPDATE assessment_slot SET location='Changed after review' WHERE id=${slotIds[2]};`);
  assert.notEqual((await api('/student/bookingAssistant/management/confirm',confirmBody(stale))).code,2000);
  sql(`UPDATE assessment_slot SET location='Management E2E Only' WHERE id=${slotIds[2]};`);
  const conflictDraft=ok(await api('/student/bookingAssistant/management/prepare',selection(conflictOriginal,'RESCHEDULE',payload(slotIds[3],'_replacement'))));
  ok(await api('/student/assessmentBooking/bookings',payload(slotIds[3],'_taken'),otherToken));
  assert.notEqual((await api('/student/bookingAssistant/management/confirm',confirmBody(conflictDraft))).code,2000);
  assert.equal(count(`SELECT status FROM assessment_booking WHERE id=${conflictOriginal.bookingId};`),0);
  assert.equal(count(`SELECT status FROM assessment_slot WHERE id=${slotIds[2]};`),1);
  assert.equal(count(`SELECT COUNT(*) FROM assessment_booking WHERE creator=${ids.student} AND assessment_slot_id=${slotIds[3]};`),0);
  checks.push('expired and changed reviews fail; a replacement taken after review leaves original booking and occupancy intact in real MySQL');
  const concurrent=ok(await api('/student/bookingAssistant/management/prepare',selection(conflictOriginal,'CANCEL')));
  const parallel=await Promise.all(Array.from({length:3},()=>api('/student/bookingAssistant/management/confirm',confirmBody(concurrent))));
  assert.ok(parallel.every(x=>x.code===2000)); assert.equal(parallel.filter(x=>x.data.replayed).length,2);
  assert.equal(count(`SELECT COUNT(*) FROM message WHERE content LIKE '%Reference: ${conflictOriginal.bookingId}%';`),4);
  const direct=ok(await api('/student/assessmentBooking/bookings',payload(slotIds[4],'_direct')));
  ok(await api(`/student/assessmentBooking/cancelBooking?id=${direct.bookingId}`,undefined));
  ok(await api(`/student/assessmentBooking/cancelBooking?id=${direct.bookingId}`,undefined));
  assert.equal(count(`SELECT COUNT(*) FROM message WHERE content LIKE '%Reference: ${direct.bookingId}%';`),4);
  assert.equal(count(`SELECT is_delete FROM assessment_booking WHERE id=${direct.bookingId};`),0);
  const mentorToken=await login('management-mentor@wba.test');
  assert.notEqual((await api('/student/bookingAssistant/management/prepare',selection(direct,'CANCEL'),mentorToken)).code,2000);
  checks.push('three concurrent confirmations create one cancellation; ordinary cancel endpoint uses same idempotent rules; supervisor role cannot use student management');
  await page.goto('http://127.0.0.1:5178/home/student/my-bookings');
  await page.getByRole('heading',{name:'My Bookings',exact:true}).waitFor();
  await page.getByText('Cancelled',{exact:true}).first().waitFor();
  await page.goto('http://127.0.0.1:5178/home/student/bookSlot');
  const beforeNew=count(`SELECT COUNT(*) FROM assessment_booking WHERE creator=${ids.student};`);
  const fresh=await ask(`帮我预约${oldDate}下午的Mini-CEX，必须是Dr Management Alpha`);
  assert.equal(fresh.status,'READY'); assert.equal(fresh.candidates[0].slotId,slotIds[0]);
  assert.equal(count(`SELECT COUNT(*) FROM assessment_booking WHERE creator=${ids.student};`),beforeNew);
  await region.getByRole('button',{name:'Review session',exact:true}).first().click();
  const newDialog=page.getByRole('dialog',{name:'Review booking request'}); await newDialog.waitFor();
  assert.equal(await newDialog.getByRole('button',{name:'Confirm booking request',exact:true}).isDisabled(),true);
  await newDialog.getByRole('checkbox').check(); await newDialog.getByRole('button',{name:'Confirm booking request',exact:true}).click();
  await region.getByText('Request saved — awaiting supervisor approval',{exact:true}).waitFor();
  assert.equal(count(`SELECT COUNT(*) FROM assessment_booking WHERE creator=${ids.student};`),beforeNew+1);
  checks.push('new booking language still follows search, review and explicit confirmation; cancellation releases the original occurrence for rebooking');
  assert.deepEqual(pageErrors,[]);
  report={date:'2026-10-26',runId:runKey,scope:'real Edge browser + DeepSeek + Spring + isolated MySQL/Redis',checks,pageErrors,githubPushes:0,originalDeploymentModified:false,cleanup:'only collision-checked fixture users and their exact bookings, messages and slots'};
})().catch(error=>{console.error(error.stack);process.exitCode=1;}).finally(async()=>{
  try{
    cleanup();
    if(baseline!==undefined) assert.equal(originalBookings(),baseline,'Existing bookings changed during verification.');
    if(report) {
      report.cleanupVerified=true; report.existingBookingsPreserved=true;
      fs.writeFileSync(`${output}/booking-management-live-report.json`,JSON.stringify(report,null,2));
      console.log(JSON.stringify({passed:true,checks,pageErrors,existingBookingsPreserved:true}));
    }
  }catch(error){console.error('Fixture cleanup verification failed:',error.message);process.exitCode=1;}
  if(client) await client.dispose(); if(browser) await browser.close();
});
