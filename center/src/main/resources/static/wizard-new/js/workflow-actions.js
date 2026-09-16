// WizardNew reuses existing safety-checked POST endpoints; it never simulates devices.
(() => {
    "use strict";

    const mode = document.body.dataset.workflowMode;
    if (!['automatic', 'manual'].includes(mode)) return;
    const oldBase = mode === 'automatic' ? '/wizard/automatic' : '/wizard/manual';
    const newBase = mode === 'automatic' ? '/wizard-new/automatic' : '/wizard-new/manual';
    let inProgress = false;

    function cookie(name) {
        const prefix = `${encodeURIComponent(name)}=`;
        return document.cookie.split(';').map(value => value.trim())
            .find(value => value.startsWith(prefix))?.slice(prefix.length) || '';
    }

    function actionHeaders() {
        const headers = { 'X-Requested-With': 'XMLHttpRequest' };
        const lockToken = window.localStorage.getItem('rgs.onlyOne.lockToken')
            || window.sessionStorage.getItem('rgs.onlyOne.lockToken')
            || window.sessionStorage.getItem('token');
        const csrf = cookie('XSRF-TOKEN');
        if (lockToken) headers['X-Lock-Token'] = lockToken;
        if (csrf) headers['X-XSRF-TOKEN'] = decodeURIComponent(csrf);
        return headers;
    }

    function showError(message) {
        const target = document.getElementById('wn-action-message');
        if (!target) return;
        target.textContent = message;
        target.hidden = false;
    }

    async function refreshWorkspace() {
        const response = await fetch(`${newBase}/status`, {
            headers: { 'Accept': 'text/html' }, cache: 'no-store'
        });
        if (!response.ok) throw new Error(`Status refresh failed: HTTP ${response.status}`);
        const parsed = new DOMParser().parseFromString(await response.text(), 'text/html');
        const updated = parsed.getElementById('wn-mode-workspace');
        const current = document.getElementById('wn-mode-workspace');
        if (!updated || !current) throw new Error('Workflow fragment is missing');
        current.replaceWith(updated);
    }

    async function action(path, body, button) {
        if (inProgress || button?.disabled) return;
        inProgress = true;
        const label = button?.textContent;
        if (button) { button.disabled = true; button.textContent = 'Please wait…'; }
        try {
            const response = await fetch(`${oldBase}/${path}`, {
                method: 'POST', headers: actionHeaders(), body
            });
            if (response.status === 401) {
                window.location.assign('/login');
                return;
            }
            const responseText = await response.text();
            if (!response.ok) {
                const reason = response.status === 423
                    ? 'Exclusive access lock is required for this action.'
                    : responseText.length < 300 && responseText.trim()
                        ? responseText : `Action failed: HTTP ${response.status}`;
                throw new Error(reason);
            }
            const oldFragment = new DOMParser().parseFromString(responseText, 'text/html');
            const actionError = oldFragment.querySelector('.wizard-action-error')?.textContent;
            await refreshWorkspace();
            if (actionError) showError(actionError);
        } catch (error) {
            if (button) { button.disabled = false; button.textContent = label; }
            showError(error.message);
        } finally {
            inProgress = false;
        }
    }

    document.addEventListener('click', event => {
        const button = event.target.closest('[data-wn-action]');
        if (button) action(button.dataset.wnAction, null, button);
    });
    document.addEventListener('submit', event => {
        const form = event.target.closest('[data-wn-form]');
        if (!form) return;
        event.preventDefault();
        const body = new URLSearchParams(new FormData(form));
        action(form.dataset.wnForm, body, form.querySelector('button[type="submit"]'));
    });
    window.setInterval(() => {
        const workspace = document.getElementById('wn-mode-workspace');
        if (!inProgress && !document.hidden && workspace?.dataset.polling === 'true') {
            inProgress = true;
            refreshWorkspace().catch(error => showError(error.message))
                .finally(() => { inProgress = false; });
        }
    }, 2000);
})();
