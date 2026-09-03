import { defineConfig } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';

const dist = process.env.AGENT_WEBMCP_DIST ?? path.resolve('build/install/agent-webmcp');
const executable = process.platform === 'win32'
  ? path.join(dist, 'bin', 'agent-webmcp.bat')
  : path.join(dist, 'bin', 'agent-webmcp');
const serverCommand = process.env.AGENT_WEBMCP_SERVER_COMMAND
  ?? `"${executable}" serve --host=127.0.0.1 --port=7188`;
const runtimeData = path.resolve('test-results/runtime-data');
fs.rmSync(runtimeData, { recursive: true, force: true });

export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  workers: 1,
  fullyParallel: false,
  use: {
    baseURL: 'http://127.0.0.1:7188',
    headless: true
  },
  webServer: {
    command: serverCommand,
    url: 'http://127.0.0.1:7188/health',
    reuseExistingServer: false,
    timeout: 30_000,
    env: {
      ...process.env,
      AGENT_WEBMCP_DATA_DIR: runtimeData
    }
  }
});
