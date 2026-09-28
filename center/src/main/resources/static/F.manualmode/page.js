let buttonsTimer = null;
let barcodeAvailabilityTimer = null;
let scalesAvailabilityTimer = null;

async function fetchButton() {
  try {
    const response = await fetch('/api/v1/buttonsState');

    if (!response.ok) {
      throw new Error('Automode button fetch request failed');
    }

    return await response.json();

  } catch (error) {
    console.error('Failed to update buttons:', error);
    return null;
  }
}

async function updateButtonsState() {
  const data = await fetchButton();

  if (!data) return;

  const start = $('#startMeasure');
  const pause = $('#manualPause');
  const stop = $('#manualStop');
  const openShield = $('#openShield');
  const closeShield = $('#closeShield');

  if (start) {
    start.disabled = data.ManualStart !== true;
  }

  if (pause) {
    pause.disabled = data.ManualPause !== true;
  }

  if (stop) {
    stop.disabled = data.ManualStop !== true;
  }

  if (openShield) {
    openShield.disabled = data.ManualOpenShield !== true;
  }

  if (closeShield) {
    closeShield.disabled = data.ManualCloseShield !== true;
  }
}

updateButtonsState();

buttonsTimer = setInterval(() => {
  updateButtonsState();
}, 1000);

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


document.addEventListener('DOMContentLoaded', () => {
    const scanBtn = document.getElementById('scanBarcodeBtn');
    const sampleIdInput = document.getElementById('sampleIdInput');
    let pollInterval = null;

    if (!scanBtn || !sampleIdInput) {
        return;
    }

    scanBtn.addEventListener('click', async () => {
        try {
            scanBtn.disabled = true;
            const initialText = scanBtn.textContent;
            scanBtn.textContent = 'Scanning...';

            // 1. Запуск операции сканирования
            const triggerResponse = await fetch('/api/barcode/read', { method: 'POST' });
            if (!triggerResponse.ok) {
                throw new Error('Failed to trigger barcode read');
            }

            // 2. Поллинг результата каждые 300 мс
            pollInterval = setInterval(async () => {
                try {
                    const res = await fetch('/api/barcode/status');
                    if( !res.ok) return;

                    const data = await res.json();

                    // Если чтение завершилось
                    if( data && !data.scanning) {
                        clearInterval(pollInterval);
                        pollInterval = null;
                        scanBtn.disabled = false;
                        scanBtn.textContent = initialText;

                        if( data?.lastBarcode && data?.lastBarcode.trim().length > 0) {
                            sampleIdInput.value = data?.lastBarcode.trim();
                        }
                    }
                } catch (pollErr) {
                    clearInterval(pollInterval);
                    pollInterval = null;
                    scanBtn.disabled = false;
                    scanBtn.textContent = initialText;
                }
            }, 300);

        } catch (err) {
            scanBtn.disabled = false;
            scanBtn.textContent = 'Scan';
        }
    });
}); 

// Generic handler for scales measurement
async function triggerWeighing( buttonElement, inputElement) {
  console.log("weighting");
  console.log(buttonElement, inputElement);

  if( !buttonElement && !inputElement ) return;

    const originalText = buttonElement.textContent;
    try {
        buttonElement.disabled = true;
        buttonElement.textContent = 'Weighing...';

        const response = await fetch('/api/scales/weigh', { method: 'POST' });
        if( !response.ok) {
            const errData = await response.json().catch(() => ({}));
            throw new Error(errData.error || 'Weighing failed');
        }

        const data = await response.json();
        if( data.success) {
            // inputElement.value = data.mass;
            if( inputElement && data.unit) {
                inputElement.value = data?.mass;
            }
        }
    } catch( error) {
        alert( 'Error measuring mass: ' + error.message);
    } finally {
        buttonElement.disabled = false;
        buttonElement.textContent = originalText;
    }
}

// Bind both buttons inside DOMContentLoaded
const weighTareBtn =  document.getElementById( 'weighTareBtn');
const tareMassInput = document.getElementById( 'tareMassInput');
const tareMassUnit =  document.getElementById( 'tareMassUnit');

const weighGrossBtn =  document.getElementById( 'weighGrossBtn');
const grossMassInput = document.getElementById( 'grossMassInput');
const grossMassUnit =  document.getElementById( 'grossMassUnit');

if( weighTareBtn) {
    weighTareBtn.addEventListener( 'click', () => {
        triggerWeighing(weighTareBtn, tareMassInput, tareMassUnit);
    });
}

if( weighGrossBtn) {
    weighGrossBtn.addEventListener( 'click', () => {
        triggerWeighing(weighGrossBtn, grossMassInput, grossMassUnit);
    });
}

// Check scales hardware connection and update button states
async function updateScalesAvailability() {
  const weighTareBtn = document.getElementById('weighTareBtn');
  const weighGrossBtn = document.getElementById('weighGrossBtn');

  try {
    const response = await fetch('/api/scales/availability');
    if (!response.ok) {
      throw new Error('Availability endpoint unreachable');
    }

    const data = await response.json();
    const isAvailable = Boolean(data.available);

    if (weighTareBtn) {
      weighTareBtn.disabled = !isAvailable;
      weighTareBtn.title = isAvailable ? 'Trigger tare weighing' : 'Scales disconnected';
    }
    if (weighGrossBtn) {
      weighGrossBtn.disabled = !isAvailable;
      weighGrossBtn.title = isAvailable ? 'Trigger gross weighing' : 'Scales disconnected';
    }
  } catch (error) {
    if (weighTareBtn) weighTareBtn.disabled = true;
    if (weighGrossBtn) weighGrossBtn.disabled = true;
  }
}

// Initial check and periodic polling every 3 seconds

updateScalesAvailability();

scalesAvailabilityTimer = setInterval(
  updateScalesAvailability,
  3000
);



// Check scanner hardware connection and update button state
async function updateBarcodeAvailability() {
  const scanBarcodeBtn = document.getElementById('scanBarcodeBtn');
  if (!scanBarcodeBtn) return;

  try {
    const response = await fetch('/api/barcode/availability');
    if (!response.ok) {
      throw new Error('Barcode availability unreachable');
    }

    const data = await response.json();
    const isAvailable = Boolean(data.available);

    scanBarcodeBtn.disabled = !isAvailable;
    scanBarcodeBtn.title = isAvailable ? 'Scan barcode' : 'Scanner disconnected';
  } catch (error) {
    scanBarcodeBtn.disabled = true;
    scanBarcodeBtn.title = 'Scanner unavailable';
  }
}

// Initial check and periodic polling every 3 seconds

updateBarcodeAvailability();

barcodeAvailabilityTimer = setInterval(
  updateBarcodeAvailability,
  3000
);