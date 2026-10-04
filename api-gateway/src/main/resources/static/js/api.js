import { isExpired, parseJwt } from './lib.js';

const KEY = 'claimsure.token';
// sessionStorage: the token disappears when the tab closes (smaller exposure window than localStorage).
export const session = {
  get token() { try { return sessionStorage.getItem(KEY); } catch { return null; } },
  set(token) { try { sessionStorage.setItem(KEY, token); } catch { /* storage blocked */ } },
  clear() { try { sessionStorage.removeItem(KEY); } catch { /* ignore */ } },
  user() {
    const t = this.token;
    if (!t || isExpired(t)) return null;
    const p = parseJwt(t);
    return { id: p.sub, email: p.email, name: p.name, roles: p.roles || [] };
  },
};

export class ApiError extends Error {
  constructor(status, problem) {
    super(problem?.detail || problem?.title || `Request failed (${status})`);
    this.status = status;
    this.fields = problem?.errors || {};
  }
}

export async function api(path, { method = 'GET', body, auth = true } = {}) {
  const headers = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (auth && session.token) headers.Authorization = `Bearer ${session.token}`;
  let res;
  try {
    res = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  } catch {
    throw new ApiError(0, { detail: 'Cannot reach the server. Is the platform running?' });
  }
  if (res.status === 401 && auth) { session.clear(); location.hash = '#/login'; }
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) throw new ApiError(res.status, data);
  return data;
}
