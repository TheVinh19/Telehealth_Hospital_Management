import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const source = await readFile(new URL('../src/utils/chatMessages.ts', import.meta.url), 'utf8');
const { outputText } = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 },
});
const { normalizeChatMessage, mergeChatMessages } = await import(
  `data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`
);

const message = (clientMessageId, overrides = {}) => ({
  id: `pending-${clientMessageId}`,
  clientMessageId,
  senderId: 'patient-1',
  body: 'hi',
  createdAt: '2026-10-15T06:30:00Z',
  ...overrides,
});

test('flat and nested CHAT events normalize to the same message', () => {
  const saved = message('msg-1', { id: 42 });
  assert.deepEqual(normalizeChatMessage({ type: 'CHAT', ...saved }),
    normalizeChatMessage({ type: 'CHAT', message: saved }));
});

test('an empty first CHAT cannot block subsequent messages as undefined duplicates', () => {
  const result = mergeChatMessages([], [
    { type: 'CHAT', message: {} },
    { type: 'CHAT', message: message('msg-1') },
    { type: 'CHAT', message: message('msg-2') },
  ]);
  assert.deepEqual(result.map(item => item.clientMessageId), ['msg-1', 'msg-2']);
  assert.equal(normalizeChatMessage({ body: 'hi' }), null);
});

test('ACK and history replace an optimistic message without duplicating it', () => {
  let state = mergeChatMessages([], [message('msg-1', { deliveryStatus: 'sending' })]);
  assert.equal(state[0].deliveryStatus, 'sending');
  state = mergeChatMessages(state, [{ type: 'CHAT', message: message('msg-1', { id: 42 }) }]);
  state = mergeChatMessages(state, [message('msg-1', { id: 42 })]);
  assert.equal(state.length, 1);
  assert.equal(state[0].id, '42');
  assert.equal(state[0].deliveryStatus, 'sent');
});

test('two identical consecutive messages remain separate while awaiting ACKs', () => {
  const state = mergeChatMessages([], [
    message('msg-1', { deliveryStatus: 'sending' }),
    message('msg-2', { deliveryStatus: 'sending' }),
  ]);
  assert.equal(state.length, 2);
  assert.ok(state.every(item => item.deliveryStatus === 'sending'));
});
