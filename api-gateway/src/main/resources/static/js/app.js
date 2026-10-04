import { api, session, ApiError } from './api.js';
import { html, raw, money, fmtDate, label, statusTone, validateClaim, validatePassword, fraudTone, isStaff } from './lib.js';

const app = document.getElementById('app');
const topbar = document.getElementById('topbar');
const toastEl = document.getElementById('toast');
let toastTimer;

const badge = (text, tone) => html`<span class="badge ${tone}">${label(text)}</span>`;
const render = (view) => { app.innerHTML = view.s; };

function toast(msg, tone = 'good') {
  toastEl.textContent = msg;
  toastEl.className = `toast ${tone}`;
  toastEl.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { toastEl.hidden = true; }, 3500);
}

function fail(e) {
  toast(e instanceof ApiError ? e.message : 'Something went wrong', 'bad');
}

function showFieldErrors(form, fields) {
  form.querySelectorAll('.field-error').forEach((n) => n.remove());
  Object.entries(fields).forEach(([name, msg]) => {
    const input = form.elements[name];
    if (!input) return;
    const p = document.createElement('p');
    p.className = 'field-error';
    p.textContent = msg;
    input.closest('label')?.append(p);
  });
}

const formData = (form) => Object.fromEntries(new FormData(form));

// ---------------------------------------------------------------- chrome
async function renderTopbar(user) {
  if (!user) {
    topbar.innerHTML = html`<a class="brand" href="#/login">${raw('<span class="logo">◈</span>')} ClaimSure</a>`.s;
    return;
  }
  const staff = isStaff(user.roles);
  const links = staff
    ? [['#/review', 'Claims queue'], ['#/policies', 'All policies']]
    : [['#/', 'Overview'], ['#/quote', 'Get a quote'], ['#/policies', 'Policies'], ['#/claims', 'Claims']];
  topbar.innerHTML = html`
    <a class="brand" href="#/">${raw('<span class="logo">◈</span>')} ClaimSure</a>
    <nav>${links.map(([h, t]) => html`<a href="${h}" data-nav="${h}">${t}</a>`)}</nav>
    <div class="right">
      <a class="bell" href="#/notifications" aria-label="Notifications">🔔<span id="bellCount" class="count" hidden></span></a>
      <span class="who">${user.name}<small>${user.roles.map(label).join(', ')}</small></span>
      <button class="link" id="logout">Sign out</button>
    </div>`.s;
  document.getElementById('logout').onclick = () => { session.clear(); location.hash = '#/login'; };
  refreshBell();
}

async function refreshBell() {
  try {
    const { unread } = await api('/api/v1/notifications/unread-count');
    const el = document.getElementById('bellCount');
    if (el) { el.textContent = unread; el.hidden = unread === 0; }
  } catch { /* the bell is best-effort */ }
}

function markActive(path) {
  topbar.querySelectorAll('[data-nav]').forEach((a) => {
    const t = a.dataset.nav.slice(1);
    a.classList.toggle('active', t === path || (t !== '/' && path.startsWith(t)));
  });
}

// ---------------------------------------------------------------- auth views
function loginView() {
  render(html`
    <section class="auth">
      <div class="auth-hero">
        <h1>Insurance that moves<br>as fast as you do.</h1>
        <p>Instant quotes, paperless policies and claims you can track step by step.</p>
      </div>
      <form id="login" class="card form" novalidate>
        <h2>Sign in</h2>
        <label>Email<input name="email" type="email" autocomplete="username" required></label>
        <label>Password<input name="password" type="password" autocomplete="current-password" required></label>
        <button class="btn primary" type="submit">Sign in</button>
        <p class="hint">New here? <a href="#/register">Create an account</a></p>
        <details class="demo"><summary>Demo accounts</summary>
          <p>customer@claimsure.test · adjuster@claimsure.test · admin@claimsure.test</p>
          <p>Password: <code>Passw0rd!demo</code></p></details>
      </form>
    </section>`);
  document.getElementById('login').onsubmit = async (e) => {
    e.preventDefault();
    try {
      const res = await api('/api/v1/auth/login', { method: 'POST', body: formData(e.target), auth: false });
      session.set(res.accessToken);
      location.hash = isStaff(res.user.roles) ? '#/review' : '#/';
    } catch (err) { fail(err); }
  };
}

