import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test, { afterEach } from 'node:test';
import ts from 'typescript';

const originalFetch = globalThis.fetch;
const originalStorage = globalThis.localStorage;
afterEach(() => {
  globalThis.fetch = originalFetch;
  if (originalStorage) globalThis.localStorage = originalStorage;
  else delete globalThis.localStorage;
  delete globalThis.__authHarness;
});

const transpile = source => ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022, jsx: ts.JsxEmit.React },
}).outputText;
const moduleUrl = source => `data:text/javascript;base64,${Buffer.from(source).toString('base64')}`;
const apiSource = transpile(await readFile(new URL('../src/services/api.ts', import.meta.url), 'utf8'));
const contextSource = transpile(await readFile(new URL('../src/context/AuthContext.tsx', import.meta.url), 'utf8'))
  .replace(/import React, \{[^}]+\} from 'react';/, `const harness = globalThis.__authHarness;
    const React = harness.React;
    const { createContext, useContext, useState, useEffect } = React;`)
  .replace(/import \{ api \} from '[^']+';/, 'const api = harness.api;');

const patient = { id: 'new-patient', email: 'new@example.test', displayName: 'Bệnh nhân mới', role: 'PATIENT' };
const details = { email: patient.email, fullName: patient.displayName, password: 'test-password' };
const respond = (data, status = 200) => new Response(JSON.stringify({ success: status < 400, data }), {
  status, headers: { 'Content-Type': 'application/json' },
});

async function freshApi() {
  const storage = new Map();
  globalThis.localStorage = {
    getItem: key => storage.get(key) ?? null,
    setItem: (key, value) => storage.set(key, String(value)),
    removeItem: key => storage.delete(key),
  };
  const { api } = await import(moduleUrl(`${apiSource}\n// ${Math.random()}`));
  return { api, storage };
}

test('registration accepts UserResponse then logs in and persists the real session token', async () => {
  const { api, storage } = await freshApi();
  const calls = [];
  globalThis.fetch = async (url, options) => {
    calls.push(url);
    if (url.endsWith('/register')) {
      assert.deepEqual(JSON.parse(options.body), {
        email: details.email, password: details.password, displayName: details.fullName,
      });
      return respond(patient, 201);
    }
    assert.ok(url.endsWith('/login'));
    assert.equal(storage.has('auth_token'), false, 'No undefined registration token is stored');
    assert.deepEqual(JSON.parse(options.body), { email: details.email, password: details.password });
    return respond({ accessToken: 'real-token', refreshToken: 'refresh', user: patient });
  };
  const result = await api.register(details);
  assert.deepEqual(calls, ['/api/v1/auth/register', '/api/v1/auth/login']);
  assert.equal(result.user.id, patient.id);
  assert.equal(result.user.role, 'PATIENT');
  assert.equal(result.user.fullName, patient.displayName);
  assert.equal(api.getToken(), 'real-token');
  assert.equal(storage.get('auth_token'), 'real-token');
});

test('duplicate email leaves the session untouched and does not try to log in', async () => {
  const { api, storage } = await freshApi();
  let calls = 0;
  globalThis.fetch = async url => {
    calls++;
    assert.ok(url.endsWith('/register'));
    return new Response(JSON.stringify({ message: 'Email đã được sử dụng' }), { status: 400 });
  };
  await assert.rejects(api.register(details), /Email đã được sử dụng/);
  assert.equal(calls, 1);
  assert.equal(storage.has('auth_token'), false);
  assert.equal(api.getToken(), null);
});

test('login failure after account creation explains that the patient should log in, not register again', async () => {
  const { api } = await freshApi();
  globalThis.fetch = async url => url.endsWith('/register')
    ? respond(patient, 201)
    : new Response(null, { status: 503 });
  await assert.rejects(api.register(details), /Tài khoản đã được tạo.*Đăng nhập/);
  assert.equal(api.getToken(), null);
});

test('pending and failed registration do not trigger the App loading screen or clear form state', async () => {
  const states = [];
  let cursor = 0;
  let rejectRegistration;
  const pending = new Promise((_resolve, reject) => { rejectRegistration = reject; });
  const harness = {
    api: { getToken: () => null, register: () => pending },
    React: {
      createContext: () => ({ Provider: 'provider' }),
      createElement: (_type, props) => props.value,
      useState: initial => {
        const index = cursor++;
        if (!(index in states)) states[index] = initial;
        return [states[index], value => { states[index] = value; }];
      },
      useEffect: () => {},
    },
  };
  globalThis.__authHarness = harness;
  const { AuthProvider } = await import(moduleUrl(`${contextSource}\n// ${Math.random()}`));
  const render = () => { cursor = 0; return AuthProvider({ children: null }); };
  render();
  states[1] = false; // Initial session check has finished.
  const context = render();
  const request = context.register(details);
  assert.equal(render().loading, false);
  rejectRegistration(new Error('Email đã được sử dụng'));
  await assert.rejects(request, /Email đã được sử dụng/);
  assert.equal(render().loading, false);
  assert.equal(render().user, null);
});
