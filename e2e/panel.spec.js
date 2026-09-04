import { expect, test } from '@playwright/test';
import path from 'node:path';

const polyfill = path.resolve('e2e/.generated/webmcp-polyfill.js');

test.skip(process.env.AGENT_WEBMCP_PANEL_FIXTURE !== 'true', 'panel fixture is a dedicated stateful-provider E2E');
test.describe.configure({ mode: 'serial' });

test.beforeEach(async ({ page }) => {
  await page.addInitScript({ path: polyfill });
});

test('Fleet Cockpit discovers provider candidates and drives Service Control end to end', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Managed services' })).toBeVisible();
  await expect(page.getByText('No managed services yet')).toBeVisible();
  await expect(page.locator('#browser-surface-count')).toContainText('18 operations exposed');

  await page.locator('#discover-services-button').click();
  await expect(page.getByRole('heading', { name: 'Discover Services' })).toBeVisible();
  await expect(page.locator('#discover-ai')).toBeEnabled();
  await page.locator('#run-discovery').click();
  await expect(page.getByText('Added · 2')).toBeVisible();
  await expect(page.getByText('Skipped · 2')).toBeVisible();
  await expect(page.getByText('demo.service', { exact: true }).last()).toBeVisible();
  await expect(page.getByText('opt-worker.service', { exact: true }).last()).toBeVisible();
  await page.locator('.modal-actions [data-close-modal]').click();

  await expect(page.locator('[data-open-service="demo.service"]')).toBeVisible();
  await expect(page.locator('[data-open-service="opt-worker.service"]')).toBeVisible();
  await page.locator('[data-open-service="demo.service"]').click();
  await expect(page.getByText('Service Control · Runtime Inspector')).toBeVisible();
  await expect(page.locator('dl').getByText('RUNNING / running')).toBeVisible();
  await expect(page.getByText('/tmp', { exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Observed lifecycle evidence' })).toBeVisible();
  await expect(page.locator('#lifecycle-evidence')).toContainText('Provider state');
  await expect(page.locator('#lifecycle-evidence')).toContainText(/PID \d+/);
  await expect(page.locator('#lifecycle-evidence')).toContainText('Not run');
  await expect(page.locator('#lifecycle-evidence')).toContainText('Logs not loaded');

  await page.getByRole('button', { name: 'Run Diagnostics' }).click();
  await expect(page.getByText('Diagnostics · healthy')).toBeVisible();
  await expect(page.getByText('No diagnostic findings.')).toBeVisible();
  await expect(page.locator('#lifecycle-evidence')).toContainText('Healthy');
  await page.locator('#logs-button').click();
  await expect(page.locator('#service-console')).toContainText('line one');
  await expect(page.locator('#service-console')).toContainText('line two');
  await expect(page.locator('#lifecycle-evidence')).toContainText('2 log line(s) loaded');

  await page.locator('[data-lifecycle="service.stop"]').click();
  await expect(page.locator('dl').getByText('STOPPED / dead')).toBeVisible();
  await page.locator('[data-lifecycle="service.start"]').click();
  await expect(page.locator('dl').getByText('RUNNING / running')).toBeVisible();
  await page.locator('[data-lifecycle="service.restart"]').click();
  await expect(page.locator('#toast')).toContainText('Restart service completed');

  await page.locator('#primary-nav [data-page="services"]').click();
  await page.locator('#discover-services-button').click();
  await page.locator('#discover-ai').check();
  await page.locator('#run-discovery').click();
  await expect(page.getByText('AI candidates · 1')).toBeVisible();
  await expect(page.getByText('vendor.service', { exact: true }).last()).toBeVisible();
  await page.locator('.modal-actions [data-close-modal]').click();
  await expect(page.locator('[data-open-service="vendor.service"]')).toBeVisible();
});

test('Jobs workspace covers deterministic, scheduled, recurring, Codex, trace, and cancellation state', async ({ page }) => {
  await page.goto('/');
  await page.locator('#primary-nav [data-page="jobs"]').click();
  await expect(page.getByText('Create Job', { exact: true })).toBeVisible();
  await expect(page.getByText(/Codex is available: codex-cli test/)).toBeVisible();
  await expect(page.locator('#job-execution-summary')).toBeVisible();
  await expect(page.locator('#job-summary-runner')).toHaveText('Canonical service operation');
  await expect(page.locator('#job-summary-schedule')).toHaveText('Run now');

  await page.locator('#job-service').selectOption('demo.service');
  await expect(page.locator('#job-summary-service')).toHaveText('demo.service');
  await page.locator('#job-operation').selectOption('service.restart');
  await page.locator('#job-submit').click();
  await expect(page.locator('#job-inspector')).toContainText('SUCCEEDED', { timeout: 10_000 });
  await expect(page.locator('#job-inspector')).toContainText('Execution Trace');
  await expect(page.locator('#job-inspector')).toContainText('Job execution started');
  await expect(page.locator('#job-inspector')).toContainText('Job succeeded');
  await expect(page.locator('#job-inspector')).toContainText('service.restart');

  await page.locator('[data-job-mode="create"]').click();
  await page.locator('#job-service').selectOption('demo.service');
  await page.locator('#job-schedule').selectOption('at');
  const future = await page.evaluate(() => {
    const date = new Date(Date.now() + 10 * 60 * 1000);
    const pad = (value) => String(value).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
  });
  await page.locator('#job-run-at').fill(future);
  await page.locator('#job-submit').click();
  await expect(page.locator('#job-inspector')).toContainText('SCHEDULED');
  await expect(page.locator('#job-cancel')).toBeVisible();
  await page.locator('#job-cancel').click();
  await expect(page.locator('#job-inspector')).toContainText('CANCELLED');
  await expect(page.locator('#job-inspector')).toContainText('Job cancelled before execution');

  await page.locator('[data-job-mode="create"]').click();
  await page.locator('#job-service').selectOption('demo.service');
  await page.locator('#job-schedule').selectOption('recurring');
  await page.locator('#job-repeat').selectOption('3600');
  await page.locator('#job-submit').click();
  await expect(page.locator('#job-inspector')).toContainText('SCHEDULED', { timeout: 10_000 });
  await expect(page.locator('#job-inspector')).toContainText('3600 seconds');
  await page.locator('#job-cancel').click();
  await expect(page.locator('#job-inspector')).toContainText('CANCELLED');

  await page.locator('[data-job-mode="create"]').click();
  await page.locator('#job-service').selectOption('demo.service');
  await page.locator('#job-prompt').fill('Inspect this service and summarize its test state.');
  await expect(page.locator('#job-operation')).toBeDisabled();
  await expect(page.locator('#job-summary-runner')).toHaveText('Installed Codex CLI');
  await expect(page.locator('#job-summary-action')).toHaveText('One-shot service prompt');
  await page.locator('#job-submit').click();
  await expect(page.locator('#job-inspector')).toContainText('SUCCEEDED', { timeout: 10_000 });
  await expect(page.locator('#job-inspector')).toContainText('Installed Codex CLI');
  await expect(page.locator('#job-inspector')).toContainText('fake Codex execution complete');
  await expect(page.locator('#job-inspector')).toContainText('codex:local');
});

test('approved registry, activity, target, settings, agents, and mobile surfaces stay truthful', async ({ page }) => {
  await page.goto('/');

  await page.locator('#primary-nav [data-page="operations"]').click();
  await expect(page.getByText('23 canonical capabilities.')).toBeVisible();
  await expect(page.getByText('service.diagnostics', { exact: true })).toBeVisible();
  await expect(page.getByText('job.cancel', { exact: true })).toBeVisible();
  await expect(page.getByText('agent.list', { exact: true })).toBeVisible();
  await expect(page.getByText('agent.inspect', { exact: true })).toBeVisible();

  await page.locator('#primary-nav [data-page="catalog"]').click();
  await expect(page.getByRole('heading', { name: 'Projection Matrix' })).toBeVisible();
  await expect(page.getByText('service.discover', { exact: true })).toBeVisible();
  await expect(page.getByText('job.execute', { exact: true })).toBeVisible();

  await page.locator('#primary-nav [data-page="activity"]').click();
  await expect(page.getByRole('heading', { name: 'Activity Ledger', level: 2 })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Resource Timeline' })).toBeVisible();
  await expect(page.getByText(/job-[a-f0-9]{12}/).first()).toBeVisible();

  await page.locator('#primary-nav [data-page="agents"]').click();
  await expect(page.getByRole('heading', { name: 'Agent Registry' })).toBeVisible();
  await expect(page.locator('[data-open-agent="codex:local"]')).toContainText('Installed Codex CLI');
  await expect(page.locator('[data-open-agent="codex:local"]')).toContainText('ONLINE');
  await expect(page.locator('[data-open-agent="codex:local"]')).toContainText('codex-cli test');
  await expect(page.locator('.agent-inspector')).toContainText('CODEX_CLI');
  await expect(page.locator('.agent-inspector')).toContainText('service-job.prompt');
  await expect(page.locator('.agent-inspector')).toContainText('service-discovery.read-only');
  await expect(page.locator('#agent-count-nav')).toHaveText('1');

  await page.locator('#target-switch-button').click();
  await expect(page.getByRole('heading', { name: 'Target Switcher' })).toBeVisible();
  await expect(page.locator('.target-table-head')).toContainText('Target');
  await expect(page.locator('.target-table-head')).toContainText('Agent');
  await expect(page.locator('.target-table-head')).toContainText('Services');
  await expect(page.locator('.target-table-head')).toContainText('Heartbeat');
  await expect(page.locator('.target-row').first()).toContainText('Installed Codex CLI');
  await expect(page.locator('.target-row').first()).toContainText('ONLINE');
  await expect(page.locator('.target-row').first()).toContainText('Observed by Agent WebMCP');
  await expect(page.locator('.health-inline')).toHaveText(/\d+ healthy · 0 degraded/);
  await page.locator('.modal-close').click();

  await page.locator('#primary-nav [data-page="settings"]').click();
  await expect(page.getByRole('heading', { name: 'Security & Exposure' })).toBeVisible();
  await page.locator('[data-settings-tab="webmcp"]').click();
  await expect(page.locator('.setting-row').filter({ hasText: 'Exposed operations' })).toContainText('18');
  await page.locator('[data-settings-tab="security"]').click();
  await expect(page.getByText('NO_AUTH is active.').last()).toBeVisible();
  await expect(page.getByText('Running job cancellation')).toBeVisible();

  await page.setViewportSize({ width: 390, height: 844 });
  await page.locator('#primary-nav [data-page="agents"]').click();
  const agentOverflow = await page.evaluate(() => {
    const wrap = document.querySelector('.agents-workspace .table-wrap');
    return { documentWidth: document.documentElement.scrollWidth, viewportWidth: window.innerWidth, internalWidth: wrap?.scrollWidth ?? 0, clientWidth: wrap?.clientWidth ?? 0 };
  });
  expect(agentOverflow.documentWidth).toBeLessThanOrEqual(agentOverflow.viewportWidth);
  expect(agentOverflow.internalWidth).toBeGreaterThanOrEqual(agentOverflow.clientWidth);

  await page.locator('#primary-nav [data-page="services"]').click();
  const overflow = await page.evaluate(() => ({
    documentWidth: document.documentElement.scrollWidth,
    viewportWidth: window.innerWidth,
    tableScrollsInternally: [...document.querySelectorAll('.table-wrap')].every((table) => table.scrollWidth >= table.clientWidth)
  }));
  expect(overflow.documentWidth).toBeLessThanOrEqual(overflow.viewportWidth);
  expect(overflow.tableScrollsInternally).toBeTruthy();

  await page.locator('#target-switch-button').click();
  await expect(page.locator('.target-table-wrap')).toBeVisible();
  const targetOverflow = await page.evaluate(() => {
    const wrap = document.querySelector('.target-table-wrap');
    return {
      documentWidth: document.documentElement.scrollWidth,
      viewportWidth: window.innerWidth,
      internalWidth: wrap?.scrollWidth ?? 0,
      clientWidth: wrap?.clientWidth ?? 0
    };
  });
  expect(targetOverflow.documentWidth).toBeLessThanOrEqual(targetOverflow.viewportWidth);
  expect(targetOverflow.internalWidth).toBeGreaterThan(targetOverflow.clientWidth);
});
