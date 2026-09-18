async function fetchButtons() {
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

async function fetchCameras() {
  try {
    const response = await fetch('/api/v1/camerasReady');

    if (!response.ok) {
      throw new Error('Cameras readiness request failed');
    }

    return await response.json();

  } catch (error) {
    console.error('Failed to get cameras readiness:', error);
    return null;
  }
}

let automodeTimer=null,
autoProgress=0,
autoSeconds=0,
acquisitionSeconds=1800;
const scanningSeconds=10,weighingSeconds=15;

const autoParamsBtn=$('#autoParamsBtn'),
autoState=$('#autoState'),
autoProgressBar=$('#autoProgressBar'),
sampleProgressBar=$('#sampleProgressBar'),
autoPercent=$('#autoPercent'),
samplePercent=$('#samplePercent'),
autoRemaining=$('#autoRemaining'),
autoStep=$('#autoStep');

// Sample workfield, queue, sequence and measurement parameters.
let samples=Array.from({length:30},(_,i)=>({x:12+(i%6)*15.2,y:14+Math.floor(i/6)*18,id:`S-${String(i+1).padStart(3,'0')}`,mass:`${(512.4+i*7.3).toFixed(1)} g`,enabled:true}));
const map=$('.field-map'), rows=$('#sampleRows');

function selectSample( index) {
    $$('.sample-dot').forEach( (dot,i)=>dot.classList.toggle('selected',i===index));
    $$('#sampleRows tr').forEach((row,i)=>row.classList.toggle('selected',i===index))
}


function renderWorkfield() {
    map.innerHTML = '';
    rows.innerHTML = '';

    samples.forEach((sample, i) => {
        const d = document.createElement('button');
        d.type = 'button';
        d.className = 'sample-dot';
        d.dataset.sample = String(i);
        d.style.left = `calc(${sample.x}% - 22px)`;
        d.style.top = `calc(${sample.y}% - 22px)`;
        d.innerHTML = `<b>${i + 1}</b>`;
        d.setAttribute('aria-label', sample.id);
        d.addEventListener('mouseenter', () => selectSample(i));
        d.addEventListener('focus', () => selectSample(i));
        d.addEventListener('click', () => selectSample(i));
        map.append(d);

        rows.insertAdjacentHTML(
            'beforeend',
            `<tr tabindex="0" data-sample="${i}">
                <td>${i + 1}</td>
                <td class="select-column">
                <input class="sample-enabled" type="checkbox" ${sample.enabled ? 'checked' : ''} aria-label="Measure ${sample.id}">
                </td>
                <td class="barcode-id"><span>Not scanned</span></td>
                <td>${sample.mass}</td>
                <td>Ready</td>
            </tr>`
        );
  });

    $$('#sampleRows tr').forEach((row, i) => {
        row.addEventListener('mouseenter', () => selectSample(i));
        row.addEventListener('focus', () => selectSample(i));
        row.addEventListener('click', () => selectSample(i));
        row.addEventListener('keydown', event => {
            if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();         selectSample(i);
            }
        });
    });
  
    $$('.sample-enabled').forEach((checkbox, i) => {
        checkbox.addEventListener('click', event => event.stopPropagation());
        checkbox.addEventListener('keydown', event => event.stopPropagation());
        checkbox.addEventListener('change', () => {
            if (autoIsActive()) return;
            samples[i].enabled = checkbox.checked;
            updateSampleSelection();
        });
    });

    updateSampleSelection();
}

renderWorkfield();

function updateSampleSelection(){
  const enabled=samples.filter(sample=>sample.enabled).length;
  $('#selectedSamplesTitle').textContent=`${enabled} sample${enabled===1?'':'s'} selected`;
  const all=$('#selectAllSamples');all.checked=enabled===samples.length;all.indeterminate=enabled>0&&enabled<samples.length;
  $$('.sample-dot').forEach((dot,i)=>dot.classList.toggle('excluded',!samples[i].enabled));
  renderAutoProgress();
}



