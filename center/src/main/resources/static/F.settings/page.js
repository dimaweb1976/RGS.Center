// Settings schemas, editors and browser-local preferences.
// Settings use device-specific schemas; drafts survive switching sections.
const settingField=(key,label,value,type='number',min=0,max)=>({key,label,value,type,min,max});
const networkFields=(ip,port)=>[settingField('ip','IP address',ip,'ip'),
  settingField('port','Port',port,'number',1,65535)];
const serialFields=port=>[settingField('comPort','COM port',port,'serial')];
const settingsSchemas={
  hardware:[
    {id:'plc',
     name:'PLC',
     groups:[{
      name:'Network connection',
      fields:networkFields('192.168.1.10',502)
    }]},
    // {id:'robot',name:'Robotic manipulator',groups:[{name:'Network connection',fields:[settingField('ip','IP address','192.168.1.12','ip'),settingField('streamPort','Parameters stream port',6875,'number',1,65535),settingField('commandPort','Dobot command port',6880,'number',1,65535)]},{name:'Safety inputs',fields:[settingField('door','Cabin door sensor DI',3)]}]},
    {id:'gripper',
      name:'Gripper',
      groups:[{
        name:'Digital I/O',
        fields:[
          settingField('captured','Captured sensor DI',1),
          settingField('released','Released sensor DI',2),
          settingField('openOutput','Open command DO',1),
          settingField('closeOutput','Close command DO',2)
        ]
      }]},
    // {id:'axis7',name:'7th axis',groups:[{name:'Motion controller',fields:[settingField('homeInput','Home sensor DI',6),settingField('positiveLimit','Positive limit DI',7),settingField('negativeLimit','Negative limit DI',8),settingField('driveOutput','Drive enable DO',3)]}]},
    {id:'bozon',
      name:'Bozon',
      groups:[{
        name:"Bozon",
        fields:networkFields('192.168.1.10',502)
      }]},
    {id:'shield',
      name:'Lead shield',
      groups:[{
        name:'Digital I/O',
        fields:[
          settingField('shieldOpen','Open sensor DI',4),
          settingField('shieldClosed','Closed sensor DI',5),
          settingField('openOutput','Open command DO',4),
          settingField('closeOutput','Close command DO',5)
        ]
      }]},
    {id:'cooling',
      name:'Cooling system “Nicole”',
      groups:[{
        name:'Network connection',
        fields:networkFields('192.168.2.12',6875)},
        {
          name:'LN₂ monitoring',
          fields:[
            settingField('ln2Warning','Low LN₂ warning level, %',20,'number',0,100)
          ]
        }
      ]},
    {id:'detector',
      name:'Detector and HV',
      groups:[{
        name:'High voltage',
        fields:[
          settingField('voltage','Operating voltage, V',2600,'number',0,5000),
          settingField('leakage','Leakage current warning, pA',100,'number',0)]},
          {
            name:'Temperature monitoring',
            fields:[
              settingField('maxTemperature','Maximum detector temperature, °C',-160,'number',-250,50)
            ]
          }
      ]},
    {id:'preamplifier',
      name:'State of Health Preamplifier',
      groups:[{
        name:'Serial connection',
        fields:serialFields('COM3')
      }]},
      {id:'gammapro',
        name:'GammaPro software',
        groups:[{
          name:'Network connection',
          fields:[settingField('commandPort','Command port',10002,'number',1,65535)]}
        ]},
    {id:'mca',
      name:'MCA',
      groups:[{
        name:'Network connection',
        fields:networkFields('192.168.1.42',10003)
      }]
    },
    {id:'ups',
      name:'UPS',
      groups:[{
        name:'Network connection',
        fields:networkFields('192.168.1.50',161)
      },
      {
        name:'Monitoring',
        fields:[
          settingField('batteryWarning','Low battery warning, %',25,'number',0,100)
        ]
      }
    ]},
    {id:'camera1',
      name:'Machine vision camera 1',
      groups:[{
        name:'Camera connection',
        fields:[
          settingField('ip','IP address','192.168.1.51','ip'),
          settingField('exposure','Exposure time, ms',1000,'number',1)
        ]
      }
    ]},
    {id:'camera2',
      name:'Machine vision camera 2',
      groups:[{
        name:'Camera connection',
        fields:[
          settingField('ip','IP address','192.168.1.52','ip'),
          settingField('exposure','Exposure time, ms',1000,'number',1)
        ]
      }]},
    {id:'scales',
      name:'Scales',
      groups:[{
        name:'Serial connection',
        fields:serialFields('COM4')
      }]},
    {id:'scanner',
      name:'Barcode scanner',
      groups:[{
        name:'Serial connection',
        fields:serialFields('COM7')
    }]}
  ],
  geometries:[
    {id:'cylinder100',
      name:'100 ml cylinder',
      groups:[{
        name:'Geometry definition',
        fields:[
          settingField('name','Geometry name','100 ml cylinder','text'),
          settingField('container','Container type','Cylinder','text'),
          settingField('volume','Nominal volume, ml',100),
          settingField('diameter','Inner diameter, mm',45),
          settingField('fillHeight','Fill height, mm',63),
          settingField('calibration','Efficiency calibration file','HPGe_CYL100_2026.eff','text')
        ]
      }]},
    {id:'bottle500',
      name:'Bottle 0.5 L',
      groups:[{
        name:'Geometry definition',
        fields:[
          settingField('name','Geometry name','Bottle 0.5 L','text'),
          settingField('container','Container type','Bottle','text'),
          settingField('volume','Nominal volume, ml',500),
          settingField('diameter','Inner diameter, mm',78),
          settingField('fillHeight','Fill height, mm',112),
          settingField('calibration','Efficiency calibration file','HPGe_BTL500_2026.eff','text')
        ]
      }]},
    {id:'marinelli1l',
      name:'Marinelli 1 L',
      groups:[{
        name:'Geometry definition',
        fields:[
          settingField('name','Geometry name','Marinelli 1 L','text'),
          settingField('container','Container type','Marinelli beaker','text'),
          settingField('volume','Nominal volume, ml',1000),
          settingField('diameter','Inner diameter, mm',128),
          settingField('fillHeight','Fill height, mm',134),
          settingField('calibration','Efficiency calibration file','HPGe_MAR1000_2026.eff','text')
        ]
      }]}
  ],
  documents:[
    {id:'operator',
      name:'RGS Operator Manual',
      groups:[{
        name:'Document details',
        fields:[
          settingField('name','Display name','RGS Operator Manual','text'),
          settingField('language','Language','English','text'),
          settingField('path','PDF file path','','file')
        ]
      }]},
    {id:'system',
      name:'RGS System Manual',
      groups:[{
        name:'Document details',
        fields:[
          settingField('name','Display name','RGS System Manual','text'),
          settingField('language','Language','English','text'),
          settingField('path','PDF file path','','file')
        ]
      }]},
    {id:'gammapro',
      name:'GammaPro User Manual',
      groups:[{
        name:'Document details',
        fields:[
          settingField('name','Display name','GammaPro User Manual','text'),
          settingField('language','Language','English','text'),
          settingField('path','PDF file path','','file')
        ]
      }]},
    {id:'maintenance',
      name:'Maintenance and Diagnostics Manual',
      groups:[{
        name:'Document details',
        fields:[
          settingField('name','Display name','Maintenance and Diagnostics Manual','text'),
          settingField('language','Language','English','text'),
          settingField('path','PDF file path','','file')
        ]
      }]}
  ],
  interface:[
    {id:'appearance',
      name:'Appearance',
      groups:[{
        name:'Theme',
        fields:[
          settingField('theme','Interface theme','light','theme')
        ]
      }]}
  ],
  account:[
    {id:'credentials',
      name:'Login and password',
      groups:[{
        name:'Account credentials',
        fields:[
          settingField('username','Login','','text'),
          settingField('currentPassword','Current password','','password'),
          settingField('newPassword','New password','','new-password'),
          settingField('confirmPassword','Confirm new password','','new-password')
        ]
      }]}
  ]
};
const settingsDrafts={};
for(const [section,items] of Object.entries(settingsSchemas))
  for(const item of items){
const key=section+'.'+item.id;
settingsDrafts[key]={};
for(const group of item.groups)
  for(const field of group.fields){
const saved=storedSettings[key]?.[field.key];settingsDrafts[key][field.key]=typeof saved==='string'||typeof saved==='number'?String(saved):String(field.value)}}

