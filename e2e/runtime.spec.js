import { expect, test } from '@playwright/test';
import path from 'node:path';

const polyfill = path.resolve('e2e/.generated/webmcp-polyfill.js');

test.beforeEach(async ({ page }) => {
  await page.addInitScript({ path: polyfill });
});

test('health and canonical catalog expose the 23-operation runtime contract', async ({ request }) => {
  const health = await request.get('/health');
  expect(health.ok()).toBeTruthy();
  const healthBody = await health.json();
  expect(healthBody).toMatchObject({
    status: 'UP',
    webServer: 'jdk-httpserver',
    transport: 'http-json',
    authMode: 'NO_AUTH',
    operationCount: 23,
    jobProvider: 'local-durable-jobs',
    metricsProvider: 'jvm-os-mxbean'
  });

  const catalog = await request.get('/api/v1/operations');
  expect(catalog.ok()).toBeTruthy();
  const operations = (await catalog.json()).operations;
  expect(operations).toHaveLength(23);
  const ids = operations.map((operation) => operation.id);
  expect(ids).toEqual(expect.arrayContaining([
    'agent.list', 'agent.inspect', 'service.discover', 'service.diagnostics', 'job.execute', 'job.cancel'
  ]));
  expect(operations.filter((operation) => operation.access === 'MUTATING')).toHaveLength(9);
  expect(operations.filter((operation) => operation.surfaces?.includes('WEBMCP'))).toHaveLength(18);
  expect(operations.filter((operation) => operation.surfaces?.includes('MCP'))).toHaveLength(16);
});

test('WebMCP registers exactly the approved 18-tool browser projection', async ({ page }) => {
  await page.goto('/');
  await page.waitForFunction(() => window.__agentWebMcp?.state === 'ready');

  const result = await page.evaluate(async () => {
    const tools = await document.modelContext.getTools();
    const tool = tools.find((candidate) => candidate.name === 'system.status');
    if (!tool) throw new Error('system.status was not registered');
    const raw = await document.modelContext.executeTool(tool, '{}');
    return { names: tools.map((candidate) => candidate.name), output: JSON.parse(raw) };
  });

  expect(result.names).toHaveLength(18);
  expect(result.names).toEqual(expect.arrayContaining([
    'system.status', 'metrics.snapshot', 'agent.list', 'agent.inspect', 'service.add', 'service.remove',
    'service.diagnostics', 'job.list', 'job.inspect', 'job.logs'
  ]));
  expect(result.names).not.toContain('service.discover');
  expect(result.names).not.toContain('job.execute');
  expect(result.names).not.toContain('job.cancel');
  expect(result.names.some((name) => name.startsWith('target.'))).toBeFalsy();
  expect(result.output.status).toBe('SUCCESS');
  expect(result.output.output.authMode).toBe('NO_AUTH');
});

test('canonical HTTP surface returns live metrics and rejects unsafe job shapes', async ({ request }) => {
  const metrics = await request.post('/api/v1/operations/metrics.snapshot', { data: {} });
  expect(metrics.ok()).toBeTruthy();
  const metricsBody = await metrics.json();
  expect(metricsBody.status).toBe('SUCCESS');
  expect(metricsBody.output.targetId).toBe('local');
  expect(metricsBody.output.metrics.availableProcessors).toBeGreaterThanOrEqual(1);

  const missingService = await request.post('/api/v1/operations/job.execute', {
    data: { operationId: 'service.restart', input: {} }
  });
  expect(missingService.status()).toBe(400);
  expect((await missingService.json()).error.code).toBe('INVALID_INPUT');

  const recurringAi = await request.post('/api/v1/operations/job.execute', {
    data: { serviceId: 'not-managed.service', prompt: 'inspect', input: {}, repeatEverySeconds: 60 }
  });
  expect(recurringAi.status()).toBe(400);
  expect((await recurringAi.json()).error.code).toBe('INVALID_INPUT');
});

test('managed lifecycle authority rejects guessed service IDs before provider mutation', async ({ request }) => {
  const response = await request.post('/api/v1/operations/service.restart', {
    data: { serviceId: 'definitely-not-enrolled-agent-webmcp.service' }
  });
  expect(response.status()).toBe(404);
  expect((await response.json()).error.code).toBe('SERVICE_NOT_MANAGED');
});

