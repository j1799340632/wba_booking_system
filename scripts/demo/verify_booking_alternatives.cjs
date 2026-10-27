if(!process.argv.includes('--live')){
  console.log('Use --live to verify real alternative suggestions in the isolated demo. Dedicated fixtures are cleaned afterward.');process.exit(0);
}
const {chromium,request}=require(process.env.WBA_PLAYWRIGHT_MODULE || 'playwright');
const {spawnSync}=require('node:child_process');
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),crypto=require('node:crypto');
const docker=process.env.WBA_DOCKER_EXE || 'docker';
const output=path.resolve(__dirname,'../../.local/wba-demo/browser');fs.mkdirSync(output,{recursive:true});
const owner='960000000001',other='960000000002',alpha='960000000003',beta='960000000004';
const users=[owner,other,alpha,beta],slots=Array.from({length:9},(_,i)=>String(960000000101+i));
const madeUsers=[],madeSlots=[],checks=[],pageErrors=[],runKey='alt_'+crypto.randomUUID();
let browser,client,token,baseline,report;
function sql(statement){const r=spawnSync(docker,['exec','-i','wba-ai-demo-mysql-1','sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -u wba_demo -N -B unsw_wba'],{input:statement,encoding:'utf8',timeout:15000});if(r.status!==0)throw new Error('Isolated alternatives SQL verification failed.');return r.stdout.trim();}
const count=q=>Number(sql(q));
const ok=b=>{assert.equal(b.code,2000,b.msg || b.message);return b.data;};
async function api(endpoint,data,auth=token,method='post'){return(await client[method]('http://127.0.0.1:5178/api'+endpoint,{data,headers:{Authorization:`Bearer ${auth}`}})).json();}
const snapshot=()=>JSON.stringify([sql(`SELECT id,creator,status,is_delete,version FROM assessment_booking WHERE creator NOT IN (${owner},${other}) ORDER BY id;`),sql('SELECT student_id,start_date,deadline,goals_json,version FROM student_assessment_plan ORDER BY student_id;')]);
const payload=(id,suffix)=>{const[startTime,endTime]=sql(`SELECT start_time,end_time FROM assessment_slot WHERE id=${id};`).split('\t');return{assessmentSlotId:id,slotVersion:0,startTime,endTime,requestKey:runKey+suffix};};
function cleanup(){
  for(const user of madeUsers.filter(id=>id===owner || id===other)){
    for(const id of sql(`SELECT id FROM assessment_booking WHERE creator=${user};`).split('\n').filter(Boolean)){assert.match(id,/^\d+$/);sql(`DELETE FROM message WHERE creator=${user} AND content LIKE '%Reference: ${id}%';DELETE FROM assessment_booking WHERE id=${id} AND creator=${user};`);}
  }
  if(madeSlots.length)sql(`DELETE FROM assessment_slot WHERE id IN (${madeSlots.join(',')});`);
  for(const user of madeUsers)sql(`DELETE FROM user_settings WHERE user_id=${user};DELETE FROM user WHERE id=${user};`);
}
(async()=>{
  assert.equal(count(`SELECT COUNT(*) FROM user WHERE id IN (${users.join(',')});`),0);assert.equal(count(`SELECT COUNT(*) FROM assessment_slot WHERE id IN (${slots.join(',')});`),0);baseline=snapshot();
  for(let i=0;i<users.length;i++){
    const id=users[i],role=i<2?4:3,name=i===2?'Dr Alternatives Alpha':i===3?'Dr Alternatives Beta':'Alternatives Student '+i;
    sql(`INSERT INTO user (id,role_id,email,password,name,is_enable,is_canceled,is_delete,create_time) SELECT ${id},${role},'alternatives-${i}@wba.test',password,'${name}',1,0,0,NOW() FROM user WHERE id=900000001;`);madeUsers.push(id);
  }
  // Date change, time change, supervisor change; the remaining slots are negative controls and an original booking.
  const specs=[[14,9,alpha,'Mini-CEX','Prince of Wales Hospital',1],[21,14,alpha,'Mini-CEX','Prince of Wales Hospital',1],[14,14,beta,'Mini-CEX','Prince of Wales Hospital',1],
    [14,14,alpha,'DOP','Prince of Wales Hospital',1],[14,14,alpha,'Mini-CEX','Other Hospital',1],[14,14,alpha,'Mini-CEX','Prince of Wales Hospital',0],
    [22,14,alpha,'Mini-CEX','Prince of Wales Hospital',1],[13,10,beta,'Mini-CEX','Prince of Wales Hospital',1],[21,14,alpha,'DOP','Prince of Wales Hospital',1]];
  for(let i=0;i<specs.length;i++){
    const[day,hour,mentor,type,hospital,enabled]=specs[i],time=String(hour).padStart(2,'0')+':00:00';
    sql(`INSERT INTO assessment_slot (id,start_time,end_time,repeat_type,next_available_time,hospital,department,location,type,status,creator,create_time,is_enable,is_delete,version) VALUES (${slots[i]},TIMESTAMP(DATE_ADD(CURDATE(),INTERVAL ${day} DAY),'${time}'),DATE_ADD(TIMESTAMP(DATE_ADD(CURDATE(),INTERVAL ${day} DAY),'${time}'),INTERVAL 30 MINUTE),0,TIMESTAMP(DATE_ADD(CURDATE(),INTERVAL ${day} DAY),'${time}'),'${hospital}','General Medicine','Alternatives E2E','${type}',0,${mentor},NOW(),${enabled},0,0);`);madeSlots.push(slots[i]);
  }
  client=await request.newContext();browser=await chromium.launch({headless:true,channel:'msedge'});
  const page=await browser.newPage({viewport:{width:1440,height:1120},timezoneId:'Asia/Shanghai'});page.setDefaultTimeout(35000);page.on('pageerror',e=>pageErrors.push(e.message));
  await page.goto('http://127.0.0.1:5178/');await page.getByLabel('Email address').fill('alternatives-0@wba.test');await page.getByLabel('Password',{exact:true}).fill('WbaDemo!2026');
  const[logged]=await Promise.all([page.waitForResponse(r=>r.url().endsWith('/general/user/login')),page.getByRole('button',{name:'Sign in to WBA Platform',exact:true}).click()]);token=ok(await logged.json()).token;
  await page.waitForURL('**/home/student/dashboard');await page.goto('http://127.0.0.1:5178/home/student/bookSlot');
  const region=page.getByRole('region',{name:'AI booking assistant'}),date=payload(slots[0],'').startTime.slice(0,10);
  const question=`Find Mini-CEX sessions at Prince of Wales Hospital in General Medicine on ${date} between 14:00 and 16:00. The supervisor must be Dr Alternatives Alpha.`;
  async function ask(text){await page.locator('#booking-assistant-message').fill(text);const[r]=await Promise.all([page.waitForResponse(r=>r.url().endsWith('/bookingAssistant/search')),region.getByRole('button',{name:'Send request',exact:true}).click()]);return ok(await r.json());}
  let result=await ask(question);assert.equal(result.status,'NO_RESULTS');assert.deepEqual(result.candidates,[]);assert.equal(result.conditions.supervisorName,'Dr Alternatives Alpha');
  assert.deepEqual(result.alternatives.map(a=>a.kind),['DATE','TIME','SUPERVISOR']);
  assert.deepEqual(result.alternatives.flatMap(a=>a.candidates.map(c=>c.slotId)),[slots[1],slots[0],slots[2]]);
  for(const a of result.alternatives){assert.equal(a.conditions.type,'Mini-CEX');assert.equal(a.conditions.hospital,'Prince of Wales Hospital');assert.equal(a.conditions.department,'General Medicine');}
  assert.equal(count(`SELECT COUNT(*) FROM assessment_booking WHERE creator=${owner};`),0);
  await page.waitForFunction(()=>document.documentElement.scrollWidth<=innerWidth);await page.screenshot({path:`${output}/alternatives-desktop.png`,fullPage:true,animations:'disabled'});
  await page.setViewportSize({width:390,height:1000});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);await page.screenshot({path:`${output}/alternatives-mobile.png`,fullPage:true});await page.setViewportSize({width:1440,height:1120});
  checks.push('real model query has no exact matches; real DATE/TIME/SUPERVISOR alternatives remain separately labeled, preserve clinical type and placement, and exclude disabled or unrelated slots');
  const mentorGroup=region.getByRole('region',{name:'A different supervisor',exact:true});await mentorGroup.getByRole('button',{name:'Review alternative',exact:true}).click();
  let dialog=page.getByRole('dialog',{name:'Review booking request'});await dialog.waitFor();await dialog.getByText('This option changes your original search conditions.',{exact:true}).waitFor();
  assert.equal(await dialog.getByRole('button',{name:'Confirm booking request',exact:true}).isDisabled(),true);await dialog.getByRole('button',{name:'Back',exact:true}).click();assert.equal(count(`SELECT COUNT(*) FROM assessment_booking WHERE creator=${owner};`),0);
  await mentorGroup.getByRole('button',{name:'Review alternative',exact:true}).click();dialog=page.getByRole('dialog',{name:'Review booking request'});await dialog.waitFor();
  let receipt;await page.route('**/api/student/bookingAssistant/confirm',async route=>{receipt=ok(await(await route.fetch()).json());await route.abort('connectionreset');});
  await dialog.getByRole('checkbox').check();await dialog.getByRole('button',{name:'Confirm booking request',exact:true}).click();await dialog.getByRole('alert').waitFor();assert.ok(receipt.bookingId);
  await page.unroute('**/api/student/bookingAssistant/confirm');const[retry]=await Promise.all([page.waitForResponse(r=>r.url().endsWith('/bookingAssistant/confirm')),dialog.getByRole('button',{name:'Confirm booking request',exact:true}).click()]);assert.equal(ok(await retry.json()).bookingId,receipt.bookingId);
  await region.getByText('Request saved — awaiting supervisor approval',{exact:true}).waitFor();assert.equal(count(`SELECT COUNT(*) FROM assessment_booking WHERE creator=${owner};`),1);assert.equal(count(`SELECT COUNT(*) FROM message WHERE content LIKE '%Reference: ${receipt.bookingId}%';`),2);
  assert.equal(await region.getByRole('region',{name:'A different supervisor',exact:true}).count(),0);
  checks.push('changed conditions are repeated in preview; closing never books; explicit acceptance and lost-response retry create one Pending booking and remove the saved alternative');
  const old=ok(await api('/student/assessmentBooking/bookings',payload(slots[7],'_old'))),oldDate=old.startTime.slice(0,10);
  await region.getByRole('button',{name:'New search',exact:true}).click();result=await ask(`把我${oldDate}的Mini-CEX预约改到${date}14点到16点，新导师必须Dr Alternatives Alpha，医院Prince of Wales Hospital，科室General Medicine`);assert.equal(result.status,'BOOKINGS');
  if(result.replacementConditions){
    const[replacements]=await Promise.all([page.waitForResponse(r=>r.url().endsWith('/management/replacements')),region.getByRole('button',{name:'Reschedule',exact:true}).click()]);
    assert.equal(ok(await replacements.json()).status,'NO_RESULTS');
  }else{
    // The model can request a second turn for replacement details. Exercise the supported manual-target path.
    await region.getByRole('button',{name:'Reschedule',exact:true}).click();await page.locator('#booking-assistant-message').fill(question);
    const[replacements]=await Promise.all([page.waitForResponse(r=>r.url().endsWith('/bookingAssistant/search')),region.getByRole('button',{name:'Find replacements',exact:true}).click()]);
    assert.equal(ok(await replacements.json()).status,'NO_RESULTS');
  }
  await region.getByRole('region',{name:'Other times on the requested dates',exact:true}).getByRole('button',{name:'Review alternative reschedule',exact:true}).click();
  dialog=page.getByRole('dialog',{name:'Review booking changes'});await dialog.waitFor();assert.equal(count(`SELECT status FROM assessment_booking WHERE id=${old.bookingId};`),0);
  await dialog.getByRole('checkbox').check();await dialog.getByRole('button',{name:'Confirm reschedule',exact:true}).click();await region.getByText('Reschedule saved — replacement awaits supervisor approval',{exact:true}).waitFor();
  assert.equal(count(`SELECT status FROM assessment_booking WHERE id=${old.bookingId};`),4);assert.equal(count(`SELECT COUNT(*) FROM assessment_booking WHERE creator=${owner} AND assessment_slot_id=${slots[0]} AND status=0;`),1);
  checks.push('rescheduling also offers explicit alternatives and atomically cancels the original only after a replacement request succeeds');
  await region.getByRole('button',{name:'New search',exact:true}).click();result=await ask(question);const later=result.alternatives.find(a=>a.kind==='DATE').candidates[0];
  const selection={assessmentSlotId:later.slotId,slotVersion:later.slotVersion,startTime:later.startTime,endTime:later.endTime};const draft=ok(await api('/student/bookingAssistant/prepare',selection));
  const login=await(await client.post('http://127.0.0.1:5178/api/general/user/login',{data:{email:'alternatives-1@wba.test',password:'WbaDemo!2026'}})).json();const otherToken=ok(login).token;
  ok(await api('/student/assessmentBooking/bookings',payload(slots[8],'_taken'),otherToken));
  assert.notEqual((await api('/student/bookingAssistant/confirm',{draftId:draft.draftId,confirmed:true})).code,2000);
  assert.equal(count(`SELECT COUNT(*) FROM assessment_booking WHERE creator=${owner} AND assessment_slot_id=${later.slotId};`),0);
  const refreshed=ok(await api('/student/bookingAssistant/search',{message:question}));assert.equal(refreshed.status,'NO_RESULTS');assert.ok(!refreshed.alternatives.flatMap(a=>a.candidates).some(c=>c.slotId===later.slotId));
  checks.push('availability is rechecked at confirmation; a different template occupying the supervisor removes the suggestion without creating a booking or failing the search');
  const exactDate=payload(slots[6],'').startTime.slice(0,10),beforeExact=count(`SELECT COUNT(*) FROM assessment_booking WHERE creator=${owner};`);
  const exact=ok(await api('/student/bookingAssistant/search',{message:question.replace(date,exactDate)}));
  assert.equal(exact.status,'READY');assert.ok(exact.candidates.some(c=>c.slotId===slots[6]));assert.deepEqual(exact.alternatives,[]);
  assert.equal(count(`SELECT COUNT(*) FROM assessment_booking WHERE creator=${owner};`),beforeExact);
  checks.push('a genuine exact match still returns READY without alternatives and performs no booking write');
  assert.deepEqual(pageErrors,[]);report={date:'2026-10-26',completedAt:new Date().toISOString(),checks,pageErrors,githubPushes:0,originalDeploymentModified:false};
})().catch(e=>{console.error(e.stack);process.exitCode=1;}).finally(async()=>{
  try{cleanup();if(baseline!==undefined)assert.equal(snapshot(),baseline,'Existing bookings or goals changed.');if(report){report.existingDataPreserved=true;report.fixtureCleanupVerified=true;fs.writeFileSync(`${output}/booking-alternatives-live-report.json`,JSON.stringify(report,null,2));console.log(JSON.stringify({passed:true,checks,pageErrors,existingDataPreserved:true}));}}
  catch(e){console.error('Alternative fixture cleanup verification failed:',e.message);process.exitCode=1;}if(client)await client.dispose();if(browser)await browser.close();
});
