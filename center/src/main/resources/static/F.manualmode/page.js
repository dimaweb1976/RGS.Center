// Manual acquisition, sample/background parameters and confirmation.
function renderServiceSpectrumProgress(active,paused){
  const unit=$('#manualDurationUnit').value,multiplier=unit==='hours'?3600:unit==='minutes'?60:1;
  const presetDuration=Math.max(0,Number($('#manualDuration').value)||0)*multiplier;
  const duration=active||serviceMeasurement.elapsed>0?serviceMeasurement.duration:presetDuration;
  const percent=duration?Math.min(100,Math.floor(serviceMeasurement.elapsed/duration*100)):0;
  const remaining=Math.max(0,duration-serviceMeasurement.elapsed);
  $('#serviceProgressBar').value=percent;$('#serviceProgressBar').setAttribute('aria-valuenow',String(percent));
  $('#serviceProgressPercent').textContent=percent+'%';$('#serviceTimeRemaining').textContent=formatTime(remaining);
  const complete=serviceMeasurement.elapsed>0&&serviceMeasurement.elapsed>=duration;
  $('#serviceProgressState').textContent=active?(paused?'Paused':serviceMeasurement.kind==='background'?'Background acquisition':'Acquiring spectrum'):complete?'Complete':serviceMeasurement.elapsed>0?'Stopped':'Ready';
  $('.manual-spectrum-progress').classList.toggle('paused',active&&paused);$('.manual-spectrum-progress').classList.toggle('complete',complete);
}
function startServiceMeasurement(kind){
  if(!hvIsReady()||processCondition||recoveryHold||autoIsActive()||serviceMeasurement.active)return;
  const manualUnit=$('#manualDurationUnit').value;
  const manualMultiplier=manualUnit==='hours'?3600:manualUnit==='minutes'?60:1;
  const duration=Number($('#manualDuration').value)*manualMultiplier;
  if(!Number.isFinite(duration)||duration<=0){showToast('Enter a measurement duration greater than zero');return}
  Object.assign(serviceMeasurement,{active:true,paused:false,kind,elapsed:0,duration});
  serviceMeasurement.timer=setInterval(()=>{if(serviceMeasurement.paused||processCondition||!hvIsReady())return;serviceMeasurement.elapsed=Math.min(serviceMeasurement.elapsed+1,serviceMeasurement.duration);if(serviceMeasurement.elapsed>=serviceMeasurement.duration)finishServiceMeasurement(true);else syncProcessControls()},1000);
  syncProcessControls();showToast(kind==='manual'?'Manual measurement started':'Background acquisition started');
}
function pauseServiceMeasurement(){if(processCondition||!serviceMeasurement.active||(serviceMeasurement.paused&&!hvIsReady()))return;serviceMeasurement.paused=!serviceMeasurement.paused;syncProcessControls()}
function finishServiceMeasurement(completed=false){
  clearInterval(serviceMeasurement.timer);serviceMeasurement.timer=null;serviceMeasurement.active=false;serviceMeasurement.paused=false;
  syncProcessControls();
}
$('#manualPause').addEventListener('click',pauseServiceMeasurement);$('#manualStop').addEventListener('click',()=>finishServiceMeasurement());
const modal=$('#confirmModal');
function openConfirm(title,text){$('#modalTitle').textContent=title;$('#modalText').textContent=text;modal.hidden=false}
let selectedServiceMeasurementType='manual';
function setServiceMeasurementType(kind){
  selectedServiceMeasurementType=kind==='background'?'background':'manual';
  const background=selectedServiceMeasurementType==='background';
  $('#sampleMeasurementType').classList.toggle('active',!background);$('#sampleMeasurementType').setAttribute('aria-pressed',String(!background));
  $('#backgroundMeasurementType').classList.toggle('active',background);$('#backgroundMeasurementType').setAttribute('aria-pressed',String(background));
  $$('.sample-only').forEach(label=>{label.classList.toggle('parameter-disabled',background);label.querySelectorAll('input,select,button').forEach(control=>control.disabled=background)});
  $('#startMeasure').innerHTML=background?'<span>▶</span> Start background acquisition':'<span>▶</span> Start measurement';
  serviceParametersStatus.textContent='Unsaved changes';
}
$('#sampleMeasurementType').addEventListener('click',()=>setServiceMeasurementType('manual'));
$('#backgroundMeasurementType').addEventListener('click',()=>setServiceMeasurementType('background'));
$('#startMeasure').onclick=()=>{pendingServiceKind=selectedServiceMeasurementType;const duration=$('#manualDuration').value+' '+$('#manualDurationUnit').selectedOptions[0].textContent+'.';if(pendingServiceKind==='background')openConfirm('Start background acquisition?','Confirm that the measurement chamber is empty. Acquisition will run for '+duration);else openConfirm('Start manual measurement?','Spectrum acquisition will run for '+duration)};
const serviceParameters=$('#serviceParameters'),serviceParametersStatus=$('#serviceParametersStatus'),saveServiceParameters=$('#saveServiceParameters');
serviceParameters.addEventListener('input',()=>{serviceParametersStatus.textContent='Unsaved changes';if(!serviceMeasurement.active&&serviceMeasurement.elapsed===0)renderServiceSpectrumProgress(false,false)});
serviceParameters.addEventListener('change',()=>{serviceParametersStatus.textContent='Unsaved changes';if(!serviceMeasurement.active&&serviceMeasurement.elapsed===0)renderServiceSpectrumProgress(false,false)});
saveServiceParameters.addEventListener('click',()=>{serviceParametersStatus.textContent='Parameters saved';showToast('Manual mode parameters saved')});
$('#cancelModal').onclick=()=>modal.hidden=true;
$('#confirmStart').onclick=()=>{modal.hidden=true;startServiceMeasurement(pendingServiceKind)};
modal.addEventListener('click',e=>{if(e.target===modal)modal.hidden=true});