$('#selectAllSamples').addEventListener('change',event=>{if(autoIsActive())return;samples.forEach(sample=>sample.enabled=event.target.checked);$$('.sample-enabled').forEach(checkbox=>checkbox.checked=event.target.checked);updateSampleSelection()});

$('#scanWorkfield').addEventListener( 'click',
    async ()=> {
        const data = await fetchScannedTable();

        if (!data) {
          showToast('Failed to scan workfield');
          return;
        }

        // Update samples array with scanned coordinates from backend
        samples = data.map((item, i) => ({
          id: item.id || `S-${String(i + 1).padStart(3, '0')}`,
          x: item.x,
          y: item.y,
          mass: item.mass || '0.0 g',
          enabled: true
        }));

        renderWorkfield();

        $('#lastScanTime').textContent = new Date().toLocaleTimeString([], {
          hour: '2-digit',
          minute: '2-digit',
          second: '2-digit'
        });

        showToast(`Workfield scanned · ${samples.length} samples found`);
    }
);

const autoStart=$('#autoStart'),
autoPause=$('#autoPause'),
autoStop=$('#autoStop');

let buttonsTimer = null;

async function updateButtonsState() {
  const data = await fetchButtons();

  if (!data) return;

  autoStart.disabled = data.AutoStart !== true;
  autoPause.disabled = data.AutoPause !== true;
  autoStop.disabled = data.AutoStop !== true;
}

updateButtonsState();

buttonsTimer = setInterval(() => {
  updateButtonsState();
}, 1000);


const camReadyLbl=$('#camera_ready_label');
const scanWorkfieldBtn = $('#scanWorkfield');
let camersReadyTimer = null;
async function updateCamerasReadyState() {
  const data = await fetchCameras();
  if (!data) return;
  
  const isReady = Boolean(data.camerasReady);

  camReadyLbl.classList.toggle('offline', !isReady);
  camReadyLbl.innerHTML = `<span></span>Cameras ${isReady ? 'online' : 'offline'}`;  
  //camReadyLbl.textContent = data.camerasReady ? 'Cameras online' : 'Cameras offline';

  // Disable button when cameras are offline
  if( scanWorkfieldBtn) {
    scanWorkfieldBtn.disabled = !isReady;
  }
}
updateCamerasReadyState();
camersReadyTimer = setInterval(() => { updateCamerasReadyState(); }, 1000);



