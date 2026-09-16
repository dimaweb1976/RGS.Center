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

fetchIndicators();

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
                
                if(label){
                    label.textContent = "Unknown";
                    
                    if( statusLower === 'open')
                        shieldBtn.className = 'module ok hv-leak';
                    else if( statusLower === 'closed')
                        shieldBtn.className = 'module ok';
                    else if( statusLower === 'in progress')
                        detectorBtn.className = 'module ok warn';
                    else
                        shieldBtn.className = 'module ok hv-off';

                  
                    if(statusLower === 'open' ||statusLower === 'closed' ||  statusLower === 'in progress'){
                       label.textContent = String( data.shield); 
                    }
                }      
            }
            
            const plcBtn = document.getElementById('indicator_plc');
            if( plcBtn && data.plc !== null) {
                const statusLower = ( String( data.plc)).toLowerCase();            
                const label = plcBtn.querySelector( 'b');
                
                if(label){
                    label.textContent = "Unknown";

                    if( statusLower === 'offline')
                        plcBtn.className = 'module ok hv-leak';
                    else if( statusLower === 'online')
                        plcBtn.className = 'module ok';
                    else if( statusLower === 'in progress')
                        plcBtn.className = 'module ok warn';
                    else
                        plcBtn.className = 'module ok hv-off';

                
                    if(statusLower === 'offline' || statusLower === 'online' ||  statusLower === 'in progress'){
                       label.textContent = String( data.plc); 
                    }
                  }  
          }

          const gammaproBtn = document.getElementById('indicator_gammapro');
            if( gammaproBtn && data.gammapro !== null) {
                const statusLower = ( String( data.gammapro)).toLowerCase();            
                const label = gammaproBtn.querySelector( 'b');
                
                if(label){
                    label.textContent = "Unknown";

                    if( statusLower === 'offline')
                        gammaproBtn.className = 'module ok hv-leak';
                    else if( statusLower === 'online')
                        gammaproBtn.className = 'module ok';
                    else if( statusLower === 'in progress')
                        gammaproBtn.className = 'module ok warn';
                    else
                        gammaproBtn.className = 'module ok hv-off';

                
                    if(statusLower === 'offline' || statusLower === 'online' ||  statusLower === 'in progress'){
                       label.textContent = String( data.gammapro); 
                    }
                } 
          }

          const coolerBtn = document.getElementById('indicator_cooler');
            if( coolerBtn && data.cooler !== null) {
                const statusLower = ( String( data.cooler)).toLowerCase();            
                const label = coolerBtn.querySelector( 'b');
                if(label){
                label.textContent = "Unknown";

                    if( statusLower === 'high temperature' ||  statusLower === 'overload')
                        coolerBtn.className = 'module ok hv-leak';
                    else if( statusLower === 'ready')
                        coolerBtn.className = 'module ok';
                    else if( statusLower === 'ln2 warning')
                        coolerBtn.className = 'module ok warn';
                    else
                        coolerBtn.className = 'module ok hv-off';

                
                    if(statusLower === 'high temperature' ||statusLower === 'overload' ||  statusLower === 'ready' ||  statusLower === 'ln2 warning'){
                       label.textContent = String( data.cooler); 
                    }
                } 
          }
          
          const mcaBtn = document.getElementById('indicator_mca');
            if( mcaBtn && data.MCA !== null) {
                const statusLower = ( String( data.MCA)).toLowerCase();            
                const label = mcaBtn.querySelector( 'b');
                if(label){
                    label.textContent = "Unknown";

                    if( statusLower === 'fail')
                        mcaBtn.className = 'module ok hv-leak';
                    else if( statusLower === 'ready')
                        mcaBtn.className = 'module ok';
                    else if( statusLower === 'in progress')
                        mcaBtn.className = 'module ok warn';
                    else
                        mcaBtn.className = 'module ok hv-off';

                
                    if(statusLower === 'fail' || statusLower === 'ready' || statusLower === 'in progress'){
                       label.textContent = String( data.MCA); 
                    }
                } 
          }

          const detectorBtn = document.getElementById('indicator_detector');
            if( detectorBtn && data.detector !== null) {
                const statusLower = ( String( data.detector)).toLowerCase();            
                const label = detectorBtn.querySelector( 'b');
                if(label){
                    label.textContent = "Unknown";

                    if( statusLower === 'fail')
                        detectorBtn.className = 'module ok hv-leak';
                    else if( statusLower === 'ready')
                        detectorBtn.className = 'module ok';
                    else if( statusLower === 'raise' || statusLower === 'fall')
                        detectorBtn.className = 'module ok warn';
                    else
                        detectorBtn.className = 'module ok hv-off';

                
                    if(statusLower === 'fail' ||statusLower === 'ready' ||  statusLower === 'raise' ||  statusLower === 'fall'){
                       label.textContent = String( data.detector); 
                    }
                } 
          }  
        })
    .catch(error => {
      // Handle network or parsing errors
      console.error('Failed to update indicators:', error);
    });
}
}