let settingsSection='hardware';
const settingsSelection={
  hardware:'plc',
  geometries:'cylinder100',
  documents:'operator',
  interface:'appearance',
  account:'credentials'
};
function applyInterfaceTheme(theme){
  const selected=theme==='dark'?'dark':'light';
  document.body.classList.toggle('theme-dark',selected==='dark');
  document.documentElement.style.colorScheme=selected;
}

applyInterfaceTheme(settingsDrafts['interface.appearance']?.theme||'light');
function renderSettings(){
  const items=settingsSchemas[settingsSection],
  selected=items.find(item=>item.id===settingsSelection[settingsSection]),
  key=settingsSection+'.'+selected.id;
  $('#view-settings').dataset.section=settingsSection;
  $$('.settings-tab').forEach(button=>{const active=button.dataset.settings===settingsSection;
    button.classList.toggle('active',active);
    button.setAttribute('aria-selected',String(active));
    button.tabIndex=active?0:-1});
  $('#settingsPanel').setAttribute('aria-labelledby','settings-'+settingsSection+'-tab');
  const list=$('#settingsList');
  list.replaceChildren();
  if(settingsSection==='geometries'||settingsSection==='documents'||settingsSection==='interface'||settingsSection==='account'){
    const head=document.createElement('div');
    head.className='device-list-head';
    head.textContent=settingsSection==='geometries'?'Available geometries':settingsSection==='documents'?'Help documents':settingsSection==='interface'?'Interface settings':'Account settings';
    list.append(head)
  }
  for(const item of items){
    const button=document.createElement('button');
    button.type='button';
    button.className='device-item'+(item===selected?' active':'')+(item.archived?' archived':'');
    button.textContent=item.name+(item.archived?' · Archived':'');
    button.setAttribute('aria-current',String(item===selected));
    button.onclick=()=>{settingsSelection[settingsSection]=item.id;renderSettings()};
    list.append(button)
  }
  $('#deviceEditorTitle').textContent=selected.name;
  $('#settingsEditorLabel').textContent=settingsSection==='hardware'?'Module configuration':settingsSection==='geometries'?'Sample geometry':settingsSection==='documents'?'Download document':settingsSection==='interface'?'Interface preferences':'Security';
  $('#settingsSaveStatus').textContent='';
  $('#geometryActions').hidden=settingsSection!=='geometries';
  $('#archiveGeometry').textContent=selected.archived?'Restore':'Archive';
  const container=$('#settingsFields');
  container.replaceChildren();
  for(const group of selected.groups){
    const fieldset=document.createElement('fieldset'),
    legend=document.createElement('legend'),
    fields=document.createElement('div');
    legend.textContent=group.name;
    fields.className='device-fields';
    fieldset.append(legend,fields);
    for(const field of group.fields){
      const label=document.createElement('label');
      label.textContent=field.label;
      const input=document.createElement(field.type==='serial'||field.type==='theme'?'select':'input');
      input.name=field.key;
      input.required=true;
      if(field.type==='serial'){
        for(let i=1;i<=32;i++){
          const option=document.createElement('option');
          option.value=option.textContent='COM'+i;
          input.append(option)}
        }else if(field.type==='theme'){
          for(const [value,text] of [['light','Light'],['dark','Dark']]){
            const option=document.createElement('option');
            option.value=value;
            option.textContent=text;
            input.append(option)}
          }else if(field.type==='password'||field.type==='new-password'){
            input.type='password';
            input.autocomplete=field.type==='password'?'current-password':'new-password';
            input.minLength=field.type==='new-password'?8:1
          }else if(field.type==='ip'){
            input.type='text';
            input.inputMode='decimal';
            input.placeholder='192.168.1.12'
          }else if(field.type==='text'||field.type==='file'){
            input.type='text';
            if(field.type==='file'){
              input.className='document-path';
              input.placeholder='/manuals/document.pdf'}
            }else{
              input.type='number';
              input.min=String(field.min);
              input.step=['maxTemperature','leakage','pause','diameter','fillHeight'].includes(field.key)?'any':'1';
              if(field.max!==undefined)input.max=String(field.max)}
      input.value=settingsSection==='account'&&field.key==='username'?(safeSession.getItem('rgs-current-login')||settingsDrafts[key][field.key]):settingsDrafts[key][field.key];
      input.addEventListener('input',()=>{
        input.setCustomValidity('');
        settingsDrafts[key][field.key]=input.value;
        if(field.type==='theme')applyInterfaceTheme(input.value);
        $('#settingsSaveStatus').textContent='Unsaved changes'}
      );
      if(field.type==='ip')input.addEventListener('change',()=>{
        const parts=input.value.split('.');
        input.setCustomValidity(parts.length===4&&parts.every(part=>/^\d{1,3}$/.test(part)&&Number(part)<=255)?'':'Enter a valid IPv4 address')});
      if(field.type==='file'){
        const row=document.createElement('div'),
        picker=document.createElement('button');
        row.className='document-file-control';
        picker.type='button';
        picker.textContent='Choose PDF';
        picker.addEventListener('click',()=>{
          const fileInput=document.createElement('input');
          fileInput.type='file';
          fileInput.accept='application/pdf';
          fileInput.addEventListener('change',()=>{
            if(!fileInput.files[0])return;
            input.value='/manuals/'+fileInput.files[0].name;
            settingsDrafts[key][field.key]=input.value;
            $('#settingsSaveStatus').textContent='Unsaved changes'});
            fileInput.click()});
            row.append(input,picker);
            label.append(row)}
            else label.append(input);
            fields.append(label)
    }container.append(fieldset)
  }
  if(settingsSection==='account'){
    const note=document.createElement('p');
    note.className='account-security-note';
    note.textContent='The new password must contain at least 8 characters. You will use the updated login next time you sign in.';
    container.append(note)
  }
  $('#deviceForm button[type="submit"]').hidden=!selected.groups.length;
  $('#deviceForm button[type="submit"]').textContent=settingsSection==='geometries'?'Save geometry':settingsSection==='documents'?'Save document':settingsSection==='account'?'Update credentials':'Save';
  if(!selected.groups.length){
    const note=document.createElement('p');
    note.className='settings-empty';
    note.textContent='No additional machine vision parameters configured.';
    const button=document.createElement('button');
    button.type='button';
    button.textContent='Camera connection settings';
    button.onclick=()=>{
      settingsSection='hardware';
      settingsSelection.hardware='vision';
      renderSettings()};
      container.append(note,button)
    }
}
$$('.settings-tab').forEach(button=>{
  button.addEventListener('click',()=>{
    settingsSection=button.dataset.settings;
    renderSettings()
  });
  button.addEventListener('keydown',event=>{
    if(['ArrowLeft','ArrowRight','Home','End'].includes(event.key)){
      event.preventDefault();
      const sections=['hardware','geometries','documents','interface','account'],
      index=sections.indexOf(settingsSection);
      settingsSection=event.key==='Home'?sections[0]:event.key==='End'?sections.at(-1):sections[(index+(event.key==='ArrowRight'?1:-1)+sections.length)%sections.length];
      renderSettings();
      $('.settings-tab.active').focus()
    }
  })});