function formatTime(seconds){seconds=Math.max(0,Math.ceil(seconds));const h=String(Math.floor(seconds/3600)).padStart(2,'0'),m=String(Math.floor(seconds%3600/60)).padStart(2,'0'),s=String(seconds%60).padStart(2,'0');return `${h}:${m}:${s}`}
function renderAutoProgress(){
  const selectedIndices=samples.map((sample,index)=>sample.enabled?index:-1).filter(index=>index>=0),selectedCount=selectedIndices.length;
  const duration=scanningSeconds+weighingSeconds+acquisitionSeconds,total=duration*selectedCount;
  autoSeconds=Math.min(autoSeconds,total);autoProgress=total?autoSeconds/total*100:0;
  const finished=selectedCount>0&&autoSeconds>=total,completed=Math.min(selectedCount,Math.floor(autoSeconds/duration)),elapsed=finished?duration:autoSeconds%duration;
  
    //const started = autoState.classList.contains('running') || autoState.classList.contains('paused') || autoSeconds > 0;
    const started = autoState?.classList?.contains('running') || autoState?.classList?.contains('paused') || autoSeconds > 0;
  
    //const stageMetric=$('.stage-metric'),running=autoState.classList.contains('running');stageMetric.classList.toggle('active-stage',running);stageMetric.classList.toggle('stopped-stage',!running);
    const stageMetric=$('.stage-metric'),running=Boolean(autoState?.classList?.contains('running'));if(stageMetric){stageMetric.classList.toggle('active-stage',running);stageMetric.classList.toggle('stopped-stage',!running);}
    
  const stage=elapsed<scanningSeconds?'scanning':elapsed<scanningSeconds+weighingSeconds?'weighing':'spectrum';
  const names={scanning:'Scanning',weighing:'Weighing',spectrum:'Spectrum acquisition'};
  const percent=Math.floor(autoProgress);
  const sampleProgress=finished?100:Math.floor(elapsed/duration*100);
  autoProgressBar.style.width=`${autoProgress}%`;sampleProgressBar.style.width=`${sampleProgress}%`;autoPercent.textContent=`${percent}%`;samplePercent.textContent=`${sampleProgress}%`;autoRemaining.textContent=formatTime(total-autoSeconds);
  $('#queueProgress').setAttribute('aria-valuenow',percent);
  $('#sampleProgress').setAttribute('aria-valuenow',sampleProgress);
  const currentIndex=selectedCount?selectedIndices[Math.min(completed,selectedCount-1)]:-1;$('#currentSample').textContent=currentIndex>=0?`Sample ${Math.min(completed+1,selectedCount)} of ${selectedCount} · ${samples[currentIndex].id}`:'No samples selected';
  $('#sampleElapsed').textContent=formatTime(elapsed);
  $('#sampleRemaining').textContent=formatTime(duration-elapsed);
  const steps=[['Sample pickup',3],['Scan',5],['Weigh',5],['Open Lead Shield',2],['Place in Lead Shield',3],['Close Lead Shield',2],['Spectrum acquisition',acquisitionSeconds],['Open shield',2],['Return sample',3]];

    let stepIndex = 0, stepEnd = steps[ 0][ 1];
    while( stepIndex < steps.length - 1 && elapsed >= stepEnd) {
        stepIndex++;
        stepEnd += steps[ stepIndex][ 1]
    }
    const stepName=steps[stepIndex][0];
    $('#currentStep').textContent=`Current step ${stepIndex+1} of ${steps.length}`;
    autoStep.textContent=finished?'Sequence complete':!started?'Sample pickup':`${stepName}${autoState.classList.contains('paused')?' · Paused':''}`;
    $$('#stageTimeline span').forEach((item,i)=>{item.classList.toggle('complete',i<stepIndex);item.classList.toggle('active',i===stepIndex)});
    $('#batchCount').textContent=`${completed} of ${selectedCount} completed`;
    $('#queueSummary').textContent=`Selected ${selectedCount} · Ready ${Math.max(0,selectedCount-completed-(started&&!finished?1:0))} · Completed ${completed}`;
  
    //syncProcessControls();
    if (typeof syncProcessControls === 'function') syncProcessControls();
  
    $$('#sampleRows tr').forEach((row,i)=>{const order=selectedIndices.indexOf(i),isCurrent=i===currentIndex&&started&&!finished;const status=!samples[i].enabled?'Excluded':order>=0&&order<completed?'Complete':isCurrent?(autoState.classList.contains('paused')?'Paused':stepName):'Ready';row.cells[4].textContent=status;row.classList.toggle('processing',isCurrent);row.classList.toggle('excluded',!samples[i].enabled);row.querySelector('.sample-enabled').disabled=autoIsActive()});
  $$('.sample-dot').forEach((dot,i)=>{const order=selectedIndices.indexOf(i);dot.classList.toggle('complete',order>=0&&order<completed);dot.classList.toggle('processing',i===currentIndex&&started&&!finished)});
  $('#selectAllSamples').disabled=autoIsActive();
  if(started&&!finished&&currentIndex>=0)selectSample(currentIndex);
}
function setAutoState(state,label){
  autoState.className='auto-state header-auto-state '+state;
  autoState.innerHTML='<i aria-hidden="true"></i><span><b>'+label+'</b><small>Auto mode</small><small class="rgs-serial">RGS sn: 2534-26</small></span>';
  syncProcessControls();
}

