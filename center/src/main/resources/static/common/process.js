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
  if(currentView==='manual'){
    $('#sampleMeasurementType').disabled=active;
    $('#backgroundMeasurementType').disabled=active;
  }
  if(currentView==='auto')autoStart.disabled=active||blocked||recoveryHold||!hvIsReady()||!samples.some(sample=>sample.enabled);
  for(const button of [$('#autoStart'),$('#startMeasure'),$('#headerPause'),$('#autoPause'),$('#manualPause')].filter(Boolean))button.title=!hvIsReady()?'Detector HV must be READY to start or resume a measurement':'';
  renderHvControls();
  if(currentView==='manual'){
    $('#manualPause').textContent=manual&&paused?'Resume':'Pause';
    renderServiceSpectrumProgress(manual,paused);
  }
}
$('#headerPause').addEventListener('click',()=>{
  if(autoIsActive())autoPause.click();
  else pauseServiceMeasurement()
});
$('#headerStop').addEventListener('click',()=>{
  if(autoIsActive())autoStop.click();
  else if(serviceMeasurement.active)finishServiceMeasurement()
  });
function pauseForHvChange(){
  if(currentView==='auto' && autoState.classList.contains('running')){
    setAutoState('paused','Paused');
    autoPause.innerHTML='<span>▶</span> Resume';
  }
  if(serviceMeasurement.active)serviceMeasurement.paused=true;
}
function renderHvControls(data = null) {
  const turnOn = $('#turnHvOn');
  const turnOff = $('#turnHvOff');

  if (data) {
    if (turnOn) {
      turnOn.disabled = data.TurnHvOn !== true;
    }

    if (turnOff) {
      turnOff.disabled = data.TurnHvOff !== true;
    }
  }

  hvRenderedState = hvState;

  const labels = {
    off: 'HV OFF',
    raise: 'Raise',
    ready: 'READY',
    fall: 'Fall',
    leak: 'LEAK'
  };


  $('#indicator_detector').className =
    'module hv-' + hvState;


  $('#detectorHvStatus').innerHTML =
    labels[hvState] +
    (
      ['raise', 'fall'].includes(hvState)
        ? '<span class="hv-dots" aria-hidden="true"><i>.</i><i>.</i><i>.</i></span>'
        : ''
    );


  $('#detectorHvStatus')
    .setAttribute(
      'aria-label',
      labels[hvState]
    );


  const messages = {
    off: 'Turn HV on and wait for READY before starting or resuming a measurement.',
    raise: 'Detector voltage is rising. Wait for READY.',
    ready: 'Detector HV is ready. Measurements can be started or resumed.',
    fall: 'Detector voltage is falling. Start and resume are unavailable.',
    leak: 'Leakage current exceeds the limit. Turn HV off and resolve the issue before continuing.'
  };


  const help = $('#diagHvHelp');

  if (help) {
    help.textContent = messages[hvState];
  }


  const value = $('#diagHvValue');

  if (value && data) {
    const voltage = Number(
      storedSettings['parameters.hv']?.voltage ?? 2600
    );

    if (data.turnHvOn === true) {
      value.textContent = voltage + ' V';
    } else if (data.turnHvOff === true) {
      value.textContent = 'HV OFF';
    } else {
      value.textContent = 'Unknown';
    }
  }

  const leakage = $('#diagLeakage');

  if (leakage && data) {
    leakage.textContent =
      data.detector_leakage ?? 'Unknown';
  }
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
window.addEventListener('rgs:leakage-current',event=>{
  const current=Number(event.detail?.current);
  if(Number.isFinite(current)&&current>=0)hvLeakageCurrent=current});
// Faults pause the existing process. A critical condition is latched for this session.
function reportProcessCondition(state,reason=''){
  if(!['fault','critical'].includes(state)||processCondition==='critical')return;
  processCondition=state;conditionReason=reason;recoveryHold=false;
  if(autoIsActive()){setAutoState('paused','Paused');autoPause.innerHTML='<span>▶</span> Resume';}
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
$('#endRecoveryHold').addEventListener('click',()=>{
  if(processCondition)return;
  recoveryHold=false;
  syncProcessControls()
});

window.addEventListener('rgs:condition',event=>{
  const detail=event.detail||{};
  reportProcessCondition(detail.state,typeof detail.reason==='string'?detail.reason:'')});
const diagnosticsDialog=$('#diagnosticsDialog');

async function fetchDiagnostics() {
  try {
    const response = await fetch('/api/v1/diagnostics');

    if (!response.ok) {
      throw new Error('Diagnostics request failed');
    }

    const data = await response.json();
    return data;

  } catch (error) {
    console.error('Failed to load diagnostics:', error);
    return null;
  }
}

let diagnosticsTimer = null;

async function openDiagnostics(event) {
  let data;
  data = await fetchDiagnostics();

  const target=$('#diagnosticsModules');
  target.replaceChildren();
  const moduleStatus=name=>{const module=$$('.system-strip .module').find(item=>item.querySelector('span')?.textContent===name);
  return module?.querySelector('b')?.textContent||'Online'};
  const hvEnabled=['raise','ready','fall','leak'].includes(hvState);
  const hvVoltage=Number(storedSettings['parameters.hv']?.voltage??2600);
  const diagnostics = [
  ['PLC', data?.plc, '', 'diagPlc'],
  ['Gripper', data?.gripper, '', 'diagGripper'],
  ['7th axis', data?.axis, '', 'diag7Axis'],
  ['GammaPro', data?.gammapro, '', 'diagGammapro'],
  ['Shield', data?.shield, '', 'diagShield'],
  ['Detector HV', data?.turnHvOn ? `${hvVoltage} V` : 'HV OFF', '', 'diagHvValue'],
  ['Detector leakage current (pA)', data?.detector_leakage, 'metric', 'diagLeakage'],
  ['Detector temperature (°C)', data?.detector_temperature, 'metric', 'diagTemperature'],
  ['MCA', data?.MCA, '', 'diagMca'],
  ['Cooling system', data?.cooling_system, '', 'diagCooler'],
  ['LN₂ level (%)', data?.ln2_lev, 'metric', 'diagLn2'],
  ['UPS (%)', data?.ups, 'metric', 'diagUps'],
  ['Machine vision camera 1', data?.camera1, '', 'diagCamera1'],
  ['Machine vision camera 2', data?.camera2, '', 'diagCamera2'],
  ['Scales', data?.scales, '', 'diagScales'],
  ['Scanner', data?.scanner, '', 'diagScanner']
];

  diagnostics.forEach(([label,value,state='', id])=>{
    const row=document.createElement('div');
    row.className='diagnostics-row'+(state?' '+state:'');
    const name=document.createElement('span'),
    status=document.createElement('b');
    status.id = id;
    name.textContent=label;
    status.textContent=value;
    if(label==='Detector HV'){
      row.id='detectorHvDiagnostics';
      status.id='diagHvValue'}
    if(label==='Detector leakage current (pA)')
      status.id='diagLeakage';
    if(state==='inactive')
      status.title='Available only when detector HV is switched on';
    row.append(name,status);
    target.append(row);
    if(label==='Detector temperature (°C)'){
      const controls=document.createElement('section');
      controls.className='diagnostics-hv-controls';
      controls.setAttribute('aria-label','Detector high voltage controls');
      controls.innerHTML='<div><strong>Detector high voltage</strong><p id="diagHvHelp"></p></div><div class="hv-control-actions"><button id="turnHvOn" class="primary" type="button">Turn HV ON</button><button id="turnHvOff" class="danger-outline" type="button">Turn HV OFF</button></div>';
      target.append(controls);
      
      $('#turnHvOn').addEventListener('click',turnHvOn);
      $('#turnHvOff').addEventListener('click',()=>{diagnosticsDialog.close();$('#hvConfirmModal').hidden=false;$('#cancelHvOff').focus()});
    }
  });

  diagnosticsTimer = setInterval(async () => {
    data = await fetchDiagnostics();

    if (!data) return;

    $('#diagPlc').textContent = data?.plc ?? 'Unknown';
    $('#diagGripper').textContent = data?.gripper ?? 'Unknown';
    $('#diagGammapro').textContent = data?.gammapro ?? 'Unknown';
    $('#diagShield').textContent = data?.shield ?? 'Unknown';
    $('#diagTemperature').textContent = data?.detector_temperature ?? 'Unknown';
    $('#diagMca').textContent = data?.MCA ?? 'Unknown';
    $('#diagCooler').textContent = data?.cooling_system ?? 'Unknown';
    $('#diagLn2').textContent = data?.ln2_lev ?? 'Unknown';
    $('#diagUps').textContent = data?.ups ?? 'Unknown';
    $('#diagScales').textContent = data?.scales ?? 'Unknown';
    $('#diagScanner').textContent = data?.scanner ?? 'Unknown';
    $('#diag7Axis').textContent = data?.axis ?? 'Unknown';
    // $('#diagCamera1').textContent = data?.camera1 ?? 'Unknown';
    // $('#diagcamera2').textContent = data?.camera2 ?? 'Unknown';

    renderHvControls(data);
  }, 1000);

  diagnosticsDialog.showModal();
  renderHvControls(data); 
  if(event?.currentTarget?.id==='indicator_detector'){
    const row=$('#detectorHvDiagnostics');
    row.classList.add('focused');
    row.scrollIntoView({block:'center'})
  }
}
$('#viewDiagnostics').addEventListener('click',openDiagnostics);
$$('.system-strip .module').forEach(module=>module.addEventListener('click',openDiagnostics));
$('#closeDiagnostics').addEventListener('click',()=>
  diagnosticsDialog.close()
);
diagnosticsDialog.addEventListener('close', () => {
  if (diagnosticsTimer !== null) {
    clearInterval(diagnosticsTimer);
    diagnosticsTimer = null;
  }
});
diagnosticsDialog.addEventListener('click',event=>{if(event.target===diagnosticsDialog){const r=diagnosticsDialog.getBoundingClientRect();if(event.clientX<r.left||event.clientX>r.right||event.clientY<r.top||event.clientY>r.bottom)diagnosticsDialog.close()}});
const hvConfirmModal=$('#hvConfirmModal');
$('#cancelHvOff').addEventListener('click',()=>hvConfirmModal.hidden=true);
$('#confirmHvOff').addEventListener('click',()=>{hvConfirmModal.hidden=true;turnHvOff()});
hvConfirmModal.addEventListener('click',event=>{if(event.target===hvConfirmModal)hvConfirmModal.hidden=true});
