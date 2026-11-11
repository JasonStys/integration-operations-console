/**
 * File: native-acceptance.mjs
 * Purpose: Actual bundled WindowsEXE/UI/job/persistence/security/lifecycle acceptance in owned synthetic roots.
 * State: owned child/browser/profile only; screenshots/receipt omit private configuration and session values.
 * Exact declarations and variables: docs/code-index.md.
 */
import { spawn } from "node:child_process";
import { createHash, randomUUID } from "node:crypto";
import { mkdir, mkdtemp, readFile, stat, writeFile } from "node:fs/promises";
import { createRequire } from "node:module";
import { tmpdir } from "node:os";
import { dirname, isAbsolute, join, resolve } from "node:path";
import process from "node:process";
import { setTimeout as delay } from "node:timers/promises";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const require = createRequire(join(root, "frontend/package.json"));
const { chromium, expect } = require("@playwright/test");
const executable = resolve(process.argv[2] ?? "");
const output = resolve(process.argv[3] ?? join(root, "artifacts/native"));
const temporary = await mkdtemp(join(tmpdir(), "ioc-native-"));
const profile = join(temporary, "workspace");
const cwd = join(temporary, "unrelated-working-directory");
await mkdir(cwd);
await mkdir(output, { recursive: true });
const metadata = JSON.parse(
  await readFile(join(dirname(executable), "SOURCE.json"), "utf8"),
);
if (
  !isAbsolute(executable) ||
  !(await stat(executable)).isFile() ||
  metadata.version !== "1.1.0" ||
  metadata.schema !== 1 ||
  !/^[a-f0-9]{40}$/u.test(metadata.source) ||
  typeof metadata.sourceDirty !== "boolean"
)
  throw new Error("A genuine current Windows application image is required.");
if (
  process.env.CI &&
  (metadata.sourceDirty !== false || metadata.source !== process.env.GITHUB_SHA)
)
  throw new Error(
    "Hosted acceptance requires the exact clean checked-out source.",
  );
if (
  metadata.javaRuntime !== "bundled" ||
  typeof metadata.javaRuntimeVersion !== "string" ||
  !/^21\./u.test(metadata.javaRuntimeVersion)
)
  throw new Error("Bundled Java21 provenance is required.");
let child, browser, context, launch;
const foreign = [];
const pageErrors = [];
const report = {
  schema: 1,
  version: metadata.version,
  source: metadata.source,
  sourceDirty: metadata.sourceDirty,
  javaRuntimeVersion: metadata.javaRuntimeVersion,
  platform: process.platform,
  acceptance: "running",
  stages: [],
};

/** Keep sanitizer-safe fixed milestones, not raw logs, profile paths or launch credentials. */
async function stage(name) {
  report.stages.push(name);
  await writeFile(
    join(output, "native-acceptance.json"),
    JSON.stringify(report, null, 2) + "\n",
  );
}

/** Spawn only the selected package, with no external Java/Node/database configuration. */
function start() {
  const environment = Object.fromEntries(
    Object.entries(process.env).filter(
      ([key]) =>
        !/^(?:IOC_|SPRING_|SERVER_|JAVA_HOME$|JAVA_TOOL_OPTIONS$|JDK_JAVA_OPTIONS$|_JAVA_OPTIONS$)/u.test(
          key,
        ),
    ),
  );
  Object.assign(environment, {
    IOC_DESKTOP_TEST: "1",
    IOC_DESKTOP_DATA: profile,
    IOC_DESKTOP_NO_WINDOW: "0",
    IOC_DESKTOP_NO_BROWSER: "1",
    PATH: join(process.env.SystemRoot ?? "C:\\Windows", "System32"),
  });
  const processHandle = spawn(executable, [], {
    cwd,
    env: environment,
    windowsHide: true,
    stdio: ["ignore", "pipe", "pipe"],
  });
  let bytes = 0;
  for (const stream of [processHandle.stdout, processHandle.stderr])
    stream.on("data", (value) => {
      bytes += value.length;
    });
  processHandle.on("error", () => {
    report.childStartError = true;
  });
  processHandle.outputBytes = () => bytes;
  return processHandle;
}

