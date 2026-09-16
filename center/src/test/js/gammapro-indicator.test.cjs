const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const staticRoot = path.resolve(__dirname, '../../main/resources/static/common');

test('GammaPRO header follows API states and never defaults to Online', async () => {
    const text = { textContent: '' };
    const button = {
        className: '', dataset: {},
        setAttribute(name, value) { this[name] = value; },
        querySelector() { return text; }
    };
    let poll;
    let payload = { gammapro: 'ONLINE' };
    let fail = false;
    let requests = 0;
    const context = vm.createContext({
        currentView: 'settings', complexState: 'idle',
        syncProcessControls() {},
        document: { getElementById: id => id === 'indicator_gammapro' ? button : null },
        console: { log() {}, error() {} }, AbortController,
        setInterval: fn => { poll = fn; },
        setTimeout: () => 1, clearTimeout() {},
        fetch: async () => {
            requests++;
            return { ok: !fail, json: async () => payload };
        }
    });
    vm.runInContext(fs.readFileSync(path.join(staticRoot, 'page-init.js'), 'utf8'), context);
    context.initializePage();
    assert.equal(text.textContent, 'Unknown');
    const settle = () => new Promise(resolve => setImmediate(resolve));
    await settle();
    for (const [value, label, className] of [
        ['ONLINE', 'Online', 'module ok'],
        ['OFFLINE', 'Offline', 'module ok hv-leak'],
        ['IN_PROGRESS', 'In progress', 'module warn'],
        ['UNKNOWN', 'Unknown', 'module ok hv-off'],
        ['unexpected', 'Unknown', 'module ok hv-off'],
        [null, 'Unknown', 'module ok hv-off'],
        [undefined, 'Unknown', 'module ok hv-off']
    ]) {
        payload = { gammapro: value };
        poll();
        poll(); // Concurrent polling must not create an extra request.
        const sent = requests;
        await settle();
        assert.equal(requests, sent);
        assert.equal(text.textContent, label);
        assert.equal(button.className, className);
        assert.equal(button['aria-label'], `GammaPRO: ${label}`);
    }
    fail = true;
    poll();
    await settle();
    assert.equal(text.textContent, 'Unknown');

    const components = fs.readFileSync(path.join(staticRoot, 'components.js'), 'utf8');
    assert.ok(components.includes('indicator_gammapro'));
    assert.ok(components.includes('>Unknown</b>'));
});
