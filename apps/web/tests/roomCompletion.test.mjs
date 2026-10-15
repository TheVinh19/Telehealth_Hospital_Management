import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test, { afterEach } from 'node:test';
import ts from 'typescript';

const originalGlobals = Object.fromEntries(
  ['window', 'navigator', 'WebSocket', 'RTCPeerConnection', '__consultationHarness']
    .map(name => [name, Object.getOwnPropertyDescriptor(globalThis, name)])
);
afterEach(() => {
  for (const [name, descriptor] of Object.entries(originalGlobals)) {
    if (descriptor) Object.defineProperty(globalThis, name, descriptor);
    else delete globalThis[name];
  }
});

const transpile = source => ts.transpileModule(source, {
  compilerOptions: {
    module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022, jsx: ts.JsxEmit.React,
  },
}).outputText;
const moduleUrl = source => `data:text/javascript;base64,${Buffer.from(source).toString('base64')}`;
const chatUrl = moduleUrl(transpile(await readFile(new URL('../src/utils/chatMessages.ts', import.meta.url), 'utf8')));
const source = transpile(await readFile(new URL('../src/components/ConsultationRoom.tsx', import.meta.url), 'utf8'))
  .replace(/import React, \{[^}]+\} from 'react';/, `const harness = globalThis.__consultationHarness;
    const React = harness.React;
    const { useState, useEffect, useRef } = React;`)
  .replace(/import \{ useAuth \} from '[^']+';/, 'const useAuth = () => harness.auth;')
  .replace(/import \{ api \} from '[^']+';/, 'const api = harness.api;')
  .replace("'../utils/chatMessages'", JSON.stringify(chatUrl))
  .replace(/import \{([^}]+)\} from 'lucide-react';/, 'const {$1} = harness.icons;');

const flush = async () => {
  for (let i = 0; i < 20; i++) await Promise.resolve();
};

async function mountRoom({ status = 'IN_PROGRESS', deferredMedia = false } = {}) {
  const effects = [];
  const intervals = new Map();
  const timeouts = new Map();
  const sockets = [];
  const peers = [];
  const leaves = [];
  const calls = { media: 0, token: 0 };
  let sequence = 0;
  let releaseMedia;
  const tracks = [{ stopCount: 0, stop() { this.stopCount++; } }, { stopCount: 0, stop() { this.stopCount++; } }];
  const stream = { getTracks: () => tracks };
  const mediaPromise = deferredMedia ? new Promise(resolve => { releaseMedia = () => resolve(stream); }) : Promise.resolve(stream);
  const state = { status };
  const harness = {
    auth: { user: { id: 'patient-1', fullName: 'Patient', role: 'PATIENT' } },
    api: {
      getAppointment: async () => ({ id: 'appointment-1', status: state.status }),
      getRoomToken: async () => { calls.token++; return { token: 'room-token', roomId: 'room-1' }; },
      getIceServers: async () => ({ iceServers: [] }),
      getRoomMessages: async () => [],
    },
    React: {
      createElement: (type, props, ...children) => {
        if (props?.ref) props.ref.current = { srcObject: null, scrollIntoView() {} };
        return { type, props, children };
      },
      useState: initial => [typeof initial === 'function' ? initial() : initial, () => {}],
      useRef: current => ({ current }),
      useEffect: effect => effects.push(effect),
    },
    icons: new Proxy({}, { get: (_target, name) => name }),
  };
  globalThis.__consultationHarness = harness;
  globalThis.window = {
    location: { protocol: 'http:', host: 'localhost:5173' },
    setInterval: fn => { const id = ++sequence; intervals.set(id, fn); return id; },
    clearInterval: id => intervals.delete(id),
    setTimeout: fn => { const id = ++sequence; timeouts.set(id, fn); return id; },
    clearTimeout: id => timeouts.delete(id),
  };
  Object.defineProperty(globalThis, 'navigator', { configurable: true, value: {
    mediaDevices: { getUserMedia: () => { calls.media++; return mediaPromise; } },
  } });
  globalThis.WebSocket = class {
    static OPEN = 1;
    readyState = 1;
    closeCount = 0;
    sent = [];
    constructor() { sockets.push(this); }
    send(data) { this.sent.push(data); }
    close() { this.closeCount++; }
  };
  globalThis.RTCPeerConnection = class {
    closeCount = 0;
    constructor() { peers.push(this); }
    addTrack() {}
    close() { this.closeCount++; }
  };
  const { ConsultationRoom } = await import(moduleUrl(`${source}\n// mount ${Math.random()}`));
  const tree = ConsultationRoom({ appointmentId: 'appointment-1', onLeave: message => leaves.push(message) });
  const cleanups = effects.map(effect => effect()).filter(Boolean);
  await flush();
  return {
    state, calls, tracks, sockets, peers, leaves, intervals, timeouts,
    releaseMedia, tree, unmount: () => cleanups.forEach(cleanup => cleanup()),
  };
}

