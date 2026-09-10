'use strict';

const API_URL = '/api/device-settings';
const ONLY_ONE_URL = '/api/onlyOne';
const LOCK_TOKEN_KEY = 'token';
const LEGACY_LOCK_TOKEN_KEY = 'rgs.onlyOne.lockToken';
const HEARTBEAT_INTERVAL_MS = 60_000;

const deviceList = document.getElementById('deviceList');
const deviceTitle = document.getElementById('deviceTitle');
const settingsFields = document.getElementById('settingsFields');
const settingsForm = document.getElementById('settingsForm');
const saveButton = document.getElementById('saveButton');
const applyButton = document.getElementById('applyButton');
const reloadButton = document.getElementById('reloadButton');
const message = document.getElementById('message');
const leaseForm = document.getElementById('leaseForm');
const leaseButton = document.getElementById('leaseButton');
const masterPassword = document.getElementById('masterPassword');
const leaseStatus = document.getElementById('leaseStatus');

let devices = [];
let selectedDevice = null;
let heartbeatTimer = null;

const displayNames = {
  camera: 'Камера',
  'lenovo-pc': 'Lenovo PC',
  nicole: 'Nicole',
  boson: 'BOSON',
  RoboticManipulator: 'Манипулятор',
  'barcode-scanner': 'Сканер штрих-кодов',
  gdrm: 'GDRM',
  scales: 'Весы',
  shp: 'SHP',
  'gamma-pro': 'Gamma-PRO',
};

function lockToken() {
  const token = sessionStorage.getItem(LOCK_TOKEN_KEY)
    || sessionStorage.getItem(LEGACY_LOCK_TOKEN_KEY);
  if (token) {
    // Keep both existing frontends interoperable while they use different keys.
    sessionStorage.setItem(LOCK_TOKEN_KEY, token);
    sessionStorage.setItem(LEGACY_LOCK_TOKEN_KEY, token);
  }
  return token || '';
}

function clearLockToken() {
  sessionStorage.removeItem(LOCK_TOKEN_KEY);
  sessionStorage.removeItem(LEGACY_LOCK_TOKEN_KEY);
  stopHeartbeat();
}

function readCookie(name) {
  const prefix = `${name}=`;
  const part = document.cookie.split('; ')
    .find((cookie) => cookie.startsWith(prefix));
  return part ? decodeURIComponent(part.slice(prefix.length)) : '';
}

function mutationHeaders() {
  const csrf = readCookie('XSRF-TOKEN');
  const token = lockToken();
  if (!csrf) throw new Error('Не найдена CSRF-cookie. Обновите страницу.');
  if (!token) throw new Error('Сначала получите монопольный доступ.');
  return {
    'Content-Type': 'application/json',
    'X-XSRF-TOKEN': csrf,
    'X-Lock-Token': token,
  };
}

async function request(url, options = {}) {
  const response = await fetch(url, {
    credentials: 'same-origin',
    cache: 'no-store',
    ...options,
  });
  const text = await response.text();
  let body = null;
  if (text) {
    try { body = JSON.parse(text); }
    catch (error) { body = {message: text}; }
  }
  if (!response.ok) {
    const error = new Error(body?.message || body?.error || `HTTP ${response.status}`);
    error.status = response.status;
    throw error;
  }
  return body;
}

function showMessage(text, kind = 'info') {
  message.textContent = text;
  message.className = `message ${kind}`;
  message.hidden = false;
}

function clearMessage() {
  message.hidden = true;
  message.textContent = '';
}

function showLease(text, kind = '') {
  leaseStatus.textContent = text;
  leaseStatus.className = `leaseStatus${kind ? ` ${kind}` : ''}`;
}

function settingLabel(stringId) {
  return stringId.split('.').at(-1)
    .split('-')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ');
}

function formatAudit(setting) {
  const who = setting.updatedByLogin || 'deployment';
  const when = setting.updatedAt
    ? new Date(setting.updatedAt).toLocaleString()
    : 'неизвестное время';
  return `Версия ${setting.version}; изменил ${who}, ${when}`;
}

function renderDeviceList() {
  deviceList.replaceChildren(...devices.map((device) => {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = `deviceButton${device.deviceCode === selectedDevice?.deviceCode ? ' active' : ''}`;
    button.textContent = displayNames[device.deviceCode] || device.deviceCode;
    button.addEventListener('click', () => selectDevice(device.deviceCode));
    return button;
  }));
}

function renderSettings() {
  renderDeviceList();
  if (!selectedDevice) return;
  deviceTitle.textContent = displayNames[selectedDevice.deviceCode]
    || selectedDevice.deviceCode;
  settingsFields.replaceChildren(...selectedDevice.settings.flatMap((setting) => {
    const wrapper = document.createElement('label');
    wrapper.className = 'field';
    const title = document.createElement('span');
    title.className = 'fieldLabel';
    title.textContent = settingLabel(setting.stringId);
    const code = document.createElement('span');
    code.className = 'fieldCode';
    code.textContent = setting.stringId;
    title.append(code);

    const input = document.createElement('input');
    input.name = setting.stringId;
    input.value = setting.value;
    input.dataset.type = setting.type;
    input.dataset.version = setting.version;
    if (setting.type === 'LONG' || setting.type === 'DOUBLE') {
      input.type = 'number';
      input.step = setting.type === 'LONG' ? '1' : 'any';
    }
    wrapper.append(title, input);

    const audit = document.createElement('span');
    audit.className = 'audit';
    audit.textContent = formatAudit(setting);
    return [wrapper, audit];
  }));
}

function selectDevice(deviceCode) {
  selectedDevice = devices.find((device) => device.deviceCode === deviceCode);
  clearMessage();
  renderSettings();
}

