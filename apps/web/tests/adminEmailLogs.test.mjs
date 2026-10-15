import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test, { afterEach } from 'node:test';
import ts from 'typescript';

afterEach(() => { delete globalThis.__adminEmailHarness; });
const source = ts.transpileModule(await readFile(new URL('../src/components/AdminDashboard.tsx', import.meta.url), 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022, jsx: ts.JsxEmit.React },
}).outputText
  .replace(/import React, \{[^}]+\} from 'react';/, `const harness = globalThis.__adminEmailHarness;
    const React = harness.React;
    const { useState, useEffect } = React;`)
  .replace(/import \{ api \} from '[^']+';/, 'const api = harness.api;')
  .replace(/import \{([^}]+)\} from 'lucide-react';/, 'const {$1} = harness.icons;')
  .replace(/import \{ addVietnamDays \} from '[^']+';/, "const addVietnamDays = () => '2026-10-16';");

const all = element => element && typeof element === 'object'
  ? [element, ...element.children.flat(Infinity).flatMap(all)] : [];
const textOf = element => typeof element === 'string' ? element
  : element && typeof element === 'object' ? element.children.flat(Infinity).map(textOf).join(' ') : '';

test('admin email log uses a neutral heading and refreshes without linking to Mailpit', async () => {
  let cursor = 0;
  const states = [];
  let finish;
  let calls = 0;
  globalThis.__adminEmailHarness = {
    React: {
      createElement: (type, props, ...children) => ({ type, props, children }),
      useState: initial => {
        const index = cursor++;
        if (!(index in states)) states[index] = index === 0 ? 'EMAILS' : (typeof initial === 'function' ? initial() : initial);
        return [states[index], value => { states[index] = typeof value === 'function' ? value(states[index]) : value; }];
      },
      useEffect() {},
    },
    api: { getEmailLogs: () => { calls++; return new Promise(resolve => { finish = resolve; }); } },
    icons: new Proxy({}, { get: (_target, name) => name }),
  };
  const { AdminDashboard } = await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`);
  const render = () => { cursor = 0; return AdminDashboard(); };
  const initial = render();
  assert.match(textOf(initial), /Nhật ký email xác nhận/);
  assert.doesNotMatch(textOf(initial), /Mailpit/);
  assert.ok(all(initial).every(element => !element.props?.href?.includes('8025')));
  const refresh = all(initial).find(element => element.props?.id === 'refresh-email-logs');
  assert.ok(refresh);
  const pending = refresh.props.onClick();
  assert.equal(calls, 1);
  assert.equal(all(render()).find(element => element.props?.id === 'refresh-email-logs').props.disabled, true);
  finish([{ id: 'mail-1', recipientEmail: 'recipient@example.com', subject: 'Xác nhận đặt lịch', status: 'SENT', retryCount: 0,
    createdAt: '2026-10-15T09:00:00Z' }]);
  await pending;
  const updated = render();
  assert.match(textOf(updated), /recipient@example.com/);
  assert.match(textOf(updated), /SENT/);
  assert.equal(all(updated).find(element => element.props?.id === 'refresh-email-logs').props.disabled, false);
});