/** Read only this profile's nonce-bound readiness, after this owned process is alive. */
async function ready(processHandle, priorInstance = null) {
  const deadline = Date.now() + 45000;
  while (Date.now() < deadline) {
    if (
      processHandle.exitCode !== null ||
      processHandle.signalCode !== null ||
      report.childStartError
    )
      throw new Error("Owned application exited before readiness.");
    if (processHandle.outputBytes() > 131072)
      throw new Error("Native output exceeded its bound.");
    try {
      const publicBytes = await readFile(join(profile, "desktop-ready.json"));
      const privateBytes = await readFile(
        join(profile, "desktop-test-session.json"),
      );
      if (publicBytes.length > 4096 || privateBytes.length > 4096)
        throw new Error("Readiness exceeds its bound.");
      const status = JSON.parse(publicBytes.toString());
      const selected = JSON.parse(privateBytes.toString());
      if (status.instance === priorInstance) {
        await delay(50);
        continue;
      }
      if (
        status.version !== metadata.version ||
        status.source !== metadata.source ||
        !/^http:\/\/127\.0\.0\.1:\d{4,5}$/u.test(status.origin) ||
        !new RegExp(
          `^${status.origin.replaceAll(".", "\\.")}\/#ioc=[a-f0-9]{64}$`,
          "u",
        ).test(selected.url)
      )
        throw new Error("Native ownership/source identity mismatch.");
      const health = await fetch(status.origin + "/desktop/health", {
        signal: AbortSignal.timeout(3000),
      });
      const value = await health.json();
      if (
        !health.ok ||
        value.instance !== status.instance ||
        value.source !== metadata.source ||
        value.mode !== "private-local-demo" ||
        value.nativeWindowVisible !== true
      )
        throw new Error("Native service/window correlation failed.");
      return {
        ...status,
        url: selected.url,
        token: selected.url.split("#ioc=")[1],
      };
    } catch (error) {
      if (error.code !== "ENOENT") throw error;
    }
    await delay(50);
  }
  throw new Error("Owned native startup deadline exceeded.");
}

/** Wait for actual EXE exit, not a closed browser or acknowledged response alone. */
async function exited(processHandle, expected = 0) {
  const deadline = Date.now() + 20000;
  while (
    processHandle.exitCode === null &&
    processHandle.signalCode === null &&
    Date.now() < deadline
  )
    await delay(50);
  expect(processHandle.exitCode).toBe(expected);
}

/** Fixed-origin request with explicit synthetic role; no values may choose another host. */
async function api(path, options = {}) {
  if (!path.startsWith("/api/v1/"))
    throw new Error("Unknown native fixture route.");
  const response = await context.request.fetch(launch.origin + path, {
    ...options,
    headers: { "X-IOC-Session": launch.token, ...options.headers },
  });
  expect(response.ok()).toBe(true);
  return response.json();
}

/** Apply identical no-external-request and renderer-error oracles to both fresh launch contexts. */
async function ownedBrowserContext() {
  const selected = await browser.newContext({
    viewport: { width: 1280, height: 960 },
    acceptDownloads: true,
  });
  await selected.route("**/*", (route) => {
    const url = route.request().url();
    if (/^https?:/u.test(url) && new URL(url).origin !== launch.origin) {
      foreign.push(new URL(url).origin);
      return route.abort();
    }
    return route.continue();
  });
  selected.on("page", (page) =>
    page.on("pageerror", () => {
      pageErrors.push("renderer-error");
    }),
  );
  return selected;
}

