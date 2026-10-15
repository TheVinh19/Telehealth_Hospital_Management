const baseUrl = process.env.REALTIME_TEST_API_URL || 'http://localhost:5173/api/v1';
const wsBaseUrl = process.env.REALTIME_TEST_WS_URL || 'ws://localhost:5173/ws';
const appointmentId = process.argv[2] || 'aaaaaaaa-1111-1111-1111-111111111111';
const doctorEmail = process.argv[3] || 'doctor.anh@demo.com';
const messagesPerParticipant = Number.parseInt(process.argv[4] || '10', 10);

async function api(path, { token, method = 'GET', body } = {}) {
  const response = await fetch(`${baseUrl}${path}`, {
    method,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(body ? { 'Content-Type': 'application/json' } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const json = await response.json();
  if (!response.ok || json.success === false) {
    throw new Error(`${method} ${path}: ${response.status} ${JSON.stringify(json)}`);
  }
  return json.data ?? json;
}

async function login(email, password) {
  return api('/auth/login', { method: 'POST', body: { email, password } });
}

const sleep = milliseconds => new Promise(resolve => setTimeout(resolve, milliseconds));

function createClient(label, admission) {
  const frames = [];
  const socket = new WebSocket(`${wsBaseUrl}?token=${encodeURIComponent(admission.token)}`);
  socket.addEventListener('open', () => {
    socket.send(JSON.stringify({
      type: 'JOIN',
      roomId: admission.roomId,
      token: admission.token,
    }));
  });
  socket.addEventListener('message', event => {
    const parsed = JSON.parse(String(event.data));
    frames.push(parsed);
    if (process.env.REALTIME_TEST_VERBOSE === '1') {
      console.log(`FRAME ${label} ${JSON.stringify(parsed)}`);
    }
  });
  socket.addEventListener('close', event => {
    console.log(`CLOSE ${label} code=${event.code} reason=${event.reason}`);
  });
  socket.addEventListener('error', event => {
    console.log(`ERROR ${label} ${event.message || event.type}`);
  });
  return { label, socket, frames };
}

async function waitFor(description, predicate, timeoutMs = 10000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (predicate()) return;
    await sleep(25);
  }
  throw new Error(`Timeout waiting for ${description}`);
}

const patientLogin = await login('patient1@demo.com', 'patient123');
const doctorLogin = await login(doctorEmail, 'doctor123');
console.log(`LOGIN patient=${patientLogin.user.id} doctor=${doctorLogin.user.id}`);

const patientAdmission = await api(`/rooms/${appointmentId}/token`, {
  token: patientLogin.accessToken,
  method: 'POST',
});
const doctorAdmission = await api(`/rooms/${appointmentId}/token`, {
  token: doctorLogin.accessToken,
  method: 'POST',
});
console.log(`ADMISSION patient=${JSON.stringify({ ...patientAdmission, token: '<redacted>' })}`);
console.log(`ADMISSION doctor=${JSON.stringify({ ...doctorAdmission, token: '<redacted>' })}`);

if (patientAdmission.roomId !== doctorAdmission.roomId) {
  throw new Error('Patient and doctor received different rooms');
}

const historyBefore = await api(`/rooms/${patientAdmission.roomId}/messages`, {
  token: patientLogin.accessToken,
});
console.log(`HISTORY_BEFORE count=${historyBefore.length}`);

const patient = createClient('PATIENT', patientAdmission);
const doctor = createClient('DOCTOR', doctorAdmission);
await Promise.all([
  waitFor('patient JOINED frame', () => patient.frames.some(frame => frame.type === 'JOINED')),
  waitFor('doctor JOINED frame', () => doctor.frames.some(frame => frame.type === 'JOINED')),
]);
await sleep(250);

const runId = `${Date.now()}-${crypto.randomUUID().slice(0, 8)}`;
const sent = [];
for (let index = 0; index < messagesPerParticipant; index += 1) {
  for (const client of [patient, doctor]) {
    const body = `e2e-${runId}-${client.label}-${index}`;
    const clientMessageId = crypto.randomUUID();
    sent.push({ sender: client.label, body, clientMessageId });
    client.socket.send(JSON.stringify({ type: 'CHAT', clientMessageId, body }));
  }
}

let realtimeTimedOut = false;
try {
  await waitFor('all chat echoes on both clients', () => {
    const patientChatIds = new Set(patient.frames.filter(frame => frame.type === 'CHAT')
      .map(frame => (frame.message || frame).clientMessageId));
    const doctorChatIds = new Set(doctor.frames.filter(frame => frame.type === 'CHAT')
      .map(frame => (frame.message || frame).clientMessageId));
    return sent.every(message => patientChatIds.has(message.clientMessageId)) &&
      sent.every(message => doctorChatIds.has(message.clientMessageId));
  }, 15000);
} catch (error) {
  realtimeTimedOut = true;
  console.log(`REALTIME_TIMEOUT ${error.message}`);
}

await sleep(500);
const historyAfter = await api(`/rooms/${patientAdmission.roomId}/messages`, {
  token: patientLogin.accessToken,
});
const runHistory = historyAfter.filter(message => message.body?.includes(runId) || message.content?.includes(runId));
const resultFor = client => {
  const chats = client.frames.filter(frame => frame.type === 'CHAT').map(frame => frame.message || frame);
  const runChats = chats.filter(message => message.body?.includes(runId) || message.content?.includes(runId));
  const malformed = client.frames.filter(frame => frame.type === 'CHAT')
    .filter(frame => {
      const message = frame.message || frame;
      return !message.body && !message.content;
    });
  return {
    totalFrames: client.frames.length,
    totalChatFrames: chats.length,
    runChatFrames: runChats.length,
    uniqueRunClientIds: new Set(runChats.map(message => message.clientMessageId)).size,
    malformedChatFrames: malformed.length,
    flatCompatible: client.frames.filter(frame => frame.type === 'CHAT').every(frame =>
      frame.clientMessageId === frame.message?.clientMessageId &&
      frame.content === frame.message?.body && typeof frame.content === 'string'),
    readyState: client.socket.readyState,
  };
};

const summary = {
  sent: sent.length,
  persistedForRun: runHistory.length,
  historyDelta: historyAfter.length - historyBefore.length,
  patient: resultFor(patient),
  doctor: resultFor(doctor),
  realtimeTimedOut,
};
console.log(`SUMMARY ${JSON.stringify(summary)}`);

patient.socket.close();
doctor.socket.close();
await sleep(250);

if (runHistory.length !== sent.length) process.exitCode = 2;
if (summary.patient.runChatFrames !== sent.length || summary.doctor.runChatFrames !== sent.length) process.exitCode = 3;
if (summary.patient.malformedChatFrames || summary.doctor.malformedChatFrames) process.exitCode = 4;
if (!summary.patient.flatCompatible || !summary.doctor.flatCompatible) process.exitCode = 5;