function registerView() {
  render(html`
    <section class="auth single">
      <form id="reg" class="card form" novalidate>
        <h2>Create your account</h2>
        <label>Full name<input name="fullName" autocomplete="name" required></label>
        <label>Email<input name="email" type="email" autocomplete="username" required></label>
        <label>Password<input name="password" type="password" autocomplete="new-password" required></label>
        <p class="hint">At least 10 characters with upper case, lower case and a digit.</p>
        <button class="btn primary" type="submit">Create account</button>
        <p class="hint">Already registered? <a href="#/login">Sign in</a></p>
      </form>
    </section>`);
  document.getElementById('reg').onsubmit = async (e) => {
    e.preventDefault();
    const data = formData(e.target);
    const pwError = validatePassword(data.password);
    if (pwError) return showFieldErrors(e.target, { password: pwError });
    try {
      await api('/api/v1/auth/register', { method: 'POST', body: data, auth: false });
      toast('Account created - please sign in');
      location.hash = '#/login';
    } catch (err) { if (err.fields) showFieldErrors(e.target, err.fields); fail(err); }
  };
}

// ---------------------------------------------------------------- customer views
async function overviewView(user) {
  render(html`<p class="muted">Loading…</p>`);
  const [pol, clm, notes] = await Promise.all([
    api('/api/v1/policies?size=100'), api('/api/v1/claims?size=100'), api('/api/v1/notifications?size=5')]);
  const active = pol.content.filter((p) => p.status === 'ACTIVE');
  const open = clm.content.filter((c) => ['SUBMITTED', 'UNDER_REVIEW', 'APPROVED'].includes(c.status));
  const premium = active.reduce((s, p) => s + Number(p.annualPremium), 0);
  render(html`
    <h1>Welcome back, ${user.name.split(' ')[0]}</h1>
    <div class="stats">
      <div class="stat"><span>${active.length}</span>Active policies</div>
      <div class="stat"><span>${money(premium)}</span>Yearly premium</div>
      <div class="stat"><span>${open.length}</span>Open claims</div>
    </div>
    <div class="grid2">
      <section class="card"><h2>Your policies</h2>
        ${active.length ? html`<ul class="list">${active.slice(0, 4).map((p) => html`
          <li><div><strong>${label(p.type)}</strong><small>${p.policyNumber}</small></div>
          <div class="right">${money(p.coverageAmount)}<small>covered</small></div></li>`)}</ul>`
          : html`<p class="muted">No policies yet.</p>`}
        <a class="btn" href="#/quote">Get a quote</a></section>
      <section class="card"><h2>Recent updates</h2>
        ${notes.content.length ? html`<ul class="list">${notes.content.map((n) => html`
          <li class="${n.read ? '' : 'unread'}"><div><strong>${n.title}</strong><small>${n.message}</small></div>
          <small>${fmtDate(n.createdAt)}</small></li>`)}</ul>` : html`<p class="muted">Nothing new.</p>`}
        <a class="btn" href="#/notifications">All notifications</a></section>
    </div>`);
}

const quoteFields = (f) => ({
  type: f.type, coverageAmount: Number(f.coverageAmount), customerAge: Number(f.customerAge),
  claimsInLast5Years: Number(f.claimsInLast5Years), smoker: f.smoker === 'on',
  assetAgeYears: f.assetAgeYears === '' || f.assetAgeYears == null ? null : Number(f.assetAgeYears),
  deductible: f.deductible,
});

