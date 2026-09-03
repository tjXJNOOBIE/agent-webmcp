#!/usr/bin/env node
import { access } from "node:fs/promises";
import { constants } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";
import process from "node:process";

const scriptPath = fileURLToPath(import.meta.url);
const scriptDir = path.dirname(scriptPath);
const projectRoot = path.resolve(scriptDir, "..");

export async function resolveLocalBrowserPath() {
  process.env.PLAYWRIGHT_BROWSERS_PATH ||= path.join(projectRoot, ".browser-cache", "playwright");
  const { chromium } = await import("playwright");
  const executablePath = chromium.executablePath();
  if (!path.isAbsolute(executablePath)) {
    throw new Error(`Playwright returned a non-absolute browser path: ${executablePath}`);
  }
  await access(executablePath, constants.X_OK);
  return executablePath;
}

if (process.argv[1] && path.resolve(process.argv[1]) === scriptPath) {
  process.stdout.write(`${await resolveLocalBrowserPath()}\n`);
}
