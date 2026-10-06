'use strict';

const http = require('node:http');

const HOST = '127.0.0.1';
const PORT = positiveInteger(process.env.FRONTEND_EMU_PORT, 3005);
const BACKEND_URL = normalizeBackendUrl(
  process.env.RGS_BACKEND_URL || 'http://localhost:8080',
);
const MAX_REQUEST_BYTES = 64 * 1024;

const ALLOWED_ENDPOINTS = new Map([
  ['POST /api/onlyOne/enter', true],
  ['POST /api/onlyOne/updateBusy', true],
  ['POST /api/onlyOne/exit', true],
  ['GET /api/onlyOne/checkBusy', true],
  ['GET /api/onlyOne/checkMe', true],
]);

const PAGE = `<!doctype html>
<html lang="ru">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>RGSCenter — OnlyOne API tester</title>
  <style>
    :root { color-scheme: light; font-family: Inter, Arial, sans-serif; }
    * { box-sizing: border-box; }
    body { margin: 0; color: #17212b; background: #edf2f5; }
    main { width: min(1050px, calc(100% - 32px)); margin: 28px auto; }
    h1 { margin-bottom: 6px; }
    .hint { margin-top: 0; color: #5d6b76; }
    .panel { margin: 16px 0; padding: 18px; border: 1px solid #ccd7df;
      border-radius: 10px; background: white; box-shadow: 0 8px 28px #20304012; }
    .grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; }
    label { display: grid; gap: 6px; font-weight: 700; }
    input { width: 100%; padding: 10px 11px; border: 1px solid #aebdc8;
      border-radius: 6px; font: inherit; }
    input:focus { border-color: #087f5b; outline: 3px solid #087f5b22; }
    .actions { display: grid; grid-template-columns: repeat(5, minmax(150px, 1fr));
      gap: 10px; overflow-x: auto; }
    button { padding: 10px 12px; border: 1px solid #7e919f; border-radius: 6px;
      background: white; cursor: pointer; font: inherit; font-weight: 700; }
    button:hover { background: #edf7f3; }
    button.primary { border-color: #087f5b; color: white; background: #087f5b; }
    button:disabled { cursor: wait; opacity: .6; }
    .toolbar { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; }
    .toolbar label { display: flex; align-items: center; font-weight: 500; }
    .toolbar input { width: auto; }
    .status { padding: 4px 9px; border-radius: 999px; background: #e8eef2; font-weight: 800; }
    .status.ok { color: #087f5b; background: #dff5ec; }
    .status.error { color: #a61b1b; background: #fde4e4; }
    pre { min-height: 260px; max-height: 52vh; margin: 0; padding: 14px;
      overflow: auto; border-radius: 7px; color: #e6edf3; background: #17212b;
      white-space: pre-wrap; overflow-wrap: anywhere; }
    code { color: #075f45; }
    @media (max-width: 760px) {
      main { width: min(100% - 20px, 1050px); margin: 16px auto; }
      .grid { grid-template-columns: 1fr; }
    }
  </style>
</head>
<body>
<main>
  <h1>OnlyOne API tester</h1>
  <p class="hint">Интерактивные GET/POST-запросы к backend
    <code id="backendUrl">__BACKEND_URL__</code>. Запросы идут в настоящий Spring backend и БД.</p>

  <section class="panel grid">
    <label>Мастер-пароль
      <input id="password" type="password" value="masterpass" autocomplete="off">
    </label>
    <label>X-Lock-Token
      <input id="token" type="text" placeholder="Будет сохранён после enter" autocomplete="off">
    </label>
    <label>Тестовый IP клиента
      <input id="clientIp" type="text" value="192.168.1.10" autocomplete="off">
    </label>
  </section>

  <section class="panel actions" aria-label="API endpoints">
    <button class="primary" data-method="POST" data-path="/api/onlyOne/enter">POST enter</button>
    <button data-method="GET" data-path="/api/onlyOne/checkBusy">GET checkBusy</button>
    <button data-method="GET" data-path="/api/onlyOne/checkMe">GET checkMe</button>
    <button data-method="POST" data-path="/api/onlyOne/updateBusy">POST updateBusy</button>
    <button data-method="POST" data-path="/api/onlyOne/exit">POST exit</button>
  </section>

  <section class="panel toolbar">
    <span id="status" class="status">Ожидание</span>
    <label><input id="pollBusy" type="checkbox"> опрашивать checkBusy каждые 5 секунд</label>
    <button id="clearToken" type="button">Очистить token</button>
    <button id="clearLog" type="button">Очистить журнал</button>
  </section>

  <section class="panel">
    <pre id="log" aria-live="polite">Выберите endpoint для выполнения запроса.</pre>
  </section>
</main>
<script>
  'use strict';

  var TOKEN_KEY = 'rgs.onlyOne.lockToken';
  var tokenInput = document.getElementById('token');
  var passwordInput = document.getElementById('password');
  var clientIpInput = document.getElementById('clientIp');
  var statusElement = document.getElementById('status');
  var logElement = document.getElementById('log');
  var pollTimer = null;

  tokenInput.value = sessionStorage.getItem(TOKEN_KEY) || '';

  function updateStatus(text, ok) {
    statusElement.textContent = text;
    statusElement.className = 'status ' + (ok === true ? 'ok' : ok === false ? 'error' : '');
  }

  function appendLog(text) {
    var initial = 'Выберите endpoint для выполнения запроса.';
    if (logElement.textContent === initial) {
      logElement.textContent = '';
    }
    logElement.textContent = text + '\n\n' + logElement.textContent;
  }

  function prettyBody(text) {
    if (!text) return '<empty body>';
    try { return JSON.stringify(JSON.parse(text), null, 2); }
    catch (error) { return text; }
  }

  async function callEndpoint(method, path, silent) {
    var headers = {Accept: 'application/json'};
    var options = {method: method, headers: headers};
    var token = tokenInput.value.trim();
    var clientIp = clientIpInput.value.trim();

    if (clientIp) headers['X-Test-Client-IP'] = clientIp;
    if (path !== '/api/onlyOne/enter' && path !== '/api/onlyOne/checkBusy' && token) {
      headers['X-Lock-Token'] = token;
    }
    if (path === '/api/onlyOne/enter') {
      headers['Content-Type'] = 'application/json';
      options.body = JSON.stringify({password: passwordInput.value});
    }

    var startedAt = new Date();
    if (!silent) updateStatus('Выполняется…', null);
    document.querySelectorAll('button[data-path]').forEach(function (button) {
      button.disabled = true;
    });

    try {
      var response = await fetch('/proxy' + path, options);
      var responseText = await response.text();
      var responseBody = null;
      try { responseBody = responseText ? JSON.parse(responseText) : null; }
      catch (error) { responseBody = null; }

      if (path === '/api/onlyOne/enter' && response.ok && responseBody && responseBody.token) {
        tokenInput.value = responseBody.token;
        sessionStorage.setItem(TOKEN_KEY, responseBody.token);
      }
      if (path === '/api/onlyOne/exit' && response.status === 204) {
        tokenInput.value = '';
        sessionStorage.removeItem(TOKEN_KEY);
      }

      updateStatus('HTTP ' + response.status, response.ok);
      if (!silent || !response.ok) {
        appendLog(
          startedAt.toLocaleTimeString() + '  ' + method + ' ' + path + '\n' +
          'HTTP ' + response.status + ' ' + response.statusText + '\n' +
          prettyBody(responseText)
        );
      }
      return response;
    } catch (error) {
      updateStatus('Backend недоступен', false);
      appendLog(startedAt.toLocaleTimeString() + '  ' + method + ' ' + path + '\nERROR: ' + error.message);
      return null;
    } finally {
      document.querySelectorAll('button[data-path]').forEach(function (button) {
        button.disabled = false;
      });
    }
  }

  document.querySelectorAll('button[data-path]').forEach(function (button) {
    button.addEventListener('click', function () {
      callEndpoint(button.dataset.method, button.dataset.path, false);
    });
  });

  tokenInput.addEventListener('input', function () {
    var token = tokenInput.value.trim();
    if (token) sessionStorage.setItem(TOKEN_KEY, token);
    else sessionStorage.removeItem(TOKEN_KEY);
  });

  document.getElementById('clearToken').addEventListener('click', function () {
    tokenInput.value = '';
    sessionStorage.removeItem(TOKEN_KEY);
    updateStatus('Token очищен', null);
  });

  document.getElementById('clearLog').addEventListener('click', function () {
    logElement.textContent = 'Выберите endpoint для выполнения запроса.';
  });

  document.getElementById('pollBusy').addEventListener('change', function (event) {
    if (pollTimer !== null) clearInterval(pollTimer);
    pollTimer = event.target.checked
      ? setInterval(function () { callEndpoint('GET', '/api/onlyOne/checkBusy', true); }, 5000)
      : null;
  });
</script>
</body>
</html>`;

