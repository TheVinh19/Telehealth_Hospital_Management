import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { setTimeout as delay } from 'node:timers/promises';

// Local Mailpit only. Creates/cleans up its own demo bookings; never edits existing appointments.
const base = 'http://localhost:5173/api/v1';
const mailpit = 'http://localhost:8025';
const run = randomUUID();
const resources = { appointments: [], slots: [], doctor: null, user: null };
const summary = { scenarios: [], cleanup: false };
let admin;
let demoRecipient = '';

async function request(path, token, method = 'GET', body) {
  return fetch(base + path, {
    method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(15_000),
  });
}
async function api(path, token, method, body) {
  const response = await request(path, token, method, body);
  const json = await response.json().catch(() => null);
  if (!response.ok || json?.success === false) throw new Error(`${method || 'GET'} ${path}: ${response.status}`);
  return json?.data;
}
const login = (email, password) => api('/auth/login', null, 'POST', { email, password });
async function waitFor(check, label) {
  for (let attempt = 0; attempt < 80; attempt++) {
    const result = await check();
    if (result) return result;
    await delay(400);
  }
  throw new Error(`Timeout: ${label}`);
}
async function messages(code) {
  const response = await fetch(`${mailpit}/api/v1/messages?limit=200`, { signal: AbortSignal.timeout(5_000) });
  assert.ok(response.ok);
  const data = await response.json();
  return data.messages.filter(message => message.Subject.includes(`#${code}`));
}
function vietnamTime(iso) {
  const date = new Date(iso);
  return {
    date: date.toLocaleDateString('en-GB', { timeZone: 'Asia/Ho_Chi_Minh' }),
    time: date.toLocaleTimeString('en-GB', { timeZone: 'Asia/Ho_Chi_Minh', hour: '2-digit', minute: '2-digit' }),
  };
}

async function scenario(patientNumber) {
  const email = `patient${patientNumber}@demo.com`;
  const recipient = demoRecipient || email;
  const patient = await login(email, 'patient123');
  const starts = [20 + patientNumber, 24 + patientNumber].map(days => new Date(Date.now() + days * 86_400_000).toISOString());
  const slots = [];
  for (const startsAt of starts) {
    const slot = await api('/admin/slots', admin.accessToken, 'POST', { doctorId: resources.doctor, startsAt });
    resources.slots.push(slot.id);
    slots.push(slot);
  }
  const intake = await api('/intakes', patient.accessToken, 'POST', {
    reason: 'AUTOMATED EMAIL TEST ONLY', symptoms: 'SYNTHETIC_PRIVATE_CONTENT_MUST_NOT_APPEAR_IN_EMAIL',
  });
  await api(`/intakes/${intake.id}/submit`, patient.accessToken, 'POST');
  const appointment = await api('/appointments', patient.accessToken, 'POST', {
    doctorId: resources.doctor, slotId: slots[0].id, intakeId: intake.id,
  });
  resources.appointments.push(appointment.id);
  assert.equal(appointment.demo, true);
  const code = appointment.id.slice(0, 8).toUpperCase();
  assert.equal((await messages(code)).length, 0, 'Unpaid hold must not generate confirmation mail');
  const order = await api(`/billing/orders/${appointment.id}`, patient.accessToken);
  await api('/billing/mock-pay', patient.accessToken, 'POST', { paymentCode: order.paymentCode });
  await waitFor(async () => (await api(`/appointments/${appointment.id}`, patient.accessToken)).status === 'CONFIRMED', 'payment');
  await waitFor(async () => (await messages(code)).length === 1, 'confirmation email');
  // Repeated successful payment callbacks must not generate another email.
  await api('/billing/mock-pay', patient.accessToken, 'POST', { paymentCode: order.paymentCode });
  await api(`/appointments/${appointment.id}/reschedule`, patient.accessToken, 'POST', { newSlotId: slots[1].id });
  const inbox = await waitFor(async () => {
    const found = await messages(code);
    return found.length >= 2 ? found : null;
  }, 'reschedule email');
  assert.equal(inbox.length, 2);
  const invalid = await request(`/appointments/${appointment.id}/reschedule`, patient.accessToken, 'POST', { newSlotId: slots[1].id });
  assert.ok([400, 403, 409].includes(invalid.status));
  await delay(500);
  assert.equal((await messages(code)).length, 2, 'Failed reschedule must not send another email');

  for (const message of inbox) {
    assert.deepEqual(message.To.map(recipient => recipient.Address), [recipient]);
    const response = await fetch(`${mailpit}/api/v1/message/${message.ID}`);
    assert.ok(response.ok);
    const full = await response.json();
    const html = full.HTML;
    assert.equal(typeof html, 'string');
    assert.ok(html.includes(`EMAIL TEST ${run.slice(0, 8)}`));
    assert.ok(!html.includes('SYNTHETIC_PRIVATE_CONTENT'));
    const oldTime = vietnamTime(starts[0]);
    assert.ok(html.includes(oldTime.date) && html.includes(oldTime.time));
    if (message.Subject.includes('đổi ca')) {
      const nextTime = vietnamTime(starts[1]);
      assert.ok(html.includes(nextTime.date) && html.includes(nextTime.time));
    } else {
      assert.ok(html.includes('100.000 VNĐ'));
    }
  }
  const logs = await api('/notifications/logs', admin.accessToken);
  assert.equal(logs.filter(log => inbox.some(message => message.To[0].Address === log.recipientEmail)
    && log.recipientEmail === recipient && log.status === 'SENT').length >= 2, true);
  const forbiddenLogs = await request('/notifications/logs', patient.accessToken);
  assert.equal(forbiddenLogs.status, 403);
  return { patient: patientNumber, confirmation: true, reschedule: true, correctRecipient: true,
    vietnamTime: true, noPrivateClinicalContent: true, duplicatePaymentAndFailedRescheduleNoExtraMail: true };
}