async function quoteView() {
  const products = await api('/api/v1/products', { auth: false });
  const today = new Date().toISOString().slice(0, 10);
  render(html`
    <h1>Get a quote</h1>
    <div class="grid2">
      <form id="quote" class="card form" novalidate>
        <label>Cover type<select name="type">${products.map((p) => html`<option value="${p.type}">${p.name} - ${p.description}</option>`)}</select></label>
        <label>Coverage amount (USD)<input name="coverageAmount" type="number" min="1000" step="1000" value="50000" required></label>
        <div class="row">
          <label>Your age<input name="customerAge" type="number" min="18" max="99" value="35" required></label>
          <label>Claims in last 5 yrs<input name="claimsInLast5Years" type="number" min="0" max="20" value="0" required></label>
        </div>
        <div class="row">
          <label id="assetLbl">Vehicle / building age (yrs)<input name="assetAgeYears" type="number" min="0" max="100" value="5"></label>
          <label>Deductible<select name="deductible"><option value="LOW">Low</option><option value="STANDARD" selected>Standard</option><option value="HIGH">High</option></select></label>
        </div>
        <label class="check"><input name="smoker" type="checkbox"> Smoker (health &amp; life)</label>
        <button class="btn primary" type="submit">Calculate premium</button>
      </form>
      <section class="card" id="result"><h2>Your premium</h2><p class="muted">Fill in the form to see a price breakdown.</p></section>
    </div>`);
  const form = document.getElementById('quote');
  const result = document.getElementById('result');
  let lastQuote = null;
  form.onsubmit = async (e) => {
    e.preventDefault();
    try {
      lastQuote = quoteFields(formData(form));
      const q = await api('/api/v2/quotes', { method: 'POST', body: lastQuote });
      result.innerHTML = html`
        <h2>Your premium</h2>
        <div class="price">${money(q.annualPremium)}<small>per year · ${money(q.monthlyPremium)} / month</small></div>
        <table class="table"><tbody>
          <tr><td>Base premium</td><td class="num">${money(q.basePremium)}</td></tr>
          ${q.factors.map((f) => html`<tr><td>${f.name}</td><td class="num">×${Number(f.multiplier).toFixed(2)}</td></tr>`)}
        </tbody></table>
        <label class="inline">Start date <input type="date" id="start" min="${today}" value="${today}"></label>
        <button class="btn primary" id="buy">Buy this policy</button>`.s;
      document.getElementById('buy').onclick = async () => {
        try {
          await api('/api/v1/policies', { method: 'POST', body: { quote: lastQuote, startDate: document.getElementById('start').value } });
          toast('Policy purchased!');
          location.hash = '#/policies';
        } catch (err) { fail(err); }
      };
    } catch (err) { if (err.fields) showFieldErrors(form, err.fields); fail(err); }
  };
}

async function policiesView(user) {
  const staff = isStaff(user.roles);
  render(html`<p class="muted">Loading…</p>`);
  const page = await api(`/api/v1/policies?size=50${staff ? '&all=true' : ''}`);
  render(html`
    <div class="head"><h1>${staff ? 'All policies' : 'Your policies'}</h1>
      ${staff ? '' : html`<a class="btn primary" href="#/quote">New policy</a>`}</div>
    ${page.content.length ? html`<div class="cards">${page.content.map((p) => html`
      <article class="card policy">
        <div class="head"><h3>${label(p.type)}</h3>${badge(p.status, statusTone(p.status))}</div>
        <p class="mono">${p.policyNumber}</p>
        <dl><dt>Coverage</dt><dd>${money(p.coverageAmount)}</dd><dt>Premium</dt><dd>${money(p.annualPremium)} / yr</dd>
        <dt>Period</dt><dd>${fmtDate(p.startDate)} – ${fmtDate(p.endDate)}</dd></dl>
        ${!staff && p.status === 'ACTIVE' ? html`<div class="actions">
          <a class="btn" href="#/claims/new/${p.id}">File a claim</a>
          <button class="btn danger" data-cancel="${p.id}">Cancel</button></div>` : ''}
      </article>`)}</div>` : html`<p class="muted">No policies to show.</p>`}`);
  app.querySelectorAll('[data-cancel]').forEach((b) => {
    b.onclick = async () => {
      if (!confirm('Cancel this policy? This cannot be undone.')) return;
      try { await api(`/api/v1/policies/${b.dataset.cancel}/cancel`, { method: 'POST' }); toast('Policy cancelled'); policiesView(user); }
      catch (err) { fail(err); }
    };
  });
}

async function claimsView() {
  render(html`<p class="muted">Loading…</p>`);
  const page = await api('/api/v1/claims?size=50');
  render(html`
    <div class="head"><h1>Your claims</h1><a class="btn primary" href="#/claims/new">File a claim</a></div>
    ${claimsTable(page.content, false)}`);
}

function claimsTable(items, staff) {
  if (!items.length) return html`<p class="muted">No claims to show.</p>`;
  return html`<table class="table claims"><thead><tr><th>Claim</th><th>Policy</th><th>Amount</th><th>Status</th>
    ${staff ? html`<th>Fraud risk</th>` : ''}<th>Filed</th></tr></thead><tbody>
    ${items.map((c) => html`<tr class="click" data-id="${c.id}">
      <td><a href="#/claims/${c.id}">${c.claimNumber}</a></td><td>${c.policyNumber}</td>
      <td class="num">${money(c.amountClaimed)}</td><td>${badge(c.status, statusTone(c.status))}</td>
      ${staff ? html`<td>${badge(c.fraudLevel, fraudTone(c.fraudLevel))} <small>${c.fraudScore}</small></td>` : ''}
      <td>${fmtDate(c.createdAt)}</td></tr>`)}</tbody></table>`;
}