function positiveInteger(value, fallback) {
  if (value === undefined || value === '') return fallback;
  const parsed = Number(value);
  if (!Number.isInteger(parsed) || parsed < 1 || parsed > 65535) {
    throw new Error('Port must be an integer from 1 to 65535');
  }
  return parsed;
}

function normalizeBackendUrl(value) {
  const url = new URL(value);
  if (url.protocol !== 'http:' && url.protocol !== 'https:') {
    throw new Error('RGS_BACKEND_URL must use HTTP or HTTPS');
  }
  return url.href.replace(/\/$/, '');
}

function escapeHtml(value) {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;');
}

async function readRequestBody(request) {
  const chunks = [];
  let size = 0;
  for await (const chunk of request) {
    size += chunk.length;
    if (size > MAX_REQUEST_BYTES) {
      throw new Error('Request body is too large');
    }
    chunks.push(chunk);
  }
  return chunks.length === 0 ? undefined : Buffer.concat(chunks);
}

function clientAddress(request) {
  const requested = request.headers['x-test-client-ip'];
  if (typeof requested === 'string' && requested.trim() !== '') {
    return requested.trim().slice(0, 45);
  }
  return (request.socket.remoteAddress || '127.0.0.1')
    .replace(/^::ffff:/, '')
    .slice(0, 45);
}

