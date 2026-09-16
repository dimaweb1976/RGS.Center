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

setInterval( fetchIndicators, 1000);
function fetchIndicators() {
    fetch('/api/v1/indicators')
        .then(response => {
            if (!response.ok) {
              throw new Error('Indicators request failed');
            }
            return response.json();
        })
        .then(data => {
            // Process incoming JSON payload
            console.log('Indicators data:', data);
            
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
    });
}
}
