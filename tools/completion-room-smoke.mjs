import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { setTimeout as delay } from 'node:timers/promises';

// Run only against the local/demo environment after deploying the services:
//   node tools/completion-room-smoke.mjs
// Each run creates its own doctor and bookings. Existing bookings are never changed.
const origin = process.env.COMPLETION_TEST_ORIGIN || 'http://localhost:5173';
const baseUrl = new URL('/api/v1', origin).href.replace(/\/$/, '');
const wsUrl = new URL('/ws', origin);
wsUrl.protocol = wsUrl.protocol === 'https:' ? 'wss:' : 'ws:';
const runId = randomUUID();
const resources = { appointments: [], slots: [], clients: [], doctorId: null, doctorUserId: null };
const summary = { scenarios: [], cleanup: false };
let admin;

async function responseFor(path, token, { body, ...options } = {}) {
  const headers = new Headers(options.headers);
  if (token) headers.set('Authorization', `Bearer ${token}`);
  if (body !== undefined && !(body instanceof FormData)) headers.set('Content-Type', 'application/json');
  return fetch(`${baseUrl}${path}`, {
    ...options,
    headers,
    body: body === undefined || body instanceof FormData ? body : JSON.stringify(body),
    signal: AbortSignal.timeout(15_000),
  });
}

async function api(path, token, options) {
  const response = await responseFor(path, token, options);
  const payload = await response.json().catch(() => null);
  if (!response.ok || payload?.success === false) {
    // Deliberately do not print server bodies, credentials, tokens or chat text.
    throw new Error(`${options?.method || 'GET'} ${path} failed (${response.status})`);
  }
  return payload?.data ?? payload;
}

const post = (path, token, body) => api(path, token, { method: 'POST', body });
const login = (email, password) => post('/auth/login', null, { email, password });

async function waitFor(description, predicate, timeoutMs = 15_000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (await predicate()) return;
    await delay(40);
  }
  throw new Error(`Timeout waiting for ${description}`);
}

async function waitForStartup() {
  const deadline = Date.now() + 60_000;
  let lastError;
  while (Date.now() < deadline) {
    try {
      const session = await login('admin@demo.com', 'admin123');
      assert.ok(session.accessToken);
      await api('/specialties', session.accessToken);
      return session;
    } catch (error) {
      lastError = error;
      await delay(1_000);
    }
  }
  throw new Error(`Local gateway/services did not become ready: ${lastError?.message}`);
}

function client(label, admission, { rejectedJoin = false } = {}) {
  const url = new URL(wsUrl);
  url.searchParams.set('token', admission.token);
  const connection = { label, frames: [], closed: false, closeCode: null, parseErrors: 0, socket: null };
  connection.socket = new WebSocket(url);
  resources.clients.push(connection);
  connection.socket.addEventListener('open', () => {
    connection.socket.send(JSON.stringify({ type: 'JOIN', token: admission.token, roomId: admission.roomId }));
    if (rejectedJoin) {
      // A retained room token must not authorize new chat after completion.
      connection.socket.send(JSON.stringify({
        type: 'CHAT', clientMessageId: randomUUID(), body: `synthetic-rejected-${runId}`,
      }));
    }
  });
  connection.socket.addEventListener('message', event => {
    try {
      connection.frames.push(JSON.parse(String(event.data)));
    } catch {
      connection.parseErrors += 1;
    }
  });
  connection.socket.addEventListener('close', event => {
    connection.closed = true;
    connection.closeCode = event.code;
  });
  connection.socket.addEventListener('error', () => {});
  return connection;
}

const framesOf = (connection, type) => connection.frames.filter(frame => frame.type === type);
const chatOf = connection => framesOf(connection, 'CHAT').map(frame => frame.message || frame);

async function disconnect(connection) {
  if (connection.socket.readyState < WebSocket.CLOSING) connection.socket.close(1000, 'Test participant left');
  await waitFor('participant disconnect', () => connection.closed);
}