function initializeGeometryDraft(item){
  const key='geometries.'+item.id;
  settingsDrafts[key]={};
  for(const group of item.groups)
    for(const field of group.fields)settingsDrafts[key][field.key]=String(field.value)}

function syncDocumentDownloads(){
  for(const documentItem of settingsSchemas.documents){
    const draft=settingsDrafts['documents.'+documentItem.id],
    row=$(`.document-row[data-document="${documentItem.id}"]`);
    if(!draft||!row)continue;
    documentItem.name=draft.name||documentItem.name;
    row.querySelector('h3').textContent=documentItem.name;
    row.querySelector('small').textContent='PDF · '+(draft.language||'English');
    const link=row.querySelector('.document-download');
    link.dataset.manual=documentItem.name;link.href=draft.path||'#'
  }}
function syncGeometryOptions(){
  const manual=[...$$('#serviceParameters label')].find(label=>label.firstChild.textContent.trim()==='Geometry')?.querySelector('select');
  for(const select of [manual,$('#autoGeometry')].filter(Boolean)){
    const value=select.value;
    select.replaceChildren();
    for(const geometry of settingsSchemas.geometries.filter(item=>!item.archived)){
      const option=document.createElement('option');
      option.value=option.textContent=geometry.name;
      select.append(option)}
      if([...select.options].some(option=>option.value===value))select.value=value
    }}
