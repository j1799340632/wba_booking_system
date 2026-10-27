// Opt-in actual provider calls and collision-checked synthetic business records. Never fabricates live accuracy.
if(!process.argv.includes('--live')){console.log('Use --live to verify AI Insights with two real model queries, Edge and the isolated demo. Fixtures are removed afterward.');process.exit(0);}
const {chromium,request}=require(process.env.WBA_PLAYWRIGHT_MODULE || 'playwright');
const {spawnSync}=require('node:child_process');
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const docker=process.env.WBA_DOCKER_EXE || 'docker',base='http://127.0.0.1:5178';
const output=path.resolve(__dirname,'../../.local/wba-demo/browser');fs.mkdirSync(output,{recursive:true});
const ids={student:'990000000001',mentor:'990000000002',admin:'990000000003',other:'990000000004',superAdmin:'990000000005'};
const slots=['990000000101','990000000102'],createdUsers=[],createdSlots=[],checks=[],pageErrors=[];
let client,browser,studentToken,adminToken,baseline,baselineEvents,report;
function sql(statement){const result=spawnSync(docker,['exec','-i','wba-ai-demo-mysql-1','sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -u wba_demo -N -B unsw_wba'],{input:statement,encoding:'utf8',timeout:15000});if(result.status!==0)throw new Error('Insights fixture SQL check failed.');return result.stdout.trim();}
const count=query=>Number(sql(query));
const ok=body=>{assert.equal(body.code,2000,body.msg || body.message);return body.data;};
async function api(endpoint,data,token=studentToken,method='get'){return(await client[method](base+'/api'+endpoint,{data,headers:{Authorization:`Bearer ${token}`}})).json();}
async function login(email){for(let n=0;n<4;n++){const body=await(await client.post(base+'/api/general/user/login',{data:{email,password:'WbaDemo!2026'}})).json();if(body.code!==5011)return ok(body).token;await new Promise(resolve=>setTimeout(resolve,1100));}throw new Error('Login remained rate limited.');}
async function dismissNotices(page){for(let i=0;i<12;i++){const close=page.locator('[data-sonner-toast][data-front="true"] [data-close-button]').first();if(!await close.isVisible())break;await close.click();await page.waitForTimeout(200);}}
const snapshot=()=>JSON.stringify([
  sql(`SELECT id,creator,assessment_slot_id,start_time,end_time,status,score,feedback,completed_time,is_delete,version FROM assessment_booking WHERE creator NOT IN (${ids.student},${ids.other}) ORDER BY id;`),
  sql(`SELECT student_id,start_date,deadline,goals_json,version FROM student_assessment_plan WHERE student_id NOT IN (${ids.student},${ids.other}) ORDER BY student_id;`),
]);
function cleanup(){
  for(const owner of createdUsers){sql(`DELETE FROM assistant_event WHERE actor_id=${owner}; DELETE FROM message WHERE creator=${owner} OR receiver=${owner};`);if(owner===ids.student || owner===ids.other)sql(`DELETE FROM assessment_booking WHERE creator=${owner}; DELETE FROM student_assessment_plan WHERE student_id=${owner};`);}
  if(createdSlots.length)sql(`DELETE FROM assessment_slot WHERE id IN (${createdSlots.join(',')});`);
  for(const id of createdUsers)sql(`DELETE FROM user_settings WHERE user_id=${id}; DELETE FROM user WHERE id=${id};`);
}
(async()=>{
  assert.equal(count(`SELECT COUNT(*) FROM user WHERE id IN (${Object.values(ids).join(',')}) OR email LIKE 'insights-%@wba.test';`),0);
  assert.equal(count(`SELECT COUNT(*) FROM assessment_slot WHERE id IN (${slots.join(',')});`),0);
  assert.equal(count(`SELECT COUNT(*) FROM assistant_event WHERE actor_id IN (${Object.values(ids).join(',')});`),0);
  baseline=snapshot();baselineEvents=sql(`SELECT id FROM assistant_event ORDER BY id;`).split('\n').filter(Boolean);
  for(const [kind,id]of Object.entries(ids)){const role=kind==='mentor'?3:kind==='admin'?2:kind==='superAdmin'?1:4;sql(`INSERT INTO user(id,role_id,email,password,name,is_enable,is_canceled,is_delete,create_time) SELECT ${id},${role},'insights-${kind}@wba.test',password,'${kind==='mentor'?'Dr Insights Alpha':'Insights '+kind}',1,0,0,NOW() FROM user WHERE id=900000001;`);createdUsers.push(id);}
  client=await request.newContext();studentToken=await login('insights-student@wba.test');adminToken=await login('insights-admin@wba.test');
  const initial=ok(await api('/registrar/assistantInsights',undefined,adminToken));assert.equal(initial.timeZone,'Australia/Sydney');
  const shift=days=>{const date=new Date(initial.endDate+'T00:00:00Z');date.setUTCDate(date.getUTCDate()+days);return date.toISOString().slice(0,10);};
  for(let index=0;index<slots.length;index++){const date=shift(14+index);sql(`INSERT INTO assessment_slot(id,start_time,end_time,repeat_type,next_available_time,hospital,department,location,type,status,creator,create_time,is_enable,is_delete,version) VALUES(${slots[index]},'${date} 14:00:00','${date} 14:30:00',0,'${date} 14:00:00','Prince of Wales Hospital','General Medicine','Insights E2E Only','Mini-CEX',0,${ids.mentor},NOW(),1,0,0);`);createdSlots.push(slots[index]);}
  const question=`请找${shift(14)}14点到16点的Mini-CEX，只要Dr Insights Alpha，医院Prince of Wales Hospital，科室General Medicine`;
  const found=ok(await api('/student/bookingAssistant/search',{message:question},studentToken,'post'));assert.equal(found.status,'READY');assert.ok(found.candidates.some(candidate=>candidate.slotId===slots[0]));
  const clarification=ok(await api('/student/bookingAssistant/search',{message:'帮我看看'},studentToken,'post'));assert.equal(clarification.status,'CLARIFY');
  const candidate=found.candidates.find(candidate=>candidate.slotId===slots[0]);const selection={assessmentSlotId:candidate.slotId,slotVersion:candidate.slotVersion,startTime:candidate.startTime,endTime:candidate.endTime};
  const draft=ok(await api('/student/bookingAssistant/prepare',selection,studentToken,'post'));
  const receipt=ok(await api('/student/bookingAssistant/confirm',{draftId:draft.draftId,confirmed:true},studentToken,'post'));
  const replay=ok(await api('/student/bookingAssistant/confirm',{draftId:draft.draftId,confirmed:true},studentToken,'post'));assert.equal(String(replay.bookingId),String(receipt.bookingId));assert.equal(replay.replayed,true);
  const other=await login('insights-other@wba.test');assert.notEqual((await api('/student/bookingAssistant/confirm',{draftId:draft.draftId,confirmed:true},other,'post')).code,2000);
  const replacements=ok(await api('/student/bookingAssistant/management/replacements',{bookingId:String(receipt.bookingId),conditions:{startTime:shift(15)+' 00:00:00',endTime:shift(16)+' 00:00:00',type:'Mini-CEX'}},studentToken,'post'));assert.equal(replacements.status,'READY');
  const change=ok(await api('/student/bookingAssistant/management/prepare',{bookingId:String(receipt.bookingId),bookingVersion:0,action:'CANCEL'},studentToken,'post'));
  ok(await api('/student/bookingAssistant/management/confirm',{draftId:change.draftId,confirmed:true},studentToken,'post'));assert.equal(ok(await api('/student/bookingAssistant/management/confirm',{draftId:change.draftId,confirmed:true},studentToken,'post')).replayed,true);
  assert.equal(count(`SELECT COUNT(*) FROM assessment_booking WHERE creator=${ids.student};`),1);assert.equal(count(`SELECT status FROM assessment_booking WHERE creator=${ids.student};`),4);
  assert.equal(count(`SELECT COUNT(*) FROM assistant_event WHERE actor_id IN (${ids.student},${ids.other});`),10);
  assert.equal(count(`SELECT COUNT(*) FROM assistant_event WHERE actor_id=${ids.student} AND stage='SEARCH' AND model_calls=1;`),2);
  assert.equal(count(`SELECT COUNT(*) FROM assistant_event WHERE actor_id=${ids.student} AND stage<>'SEARCH' AND model_calls<>0;`),0);
  assert.equal(count(`SELECT COUNT(*) FROM assistant_event WHERE actor_id=${ids.student} AND stage='CONFIRM_BOOKING' AND outcome='SAVED';`),1);
  assert.equal(count(`SELECT COUNT(*) FROM assistant_event WHERE actor_id=${ids.student} AND stage='CONFIRM_BOOKING' AND outcome='REPLAYED';`),1);
  checks.push('two real DeepSeek requests produce READY and CLARIFY observations; booking review, new save, replay, rejected ownership, replacement search and cancellation/replay are recorded as ten distinct operations; only the two search operations call the model and one booking is created');
  // Backdate only this run's synthetic observations to test an isolated historical calendar day; business timestamps stay unchanged.
  const yesterday=shift(-1),emptyDate=shift(-3);assert.equal(count(`SELECT COUNT(*) FROM assistant_event WHERE created_at>='${yesterday} 00:00:00' AND created_at<'${initial.endDate} 00:00:00';`),0);
  sql(`UPDATE assistant_event SET created_at='${yesterday} 12:00:00' WHERE actor_id IN (${ids.student},${ids.other});`);
  const summary=ok(await api(`/registrar/assistantInsights?startDate=${yesterday}&endDate=${yesterday}`,undefined,adminToken));
  assert.equal(summary.observedOperations,10);assert.equal(summary.searchRequests,2);assert.equal(summary.newBookingRequests,1);assert.equal(summary.bookingConfirmAttempts,3);assert.equal(summary.bookingConfirmReplays,1);assert.equal(summary.bookingConfirmFailures,1);assert.equal(summary.modelCalls,2);
  const measured=sql(`SELECT SUM(CASE WHEN usage_known=1 THEN total_tokens ELSE 0 END),SUM(CASE WHEN usage_known=1 THEN model_calls ELSE 0 END) FROM assistant_event WHERE actor_id IN (${ids.student},${ids.other});`).split('\t').map(Number);
  assert.equal(summary.totalTokens,measured[0]);assert.equal(summary.callsWithKnownUsage,measured[1]);assert.ok(summary.searchP95Ms>=summary.searchP50Ms);assert.equal(summary.days.length,1);
  const encoded=JSON.stringify(summary);for(const value of [question,'insights-student@wba.test','Dr Insights Alpha','"actorId"','"bookingId"','"feedback"','"apiKey"'])assert.equal(encoded.includes(value),false);
  const empty=ok(await api(`/registrar/assistantInsights?startDate=${emptyDate}&endDate=${emptyDate}`,undefined,adminToken));assert.equal(empty.observedOperations,0);assert.equal(empty.searchP50Ms,null);assert.equal(empty.searchP95Ms,null);assert.equal(empty.callsWithKnownUsage,0);
  assert.notEqual((await api(`/registrar/assistantInsights?startDate=${shift(-32)}&endDate=${initial.endDate}`,undefined,adminToken)).code,2000);
  checks.push('historical Sydney date filtering matches exact SQL counts, including one new save, one replay and one rejection; reported tokens match provider-backed observations; empty periods keep latency unknown; aggregation returns no conversation, booking identifiers, names, grades or credentials');
  const mentor=await login('insights-mentor@wba.test');assert.notEqual((await api('/registrar/assistantInsights',undefined,studentToken)).code,2000);assert.notEqual((await api('/registrar/assistantInsights',undefined,mentor)).code,2000);
  const before=count(`SELECT COUNT(*) FROM assistant_event WHERE actor_id IN (${Object.values(ids).join(',')});`);assert.notEqual((await api('/student/bookingAssistant/search',{message:'Find sessions'},mentor,'post')).code,2000);assert.equal(count(`SELECT COUNT(*) FROM assistant_event WHERE actor_id IN (${Object.values(ids).join(',')});`),before);
  const superAdmin=await login('insights-superAdmin@wba.test');assert.equal(ok(await api(`/registrar/assistantInsights?startDate=${yesterday}&endDate=${yesterday}`,undefined,superAdmin)).observedOperations,10);
  checks.push('administrator and super-administrator can read aggregate observations; student and supervisor cannot; rejected supervisor use of the student assistant is not counted as student usage');
  browser=await chromium.launch({headless:true,channel:'msedge'});const page=await browser.newPage({viewport:{width:1440,height:1100},timezoneId:'America/Los_Angeles'});page.setDefaultTimeout(30000);page.on('pageerror',error=>pageErrors.push(error.message));
  await new Promise(resolve=>setTimeout(resolve,1100));await page.goto(base);await page.getByLabel('Email address').fill('insights-admin@wba.test');await page.getByLabel('Password',{exact:true}).fill('WbaDemo!2026');await page.getByRole('button',{name:'Sign in to WBA Platform',exact:true}).click();await page.waitForURL('**/home/admin/dashboard');
  await page.getByRole('button',{name:'AI Insights',exact:true}).click();await page.getByRole('heading',{name:'AI Insights',exact:true}).waitFor();
  await page.getByLabel('From',{exact:true}).fill(yesterday);await page.getByLabel('Through, inclusive',{exact:true}).fill(yesterday);await dismissNotices(page);
  const [loaded]=await Promise.all([page.waitForResponse(response=>response.url().includes('/registrar/assistantInsights?')),page.getByRole('button',{name:'Apply and refresh',exact:true}).click()]);assert.equal(ok(await loaded.json()).observedOperations,10);
  const metrics=page.getByRole('region',{name:'Observed assistant metrics'});await metrics.getByText('66.7%',{exact:true}).waitFor();
  await page.waitForFunction(()=>document.documentElement.scrollWidth<=innerWidth);await page.screenshot({path:`${output}/assistant-insights-desktop.png`,animations:'disabled'});
  await page.setViewportSize({width:390,height:1000});await page.waitForFunction(()=>document.documentElement.scrollWidth<=innerWidth);await page.screenshot({path:`${output}/assistant-insights-mobile.png`,animations:'disabled'});await page.setViewportSize({width:1440,height:1100});
  const [download]=await Promise.all([page.waitForEvent('download'),page.getByRole('button',{name:'Download summary',exact:true}).click()]);const exportPath=path.join(output,'assistant-insights-export.json');await download.saveAs(exportPath);const exported=JSON.parse(fs.readFileSync(exportPath,'utf8'));assert.equal(exported.observedOperations,10);assert.equal(exported.modelCalls,2);
  checks.push('real admin page shows the historical period and 66.7% confirmation-attempt save rate, retains Sydney dates in a Los Angeles browser, fits 1440px and 390px screens, and downloads the same aggregate JSON summary');
  await page.getByLabel('From',{exact:true}).fill(emptyDate);await page.getByLabel('Through, inclusive',{exact:true}).fill(emptyDate);await page.getByRole('button',{name:'Apply and refresh',exact:true}).click();await page.getByText(/No observations in this period/).waitFor();
  await page.route('**/api/registrar/assistantInsights?*',route=>route.abort('connectionreset'));await page.getByRole('button',{name:'Last 7 days',exact:true}).click();await page.getByRole('alert').filter({hasText:'The last loaded report remains below.'}).waitFor();assert.equal(await page.getByText(/No observations in this period/).count(),1);
  await page.unroute('**/api/registrar/assistantInsights?*');await dismissNotices(page);
  const [recovered]=await Promise.all([page.waitForResponse(response=>response.url().includes('/registrar/assistantInsights?')),page.getByRole('button',{name:'Last 7 days',exact:true}).click()]);assert.ok(ok(await recovered.json()).observedOperations>=10);await page.getByRole('alert').filter({hasText:'The last loaded report remains below.'}).waitFor({state:'hidden'});
  assert.equal(count(`SELECT COUNT(*) FROM assistant_event WHERE actor_id IN (${ids.student},${ids.other});`),10);assert.deepEqual(pageErrors,[]);
  checks.push('empty periods show explicit no-data guidance instead of invented success; a failed refresh retains the previous report, recovery works, and admin reads/downloads never increment assistant operation counts');
  report={date:'2026-10-26',completedAt:new Date().toISOString(),scope:'real Edge + two DeepSeek requests + Spring + isolated MySQL/Redis; fixture observations backdated only for period testing',checks,pageErrors,providerCalls:2,githubPushes:0,originalDeploymentModified:false};
})().catch(error=>{console.error(error.stack);process.exitCode=1;}).finally(async()=>{
  try{cleanup();if(baseline!==undefined)assert.equal(snapshot(),baseline,'Existing bookings or targets changed.');if(baselineEvents?.length)assert.equal(count(`SELECT COUNT(*) FROM assistant_event WHERE id IN (${baselineEvents.join(',')});`),baselineEvents.length);
    if(report){assert.equal(count(`SELECT COUNT(*) FROM user WHERE id IN (${Object.values(ids).join(',')});`),0);assert.equal(count(`SELECT COUNT(*) FROM assessment_slot WHERE id IN (${slots.join(',')});`),0);assert.equal(count(`SELECT COUNT(*) FROM assistant_event WHERE actor_id IN (${Object.values(ids).join(',')});`),0);report.existingDataPreserved=true;report.fixtureCleanupVerified=true;fs.writeFileSync(path.join(output,'assistant-insights-live-report.json'),JSON.stringify(report,null,2));console.log(JSON.stringify({passed:true,checks,pageErrors,existingDataPreserved:true}));}}
  catch(error){console.error('Insights fixture cleanup verification failed:',error.message);process.exitCode=1;}if(client)await client.dispose();if(browser)await browser.close();
});
