#!/usr/bin/env node
import { access } from "node:fs/promises";
import { constants } from "node:fs";
import { resolveLocalBrowserPath } from "./local-browser-path.mjs";

const executablePath = await resolveLocalBrowserPath();
await access(executablePath, constants.X_OK);
const { default: puppeteer } = await import("puppeteer");
const browser = await puppeteer.launch({
  executablePath,
  headless: true,
  args: ["--no-sandbox", "--disable-setuid-sandbox", "--disable-dev-shm-usage"],
});
try {
  const page = await browser.newPage();
  await page.setContent("<!doctype html><title>Agent WebMCP browser smoke</title><main id='ok'>local-browser-ok</main>");
  const text = await page.$eval("#ok", (node) => node.textContent);
  if (text !== "local-browser-ok") {
    throw new Error(`Unexpected browser smoke result: ${text}`);
  }
  console.log(`[ok] local Chromium ${await browser.version()}`);
  console.log(`[ok] executable ${executablePath}`);
} finally {
  await browser.close();
}
