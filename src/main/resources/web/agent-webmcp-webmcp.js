(() => {
  const runtime = window.__agentWebMcp = { state: 'starting', registeredOperationIds: [] };
  async function executeOperation(operationId, input, signal) {
    const response = await fetch(`/api/v1/operations/${encodeURIComponent(operationId)}`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(input ?? {}), signal });
    const payload = await response.json();
    if (!response.ok || payload.status === 'FAILURE') throw new Error(payload?.error?.message ?? `Operation ${operationId} failed with HTTP ${response.status}`);
    return payload;
  }
  async function register() {
    if (!document.modelContext) { runtime.state = 'unavailable'; return; }
    const response = await fetch('/api/v1/operations');
    if (!response.ok) throw new Error(`Unable to load operation catalog: HTTP ${response.status}`);
    const payload = await response.json();
    const exposed = (payload.operations ?? []).filter(operation => operation.surfaces?.includes('WEBMCP'));
    for (const operation of exposed) {
      await document.modelContext.registerTool({ name: operation.id, description: operation.description, inputSchema: operation.inputSchema, annotations: { readOnlyHint: operation.access === 'READ_ONLY' }, execute: async (input, options = {}) => executeOperation(operation.id, input, options.signal) });
      runtime.registeredOperationIds.push(operation.id);
    }
    runtime.state = 'ready';
    document.dispatchEvent(new CustomEvent('agent-webmcp:ready', { detail: { operationCount: runtime.registeredOperationIds.length } }));
  }
  register().catch(error => { runtime.state='failed'; runtime.error=String(error?.message ?? error); console.error('[Agent WebMCP] WebMCP registration failed', error); });
})();
