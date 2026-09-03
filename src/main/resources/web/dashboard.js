(() => {
  const state = { operations: [], services: [], jobs: [], selected: null, logCursor: '' };
  const byId = (id) => document.getElementById(id);
  const escapeHtml = (value) => String(value ?? '').replace(/[&<>"']/g, (character) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  })[character]);
  const toast = (message) => {
    const element = byId('toast');
    element.textContent = message;
    element.classList.add('show');
    window.setTimeout(() => element.classList.remove('show'), 1700);
  };

  async function execute(operationId, input = {}) {
    const response = await fetch(`/api/v1/operations/${encodeURIComponent(operationId)}`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(input)
    });
    const payload = await response.json();
    if (!response.ok || payload.status === 'FAILURE') {
      throw new Error(payload?.error?.message ?? `${operationId} failed`);
    }
    return payload.output;
  }

  const stateDot = (serviceState) => serviceState === 'RUNNING' ? 'good'
    : serviceState === 'FAILED' || serviceState === 'DEGRADED' ? 'bad' : 'warn';
  const bytes = (raw) => {
    const value = Number(raw || 0);
    if (value < 1024) return `${value} B`;
    if (value < 1024 ** 2) return `${(value / 1024).toFixed(1)} KB`;
    if (value < 1024 ** 3) return `${(value / 1024 ** 2).toFixed(1)} MB`;
    return `${(value / 1024 ** 3).toFixed(1)} GB`;
  };

  async function refreshHealth() {
    const health = await (await fetch('/health')).json();
    byId('connection-badge').innerHTML = `<span class="dot good"></span>${escapeHtml(health.transport)} · ${health.operationCount} ops`;
  }

  async function refreshCatalog() {
    const response = await fetch('/api/v1/operations');
    const body = await response.json();
    state.operations = body.operations ?? [];
    byId('operation-count').textContent = state.operations.length;
    byId('operation-count-nav').textContent = state.operations.length;
    const mcp = state.operations.filter((operation) => operation.surfaces?.includes('MCP'));
    byId('mcp-tool-count').textContent = `${mcp.length} tools`;
    byId('tool-list').innerHTML = mcp.slice(0, 15).map((operation) =>
      `<div class="tool-row"><span class="mono">${escapeHtml(operation.id)}</span><span>${operation.access === 'READ_ONLY' ? 'read' : 'mutate'}</span></div>`
    ).join('');
  }

  function setLifecycleEnabled(enabled) {
    document.querySelectorAll('[data-service-action]').forEach((button) => { button.disabled = !enabled; });
    byId('logs-button').disabled = !enabled;
    byId('logs-more-button').disabled = !enabled;
  }

  async function inspectSelected() {
    if (!state.selected) return;
    const summary = state.services.find((service) => service.id === state.selected);
    if (summary?.state === 'UNKNOWN') {
      byId('selected-card').hidden = false;
      byId('selected-name').textContent = summary.id;
      byId('selected-description').textContent = summary.description;
      byId('selected-state').className = 'badge';
      byId('selected-state').textContent = `UNKNOWN / ${summary.subState}`;
      byId('selected-pid').textContent = '0';
      byId('selected-memory').textContent = '0 B';
      byId('selected-cpu').textContent = '0';
      byId('console-wrap').hidden = true;
      setLifecycleEnabled(false);
      document.querySelectorAll('.service-row').forEach((row) => row.classList.toggle('selected', row.dataset.serviceId === state.selected));
      return;
    }

    const details = await execute('service.inspect', { serviceId: state.selected });
    setLifecycleEnabled(true);
    byId('selected-card').hidden = false;
    byId('selected-name').textContent = details.id;
    byId('selected-description').textContent = details.description;
    byId('selected-state').className = `badge ${details.state === 'RUNNING' ? 'good' : details.state === 'FAILED' ? 'danger' : ''}`;
    byId('selected-state').textContent = `${details.state} / ${details.subState}`;
    byId('selected-pid').textContent = details.pid;
    byId('selected-memory').textContent = bytes(details.memoryBytes);
    byId('selected-cpu').textContent = details.cpuUsageNanoseconds;
    document.querySelectorAll('.service-row').forEach((row) => row.classList.toggle('selected', row.dataset.serviceId === state.selected));
  }

  async function refreshServices(preserveSelection = true) {
    state.services = await execute('service.list', {});
    byId('service-count-nav').textContent = state.services.length;
    const healthy = state.services.filter((service) => service.state === 'RUNNING').length;
    byId('healthy-count').textContent = `${healthy} / ${state.services.length}`;
    byId('degraded-count').textContent = state.services.filter((service) => ['FAILED', 'DEGRADED'].includes(service.state)).length;
    const rows = byId('service-rows');
    if (!state.services.length) {
      rows.innerHTML = '<div class="empty">No managed services yet. Add an existing systemd unit above.</div>';
      state.selected = null;
      byId('selected-card').hidden = true;
      return;
    }
    rows.innerHTML = state.services.map((service) => {
      const unavailable = service.state === 'UNKNOWN';
      const controls = unavailable
        ? `<button data-row-action="inspect" data-id="${escapeHtml(service.id)}">Inspect</button>`
        : `<button data-row-action="inspect" data-id="${escapeHtml(service.id)}">Inspect</button><button data-row-action="restart" data-id="${escapeHtml(service.id)}">Restart</button><button data-row-action="logs" data-id="${escapeHtml(service.id)}">Logs</button>`;
      return `<div class="service-row ${state.selected === service.id ? 'selected' : ''}" data-service-id="${escapeHtml(service.id)}" role="button" tabindex="0"><span class="service-id mono">${escapeHtml(service.id)}</span><span class="state"><i class="dot ${stateDot(service.state)}"></i>${escapeHtml(service.state)}</span><span>${escapeHtml(service.subState)}</span><span class="service-desc">${escapeHtml(service.description)}</span><span class="row-controls">${controls}</span></div>`;
    }).join('');
    if (!preserveSelection || !state.selected || !state.services.some((service) => service.id === state.selected)) {
      state.selected = state.services[0].id;
    }
    await inspectSelected();
  }

  async function refreshJobs() {
    state.jobs = await execute('job.list', { limit: 12 });
    byId('job-count-nav').textContent = state.jobs.length;
    byId('recent-job-count').textContent = state.jobs.length;
    byId('job-list').innerHTML = state.jobs.length ? state.jobs.map((job) => `<div class="job-item"><span class="dot ${job.state === 'SUCCEEDED' ? 'good' : job.state === 'RUNNING' || job.state === 'QUEUED' ? 'warn' : 'bad'}"></span><span><strong class="mono">${escapeHtml(job.id)}</strong><small>${escapeHtml(job.agentId || 'unlinked agent')} · ${escapeHtml(job.operationId)}</small></span><em>${escapeHtml(job.state)}</em></div>`).join('') : '<div class="empty small-empty">No jobs yet.</div>';
  }

  async function loadLogs(newer = false) {
    if (!state.selected) return;
    const input = { serviceId: state.selected, lines: 120 };
    if (newer && state.logCursor) input.cursor = state.logCursor;
    const result = await execute('service.logs', input);
    state.logCursor = result.cursor || '';
    byId('console-wrap').hidden = false;
    const previous = newer ? byId('service-console').textContent : '';
    byId('service-console').textContent = `${previous ? `${previous}\n` : ''}${result.output || 'No journal output.'}`;
  }

  async function lifecycle(operationId) {
    if (!state.selected) return;
    const serviceId = state.selected;
    await execute(operationId, { serviceId });
    toast(`${operationId} · ${serviceId}`);
    await refreshServices(true);
  }

  async function addService() {
    const input = byId('service-add-id');
    const serviceId = input.value.trim();
    if (!serviceId) return;
    await execute('service.add', { serviceId });
    input.value = '';
    state.selected = serviceId;
    toast(`Managed ${serviceId}`);
    await refreshServices(true);
  }

  async function removeService() {
    if (!state.selected) return;
    const serviceId = state.selected;
    await execute('service.remove', { serviceId });
    state.selected = null;
    state.logCursor = '';
    toast(`Removed ${serviceId} from Agent WebMCP`);
    await refreshServices(false);
  }

  document.addEventListener('click', async (event) => {
    try {
      const rowAction = event.target.closest('[data-row-action]');
      if (rowAction) {
        event.preventDefault(); event.stopPropagation();
        state.selected = rowAction.dataset.id;
        await inspectSelected();
        if (rowAction.dataset.rowAction === 'restart') await lifecycle('service.restart');
        if (rowAction.dataset.rowAction === 'logs') await loadLogs(false);
        return;
      }
      const row = event.target.closest('[data-service-id]');
      if (row) { state.selected = row.dataset.serviceId; await inspectSelected(); return; }
      const action = event.target.closest('[data-service-action]');
      if (action) { await lifecycle(action.dataset.serviceAction); }
    } catch (error) { toast(error.message); console.error(error); }
  });

  byId('service-add-button').addEventListener('click', () => addService().catch((error) => toast(error.message)));
  byId('service-add-id').addEventListener('keydown', (event) => { if (event.key === 'Enter') addService().catch((error) => toast(error.message)); });
  byId('remove-button').addEventListener('click', () => removeService().catch((error) => toast(error.message)));
  byId('logs-button').addEventListener('click', () => loadLogs(false).catch((error) => toast(error.message)));
  byId('logs-more-button').addEventListener('click', () => loadLogs(true).catch((error) => toast(error.message)));
  byId('refresh-button').addEventListener('click', () => refreshAll().catch((error) => toast(error.message)));

  async function refreshAll() {
    await Promise.all([refreshHealth(), refreshCatalog(), refreshJobs()]);
    await refreshServices(true);
  }
  refreshAll().catch((error) => { toast(error.message); console.error(error); });
})();
