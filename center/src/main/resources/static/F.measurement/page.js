// Demonstration batches, filtering, file selection and report preview.
const databaseBatches=[
  {id:'BATCH-2026-0418',created:'13 Sep 2026',date:'2026-09-13',spectra:3,reports:2,samples:['LAB-418','LAB-419','LAB-420'],params:{time:'30 minutes',tare:'32.4 g',volume:'100 ml',activity:'Bq',geometry:'100 ml cylinder',comments:'Routine laboratory batch.'}},
  {id:'BATCH-2026-0417',created:'12 Sep 2026',date:'2026-09-12',spectra:12,reports:1,samples:['CON-201','CON-202','CON-203'],params:{time:'60 minutes',tare:'28.6 g',volume:'500 ml',activity:'Bq',geometry:'Bottle 0.5 L',comments:'Concrete samples from production control.'}},
  {id:'BATCH-2026-0416',created:'12 Sep 2026',date:'2026-09-12',spectra:7,reports:0,samples:['W-061','W-062','W-063'],params:{time:'45 minutes',tare:'41.2 g',volume:'1 L',activity:'Bq',geometry:'Marinelli 1 L',comments:'Water samples. Review spectrum acquisition before reporting.'}},
  {id:'BATCH-2026-0415',created:'11 Sep 2026',date:'2026-09-11',spectra:5,reports:2,samples:['SOIL-81','SOIL-82','SOIL-83'],params:{time:'30 minutes',tare:'18.9 g',volume:'100 ml',activity:'Bq',geometry:'100 ml cylinder',comments:'Soil screening batch.'}},
  {id:'BATCH-2026-0414',created:'10 Sep 2026',date:'2026-09-10',spectra:9,reports:1,samples:['F-114','F-115','F-116'],params:{time:'20 minutes',tare:'14.5 g',volume:'—',activity:'Ku',geometry:'Petri dish',comments:'Final control measurements.'}}
];
let selectedDatabaseBatch=databaseBatches[0],selectedDatabaseFile=null;
function databaseFiles(batch){const files=batch.samples.slice(0,Math.min(batch.spectra,3)).map((sample,index)=>({name:sample+'.spc',sample,type:'Spectrum',created:`${batch.created} · ${String(10+index).padStart(2,'0')}:42`}));for(let i=0;i<batch.reports;i++)files.push({name:i?'Measurement summary.pdf':'Batch report.pdf',sample:i?batch.samples[Math.min(i-1,batch.samples.length-1)]:'All samples',type:'PDF report',created:`${batch.created} · 14:${20+i}`});return files}
function renderDatabaseFiles(){
  const files=databaseFiles(selectedDatabaseBatch);selectedDatabaseFile=files.find(file=>file.type==='PDF report')||files[0]||null;
  $('#databaseFileRows').innerHTML=files.map((file,index)=>`<tr class="${file===selectedDatabaseFile?'selected':''}" data-file="${index}"><td><b>${file.name}</b></td><td>${file.sample}</td><td><span class="file-type ${file.type==='PDF report'?'pdf':''}">${file.type}</span></td><td>${file.created}</td><td><button type="button" data-file-action="${index}">${file.type==='PDF report'?'Preview':'Download'}</button></td></tr>`).join('');
  $$('#databaseFileRows tr').forEach((row,index)=>row.addEventListener('click',()=>selectDatabaseFile(files[index],row)));
  updatePdfPreview();
}
function selectDatabaseFile(file,row){selectedDatabaseFile=file;$$('#databaseFileRows tr').forEach(item=>item.classList.toggle('selected',item===row));if(file.type==='PDF report')updatePdfPreview();else showToast(file.name+' ready to download')}
function updatePdfPreview(){if(!selectedDatabaseFile)return;$('#pdfPreviewTitle').textContent=selectedDatabaseFile.type==='PDF report'?selectedDatabaseFile.name:'Select a PDF report';$('#pdfBatchId').textContent=selectedDatabaseBatch.id;$('#pdfSampleId').textContent=selectedDatabaseFile.sample;$('#pdfMeasurementDate').textContent=selectedDatabaseFile.created}
function selectDatabaseBatch(batch){selectedDatabaseBatch=batch;$('#selectedBatchTitle').textContent=batch.id;$('#selectedBatchMeta').textContent=`Created automatically · ${batch.created}`;$('#batchSpectraCount').textContent=batch.spectra;$('#batchReportsCount').textContent=batch.reports;$('#batchParameterId').textContent=batch.id;$('#batchParameterTime').textContent=batch.params.time;$('#batchParameterTare').textContent=batch.params.tare;$('#batchParameterVolume').textContent=batch.params.volume;$('#batchParameterActivity').textContent=batch.params.activity;$('#batchParameterGeometry').textContent=batch.params.geometry;$('#batchParameterComments').textContent=batch.params.comments;renderDatabaseFiles();renderDatabaseBatches()}
function renderDatabaseBatches(){
  const query=$('#databaseSearch').value.trim().toLowerCase(),date=$('#databaseDateFilter').value;
  const filtered=databaseBatches.filter(batch=>(!date||batch.date===date)&&(!query||[batch.id,...batch.samples].some(value=>value.toLowerCase().includes(query))));
  $('#databaseResultCount').textContent=filtered.length+' batches';
  $('#databaseBatchRows').innerHTML=filtered.map(batch=>`<tr tabindex="0" class="${batch===selectedDatabaseBatch?'selected':''}" data-batch="${databaseBatches.indexOf(batch)}"><td><b>${batch.id}</b></td><td>${batch.created}</td><td>${batch.spectra+batch.reports}</td></tr>`).join('')||'<tr><td colspan="3" class="database-empty">No matching batches</td></tr>';
  $$('#databaseBatchRows tr[data-batch]').forEach(row=>{const open=()=>selectDatabaseBatch(databaseBatches[Number(row.dataset.batch)]);row.addEventListener('click',open);row.addEventListener('keydown',event=>{if(event.key==='Enter'||event.key===' '){event.preventDefault();open()}})});
}
$('#databaseSearch').addEventListener('input',renderDatabaseBatches);$('#databaseDateFilter').addEventListener('change',renderDatabaseBatches);
$('#downloadBatch').addEventListener('click',()=>showToast(selectedDatabaseBatch.id+' download prepared'));
$('#downloadSelectedFile').addEventListener('click',()=>showToast((selectedDatabaseFile?.name||'Report')+' download prepared'));
$('#openPdfPreview').addEventListener('click',()=>$('.pdf-preview').classList.toggle('expanded'));
selectDatabaseBatch(selectedDatabaseBatch);