function runAutoSequence(){
  if(processCondition||!hvIsReady())
    return;
  clearInterval(automodeTimer);
  renderAutoProgress();
  automodeTimer=setInterval(()=>{if(
  processCondition||!hvIsReady())
  return;

autoSeconds++;
renderAutoProgress();
if(autoProgress>=100){
  clearInterval(automodeTimer);
automodeTimer=null;
setAutoState('idle','Complete');
}},
1000)}

autoStart.addEventListener('click',()=>{if(!samples.some(sample=>sample.enabled)){showToast('Select at least one sample');return}if(!hvIsReady()||processCondition||recoveryHold||serviceMeasurement.active||autoIsActive())return;if(autoProgress>=100){autoProgress=0;autoSeconds=0}setAutoState('running','Measuring');runAutoSequence();showToast('Automated sequence started')});
autoPause.addEventListener('click',()=>{if(processCondition||!autoIsActive())return;const paused=autoState.classList.contains('paused');if(paused){if(!hvIsReady())return;setAutoState('running','Measuring');autoPause.innerHTML='<span>Ⅱ</span> Pause';runAutoSequence();showToast('Automated sequence resumed')}else{clearInterval(automodeTimer);setAutoState('paused','Paused');autoPause.innerHTML='<span>▶</span> Resume';renderAutoProgress();showToast('Automated sequence paused')}});
autoStop.addEventListener('click',()=>{clearInterval(automodeTimer);autoProgress=0;autoSeconds=0;setAutoState('stopped','Stopped');renderAutoProgress();autoPause.innerHTML='<span>Ⅱ</span> Pause';showToast('Automated sequence stopped')});
const autoParamsModal=$('#autoParamsModal');
function closeAutoParams(){autoParamsModal.hidden=true}
function openAutoParams(){const editable=!autoState.classList.contains('running');
  $$('#autoParamsForm select,#autoParamsForm input,#autoParamsForm textarea').forEach(control=>control.disabled=!editable);$('#saveAutoParams').hidden=!editable;$('#autoParamsAccess').innerHTML=editable?'<span>Parameters can be edited before starting or while the sequence is paused.</span>':'<span><b>View only:</b> pause or stop the sequence to edit these parameters.</span>';autoParamsModal.hidden=false;(editable?$('#autoBatchId'):$('#closeAutoParams')).focus()}
autoParamsBtn.addEventListener('click',openAutoParams);
$('#closeAutoParams').addEventListener('click',closeAutoParams);
$('#cancelAutoParams').addEventListener('click',closeAutoParams);
autoParamsModal.addEventListener('click',event=>{if(event.target===autoParamsModal)closeAutoParams()});
$('#autoParamsForm').addEventListener('submit',event=>{event.preventDefault();const value=Number($('#autoPresetTime').value),unit=$('#autoPresetUnit').value,multiplier=unit==='hours'?3600:unit==='minutes'?60:1;if(!Number.isFinite(value)||value<=0){showToast('Enter a preset time greater than zero');return}const oldDuration=scanningSeconds+weighingSeconds+acquisitionSeconds,completed=Math.floor(autoSeconds/oldDuration),elapsed=autoSeconds%oldDuration;acquisitionSeconds=value*multiplier;const newDuration=scanningSeconds+weighingSeconds+acquisitionSeconds;autoSeconds=completed*newDuration+Math.min(elapsed,newDuration);renderAutoProgress();closeAutoParams();showToast('Measurement parameters saved for Auto mode')});

function autoIsActive(){
    return Boolean(autoState?.classList?.contains('running') || autoState?.classList?.contains('paused'));
}

// Fetch scanned table coordinates from backend
async function fetchScannedTable() {
  try {
    const response = await fetch('/api/getScannedTable');

    if (!response.ok) {
      throw new Error('Get scanned table request failed');
    }

    return await response.json();

  } catch (error) {
    console.error('Failed to get scanned table:', error);
    return null;
  }
}