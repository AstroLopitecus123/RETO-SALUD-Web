// Run from Frontend: node tests/ui/local-visual.cjs
// No backend, production server, package changes, or external network required.
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { spawn } = require('node:child_process');
const WebSocket = require('ws');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '../../browser');
const output = path.join(os.tmpdir(), 'opencode', `ui-visual-${Date.now()}`);
fs.mkdirSync(output, { recursive: true });
const pause = ms => new Promise(r => setTimeout(r, ms));
const doctor = { id: 21, medicoId: 7, nombre: 'Ana Maria', apellido: 'Perez Torres', correo: 'fixture@gmail.com', telefono: '999123456', dni: '12345678', rol: 'MEDICO', fotoUrl: '', especialidad: { id: 1, nombre: 'Medicina General' } };
const report = { checks: [], requests: [], errors: [], output };
let chrome, ws, server;
let nextId = 0;
const pending = new Map();
function send(method, params = {}) {
  return new Promise((resolve, reject) => {
    const id = ++nextId;
    pending.set(id, { resolve, reject });
    ws.send(JSON.stringify({ id, method, params }));
  });
}
async function evaluate(expression) {
  const result = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
  if (result.exceptionDetails) throw new Error(JSON.stringify(result.exceptionDetails));
  return result.result.value;
}
async function wait(expression) {
  for (let i = 0; i < 100; i++) {
    if (await evaluate(`Boolean(${expression})`)) return;
    await pause(100);
  }
  throw new Error(`Timeout: ${expression}`);
}
async function click(selector) {
  await evaluate(`document.querySelector(${JSON.stringify(selector)}).scrollIntoView({block:'center'})`);
  await pause(500);
  const r = await evaluate(`(()=>{const r=document.querySelector(${JSON.stringify(selector)}).getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2}})()`);
  await send('Input.dispatchMouseEvent', { type: 'mousePressed', ...r, button: 'left', clickCount: 1 });
  await send('Input.dispatchMouseEvent', { type: 'mouseReleased', ...r, button: 'left', clickCount: 1 });
  await pause(200);
}
async function key(key, shift = false) {
  await send('Input.dispatchKeyEvent', { type: 'keyDown', key, code: key, windowsVirtualKeyCode: key === 'Tab' ? 9 : key === 'Enter' ? 13 : 27, modifiers: shift ? 8 : 0 });
  await send('Input.dispatchKeyEvent', { type: 'keyUp', key, code: key });
}
async function shot(name) {
  const { data } = await send('Page.captureScreenshot', { format: 'png' });
  fs.writeFileSync(path.join(output, `${name}.png`), Buffer.from(data, 'base64'));
}
async function check(name, fn) {
  try { const measures = await fn(); report.checks.push({ name, pass: true, measures }); }
  catch (e) { report.checks.push({ name, pass: false, error: e.message }); await shot(`FAIL-${report.checks.length}`); }
  console.log(JSON.stringify(report.checks.at(-1)));
}
async function visit(route, role, width, height) {
  await send('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: false });
  await send('Page.navigate', { url: `${origin}/blank-fixture` });
  await wait('document.readyState === "complete"');
  const user = { id: 42, nombre: 'Usuario Fixture', apellido: 'Local', correo: 'local@example.invalid', rol: role };
  const b64 = value => Buffer.from(JSON.stringify(value)).toString('base64url');
  const token = `${b64({ alg: 'HS256', typ: 'JWT' })}.${b64({ sub: user.correo, id: 42, rol: role, exp: Math.floor(Date.now() / 1000) + 86400 })}.fake-local-signature`;
  await evaluate(`localStorage.clear();sessionStorage.clear();${role ? `localStorage.setItem('usuario',${JSON.stringify(JSON.stringify(user))});localStorage.setItem('token',${JSON.stringify(token)});` : ''}`);
  await send('Page.navigate', { url: origin + route });
  await wait('document.querySelector("router-outlet") && !document.querySelector(".page-loader")');
  await pause(700);
}
let origin;
async function main() {
  server = http.createServer((req, res) => {
    if (req.url === '/blank-fixture') { res.end('<!doctype html><title>Local fixture</title>'); return; }
    const pathname = decodeURIComponent(new URL(req.url, 'http://localhost').pathname);
    let file = path.resolve(root, '.' + pathname);
    if (!file.startsWith(root + path.sep) && file !== root) { res.writeHead(403).end(); return; }
    if (!fs.existsSync(file) || fs.statSync(file).isDirectory()) file = path.join(root, 'index.html');
    const types = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.webp': 'image/webp', '.png': 'image/png', '.woff2': 'font/woff2' };
    res.setHeader('Content-Type', types[path.extname(file)] || 'application/octet-stream');
    // Defense in depth: external scripts, fonts, frames, images and workers cannot load.
    res.setHeader('Content-Security-Policy', "default-src 'self' data: blob:; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; connect-src *; frame-src 'none'; worker-src 'none'");
    fs.createReadStream(file).pipe(res);
  });
  await new Promise(r => server.listen(0, '127.0.0.1', r));
  origin = `http://127.0.0.1:${server.address().port}`;
  chrome = spawn(process.env.CHROME_PATH || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe', [
    '--headless=new', '--remote-debugging-port=0', `--user-data-dir=${path.join(output, 'chrome-profile')}`,
    '--no-first-run', '--disable-background-networking', '--disable-component-update', '--disable-sync',
    '--proxy-server=http://127.0.0.1:9', '--proxy-bypass-list=127.0.0.1;localhost',
    '--host-resolver-rules=MAP * ~NOTFOUND, EXCLUDE 127.0.0.1, EXCLUDE localhost', 'about:blank'
  ], { stdio: ['ignore', 'ignore', 'pipe'] });
  const endpoint = await new Promise((resolve, reject) => {
    let text = '';
    chrome.stderr.on('data', data => { text += data; const m = text.match(/DevTools listening on (ws:\/\/[^\s]+)/); if (m) resolve(m[1]); });
    chrome.on('error', reject);
  });
  const port = new URL(endpoint).port;
  const tabs = await (await fetch(`http://127.0.0.1:${port}/json`)).json();
  ws = new WebSocket(tabs.find(t => t.type === 'page').webSocketDebuggerUrl);
  await new Promise(r => ws.once('open', r));
  ws.on('message', async raw => {
    const m = JSON.parse(raw);
    if (m.id) { const p = pending.get(m.id); pending.delete(m.id); if (m.error) p.reject(new Error(JSON.stringify(m.error))); else p.resolve(m.result); }
    if (m.method === 'Fetch.requestPaused') {
      const { requestId, request } = m.params;
      const url = new URL(request.url);
      try {
        if (url.pathname.startsWith('/api/') || url.pathname.startsWith('/auth/')) {
          let body = [];
          if (url.pathname === '/api/medicos') body = [doctor];
          else if (url.pathname === '/api/especialidades') body = [doctor.especialidad];
          else if (/\/usuarios\/\d+$/.test(url.pathname)) body = doctor;
          else if (/contar|count|no-leidas/.test(url.pathname)) body = 0;
          report.requests.push({ url: request.url, method: request.method, action: 'fixture' });
          await send('Fetch.fulfillRequest', { requestId, responseCode: 200, responseHeaders: [{ name: 'Content-Type', value: 'application/json' }, { name: 'Access-Control-Allow-Origin', value: origin }, { name: 'Access-Control-Allow-Headers', value: '*' }], body: Buffer.from(JSON.stringify(body)).toString('base64') });
        } else if (url.origin === origin) await send('Fetch.continueRequest', { requestId });
        else { report.requests.push({ url: request.url, action: 'blocked' }); await send('Fetch.failRequest', { requestId, errorReason: 'BlockedByClient' }); }
      } catch (e) { report.errors.push(e.message); }
    }
    if (m.method === 'Runtime.exceptionThrown') report.errors.push(m.params.exceptionDetails.text);
  });
  await send('Page.enable');
  await send('Runtime.enable');
  await send('Network.enable');
  await send('Network.setCacheDisabled', { cacheDisabled: true });
  await send('Fetch.enable', { patterns: [{ urlPattern: '*' }] });
  report.browser = await send('Browser.getVersion');
  report.build = { indexModified: fs.statSync(path.join(root, 'index.html')).mtime.toISOString(), indexSha256: require('node:crypto').createHash('sha256').update(fs.readFileSync(path.join(root, 'index.html'))).digest('hex') };
  for (const [w, h] of [[1280, 900], [375, 812], [320, 640], [667, 375]]) {
    await visit('/medicos', 'ADMIN', w, h);
    await wait('document.querySelector(".btn-edit")');
    await click('.btn-edit');
    await wait('document.querySelector(".form-grid-edit")');
    await pause(350);
    await shot(`doctor-${w}x${h}`);
    await check(`doctor inputs contained and disjoint ${w}x${h}`, async () => {
      const m = await evaluate(`(()=>{const grid=document.querySelector('.form-grid-edit');const r=grid.getBoundingClientRect();return {grid:{left:r.left,right:r.right},inputs:[...grid.querySelectorAll('input')].map(e=>{const r=e.getBoundingClientRect();return {left:r.left,right:r.right,top:r.top,bottom:r.bottom,width:r.width}})}})()`);
      for (const r of m.inputs) { assert(r.left >= m.grid.left - 1 && r.right <= m.grid.right + 1); assert(r.left >= 0 && r.right <= w); }
      for (let i = 0; i < m.inputs.length; i++) for (let j = i + 1; j < m.inputs.length; j++) { const a = m.inputs[i], b = m.inputs[j]; assert(!(a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top)); }
      return m;
    });
    await check(`modal footer reachable ${w}x${h}`, async () => {
      await evaluate(`document.querySelector('.modal-footer-glass .btn-confirm-glass').scrollIntoView({block:'center'})`);
      const m = await evaluate(`(()=>{const e=document.querySelector('.modal-footer-glass .btn-confirm-glass'),r=e.getBoundingClientRect();return {top:r.top,bottom:r.bottom,hit:e.contains(document.elementFromPoint(r.x+r.width/2,r.y+r.height/2))}})()`);
      assert(m.top >= 0 && m.bottom <= h && m.hit); await shot(`footer-${w}x${h}`); return m;
    });
    await check(`modal focus trap and Escape ${w}x${h}`, async () => {
      await evaluate(`document.querySelector('.modal-footer-glass .btn-confirm-glass').focus()`);
      await key('Tab');
      assert(await evaluate(`document.activeElement.matches('.btn-close-glass')`));
      await key('Tab', true);
      assert(await evaluate(`document.activeElement.matches('.btn-confirm-glass')`));
      await key('Escape'); await pause(150);
      assert(await evaluate(`!document.querySelector('.glass-overlay')`));
      assert(await evaluate(`document.activeElement.matches('.btn-edit')`));
    });
  }
  for (const w of [375, 320]) {
    await visit('/notificaciones', 'PACIENTE', w, 740);
    await check(`patient sidebar toggle ${w}`, async () => {
      await click('.sidebar-mobile-toggle');
      await pause(500);
      assert(await evaluate(`document.querySelector('#dashboard-sidebar').classList.contains('open')`), 'toggle did not open sidebar');
      const stacking = await evaluate(`(()=>{const s=document.querySelector('#dashboard-sidebar'),b=document.querySelector('.sidebar-backdrop');return {sidebarZ:getComputedStyle(s).zIndex,backdropZ:getComputedStyle(b).zIndex,hitAtClose:document.elementFromPoint(100,30)?.className,active:document.activeElement.className}})()`);
      await shot(`sidebar-${w}`);
      return stacking;
    });
    await check(`patient sidebar focus trap ${w}`, async () => {
      await evaluate(`document.querySelector('.btn-logout-sidebar').focus()`); await key('Tab');
      assert(await evaluate(`document.activeElement.matches('.sidebar-mobile-close')`), 'Tab from logout did not wrap to close');
    });
    await check(`patient sidebar Escape ${w}`, async () => {
      await key('Escape'); await pause(100);
      assert(await evaluate(`!document.querySelector('#dashboard-sidebar').classList.contains('open') && document.activeElement.matches('.sidebar-mobile-toggle')`), 'Escape did not close and restore toggle focus');
    });
    await check(`patient sidebar backdrop ${w}`, async () => {
      if (!await evaluate(`document.querySelector('.sidebar-backdrop') !== null`)) await click('.sidebar-mobile-toggle');
      await send('Input.dispatchMouseEvent', { type: 'mousePressed', x: w - 20, y: 400, button: 'left', clickCount: 1 });
      await send('Input.dispatchMouseEvent', { type: 'mouseReleased', x: w - 20, y: 400, button: 'left', clickCount: 1 });
      await pause(200);
      assert(await evaluate(`!document.querySelector('.sidebar-backdrop')`));
    });
  }
  for (const w of [1280, 375, 320]) {
    await visit('/trabaja-con-nosotros', null, w, 900);
    await wait('document.querySelector(".job-form")');
    await check(`application inputs ${w}`, async () => {
      const m = await evaluate(`[...document.querySelectorAll('.job-form input,.job-form textarea')].map(e=>{const r=e.getBoundingClientRect(),p=e.parentElement.getBoundingClientRect();return {id:e.id,left:r.left,right:r.right,top:r.top,bottom:r.bottom,parentLeft:p.left,parentRight:p.right,width:r.width}})`);
      for (const r of m) assert(r.left >= 0 && r.right <= w && r.left >= r.parentLeft - 1 && r.right <= r.parentRight + 1);
      for (let i=0;i<m.length;i++) for(let j=i+1;j<m.length;j++) { const a=m[i],b=m[j]; assert(!(a.left<b.right && a.right>b.left && a.top<b.bottom && a.bottom>b.top)); }
      await evaluate(`document.querySelector('.job-form').scrollIntoView({block:'center'})`); await shot(`application-${w}`); return m;
    });
    await check(`application submit center clickable ${w}`, async () => {
      await evaluate(`document.querySelector('.btn-submit-premium').scrollIntoView({block:'center'})`); await pause(500);
      const m = await evaluate(`(()=>{const e=document.querySelector('.btn-submit-premium'),r=e.getBoundingClientRect(),hit=document.elementFromPoint(r.x+r.width/2,r.y+r.height/2);return {hit:hit?.className,clickable:e.contains(hit),x:r.x+r.width/2,y:r.y+r.height/2}})()`);
      assert(m.clickable, JSON.stringify(m)); return m;
    });
    await check(`toast bounded ${w}`, async () => {
      await evaluate(`for(const [id,value] of Object.entries({nombre:'A',apellido:'Perez',correo:'fixture@gmail.com',dni:'12345678',telefono:'999123456'})){const e=document.getElementById(id);e.value=value;e.dispatchEvent(new Event('input',{bubbles:true}));}`);
      // DOM activation isolates toast layout from the separately tested real-pointer obstruction.
      await evaluate(`document.querySelector('.btn-submit-premium').click()`); await wait('document.querySelector(".toast-item")'); await pause(600);
      const m = await evaluate(`(()=>{const e=document.querySelector('.toast-item'),r=e.getBoundingClientRect();return {left:r.left,right:r.right,width:r.width,scrollWidth:e.scrollWidth,clientWidth:e.clientWidth}})()`);
      assert(m.left >= 0 && m.right <= w && m.scrollWidth <= m.clientWidth + 1); await shot(`toast-${w}`); return m;
    });
  }
  for (const w of [1280, 375, 320]) {
    await visit('/recepcion/medicos', 'RECEPCION', w, 800);
    await check(`reception read-only new route ${w}`, async () => {
      await wait('document.querySelector("app-recepcion-medicos tbody tr")');
      assert(await evaluate(`location.pathname==='/recepcion/medicos' && document.querySelector('app-recepcion-medicos').textContent.includes('Ana Maria') && !document.querySelector('app-recepcion-medicos button')`));
      const m = await evaluate(`({width:innerWidth,documentWidth:document.documentElement.scrollWidth,heading:document.querySelector('h1').textContent})`);
      assert(m.documentWidth <= w); await shot(`reception-${w}`); return m;
    });
  }
}
main().catch(e => { report.fatal = e.stack; console.error(e); }).finally(async () => {
  fs.writeFileSync(path.join(output, 'report.json'), JSON.stringify(report, null, 2));
  console.log(`Artifacts: ${output}`);
  if (ws) ws.close();
  if (chrome) chrome.kill();
  if (server) server.close();
  process.exitCode = report.fatal || report.checks.some(c => !c.pass) ? 1 : 0;
});
