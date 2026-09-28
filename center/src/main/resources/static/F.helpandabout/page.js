// Document links use paths configured in Settings.
$$('.document-download').forEach(link=>link.addEventListener('click',event=>{
    if(link.getAttribute('href')==='#'){
        event.preventDefault();
        showToast(link.dataset.manual+' · PDF download')
    }
}));

// Document paths can be configured locally until an API is connected.
$$('.document-row').forEach(row=>{
    const draft=storedSettings['documents.'+row.dataset.document];
    if(!draft)return;
    const link=row.querySelector('.document-download');
    if(draft.name){
        row.querySelector('h3').textContent=draft.name;
        link.dataset.manual=draft.name
    }
    if(draft.path)link.href=draft.path;
    if(draft.language)row.querySelector('small').textContent='PDF · '+draft.language
});