function findElement(element, id) {
  if (!element || typeof element !== 'object') return null;
  if (element.props?.id === id) return element;
  return element.children?.flat(Infinity).map(child => findElement(child, id)).find(Boolean);
}

test('opening a completed appointment returns to the schedule without requesting camera or a room token', async () => {
  const room = await mountRoom({ status: 'COMPLETED' });
  assert.equal(room.calls.media, 0);
  assert.equal(room.calls.token, 0);
  assert.equal(room.leaves.length, 1);
  assert.match(room.leaves[0], /Ca khám đã hoàn tất/);
  assert.equal(room.intervals.size, 0);
  room.unmount();
});

test('ROOM_CLOSED stops both media tracks, closes transports and does not reconnect or notify twice', async () => {
  const room = await mountRoom();
  const socket = room.sockets[0];
  const onmessage = socket.onmessage;
  await onmessage({ data: JSON.stringify({ type: 'ROOM_CLOSED', reason: 'COMPLETED' }) });
  assert.ok(room.tracks.every(track => track.stopCount > 0));
  assert.equal(socket.closeCount, 1);
  assert.equal(room.peers[0].closeCount, 1);
  assert.equal(room.intervals.size, 0);
  assert.equal(room.timeouts.size, 0);
  await onmessage({ data: JSON.stringify({ type: 'ROOM_CLOSED', reason: 'COMPLETED' }) });
  assert.equal(room.leaves.length, 1);
  assert.match(room.leaves[0], /Ca khám đã hoàn tất/);
  room.unmount();
});

test('appointment polling closes the room when the completion broadcast is missed', async () => {
  const room = await mountRoom();
  room.state.status = 'COMPLETED';
  const scheduledPolls = [...room.intervals.values()];
  scheduledPolls.forEach(poll => poll());
  await flush();
  assert.equal(room.leaves.length, 1);
  assert.ok(room.tracks.every(track => track.stopCount > 0));
  room.unmount();
});

test('leaving manually cancels pending watchers so later completion cannot redirect or display a notice', async () => {
  const room = await mountRoom();
  const scheduledPolls = [...room.intervals.values()];
  findElement(room.tree, 'leave-room-btn').props.onClick();
  assert.deepEqual(room.leaves, [undefined]);
  room.state.status = 'COMPLETED';
  scheduledPolls.forEach(poll => poll());
  await flush();
  assert.deepEqual(room.leaves, [undefined]);
  assert.equal(room.intervals.size, 0);
  room.unmount();
});

test('media granted after completion is immediately stopped and cannot open a socket', async () => {
  const room = await mountRoom({ deferredMedia: true });
  assert.equal(room.calls.media, 1);
  room.state.status = 'COMPLETED';
  [...room.intervals.values()].forEach(poll => poll());
  await flush();
  room.releaseMedia();
  await flush();
  assert.equal(room.leaves.length, 1);
  assert.ok(room.tracks.every(track => track.stopCount > 0));
  assert.equal(room.sockets.length, 0);
  room.unmount();
});
