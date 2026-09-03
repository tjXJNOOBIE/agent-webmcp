import { expect, test } from '@playwright/test';

test.skip(process.env.AGENT_WEBMCP_PANEL_FIXTURE !== 'true', 'panel fixture is a dedicated stateful-provider E2E');

test('Fleet Cockpit adds, mutates, reads logs, and removes a managed service', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Fleet Cockpit' })).toBeVisible();
  await expect(page.getByText('No managed services yet')).toBeVisible();

  await page.locator('#service-add-id').fill('demo.service');
  await page.locator('#service-add-button').click();
  await expect(page.locator('[data-service-id="demo.service"]')).toBeVisible();
  await expect(page.locator('#selected-name')).toHaveText('demo.service');
  await expect(page.locator('#selected-state')).toContainText('RUNNING');

  await page.locator('[data-service-action="service.stop"]').click();
  await expect(page.locator('#selected-state')).toContainText('STOPPED');

  await page.locator('[data-service-action="service.start"]').click();
  await expect(page.locator('#selected-state')).toContainText('RUNNING');

  await page.locator('[data-service-action="service.restart"]').click();
  await expect(page.locator('#selected-state')).toContainText('RUNNING');

  await page.locator('#logs-button').click();
  await expect(page.locator('#service-console')).toContainText('line one');
  await expect(page.locator('#service-console')).toContainText('line two');

  await page.locator('#remove-button').click();
  await expect(page.locator('[data-service-id="demo.service"]')).toHaveCount(0);
  await expect(page.getByText('No managed services yet')).toBeVisible();
});