try {
  admin = await login('admin@demo.com', 'admin123');
  const configuration = await api('/notifications/config', admin.accessToken);
  assert.ok(['mailpit', 'localhost', '127.0.0.1'].includes(configuration.smtpHost) && configuration.smtpPort === 1025,
    'This smoke test only supports Mailpit. Refusing to send synthetic messages through external SMTP.');
  demoRecipient = configuration.demoRecipient;
  const specialties = await api('/specialties', admin.accessToken);
  const account = await api('/admin/users/doctor', admin.accessToken, 'POST', {
    email: `email-test-${run}@demo.com`, password: `Test-${run}!`, displayName: `EMAIL TEST ${run.slice(0, 8)}`,
  });
  resources.user = account.id;
  const doctor = await api('/admin/doctors', admin.accessToken, 'POST', {
    userId: account.id, specialtyId: specialties[0].id, displayName: `EMAIL TEST ${run.slice(0, 8)}`,
  });
  resources.doctor = doctor.id;
  for (const number of [1, 2]) summary.scenarios.push(await scenario(number));
} catch (error) {
  summary.failure = error.message;
  process.exitCode = 1;
} finally {
  const failures = [];
  const remove = async path => {
    try { await api(path, admin.accessToken, 'DELETE'); }
    catch { failures.push(path); }
  };
  if (admin) {
    for (const id of resources.appointments) await remove(`/admin/appointments/${id}`);
    for (const id of resources.slots) await remove(`/admin/slots/${id}`);
    if (resources.doctor) await remove(`/admin/doctors/${resources.doctor}`);
    if (resources.user) await remove(`/admin/users/${resources.user}`);
  }
  summary.cleanup = failures.length === 0;
  if (failures.length) { summary.cleanupFailures = failures; process.exitCode = 1; }
  // Keep synthetic emails in Mailpit so the user can open the two templates.
  console.log(JSON.stringify(summary));
}
