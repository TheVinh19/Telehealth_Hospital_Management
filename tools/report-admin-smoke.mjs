import assert from 'node:assert/strict';

const base = 'http://localhost:5173/api/v1';
async function request(path, token, options = {}) {
  const headers = new Headers(options.headers);
  if (token) headers.set('Authorization', `Bearer ${token}`);
  if (options.body && !(options.body instanceof FormData)) headers.set('Content-Type', 'application/json');
  const response = await fetch(base + path, { ...options, headers });
  const json = await response.json();
  if (!response.ok || json.success === false) throw new Error(`${response.status} ${path}: ${JSON.stringify(json)}`);
  return json.data;
}
const post = (path, token, body) => request(path, token, { method: 'POST', body: body ? JSON.stringify(body) : undefined });
const login = (email, password) => post('/auth/login', null, { email, password });
const admin = await login('admin@demo.com', 'admin123');
const patient = await login('patient2@demo.com', 'patient123');
const doctor = await login('doctor.binh@demo.com', 'doctor123');
const doctorProfile = (await request('/doctors', admin.accessToken)).find(item => item.userId === doctor.user.id);
assert.ok(doctorProfile);

let slot;
let appointment;
let removed = false;
try {
  const startsAt = new Date(Date.now() + 60 * 86_400_000);
  startsAt.setUTCSeconds(0, 0);
  slot = await post('/admin/slots', admin.accessToken, { doctorId: doctorProfile.id, startsAt: startsAt.toISOString() });
  const intake = await post('/intakes', patient.accessToken, { reason: 'AUTOMATED TEST ONLY', symptoms: 'Synthetic report upload regression test' });
  appointment = await post('/appointments', patient.accessToken, { doctorId: doctorProfile.id, slotId: slot.id, intakeId: intake.id });
  assert.equal(appointment.demo, true);
  console.log(JSON.stringify({ testAppointmentId: appointment.id, testSlotId: slot.id }));
  const order = await request(`/billing/orders/${appointment.id}`, patient.accessToken);
  await post('/billing/mock-pay', patient.accessToken, { paymentCode: order.paymentCode });

  const form = new FormData();
  const content = 'AUTOMATED TEST ONLY - no clinical information';
  form.append('file', new Blob([content], { type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' }), 'automated-test.docx');
  const report = await request(`/clinical/encounters/${appointment.id}/reports`, doctor.accessToken, { method: 'POST', body: form });
  const encounter = await request(`/clinical/encounters/${appointment.id}`, doctor.accessToken);
  assert.equal(encounter.intakeId, intake.id);
  assert.equal(encounter.patientId, patient.user.id);
  assert.equal(encounter.doctorId, doctor.user.id);
  assert.equal(encounter.files.length, 1);
  const published = await post(`/clinical/encounters/${appointment.id}/publish`, doctor.accessToken);
  assert.equal(published.state, 'PUBLISHED');
  const download = await fetch(base + `/clinical/encounters/${appointment.id}/reports/${report.id}`, { headers: { Authorization: `Bearer ${patient.accessToken}` } });
  assert.equal(download.status, 200);
  assert.equal(await download.text(), content);

  const forbidden = await fetch(base + `/admin/appointments/${appointment.id}`, { method: 'DELETE', headers: { Authorization: `Bearer ${patient.accessToken}` } });
  assert.equal(forbidden.status, 403);
  await request(`/admin/appointments/${appointment.id}`, admin.accessToken, { method: 'DELETE' });
  removed = true;
  assert.ok(!(await request('/admin/appointments/demo', admin.accessToken)).some(item => item.id === appointment.id));
  assert.ok(!(await request('/appointments', patient.accessToken)).some(item => item.id === appointment.id));
  assert.equal((await request(`/clinical/encounters/${appointment.id}`, doctor.accessToken)).files.length, 1);
  console.log(JSON.stringify({ upload: true, correctIntakeAndPatient: true, published: true, patientDownload: true, nonAdminDelete: 403, adminDemoDelete: true, reportPreserved: true }));
} finally {
  if (appointment && !removed) await request(`/admin/appointments/${appointment.id}`, admin.accessToken, { method: 'DELETE' });
  if (slot) await request(`/admin/slots/${slot.id}`, admin.accessToken, { method: 'DELETE' });
}