function sendJson(response, status, body) {
  response.writeHead(status, {'Content-Type': 'application/json; charset=utf-8'});
  response.end(JSON.stringify(body));
}

async function proxyRequest(request, response, url) {
  const endpoint = url.pathname.substring('/proxy'.length);
  if (!ALLOWED_ENDPOINTS.has(request.method + ' ' + endpoint)) {
    sendJson(response, 405, {error: 'Endpoint or HTTP method is not allowed'});
    return;
  }

  const headers = {
    Accept: 'application/json',
    'X-Forwarded-For': clientAddress(request),
  };
  if (request.headers['content-type']) {
    headers['Content-Type'] = request.headers['content-type'];
  }
  if (request.headers['x-lock-token']) {
    headers['X-Lock-Token'] = request.headers['x-lock-token'];
  }

  const body = request.method === 'POST' ? await readRequestBody(request) : undefined;
  const backendResponse = await fetch(BACKEND_URL + endpoint, {
    method: request.method,
    headers,
    body,
    redirect: 'manual',
  });
  const responseBody = Buffer.from(await backendResponse.arrayBuffer());
  response.writeHead(backendResponse.status, {
    'Content-Type': backendResponse.headers.get('content-type') || 'application/json; charset=utf-8',
    'Cache-Control': 'no-store',
  });
  response.end(responseBody);
}

const server = http.createServer((request, response) => {
  const url = new URL(request.url, 'http://' + HOST + ':' + PORT);

  if (request.method === 'GET' && url.pathname === '/') {
    response.writeHead(200, {
      'Content-Type': 'text/html; charset=utf-8',
      'Cache-Control': 'no-store',
    });
    response.end(PAGE.replace('__BACKEND_URL__', escapeHtml(BACKEND_URL)));
    return;
  }

  if (url.pathname.startsWith('/proxy/api/onlyOne/')) {
    proxyRequest(request, response, url).catch((error) => {
      sendJson(response, 502, {
        error: 'Cannot reach RGSCenter backend',
        detail: error.message,
      });
    });
    return;
  }

  sendJson(response, 404, {error: 'Not found'});
});

server.listen(PORT, HOST, () => {
  console.log('RGSCenter OnlyOne frontend emulator');
  console.log('UI:      http://' + HOST + ':' + PORT);
  console.log('Backend: ' + BACKEND_URL);
  console.log('Press Ctrl+C to stop.');
});