try {
  child = start();
  launch = await ready(child);
  await stage("owned-exe-ready-with-bundled-runtime");
  browser = await chromium.launch();
  context = await ownedBrowserContext();
  const page = await context.newPage();
  await page.goto(launch.url);
  await expect(
    page.getByRole("heading", {
      name: "Integration Operations Console",
      exact: true,
    }),
  ).toBeVisible();
  await expect(
    page.getByText("Local application 1.1.0", { exact: true }),
  ).toBeVisible();
  await expect(
    page.getByText("Your local workspace persists between launches.", {
      exact: false,
    }),
  ).toBeVisible();
  expect(page.url()).not.toContain("#ioc=");
  await page
    .getByLabel("Display name", { exact: true })
    .fill("Fictional distribution operations");
  await page
    .getByLabel("Provider account reference", { exact: true })
    .fill("invalid reference!");
  expect(
    await page
      .getByLabel("Provider account reference", { exact: true })
      .evaluate((input) => input.checkValidity()),
  ).toBe(false);
  expect((await api("/api/v1/accounts")).length).toBe(0);
  await page
    .getByLabel("Provider account reference", { exact: true })
    .fill("native-synthetic-" + randomUUID());
  await page.getByRole("button", { name: "Link account", exact: true }).click();
  await expect(
    page.getByText("Fake account is linked.", { exact: true }),
  ).toBeVisible();
  const accounts = await api("/api/v1/accounts");
  expect(accounts).toHaveLength(1);
  expect(accounts[0].connectorId).toBe("atlas-ads");
  const submitted = page.waitForResponse(
    (response) =>
      response.request().method() === "POST" &&
      new URL(response.url()).pathname === "/api/v1/jobs",
  );
  await page
    .getByRole("button", { name: "Submit idempotent job", exact: true })
    .click();
  let job = (await (await submitted).json()).resource;
  await page
    .getByRole("button", { name: "Submit idempotent job", exact: true })
    .click();
  await expect(
    page
      .getByRole("region", { name: "Recent integration jobs" })
      .locator("tbody tr"),
  ).toHaveCount(1);
  for (
    let attempt = 0;
    job.status !== "SUCCEEDED" && attempt < 4;
    attempt += 1
  ) {
    const running = page.waitForResponse(
      (response) =>
        response.request().method() === "POST" &&
        new URL(response.url()).pathname === `/api/v1/jobs/${job.id}/run`,
    );
    await page
      .getByRole("region", { name: "Recent integration jobs" })
      .getByRole("button", { name: "Run", exact: true })
      .click();
    job = await (await running).json();
  }
  expect(job.status).toBe("SUCCEEDED");
  expect(job.recordsProcessed).toBe(5);
  await page
    .getByRole("region", { name: "Recent integration jobs" })
    .getByRole("button", { name: "Audit", exact: true })
    .click();
  await expect(
    page.getByRole("button", {
      name: "Download captured job review",
      exact: true,
    }),
  ).toBeVisible();
  const downloading = page.waitForEvent("download");
  await page
    .getByRole("button", { name: "Download captured job review", exact: true })
    .click();
  const download = await downloading;
  const inspection = JSON.parse(await readFile(await download.path(), "utf8"));
  expect(inspection.job.id).toBe(job.id);
  expect(inspection.job.recordsProcessed).toBe(5);
  expect(inspection.history.items.length).toBeGreaterThan(0);
  await page
    .locator('section[aria-labelledby="audit-heading"]')
    .screenshot({ path: join(output, "native-audit.png") });
  await page.evaluate(() => {
    document.activeElement?.blur();
    window.scrollTo(0, 0);
  });
  await page.screenshot({ path: join(output, "native-console.png") });
  await page
    .getByRole("button", { name: "Generate new sync request key", exact: true })
    .click();
  await page
    .getByLabel("Failure scenario", { exact: true })
    .selectOption("PERMANENT");
  const failedSubmission = page.waitForResponse(
    (response) =>
      response.request().method() === "POST" &&
      new URL(response.url()).pathname === "/api/v1/jobs",
  );
  await page
    .getByRole("button", { name: "Submit idempotent job", exact: true })
    .click();
  const failed = (await (await failedSubmission).json()).resource;
  const failedRow = page
    .getByRole("region", { name: "Recent integration jobs" })
    .locator("tbody tr")
    .filter({ has: page.locator(`td[title="${failed.id}"]`) });
  await failedRow.getByRole("button", { name: "Run", exact: true }).click();
  await expect(failedRow).toContainText("DEAD LETTERED");
  expect((await api("/api/v1/jobs/" + failed.id)).lastError).toBe(
    "provider schema is intentionally incompatible",
  );
  await failedRow.getByRole("button", { name: "Replay", exact: true }).click();
  await expect(failedRow).toContainText("QUEUED");
  expect((await api("/api/v1/jobs/" + failed.id)).attempt).toBe(0);
  await failedRow.getByRole("button", { name: "Cancel", exact: true }).click();
  await expect(failedRow).toContainText("CANCELLED");
  await page
    .getByLabel("Filter state", { exact: true })
    .selectOption("SUCCEEDED");
  await page
    .getByRole("button", { name: "Apply job filters", exact: true })
    .click();
  await expect(
    page.getByRole("region", { name: "Filtered integration jobs" }),
  ).toContainText(job.id);
  await page
    .locator('section[aria-labelledby="search-heading"]')
    .screenshot({ path: join(output, "native-review.png") });
  expect(
    (
      await context.request.post(launch.origin + "/api/v1/accounts", {
        headers: { "X-IOC-Session": launch.token, "X-Demo-Role": "viewer" },
        data: {
          connectorId: "atlas-ads",
          displayName: "invalid",
          externalReference: "blocked-viewer",
        },
      })
    ).status(),
  ).toBe(403);
  expect(
    (
      await context.request.get(launch.origin + "/api/v1/accounts", {
        headers: {
          "X-IOC-Session": launch.token,
          Origin: "https://example.invalid",
        },
      })
    ).status(),
  ).toBe(403);
  expect(
    (
      await context.request.get(launch.origin + "/api/v1/accounts", {
        headers: { "X-IOC-Session": "" },
      })
    ).status(),
  ).toBe(403);
  expect(
    (
      await context.request.post(launch.origin + "/api/v1/accounts", {
        headers: { "X-IOC-Session": launch.token, "X-Demo-Role": "operator" },
        data: {
          connectorId: "atlas-ads",
          displayName: "",
          externalReference: "bad reference!",
        },
      })
    ).status(),
  ).toBe(400);
  const second = start();
  await exited(second, 2);
  expect((await api("/api/v1/accounts")).length).toBe(1);
  await stage("actual-ui-idempotent-job-export-and-refusals");
  const settingsHash = createHash("sha256")
    .update(await readFile(join(profile, "desktop.properties")))
    .digest("hex");
  await page
    .getByRole("button", { name: "Quit application", exact: true })
    .click();
  await exited(child);
  await context.close();
  const previous = launch;
  child = start();
  launch = await ready(child, previous.instance);
  expect(launch.instance).not.toBe(previous.instance);
  expect(
    createHash("sha256")
      .update(await readFile(join(profile, "desktop.properties")))
      .digest("hex"),
  ).toBe(settingsHash);
  context = await ownedBrowserContext();
  const reopened = await context.newPage();
  await reopened.goto(launch.url);
  await expect(
    reopened.getByRole("region", { name: "Recent integration jobs" }),
  ).toContainText("SUCCEEDED");
  const retained = await api("/api/v1/jobs/" + job.id);
  expect(retained.status).toBe("SUCCEEDED");
  expect(retained.recordsProcessed).toBe(5);
  const auditAfter = await api("/api/v1/jobs/" + job.id + "/inspection");
  expect(auditAfter.history.items).toEqual(inspection.history.items);
  expect((await api("/api/v1/jobs/" + failed.id)).status).toBe("CANCELLED");
  expect((await api("/api/v1/accounts"))[0].id).toBe(accounts[0].id);
  expect(
    (
      await context.request.get(launch.origin + "/api/v1/accounts", {
        headers: { "X-IOC-Session": previous.token },
      })
    ).status(),
  ).toBe(403);
  await reopened
    .getByRole("button", { name: "Quit application", exact: true })
    .click();
  await exited(child);
  expect(foreign).toEqual([]);
  expect(pageErrors).toEqual([]);
  await stage("restarted-durable-records-and-real-exit");
  report.acceptance = "passed";
  report.recordsProcessed = 5;
  report.scenarios = [
    "healthy-idempotent-two-page-sync",
    "permanent-schema-failure-replay-cancel",
  ];
  report.thirdPartyRequests = 0;
  report.nativeWindowVisibleBothLaunches = true;
  report.preservation =
    "settings bytes and actual linked account/job/audit retained across restart";
  report.distribution =
    "actual unsigned jpackage app-image; no OS installation claimed";
  await stage("complete");
  console.log("Actual native application acceptance passed.");
} catch (error) {
  report.acceptance = "failed";
  report.failure = String(error.message)
    .replace(/#ioc=[a-f0-9]{64}/gu, "#ioc=[redacted]")
    .slice(0, 4000);
  console.error(report.failure);
  process.exitCode = 1;
} finally {
  await context?.close();
  await browser?.close();
  if (child && child.exitCode === null && launch) {
    try {
      await fetch(launch.origin + "/desktop/quit", {
        method: "POST",
        headers: {
          "X-IOC-Session": launch.token,
          "Content-Type": "application/json",
        },
        body: "{}",
        signal: AbortSignal.timeout(3000),
      });
      await exited(child);
    } catch {
      report.cleanup =
        "owned disposable process needs review; profile preserved";
    }
  }
  await writeFile(
    join(output, "native-acceptance.json"),
    JSON.stringify(report, null, 2) + "\n",
  );
}
