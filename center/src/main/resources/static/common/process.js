// Shared process controls, HV lifecycle, conditions and diagnostics.
function syncProcessControls(){
  const auto=autoIsActive(),manual=serviceMeasurement.active,active=auto||manual;
  const paused=recoveryHold||(auto?autoState.classList.contains('paused'):serviceMeasurement.paused);
  const blocked=!!processCondition;
  const status=processCondition||(paused?'paused':auto?'auto':manual?'manual':'idle');
  setRobotStatus(status);
  $('#conditionNotice').hidden=!blocked&&!recoveryHold;
  $('#conditionNotice').className='condition-notice '+status;
  $('#conditionTitle').textContent=processCondition==='critical'?'CRITICAL':processCondition==='fault'?'FAULT':'PAUSED';
  $('#conditionMessage').textContent=processCondition==='critical'?(conditionReason||'The process cannot continue. Service intervention is required.'):processCondition==='fault'?(conditionReason||'Inspect the complex and resolve the issue before confirming.'): 'The issue is confirmed resolved. The process remains paused.';
  $('#acknowledgeFault').hidden=processCondition!=='fault';
  $('#endRecoveryHold').hidden=!recoveryHold||active;
  $('#headerPause').disabled=blocked||(paused&&!hvIsReady());
  if(currentView==='auto')autoPause.disabled=!auto||blocked||(paused&&!hvIsReady());
  if($('#confirmStart'))$('#confirmStart').disabled=active||blocked||recoveryHold||!hvIsReady();
  const progress=auto?autoProgress:serviceMeasurement.elapsed/serviceMeasurement.duration*100;
  const percent=Math.min(100,Math.max(0,Math.floor(progress)));
  const mode=auto?'Auto mode':serviceMeasurement.kind==='background'?'Background acquisition':'Manual measurement';
  $('#headerProcess').hidden=!active||currentView===(auto?'auto':'manual');
  $('#headerPause').innerHTML='<span aria-hidden="true">'+(paused?'▶':'Ⅱ')+'</span>';
  $('#headerPause').setAttribute('aria-label',(paused?'Resume':'Pause')+' measurement');
  $('#headerPause').title=(paused?'Resume':'Pause')+' measurement';
  $('#headerProgress').value=percent;
  $('#headerProgress').setAttribute('aria-label',mode+' progress');
  $('#headerProgress').setAttribute('aria-valuetext',percent+'%'+(paused?' · Paused':''));
  $('#headerProgressText').textContent=percent+'%'+(paused?' · Paused':'');
  $('#headerProcess').classList.toggle('paused',paused);
  $('#headerProcess').title=mode;
  if($('#startMeasure'))$('#startMeasure').disabled=active||blocked||recoveryHold||!hvIsReady();
  if(currentView==='manual'){$('#sampleMeasurementType').disabled=active;$('#backgroundMeasurementType').disabled=active;}
  if(currentView==='auto')autoStart.disabled=active||blocked||recoveryHold||!hvIsReady()||!samples.some(sample=>sample.enabled);
  for(const button of [$('#autoStart'),$('#startMeasure'),$('#headerPause'),$('#autoPause'),$('#manualPause')].filter(Boolean))button.title=!hvIsReady()?'Detector HV must be READY to start or resume a measurement':'';
  renderHvControls();
  if(currentView==='manual'){$('#manualPause').disabled=!manual||blocked||(paused&&!hvIsReady());$('#manualStop').disabled=!manual;
  $('#manualPause').textContent=manual&&paused?'Resume':'Pause';
  renderServiceSpectrumProgress(manual,paused);}
}
$('#headerPause').addEventListener('click',()=>{if(autoIsActive())autoPause.click();else pauseServiceMeasurement()});
$('#headerStop').addEventListener('click',()=>{if(autoIsActive())autoStop.click();else if(serviceMeasurement.active)finishServiceMeasurement()});
function pauseForHvChange(){
  if(currentView==='auto'&&autoState.classList.contains('running')){clearInterval(autoTimer);autoTimer=null;setAutoState('paused','Paused');autoPause.innerHTML='<span>▶</span> Resume';}
  if(serviceMeasurement.active)serviceMeasurement.paused=true;
}
function renderHvControls(){
  const turnOn=$('#turnHvOn'),turnOff=$('#turnHvOff');
  if(turnOn)turnOn.disabled=!['off','fall'].includes(hvState)||processCondition==='critical';
  if(turnOff)turnOff.disabled=!['raise','ready','leak'].includes(hvState);
  hvRenderedState=hvState;
  const labels={off:'HV OFF',raise:'Raise',ready:'READY',fall:'Fall',leak:'LEAK'};
  $('#indicator_detector').className='module hv-'+hvState;
  $('#detectorHvStatus').innerHTML=labels[hvState]+(['raise','fall'].includes(hvState)?'<span class="hv-dots" aria-hidden="true"><i>.</i><i>.</i><i>.</i></span>':'');
  $('#detectorHvStatus').setAttribute('aria-label',labels[hvState]);
  const messages={off:'Turn HV on and wait for READY before starting or resuming a measurement.',raise:'Detector voltage is rising. Wait for READY.',ready:'Detector HV is ready. Measurements can be started or resumed.',fall:'Detector voltage is falling. Start and resume are unavailable.',leak:'Leakage current exceeds the limit. Turn HV off and resolve the issue before continuing.'};
  const help=$('#diagHvHelp');if(help)help.textContent=messages[hvState];
  const value=$('#diagHvValue');if(value){const voltage=Number(storedSettings['parameters.hv']?.voltage??2600);value.textContent=['raise','ready','fall','leak'].includes(hvState)?labels[hvState]+' · '+voltage+' V':'HV OFF'}
  const leakage=$('#diagLeakage');if(leakage)leakage.textContent=['raise','ready','fall','leak'].includes(hvState)?hvLeakageCurrent.toFixed(1):'—';
}
async function turnHvOn(){
    try {
        const res = await fetch('/gammapro-boson/hv/on', { method: 'POST' });
        if (!res.ok) throw new Error('HV ON failed: ' + res.status);
    } catch (err) {
        console.error(err);
    }

  //if(!['off','fall'].includes(hvState)||processCondition==='critical')return;
  //clearTimeout(hvRampTimer);clearTimeout(hvLeakTimer);
  //hvState='raise';syncProcessControls();
  //hvRampTimer=setTimeout(()=>{hvState='ready';syncProcessControls()},10000);
  //hvLeakTimer=setTimeout(()=>{
  //  const threshold=Number(storedSettings['parameters.hv']?.leakage??50);
  //  if(hvState==='ready'&&hvLeakageCurrent>threshold){hvState='leak';pauseForHvChange();reportProcessCondition('fault','Detector leakage current exceeds the configured limit. Turn HV off and inspect the detector.');syncProcessControls()}
  //},60000);
}
async function turnHvOff(){
    try {
        const res = await fetch('/gammapro-boson/hv/off', { method: 'POST' });
        if (!res.ok) throw new Error('HV OFF failed: ' + res.status);
    } catch (err) {
        console.error(err);
    }
  //if(!['raise','ready','leak'].includes(hvState))return;
  //clearTimeout(hvRampTimer);clearTimeout(hvLeakTimer);
  //hvState='fall';pauseForHvChange();syncProcessControls();
  //hvRampTimer=setTimeout(()=>{hvState='off';syncProcessControls()},10000);
}
// Demo input for a future detector-current feed; normal demo current is zero.
window.addEventListener('rgs:leakage-current',event=>{const current=Number(event.detail?.current);if(Number.isFinite(current)&&current>=0)hvLeakageCurrent=current});
// Faults pause the existing process. A critical condition is latched for this session.
function reportProcessCondition(state,reason=''){
  if(!['fault','critical'].includes(state)||processCondition==='critical')return;
  processCondition=state;conditionReason=reason;recoveryHold=false;
  if(autoIsActive()){clearInterval(autoTimer);autoTimer=null;setAutoState('paused','Paused');autoPause.innerHTML='<span>▶</span> Resume';}
  if(serviceMeasurement.active)serviceMeasurement.paused=true;
  syncProcessControls();
}
function acknowledgeProcessFault(){
  if(processCondition!=='fault')return;
  processCondition=null;conditionReason='';
  recoveryHold=!autoIsActive()&&!serviceMeasurement.active;
  syncProcessControls();
  showToast('Issue confirmed resolved. Process paused.');
}
$('#acknowledgeFault').addEventListener('click',acknowledgeProcessFault);
$('#endRecoveryHold').addEventListener('click',()=>{if(processCondition)return;recoveryHold=false;syncProcessControls()});
window.addEventListener('rgs:condition',event=>{const detail=event.detail||{};reportProcessCondition(detail.state,typeof detail.reason==='string'?detail.reason:'')});
const diagnosticsDialog=$('#diagnosticsDialog');
function openDiagnostics(event){
  const target=$('#diagnosticsModules');target.replaceChildren();
  const moduleStatus=name=>{const module=$$('.system-strip .module').find(item=>item.querySelector('span')?.textContent===name);return module?.querySelector('b')?.textContent||'Online'};
  const hvEnabled=['raise','ready','fall','leak'].includes(hvState);
  const hvVoltage=Number(storedSettings['parameters.hv']?.voltage??2600);
  const diagnostics=[
    ['PLC',moduleStatus('PLC')],
    ['Gripper','Online'],
    ['7th axis','Online'],
    ['GammaPro',moduleStatus('GammaPro')],
    ['Shield',moduleStatus('Shield')],
    ['Detector HV',hvEnabled?`${hvVoltage} V`:'HV OFF',hvEnabled?'metric':''],
    ['Detector leakage current (pA)',hvEnabled?hvLeakageCurrent.toFixed(1):'—',hvEnabled?'metric':'inactive'],
    ['Detector temperature (°C)','−168','metric'],
    ['MCA',moduleStatus('MCA')],
    ['Cooling system',moduleStatus('Cooling system')],
    ['LN₂ level (%)','78','metric'],
    ['UPS (%)','100','metric'],
    ['Machine vision camera 1','Online'],
    ['Machine vision camera 2','Online'],
    ['Scales','Online'],
    ['Scanner','Online']
  ];
  diagnostics.forEach(([label,value,state=''])=>{
    const row=document.createElement('div');row.className='diagnostics-row'+(state?' '+state:'');
    const name=document.createElement('span'),status=document.createElement('b');name.textContent=label;status.textContent=value;
    if(label==='Detector HV'){row.id='detectorHvDiagnostics';status.id='diagHvValue'}
    if(label==='Detector leakage current (pA)')status.id='diagLeakage';
    if(state==='inactive')status.title='Available only when detector HV is switched on';
    row.append(name,status);target.append(row);
    if(label==='Detector temperature (°C)'){
      const controls=document.createElement('section');controls.className='diagnostics-hv-controls';controls.setAttribute('aria-label','Detector high voltage controls');
      controls.innerHTML='<div><strong>Detector high voltage</strong><p id="diagHvHelp"></p></div><div class="hv-control-actions"><button id="turnHvOn" class="primary" type="button">Turn HV ON</button><button id="turnHvOff" class="danger-outline" type="button">Turn HV OFF</button></div>';
      target.append(controls);
      $('#turnHvOn').addEventListener('click',turnHvOn);
      $('#turnHvOff').addEventListener('click',()=>{diagnosticsDialog.close();$('#hvConfirmModal').hidden=false;$('#cancelHvOff').focus()});
    }
  });
  diagnosticsDialog.showModal();
  renderHvControls();
  if(event?.currentTarget?.id==='indicator_detector'){const row=$('#detectorHvDiagnostics');row.classList.add('focused');row.scrollIntoView({block:'center'})}
}
$('#viewDiagnostics').addEventListener('click',openDiagnostics);
$$('.system-strip .module').forEach(module=>module.addEventListener('click',openDiagnostics));
$('#closeDiagnostics').addEventListener('click',()=>diagnosticsDialog.close());
diagnosticsDialog.addEventListener('click',event=>{if(event.target===diagnosticsDialog){const r=diagnosticsDialog.getBoundingClientRect();if(event.clientX<r.left||event.clientX>r.right||event.clientY<r.top||event.clientY>r.bottom)diagnosticsDialog.close()}});
const hvConfirmModal=$('#hvConfirmModal');
$('#cancelHvOff').addEventListener('click',()=>hvConfirmModal.hidden=true);
$('#confirmHvOff').addEventListener('click',()=>{hvConfirmModal.hidden=true;turnHvOff()});
hvConfirmModal.addEventListener('click',event=>{if(event.target===hvConfirmModal)hvConfirmModal.hidden=true});
