import { expect, test } from '@playwright/test';
import path from 'node:path';

const polyfill = path.resolve('e2e/.generated/webmcp-polyfill.js');

test.beforeEach(async ({ page }) => {
  await page.addInitScript({ path: polyfill });
});

test('health and complete catalog are served from the Java runtime', async ({ request }) => {
  const health = await request.get('/health');
  expect(health.ok()).toBeTruthy();
  const healthBody = await health.json();
  expect(healthBody.status).toBe('UP');
  expect(healthBody.authMode).toBe('NO_AUTH');
  expect(healthBody.operationCount).toBe(16);
  expect(healthBody.jobProvider).toBe('local-durable-jobs');
  expect(healthBody.metricsProvider).toBe('jvm-os-mxbean');

  const catalog = await request.get('/api/v1/operations');
  expect(catalog.ok()).toBeTruthy();
  const ids = (await catalog.json()).operations.map((operation) => operation.id);
  expect(ids).toContain('system.status');
  expect(ids).toContain('metrics.snapshot');
  expect(ids).toContain('service.status');
  expect(ids).toContain('service.restart');
  expect(ids).toContain('job.execute');
  expect(ids).toContain('job.logs');
});

test('WebMCP discovers the canonical catalog and executes system.status', async ({ page }) => {
  await page.goto('/');
  await page.waitForFunction(() => window.__agentWebMcp?.state === 'ready');

  const result = await page.evaluate(async () => {
    const tools = await document.modelContext.getTools();
    const tool = tools.find((candidate) => candidate.name === 'system.status');
    if (!tool) throw new Error('system.status was not registered');
    const raw = await document.modelContext.executeTool(tool, '{}');
    return { names: tools.map((candidate) => candidate.name), output: JSON.parse(raw) };
  });

  expect(result.names).toHaveLength(16);
  expect(result.names).toContain('service.status');
  expect(result.names).toContain('metrics.snapshot');
  expect(result.names).toContain('job.execute');
  expect(result.output.status).toBe('SUCCESS');
  expect(result.output.output.authMode).toBe('NO_AUTH');
});

test('WebMCP schedules a durable canonical job and exposes its completed result', async ({ page }) => {
  await page.goto('/');
  await page.waitForFunction(() => window.__agentWebMcp?.state === 'ready');

  const result = await page.evaluate(async () => {
    const tools = await document.modelContext.getTools();
    const jobTool = tools.find((candidate) => candidate.name === 'job.execute');
    if (!jobTool) throw new Error('job.execute was not registered');
    const raw = await document.modelContext.executeTool(jobTool, JSON.stringify({
      operationId: 'system.status',
      input: {},
      timeoutSeconds: 5
    }));
    const submission = JSON.parse(raw);
    const jobId = submission.output.jobId;

    for (let attempt = 0; attempt < 120; attempt += 1) {
      const response = await fetch('/api/v1/operations/job.inspect', {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ jobId })
      });
      const inspection = await response.json();
      const state = inspection.output?.state;
      if (state === 'SUCCEEDED') return { jobId, inspection };
      if (state === 'FAILED' || state === 'TIMED_OUT') {
        throw new Error(`job ${jobId} terminated as ${state}`);
      }
      await new Promise((resolve) => setTimeout(resolve, 25));
    }
    throw new Error(`job ${jobId} did not finish`);
  });

  expect(result.jobId).toMatch(/^job-[a-f0-9]{12}$/);
  expect(result.inspection.output.execution.operationId).toBe('system.status');
  expect(result.inspection.output.execution.output.authMode).toBe('NO_AUTH');
});

test('metrics.snapshot executes through the same HTTP operation surface', async ({ request }) => {
  const response = await request.post('/api/v1/operations/metrics.snapshot', { data: {} });
  expect(response.ok()).toBeTruthy();
  const body = await response.json();
  expect(body.status).toBe('SUCCESS');
  expect(body.operationId).toBe('metrics.snapshot');
  expect(body.output.targetId).toBe('local');
  expect(body.output.metrics.availableProcessors).toBeGreaterThanOrEqual(1);
});

test('HTTP operation execution uses the same canonical executor', async ({ request }) => {
  const response = await request.post('/api/v1/operations/system.status', { data: {} });
  expect(response.ok()).toBeTruthy();
  const body = await response.json();
  expect(body.status).toBe('SUCCESS');
  expect(body.operationId).toBe('system.status');
  expect(body.output.authMode).toBe('NO_AUTH');
});

test('recursive job execution is rejected before scheduling', async ({ request }) => {
  const response = await request.post('/api/v1/operations/job.execute', {
    data: { operationId: 'job.execute', input: {} }
  });
  expect(response.status()).toBe(400);
  const body = await response.json();
  expect(body.error.code).toBe('RECURSIVE_JOB_EXECUTION');
});

test('unknown operations are rejected instead of falling through to shell behavior', async ({ request }) => {
  const response = await request.post('/api/v1/operations/not.real', { data: {} });
  expect(response.status()).toBe(404);
  const body = await response.json();
  expect(body.error.code).toBe('OPERATION_NOT_FOUND');
});