$('#addGeometry').addEventListener('click',()=>{const id='geometry'+Date.now(),item={id,name:'New geometry',groups:[{name:'Geometry definition',fields:[settingField('name','Geometry name','New geometry','text'),settingField('container','Container type','','text'),settingField('volume','Nominal volume, ml',100),settingField('diameter','Inner diameter, mm',0),settingField('fillHeight','Fill height, mm',0),settingField('calibration','Efficiency calibration file','','text')]}]};settingsSchemas.geometries.push(item);initializeGeometryDraft(item);settingsSelection.geometries=id;renderSettings();$('#settingsFields input[name="name"]').select()});
$('#duplicateGeometry').addEventListener('click',()=>{const source=settingsSchemas.geometries.find(item=>item.id===settingsSelection.geometries),id='geometry'+Date.now(),copy={id,name:source.name+' copy',groups:source.groups.map(group=>({name:group.name,fields:group.fields.map(field=>({...field}))}))};settingsSchemas.geometries.push(copy);initializeGeometryDraft(copy);Object.assign(settingsDrafts['geometries.'+id],settingsDrafts['geometries.'+source.id],{name:copy.name});settingsSelection.geometries=id;renderSettings();showToast('Geometry duplicated')});
$('#archiveGeometry').addEventListener('click',()=>{const item=settingsSchemas.geometries.find(entry=>entry.id===settingsSelection.geometries);item.archived=!item.archived;syncGeometryOptions();renderSettings();showToast(item.archived?'Geometry archived':'Geometry restored')});
$('#deviceForm').addEventListener('submit',event=>{event.preventDefault();const key=settingsSection+'.'+settingsSelection[settingsSection];try{if(settingsSection==='account'){const draft=settingsDrafts[key],current=safeSession.getItem('rgs-current-password')||($('#password')?.value||'');if(draft.currentPassword!==current){const input=$('#settingsFields input[name="currentPassword"]');input.setCustomValidity('Current password is incorrect');input.reportValidity();return}if(draft.newPassword.length<8){const input=$('#settingsFields input[name="newPassword"]');input.setCustomValidity('Use at least 8 characters');input.reportValidity();return}if(draft.newPassword!==draft.confirmPassword){const input=$('#settingsFields input[name="confirmPassword"]');input.setCustomValidity('Passwords do not match');input.reportValidity();return}safeSession.setItem('rgs-current-login',draft.username);safeSession.setItem('rgs-current-password',draft.newPassword);draft.currentPassword='';draft.newPassword='';draft.confirmPassword='';$('#settingsSaveStatus').textContent='Credentials updated';showToast('Login and password updated');renderSettings();return}const latest=JSON.parse(safeLocal.getItem(settingsStorageKey)||'{}')||{};latest[key]={...settingsDrafts[key]};safeLocal.setItem(settingsStorageKey,JSON.stringify(latest));storedSettings=latest;if(settingsSection==='geometries'){const selected=settingsSchemas.geometries.find(item=>item.id===settingsSelection.geometries);selected.name=settingsDrafts[key].name||selected.name;syncGeometryOptions();renderSettings()}if(settingsSection==='documents'){const id=settingsSelection.documents,documentItem=settingsSchemas.documents.find(item=>item.id===id),draft=settingsDrafts[key],row=$(`.document-row[data-document="${id}"]`);documentItem.name=draft.name||documentItem.name;if(row){row.querySelector('h3').textContent=documentItem.name;row.querySelector('small').textContent='PDF · '+(draft.language||'English');const link=row.querySelector('.document-download');link.dataset.manual=documentItem.name;link.href=draft.path||'#'}renderSettings()}if(settingsSection==='interface')applyInterfaceTheme(settingsDrafts[key].theme);$('#settingsSaveStatus').textContent='Saved in this browser';showToast(settingsSection==='geometries'?'Geometry saved':settingsSection==='documents'?'Document settings saved':settingsSection==='interface'?'Interface theme saved':'Settings saved in this browser')}catch{$('#settingsSaveStatus').textContent='Could not save settings. Please try again.'}});
syncGeometryOptions();syncDocumentDownloads();renderSettings();