function parsedValue(input) {
  if (input.dataset.type === 'LONG') {
    if (!/^-?\d+$/.test(input.value.trim())) throw new Error(`${input.name}: требуется целое число.`);
    return Number(input.value);
  }
  if (input.dataset.type === 'DOUBLE') {
    const value = Number(input.value);
    if (!Number.isFinite(value)) throw new Error(`${input.name}: требуется число.`);
    return value;
  }
  if (!input.value.trim()) throw new Error(`${input.name}: значение не должно быть пустым.`);
  return input.value.trim();
}

async function loadSettings(preferredDevice) {
  setBusy(true);
  clearMessage();
  try {
    devices = await request(API_URL);
    const target = preferredDevice || selectedDevice?.deviceCode || devices[0]?.deviceCode;
    selectedDevice = devices.find((device) => device.deviceCode === target) || devices[0] || null;
    renderSettings();
    if (!selectedDevice) showMessage('Backend не вернул параметры приборов.', 'info');
  } catch (error) {
    handleError(error);
  } finally {
    setBusy(false);
  }
}

async function saveSettings(event) {
  event.preventDefault();
  if (!selectedDevice) return;
  setBusy(true);
  clearMessage();
  try {
    const inputs = [...settingsFields.querySelectorAll('input')];
    const body = {
      settings: inputs.map((input) => ({
        stringId: input.name,
        value: parsedValue(input),
        version: Number(input.dataset.version),
      })),
    };
    const saved = await request(`${API_URL}/${encodeURIComponent(selectedDevice.deviceCode)}`, {
      method: 'PUT',
      headers: mutationHeaders(),
      body: JSON.stringify(body),
    });
    selectedDevice = saved;
    devices = devices.map((device) => device.deviceCode === saved.deviceCode ? saved : device);
    renderSettings();
    showMessage('Параметры сохранены в PostgreSQL.', 'success');
  } catch (error) {
    handleError(error);
  } finally {
    setBusy(false);
  }
}

async function applySettings() {
  if (!selectedDevice) return;
  setBusy(true);
  clearMessage();
  try {
    const result = await request(
      `${API_URL}/${encodeURIComponent(selectedDevice.deviceCode)}/apply`,
      {method: 'POST', headers: mutationHeaders()},
    );
    showMessage(`${result.message} (${result.status}).`, 'success');
  } catch (error) {
    handleError(error);
  } finally {
    setBusy(false);
  }
}

async function checkLease() {
  const token = lockToken();
  if (!token) {
    showLease('Для сохранения и применения получите монопольный доступ.');
    return false;
  }
  try {
    const state = await request(`${ONLY_ONE_URL}/checkMe`, {
      headers: {'X-Lock-Token': token},
    });
    if (!state.active) throw new Error('Аренда не активна.');
    showLease(`Доступ активен до ${new Date(state.expiresAt).toLocaleString()}.`, 'active');
    startHeartbeat();
    return true;
  } catch (error) {
    clearLockToken();
    showLease('Токен отсутствует или истёк. Получите доступ повторно.', 'error');
    return false;
  }
}

async function acquireLease(event) {
  event.preventDefault();
  leaseButton.disabled = true;
  try {
    const result = await request(`${ONLY_ONE_URL}/enter`, {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({password: masterPassword.value}),
    });
    sessionStorage.setItem(LOCK_TOKEN_KEY, result.token);
    sessionStorage.setItem(LEGACY_LOCK_TOKEN_KEY, result.token);
    masterPassword.value = '';
    showLease(`Доступ получен до ${new Date(result.expiresAt).toLocaleString()}.`, 'active');
    startHeartbeat();
  } catch (error) {
    showLease(error.status === 423
      ? 'Система занята другим клиентом.'
      : `Не удалось получить доступ: ${error.message}`, 'error');
  } finally {
    leaseButton.disabled = false;
  }
}

async function heartbeat() {
  const token = lockToken();
  if (!token) return;
  try {
    const state = await request(`${ONLY_ONE_URL}/updateBusy`, {
      method: 'POST',
      headers: {'X-Lock-Token': token},
    });
    showLease(`Доступ активен до ${new Date(state.expiresAt).toLocaleString()}.`, 'active');
  } catch (error) {
    clearLockToken();
    showLease('Монопольный доступ завершён. Получите доступ повторно.', 'error');
  }
}

function startHeartbeat() {
  stopHeartbeat();
  heartbeatTimer = window.setInterval(heartbeat, HEARTBEAT_INTERVAL_MS);
}

function stopHeartbeat() {
  if (heartbeatTimer !== null) {
    window.clearInterval(heartbeatTimer);
    heartbeatTimer = null;
  }
}

function handleError(error) {
  if (error.status === 401) {
    showMessage('Требуется авторизация пользователя. Перейдите по ссылке «Войти».', 'error');
  } else if (error.status === 403) {
    showMessage('Для изменения параметров нужна роль administrator.', 'error');
  } else if (error.status === 409) {
    showMessage('Параметры уже изменил другой администратор. Обновите данные перед сохранением.', 'error');
  } else if (error.status === 423) {
    showMessage('Монопольный доступ принадлежит другому клиенту.', 'error');
  } else {
    showMessage(error.message || 'Ошибка запроса.', 'error');
  }
}

function setBusy(busy) {
  saveButton.disabled = busy;
  applyButton.disabled = busy;
  reloadButton.disabled = busy;
}

settingsForm.addEventListener('submit', saveSettings);
applyButton.addEventListener('click', applySettings);
reloadButton.addEventListener('click', () => loadSettings());
leaseForm.addEventListener('submit', acquireLease);

checkLease();
loadSettings();
