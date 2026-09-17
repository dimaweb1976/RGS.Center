// A storage-denied file:// browser still renders the complete page.
function browserStorage(name){
  const memory=new Map();
  return {getItem(key){
    try{
      return window[name].getItem(key)??memory.get(key)??null
    }catch{
      return memory.get(key)??null}},setItem(key,value){memory.set(key,String(value));
    try{window[name].setItem(key,String(value))}
    catch{}
},
removeItem(key){memory.delete(key);
try{window[name].removeItem(key)}
catch{}
}}}
const safeSession=browserStorage('sessionStorage'),safeLocal=browserStorage('localStorage');
// Shared state, session demonstration, navigation and notifications.
const $=s=>document.querySelector(s), $$=s=>[...document.querySelectorAll(s)];
const params=new URLSearchParams(location.search);
function showToast(message){const toast=document.createElement('div');toast.className='toast';toast.textContent=message;document.body.append(toast);setTimeout(()=>toast.remove(),3200)}
const currentView=document.body.dataset.page;
const serviceMeasurement={active:false,paused:false,kind:'manual',elapsed:0,duration:1800,timer:null};
let pendingServiceKind='manual';
const complexState=['idle','auto','manual','background','paused','fault','critical'].includes(params.get('state'))?params.get('state'):'idle';
let processCondition=null,conditionReason='',recoveryHold=false;
let hvState='off',hvRenderedState=null,hvRampTimer=null,hvLeakTimer=null,hvLeakageCurrent=0;
function hvIsReady(){return hvState==='ready'}

function setRobotStatus(data) {
  const colors = {
    0: 'gray',
    1: 'idle',
    2: 'yellow',
    3: 'stopped'
  };

  const colorClass =
    colors[Number(data?.color)] || 'gray';

  const status = $('#robotState');
  const label = $('#robotStateLabel');

  if (!status || !label) {
    return;
  }

  status.className =
    'auto-state header-auto-state ' + colorClass;

  label.textContent =
    data?.status ?? 'Unknown';

  const description =
    $('#robotStatusDescription');

  if (description) {
    description.textContent =
      data?.description ?? '-';
  }

  const serial =
    $('#robotStatusSerial');

  if (serial) {
    serial.textContent =
      data?.sernum ?? '-';
  }
}

async function fetchMainIndicator() {
  try {
    const response = await fetch('/api/v1/MainIndicator');

    if (!response.ok) {
      throw new Error(
        'MainIndicator request failed: ' + response.status
      );
    }

    const data = await response.json();

    console.log('MainIndicator:', data);

    setRobotStatus(data);

  } catch (error) {
    console.error(
      'Failed to update MainIndicator:',
      error
    );
  }
}

fetchMainIndicator();

let mainIndicatorTimer = setInterval(
  fetchMainIndicator,
  1000
);

$('#logoutBtn').addEventListener('click',()=>{
  // Add server-side logout / token cleanup here.
  window.location.replace(new URL('Login/index.html',window.RGSRoot).href);
});

const settingsStorageKey='try2-settings-v1';
let storedSettings={};try{storedSettings=JSON.parse(safeLocal.getItem(settingsStorageKey)||'{}')||{}}catch{}
function applyInterfaceTheme(theme){const selected=theme==='dark'?'dark':'light';document.body.classList.toggle('theme-dark',selected==='dark');document.documentElement.style.colorScheme=selected}
applyInterfaceTheme(storedSettings['interface.appearance']?.theme||'light');
function autoIsActive(){return currentView==='auto'&&($('#autoState').classList.contains('running')||$('#autoState').classList.contains('paused'))}
function formatTime(seconds){seconds=Math.max(0,Math.ceil(seconds));return [Math.floor(seconds/3600),Math.floor(seconds%3600/60),seconds%60].map(n=>String(n).padStart(2,'0')).join(':')}
$$('.nav-item').forEach(link=>{const selected=link.dataset.view===currentView;link.classList.toggle('active',selected);if(selected)link.setAttribute('aria-current','page')});