async function newClaimView(preselect) {
  const page = await api('/api/v1/policies?size=100');
  const active = page.content.filter((p) => p.status === 'ACTIVE');
  if (!active.length) return render(html`<h1>File a claim</h1><p class="muted">You need an active policy first. <a href="#/quote">Get a quote</a></p>`);
  render(html`
    <h1>File a claim</h1>
    <form id="claim" class="card form narrow" novalidate>
      <label>Policy<select name="policyId">${active.map((p) =>
        html`<option value="${p.id}" ${p.id === preselect ? raw('selected') : ''}>${label(p.type)} · ${p.policyNumber} (${money(p.coverageAmount)})</option>`)}</select></label>
      <div class="row">
        <label>Incident date<input name="incidentDate" type="date" max="${new Date().toISOString().slice(0, 10)}" required></label>
        <label>Amount claimed (USD)<input name="amountClaimed" type="number" min="1" step="0.01" required></label>
      </div>
      <label>What happened?<textarea name="description" rows="5" maxlength="2000" required></textarea></label>
      <button class="btn primary" type="submit">Submit claim</button>
    </form>`);
  const form = document.getElementById('claim');
  form.onsubmit = async (e) => {
    e.preventDefault();
    const data = formData(form);
    const errors = validateClaim(data);
    showFieldErrors(form, errors);
    if (Object.keys(errors).length) return;
    try {
      const c = await api('/api/v1/claims', { method: 'POST', body: { ...data, amountClaimed: Number(data.amountClaimed) } });
      toast(`Claim ${c.claimNumber} submitted`);
      location.hash = `#/claims/${c.id}`;
    } catch (err) { if (err.fields) showFieldErrors(form, err.fields); fail(err); }
  };
}

async function claimDetailView(id, user) {
  const c = await api(`/api/v1/claims/${encodeURIComponent(id)}`);
  const staff = isStaff(user.roles);
  const open = !['REJECTED', 'PAID', 'WITHDRAWN'].includes(c.status);
  render(html`
    <p><a href="${staff ? '#/review' : '#/claims'}">← Back</a></p>
    <div class="head"><h1>${c.claimNumber}</h1>${badge(c.status, statusTone(c.status))}</div>
    <div class="grid2">
      <section class="card"><h2>Details</h2>
        <dl><dt>Policy</dt><dd>${c.policyNumber}</dd><dt>Incident</dt><dd>${fmtDate(c.incidentDate)}</dd>
        <dt>Claimed</dt><dd>${money(c.amountClaimed)}</dd><dt>Approved</dt><dd>${money(c.approvedAmount)}</dd>
        ${staff ? html`<dt>Customer</dt><dd>${c.customerEmail}</dd>` : ''}</dl>
        <p class="desc">${c.description}</p>
        ${staff ? html`<div class="fraud ${fraudTone(c.fraudLevel)}"><strong>Fraud screening: ${label(c.fraudLevel)} (${c.fraudScore}/100)</strong>
          ${c.fraudFlags.length ? html`<ul>${c.fraudFlags.map((f) => html`<li>${f}</li>`)}</ul>` : html`<p>No risk signals.</p>`}</div>` : ''}
      </section>
      <section class="card"><h2>Timeline</h2>
        <ol class="timeline">
          <li><strong>Submitted</strong><small>${fmtDate(c.createdAt)}</small></li>
          ${c.history.map((h) => html`<li><strong>${label(h.to)}</strong><small>${fmtDate(h.at)}${staff ? ' · ' + h.by : ''}</small>${h.note ? html`<p>${h.note}</p>` : ''}</li>`)}
        </ol>
        <div class="actions" id="actions"></div>
      </section>
    </div>`);
  const actions = document.getElementById('actions');
  const act = async (path, body) => {
    try { await api(`/api/v1/claims/${id}/${path}`, { method: 'POST', body }); toast('Updated'); claimDetailView(id, user); }
    catch (err) { fail(err); }
  };
  const btn = (text, cls, fn) => { const b = document.createElement('button'); b.className = `btn ${cls}`; b.textContent = text; b.onclick = fn; actions.append(b); };
  if (!staff && ['SUBMITTED', 'UNDER_REVIEW'].includes(c.status)) btn('Withdraw claim', 'danger', () => confirm('Withdraw this claim?') && act('withdraw'));
  if (staff && c.status === 'SUBMITTED' && user.roles.includes('ADJUSTER')) btn('Start review', 'primary', () => act('review'));
  if (staff && c.status === 'UNDER_REVIEW') {
    btn('Approve…', 'primary', () => {
      const v = prompt('Approved amount (max ' + c.amountClaimed + ')', c.amountClaimed);
      if (v !== null && Number(v) > 0) act('approve', { approvedAmount: Number(v), note: 'Approved after review' });
    });
    btn('Reject…', 'danger', () => { const r = prompt('Reason for rejection'); if (r) act('reject', { reason: r }); });
  }
  if (staff && c.status === 'APPROVED' && user.roles.includes('ADMIN')) btn('Issue payment', 'primary', () => act('pay'));
  if (!open && !actions.children.length) actions.remove();
}

