import test from 'node:test';
import assert from 'node:assert/strict';
import { esc, html, raw, money, label, statusTone, parseJwt, isExpired, validateClaim, validatePassword, fraudTone, isStaff } from '../api-gateway/src/main/resources/static/js/lib.js';

const jwt = (payload) => `h.${Buffer.from(JSON.stringify(payload)).toString('base64url')}.s`;

test('esc neutralises markup', () => {
  assert.equal(esc('<img src=x onerror="a()">'), '&lt;img src=x onerror=&quot;a()&quot;&gt;');
  assert.equal(esc(null), '');
});

test('html tag escapes interpolations but keeps raw fragments', () => {
  const evil = '<script>alert(1)</script>';
  assert.equal(html`<p>${evil}</p>`.s, '<p>&lt;script&gt;alert(1)&lt;/script&gt;</p>');
  assert.equal(html`<ul>${[1, 2].map((n) => html`<li>${n}</li>`)}</ul>`.s, '<ul><li>1</li><li>2</li></ul>');
  assert.equal(html`<b>${raw('<i>ok</i>')}</b>`.s, '<b><i>ok</i></b>');
});

test('formatting helpers', () => {
  assert.equal(money(1234.5), '$1,234.50');
  assert.equal(money(null), '—');
  assert.equal(label('UNDER_REVIEW'), 'Under review');
});

test('status and fraud tones', () => {
  assert.equal(statusTone('PAID'), 'good');
  assert.equal(statusTone('REJECTED'), 'bad');
  assert.equal(statusTone('WITHDRAWN'), 'muted');
  assert.equal(fraudTone('HIGH'), 'bad');
});

test('jwt parsing and expiry', () => {
  const t = jwt({ sub: 'u1', exp: 2000 });
  assert.equal(parseJwt(t).sub, 'u1');
  assert.equal(isExpired(t, 1999 * 1000), false);
  assert.equal(isExpired(t, 2001 * 1000), true);
  assert.equal(isExpired('garbage'), true);
});

test('claim validation', () => {
  const today = new Date('2026-06-01');
  assert.deepEqual(validateClaim({ policyId: 'p', incidentDate: '2026-05-01', amountClaimed: 100, description: 'A burst pipe flooded it' }, today), {});
  const e = validateClaim({ policyId: '', incidentDate: '2026-07-01', amountClaimed: 0, description: 'short' }, today);
  assert.deepEqual(Object.keys(e).sort(), ['amountClaimed', 'description', 'incidentDate', 'policyId']);
});

test('password policy mirrors the server', () => {
  assert.ok(validatePassword('short'));
  assert.ok(validatePassword('alllowercase12'));
  assert.equal(validatePassword('Str0ngPassword'), null);
});

test('role helper', () => {
  assert.equal(isStaff(['CUSTOMER']), false);
  assert.equal(isStaff(['ADJUSTER']), true);
});