test('unknown operations are rejected instead of falling through to process execution', async ({ request }) => {
  const response = await request.post('/api/v1/operations/not.real', { data: {} });
  expect(response.status()).toBe(404);
  expect((await response.json()).error.code).toBe('OPERATION_NOT_FOUND');
});

test('Streamable HTTP MCP exposes exactly 16 bounded tools', async ({ request }) => {
  const initialize = await request.post('/mcp', {
    headers: {
      accept: 'application/json, text/event-stream',
      'content-type': 'application/json',
      'mcp-protocol-version': '2025-06-18'
    },
    data: {
      jsonrpc: '2.0', id: 'init-e2e', method: 'initialize',
      params: { protocolVersion: '2025-06-18', capabilities: {}, clientInfo: { name: 'agent-webmcp-e2e', version: '1' } }
    }
  });
  expect(initialize.ok()).toBeTruthy();
  const sessionId = initialize.headers()['mcp-session-id'];
  expect(sessionId).toBeTruthy();

  const headers = {
    accept: 'application/json, text/event-stream',
    'content-type': 'application/json',
    'mcp-protocol-version': '2025-06-18',
    'mcp-session-id': sessionId
  };
  const toolsResponse = await request.post('/mcp', {
    headers, data: { jsonrpc: '2.0', id: 'tools-e2e', method: 'tools/list', params: {} }
  });
  expect(toolsResponse.ok()).toBeTruthy();
  const toolDescriptors = (await toolsResponse.json()).result.tools;
  const names = toolDescriptors.map((tool) => tool.name);
  expect(names).toHaveLength(16);
  const statusTool = toolDescriptors.find((tool) => tool.name === 'system.status');
  expect(statusTool?._meta?.ui?.resourceUri).toBe('ui://agent-webmcp/fleet-cockpit-v1');
  expect(statusTool?._meta?.['openai/outputTemplate']).toBe('ui://agent-webmcp/fleet-cockpit-v1');
  for (const tool of toolDescriptors.filter((candidate) => candidate.name !== 'system.status')) {
    expect(tool?._meta?.ui?.resourceUri).toBeUndefined();
    expect(tool?._meta?.['openai/outputTemplate']).toBeUndefined();
  }
  expect(names).toEqual(expect.arrayContaining([
    'system.status', 'agent.list', 'agent.inspect', 'service.logs', 'service.diagnostics', 'service.restart',
    'job.list', 'job.inspect', 'job.logs'
  ]));
  for (const hidden of ['service.add', 'service.remove', 'service.discover', 'job.execute', 'job.cancel', 'target.list', 'target.inspect']) {
    expect(names).not.toContain(hidden);
  }

  const resourcesResponse = await request.post('/mcp', {
    headers, data: { jsonrpc: '2.0', id: 'resources-e2e', method: 'resources/list', params: {} }
  });
  expect(resourcesResponse.ok()).toBeTruthy();
  const resources = (await resourcesResponse.json()).result.resources;
  expect(resources).toHaveLength(1);
  expect(resources[0]).toMatchObject({
    uri: 'ui://agent-webmcp/fleet-cockpit-v1',
    mimeType: 'text/html;profile=mcp-app'
  });
  expect(resources[0]._meta.ui.prefersBorder).toBe(true);
  expect(resources[0]._meta.ui.csp.connectDomains).toEqual([]);
  expect(resources[0]._meta.ui.csp.resourceDomains).toEqual([]);

  const appResponse = await request.post('/mcp', {
    headers,
    data: { jsonrpc: '2.0', id: 'resource-read-e2e', method: 'resources/read', params: { uri: resources[0].uri } }
  });
  expect(appResponse.ok()).toBeTruthy();
  const appHtml = (await appResponse.json()).result.contents[0].text;
  expect(appHtml).toContain('ui/initialize');
  expect(appHtml).toContain('ui/notifications/tool-result');
  expect(appHtml).toContain('tools/call');
  expect(appHtml).not.toContain("fetch('/api");

  const call = await request.post('/mcp', {
    headers,
    data: { jsonrpc: '2.0', id: 'call-e2e', method: 'tools/call', params: { name: 'system.status', arguments: {} } }
  });
  expect(call.ok()).toBeTruthy();
  const callBody = await call.json();
  expect(callBody.result.isError).toBe(false);
  expect(callBody.result.structuredContent.status).toBe('SUCCESS');
});
