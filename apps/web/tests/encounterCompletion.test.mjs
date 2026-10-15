import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test, { afterEach } from 'node:test';
import ts from 'typescript';

globalThis.localStorage = { getItem: () => null, setItem() {}, removeItem() {} };
const originalFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = originalFetch; });

const source = await readFile(new URL('../src/services/api.ts', import.meta.url), 'utf8');
const { outputText } = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 },
});
const { api } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);
const respond = data => new Response(JSON.stringify({ success: true, data }), {
  headers: { 'Content-Type': 'application/json' },
});

test('a published clinical response enables completion on initial load and after publication', async () => {
  const report = { id: 'report-1', originalName: 'ket-luan.pdf' };
  const published = {
    appointmentId: 'appt-1', patientId: 'patient-1', doctorId: 'doctor-1',
    state: 'PUBLISHED', publishedAt: '2026-10-15T07:00:00Z', files: [report],
  };
  globalThis.fetch = async (url, options) => {
    if (url.endsWith('/publish')) assert.equal(options.method, 'POST');
    return respond(published);
  };
  for (const result of [await api.getEncounter('appt-1'), await api.publishReports('appt-1')]) {
    assert.equal(result.published, true);
    assert.deepEqual(result.files, [report]);
    assert.equal(result.publishedAt, published.publishedAt);
  }
});

test('draft and missing encounters keep completion disabled with an empty file list', async () => {
  for (const raw of [{ state: 'DRAFT', published: true }, null, undefined]) {
    globalThis.fetch = async () => respond(raw);
    const result = await api.getEncounter('appt-1');
    assert.equal(result.published, false);
    assert.deepEqual(result.files, []);
    assert.equal(result.appointmentId, 'appt-1');
  }
});

test('publication failure is surfaced and cannot unlock completion', async () => {
  globalThis.fetch = async () => new Response(JSON.stringify({ message: 'Chưa có báo cáo hợp lệ' }), { status: 409 });
  await assert.rejects(api.publishReports('appt-1'), /Chưa có báo cáo hợp lệ/);
});
