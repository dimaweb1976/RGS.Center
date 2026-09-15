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
}