function syntheticPdf() {
  const contents = 'BT /F1 12 Tf 30 100 Td (AUTOMATED TEST ONLY - no clinical information) Tj ET';
  const objects = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 400 200] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>',
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
    `<< /Length ${contents.length} >>\nstream\n${contents}\nendstream`,
  ];
  let pdf = '%PDF-1.4\n';
  const offsets = [0];
  objects.forEach((object, index) => {
    offsets.push(Buffer.byteLength(pdf));
    pdf += `${index + 1} 0 obj\n${object}\nendobj\n`;
  });
  const xrefOffset = Buffer.byteLength(pdf);
  pdf += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
  offsets.slice(1).forEach(offset => { pdf += `${String(offset).padStart(10, '0')} 00000 n \n`; });
  pdf += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xrefOffset}\n%%EOF\n`;
  return pdf;
}

async function assertReentryDenied(appointmentId, patient, admission) {
  const denied = await responseFor(`/rooms/${appointmentId}/token`, patient.accessToken, { method: 'POST' });
  assert.ok([400, 403, 409, 410].includes(denied.status), `Reentry must be denied, got ${denied.status}`);
  const oldTokenClient = client('retained-token', admission, { rejectedJoin: true });
  await waitFor('retained token rejection', () => oldTokenClient.closed);
  assert.equal(framesOf(oldTokenClient, 'JOINED').length, 0, 'A pre-completion token must not join a closed room');
  assert.equal(chatOf(oldTokenClient).length, 0);
  assert.equal(oldTokenClient.parseErrors, 0);
  return denied.status;
}

async function scenario({ patient, doctor, doctorId, patientLeavesFirst, index }) {
  const slot = await post('/admin/slots', admin.accessToken, {
    doctorId,
    startsAt: new Date(Date.now() - (5 - index) * 60_000).toISOString(),
  });
  resources.slots.push(slot.id);
  const intake = await post('/intakes', patient.accessToken, {
    reason: 'AUTOMATED TEST ONLY',
    symptoms: 'Synthetic consultation completion regression test',
  });
  const intakeId = intake.id || intake.intakeId;
  assert.ok(intakeId);
  await post(`/intakes/${intakeId}/submit`, patient.accessToken);
  const appointment = await post('/appointments', patient.accessToken, { doctorId, slotId: slot.id, intakeId });
  resources.appointments.push(appointment.id);
  assert.equal(appointment.demo, true, 'Cleanup is permitted only for a demo booking');
  const order = await api(`/billing/orders/${appointment.id}`, patient.accessToken);
  await post('/billing/mock-pay', patient.accessToken, { paymentCode: order.paymentCode });
  await waitFor('payment confirmation', async () =>
    (await api(`/appointments/${appointment.id}`, doctor.accessToken)).status === 'CONFIRMED');
  const started = await post(`/appointments/${appointment.id}/start`, doctor.accessToken);
  assert.equal(started.status, 'IN_PROGRESS');

  const patientAdmission = await post(`/rooms/${appointment.id}/token`, patient.accessToken);
  const doctorAdmission = await post(`/rooms/${appointment.id}/token`, doctor.accessToken);
  assert.equal(patientAdmission.roomId, doctorAdmission.roomId);
  const patientClient = client('patient', patientAdmission);
  const doctorClient = client('doctor', doctorAdmission);
  await Promise.all([patientClient, doctorClient].map(connection =>
    waitFor(`${connection.label} admission`, () => framesOf(connection, 'JOINED').length === 1)));
  const clientMessageId = randomUUID();
  const body = `synthetic-completion-${runId}-${index}`;
  patientClient.socket.send(JSON.stringify({ type: 'CHAT', clientMessageId, body }));
  await waitFor('persisted chat echo to both participants', () => [patientClient, doctorClient].every(connection =>
    chatOf(connection).some(message => message.clientMessageId === clientMessageId)));

  let departedFrameCount;
  if (patientLeavesFirst) {
    await disconnect(patientClient);
    departedFrameCount = patientClient.frames.length;
  }

  const reportContents = syntheticPdf();
  const form = new FormData();
  form.append('file', new Blob([reportContents], { type: 'application/pdf' }), 'synthetic-completion-report.pdf');
  const report = await api(`/clinical/encounters/${appointment.id}/reports`, doctor.accessToken, { method: 'POST', body: form });
  const published = await post(`/clinical/encounters/${appointment.id}/publish`, doctor.accessToken);
  assert.equal(published.state, 'PUBLISHED');
  const completed = await post(`/appointments/${appointment.id}/complete`, doctor.accessToken);
  assert.equal(completed.status, 'COMPLETED');
  const activeClients = patientLeavesFirst ? [doctorClient] : [patientClient, doctorClient];
  await Promise.all(activeClients.map(connection => waitFor(`${connection.label} room completion`, () =>
    framesOf(connection, 'ROOM_CLOSED').length >= 1 && connection.closed, 25_000)));

  // Completion and delivery retries must remain idempotent.
  const completedAgain = await post(`/appointments/${appointment.id}/complete`, doctor.accessToken);
  assert.equal(completedAgain.status, 'COMPLETED');
  await delay(200);
  activeClients.forEach(connection => {
    assert.equal(framesOf(connection, 'ROOM_CLOSED').length, 1);
    assert.equal(connection.parseErrors, 0);
  });
  if (patientLeavesFirst) {
    assert.equal(patientClient.frames.length, departedFrameCount, 'An already-left patient must receive no new event');
    assert.equal(framesOf(patientClient, 'ROOM_CLOSED').length, 0);
  }

  const reentryStatus = await assertReentryDenied(appointment.id, patient, patientAdmission);
  const history = await api(`/rooms/${patientAdmission.roomId}/messages`, patient.accessToken);
  assert.equal(history.length, 1, 'Late chat after completion must not be stored');
  assert.equal(history[0].clientMessageId, clientMessageId);
  assert.equal(history[0].body || history[0].content, body);
  const finalAppointment = await api(`/appointments/${appointment.id}`, patient.accessToken);
  assert.equal(finalAppointment.status, 'COMPLETED');
  const encounter = await api(`/clinical/encounters/${appointment.id}`, patient.accessToken);
  assert.equal(encounter.state, 'PUBLISHED');
  assert.equal(encounter.files.length, 1);
  const downloaded = await responseFor(`/clinical/encounters/${appointment.id}/reports/${report.id}`, patient.accessToken);
  assert.equal(downloaded.status, 200);
  assert.equal(await downloaded.text(), reportContents);

  return {
    scenario: patientLeavesFirst ? 'patient-already-left' : 'both-in-room',
    completed: true,
    activeParticipantsClosed: activeClients.length,
    closeEventsPerActiveParticipant: 1,
    departedPatientUnchanged: patientLeavesFirst || undefined,
    reentryStatus,
    retainedTokenDenied: true,
    chatPreserved: true,
    lateChatRejected: true,
    reportDownloadPreserved: true,
  };
}

async function cleanup() {
  const failures = [];
  for (const connection of resources.clients) {
    try {
      if (connection.socket.readyState < WebSocket.CLOSING) connection.socket.close();
    } catch {}
  }
  if (!admin) return failures;
  const remove = async (path, kind) => {
    for (let attempt = 0; attempt < 3; attempt += 1) {
      try {
        await api(path, admin.accessToken, { method: 'DELETE' });
        return;
      } catch {
        if (attempt < 2) await delay(300);
      }
    }
    failures.push(kind);
  };
  // Every identifier below came from a create response in this run.
  for (const id of resources.appointments) await remove(`/admin/appointments/${id}`, `test appointment ${id}`);
  for (const id of resources.slots) await remove(`/admin/slots/${id}`, `test slot ${id}`);
  if (resources.doctorId) await remove(`/admin/doctors/${resources.doctorId}`, `test doctor ${resources.doctorId}`);
  if (resources.doctorUserId) await remove(`/admin/users/${resources.doctorUserId}`, `test doctor account ${resources.doctorUserId}`);
  return failures;
}

try {
  assert.equal(typeof WebSocket, 'function', 'Use a Node.js release with built-in WebSocket support (Node 22+)');
  admin = await waitForStartup();
  const patient = await login('patient2@demo.com', 'patient123');
  const specialties = await api('/specialties', admin.accessToken);
  assert.ok(specialties.length > 0);
  const email = `completion-${runId}@demo.com`;
  const password = `Completion-${runId}!`;
  const account = await post('/admin/users/doctor', admin.accessToken, {
    email, password, displayName: `AUTOMATED TEST ${runId.slice(0, 8)}`,
  });
  resources.doctorUserId = account.id;
  const profile = await post('/admin/doctors', admin.accessToken, {
    userId: account.id,
    specialtyId: specialties[0].id,
    displayName: `AUTOMATED TEST ${runId.slice(0, 8)}`,
    bio: 'Synthetic test-only doctor, disabled by the smoke test cleanup',
  });
  resources.doctorId = profile.id;
  const doctor = await login(email, password);
  for (const [index, patientLeavesFirst] of [false, true].entries()) {
    summary.scenarios.push(await scenario({ patient, doctor, doctorId: profile.id, patientLeavesFirst, index }));
  }
} catch (error) {
  summary.failure = error.message;
  process.exitCode = 1;
} finally {
  const failures = await cleanup();
  summary.cleanup = failures.length === 0;
  if (failures.length) {
    summary.cleanupFailures = failures;
    process.exitCode = 1;
  }
  console.log(JSON.stringify(summary));
}
