// Initialize the current instrument page after the shared init hook.
function initializePage(){
// Each HTML document owns just its page controller. No router or HTML loader.
if(currentView==='auto')renderAutoProgress();
if(complexState!=='idle'){hvState='ready';syncProcessControls()}
if(currentView==='auto'&&['auto','paused','fault','critical'].includes(complexState))autoStart.click();
if(currentView==='manual'&&['manual','background'].includes(complexState)){setServiceMeasurementType(complexState);startServiceMeasurement(complexState)}
if(currentView==='auto'&&complexState==='paused')autoPause.click();
if(['fault','critical'].includes(complexState))reportProcessCondition(complexState);
syncProcessControls();

let coolerHasData = false;
let indicatorRequestPending = false;
function updateGammaProIndicator(status) {
    const button = document.getElementById('indicator_gammapro');
    if (!button) return;
    const value = typeof status === 'string'
        ? status.trim().toUpperCase() : 'UNKNOWN';
    const states = {
        ONLINE: ['module ok', 'Online'],
        OFFLINE: ['module ok hv-leak', 'Offline'],
        IN_PROGRESS: ['module warn', 'In progress'],
        UNKNOWN: ['module ok hv-off', 'Unknown']
    };
    const [className, label] = states[value] || states.UNKNOWN;
    button.className = className;
    button.dataset.status = states[value] ? value : 'UNKNOWN';
    button.setAttribute('aria-label', `GammaPRO: ${label}`);
    const text = button.querySelector('b');
    if (text) text.textContent = label;
}
function updateCoolingIndicator(status) {
    const button = document.getElementById('indicator_cooler');
    if (!button) return;
    const value = typeof status === 'string' ? status.trim().toUpperCase() : 'NO_DATA';
    const states = {
        READY: ['module ok', 'Ready'],
        ONLINE: ['module ok', 'Online'],
        OFFLINE: ['module ok hv-leak', 'Offline'],
        ALERT: ['module ok hv-leak', 'Alert'],
        STALE: ['module ok hv-leak', 'Stale'],
        BUSY: ['module warn', 'Busy'],
        CONNECTING: ['module warn', 'Connecting'],
        NO_DATA: ['module ok hv-off', 'No data'],
        DISABLED: ['module ok hv-off', 'Disabled']
    };
    const [className, label] = states[value] || states.NO_DATA;
    button.className = className;
    const text = button.querySelector('b');
    if (text) text.textContent = label;
}
updateGammaProIndicator('UNKNOWN');
setInterval( fetchIndicators, 1000);
fetchIndicators();
function fetchIndicators() {
    // Не допускаем наложения запросов и запоздалого перезаписывания статуса.
    if (indicatorRequestPending) return;
    indicatorRequestPending = true;
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 5000);
    fetch('/api/v1/indicators', {
        signal: controller.signal,
        credentials: 'same-origin',
        cache: 'no-store'
    })
        .then(response => {
            if (!response.ok) {
              throw new Error('Indicators request failed');
            }
            return response.json();
        })
        .then(data => {
            // Process incoming JSON payload
            console.log('Indicators data:', data);
            updateGammaProIndicator(data.gammapro);
            updateCoolingIndicator(data.cooler);
            coolerHasData = typeof data.cooler === 'string' && data.cooler.trim() !== '';
            
            const shieldBtn = document.getElementById('indicator_shield');
            if (shieldBtn && data.shield !== null) {
                const statusLower = (String( data.shield)).toLowerCase();
                const label = shieldBtn.querySelector('b');

                if( statusLower === 'open')
                    shieldBtn.className = 'module ok hv-leak';
                else if( statusLower === 'closed')
                    shieldBtn.className = 'module ok';
                else
                    shieldBtn.className = 'module ok hv-off';
                if( label)
                    label.textContent = String( data.shield);
            }
            
            const plcBtn = document.getElementById('indicator_plc');
            if( plcBtn && data.plc !== null) {
                const statusLower = ( String( data.plc)).toLowerCase();            
                const label = plcBtn.querySelector( 'b');

                if( statusLower === 'fault')
                    plcBtn.className = 'module ok hv-leak';
                else if( statusLower === 'in process')
                    plcBtn.className = 'module ok warn';
                else
                    plcBtn.className = 'module ok';

            if( label)
                    label.textContent = String( data.plc);
          }
            
        })
    .catch(error => {
      // Handle network or parsing errors
      console.error('Failed to update indicators:', error);
      updateGammaProIndicator('UNKNOWN');
      updateCoolingIndicator(coolerHasData ? 'STALE' : 'NO_DATA');
    })
    .finally(() => {
      clearTimeout(timeout);
      indicatorRequestPending = false;
    });
}
}
