// Pure helpers (no DOM, no network) so they can be unit-tested with `node --test`.

const ESC = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };
export const esc = (v) => String(v ?? '').replace(/[&<>"']/g, (c) => ESC[c]);

/** Marks a string as already-safe HTML for the `html` tag below. */
export class Raw { constructor(s) { this.s = s; } }
export const raw = (s) => new Raw(s);

/** Tagged template: every interpolation is HTML-escaped unless wrapped in raw() or an array of raw()s. */
export function html(strings, ...vals) {
  const part = (v) => (v instanceof Raw ? v.s : Array.isArray(v) ? v.map(part).join('') : esc(v));
  return new Raw(strings.reduce((out, s, i) => out + s + (i < vals.length ? part(vals[i]) : ''), ''));
}

export const money = (n) =>
  n == null ? '—' : new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(Number(n));

export const fmtDate = (d) => (d ? new Date(d).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' }) : '—');

export const label = (s) => String(s ?? '').toLowerCase().replace(/_/g, ' ').replace(/^\w/, (c) => c.toUpperCase());

export function statusTone(status) {
  switch (status) {
    case 'ACTIVE': case 'APPROVED': case 'PAID': return 'good';
    case 'UNDER_REVIEW': case 'SUBMITTED': return 'info';
    case 'REJECTED': case 'CANCELLED': return 'bad';
    default: return 'muted';
  }
}

export function parseJwt(token) {
  try {
    const b64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    return JSON.parse(decodeURIComponent(escape(atob(b64))));
  } catch { return null; }
}

export const isExpired = (token, now = Date.now()) => {
  const p = parseJwt(token);
  return !p || !p.exp || p.exp * 1000 <= now;
};

export function validateClaim({ policyId, incidentDate, amountClaimed, description }, today = new Date()) {
  const errors = {};
  if (!policyId) errors.policyId = 'Choose a policy';
  if (!incidentDate) errors.incidentDate = 'Enter the incident date';
  else if (new Date(incidentDate) > today) errors.incidentDate = 'Incident date cannot be in the future';
  const amt = Number(amountClaimed);
  if (!(amt >= 1)) errors.amountClaimed = 'Enter an amount greater than 0';
  if (!description || description.trim().length < 10) errors.description = 'Describe what happened (at least 10 characters)';
  return errors;
}

export function validatePassword(pw) {
  if (!pw || pw.length < 10) return 'Use at least 10 characters';
  if (!/[a-z]/.test(pw) || !/[A-Z]/.test(pw) || !/\d/.test(pw)) return 'Mix upper case, lower case and a digit';
  return null;
}

export const fraudTone = (level) => ({ HIGH: 'bad', MEDIUM: 'warn', LOW: 'good' }[level] || 'muted');

export const isStaff = (roles = []) => roles.includes('ADJUSTER') || roles.includes('ADMIN');