// ---------------------------------------------------------------- staff + shared
async function reviewQueueView() {
  render(html`<p class="muted">Loading…</p>`);
  const filter = new URLSearchParams(location.hash.split('?')[1] || '').get('status') || '';
  const page = await api(`/api/v1/claims?all=true&size=100${filter ? '&status=' + encodeURIComponent(filter) : ''}`);
  const statuses = ['', 'SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'REJECTED', 'PAID', 'WITHDRAWN'];
  render(html`
    <div class="head"><h1>Claims queue</h1>
      <label class="inline">Status <select id="statusFilter">${statuses.map((s) =>
        html`<option value="${s}" ${s === filter ? raw('selected') : ''}>${s ? label(s) : 'All'}</option>`)}</select></label></div>
    ${claimsTable(page.content, true)}`);
  document.getElementById('statusFilter').onchange = (e) => { location.hash = `#/review${e.target.value ? '?status=' + e.target.value : ''}`; };
}

async function notificationsView() {
  render(html`<p class="muted">Loading…</p>`);
  const page = await api('/api/v1/notifications?size=50');
  render(html`<h1>Notifications</h1>
    ${page.content.length ? html`<ul class="list card">${page.content.map((n) => html`
      <li class="${n.read ? '' : 'unread'}"><div><strong>${n.title}</strong><small>${n.message}</small></div>
      <div class="right"><small>${fmtDate(n.createdAt)}</small>
      ${n.read ? '' : html`<button class="link" data-read="${n.id}">Mark read</button>`}</div></li>`)}</ul>`
      : html`<p class="muted">You're all caught up.</p>`}`);
  app.querySelectorAll('[data-read]').forEach((b) => {
    b.onclick = async () => { await api(`/api/v1/notifications/${b.dataset.read}/read`, { method: 'POST' }).catch(fail); notificationsView(); refreshBell(); };
  });
}

// ---------------------------------------------------------------- router
async function route() {
  const path = '/' + location.hash.slice(1).split('?')[0].split('/').filter(Boolean).join('/');
  const parts = path.split('/').filter(Boolean);
  const user = session.user();
  const publicRoute = ['login', 'register'].includes(parts[0]);
  if (!user && !publicRoute) { location.hash = '#/login'; return; }
  if (user && publicRoute) { location.hash = isStaff(user.roles) ? '#/review' : '#/'; return; }
  await renderTopbar(user);
  markActive(path);
  try {
    switch (parts[0]) {
      case 'login': return loginView();
      case 'register': return registerView();
      case undefined: return isStaff(user.roles) ? (location.hash = '#/review') : await overviewView(user);
      case 'quote': return await quoteView();
      case 'policies': return await policiesView(user);
      case 'claims':
        if (parts[1] === 'new') return await newClaimView(parts[2]);
        return parts[1] ? await claimDetailView(parts[1], user) : await claimsView();
      case 'review': return await reviewQueueView();
      case 'notifications': return await notificationsView();
      default: return render(html`<h1>Page not found</h1><a href="#/">Go home</a>`);
    }
  } catch (err) { render(html`<div class="card"><h2>Something went wrong</h2><p>${err.message}</p></div>`); }
}

window.addEventListener('hashchange', route);
route();
