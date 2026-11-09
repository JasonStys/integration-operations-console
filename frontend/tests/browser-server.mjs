/**
 * File: browser-server.mjs
 * Purpose: Own a loopback Spring/H2 process and serve the actual compiled UI with a same-origin API proxy.
 * Symbols: checkedPort, ensureUnused, waitForOwnedApi, serve, stop, main; docs/code-index.md lists lines.
 * Variables: child/server handles belong only to this fixture; logs are bounded and no external database is used.
 */
import { spawn } from 'node:child_process';
import console from 'node:console';
import { existsSync, readFileSync, statSync } from 'node:fs';
import { createServer, get, request } from 'node:http';
import { createServer as createPortProbe } from 'node:net';
import { dirname, extname, isAbsolute, join, relative, resolve } from 'node:path';
import process from 'node:process';
import { setTimeout as sleep } from 'node:timers/promises';
import { fileURLToPath, URL } from 'node:url';
import { stripVTControlCharacters } from 'node:util';

const frontend = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const dist = join(frontend, 'dist');
const jar = resolve(frontend, '../backend/target/integration-operations-console-1.0.0.jar');
const apiPort = checkedPort(process.env.IOC_E2E_API_PORT, 18961);
const uiPort = checkedPort(process.env.IOC_E2E_UI_PORT, 18962);
const java =
  process.env.IOC_E2E_JAVA ??
  (process.env.JAVA_HOME
    ? join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java')
    : 'java');
let child;
let server;
let logs = '';
let ownedBinding = false;
let stopping = false;

/** Reject invalid or overlapping ports rather than reusing any existing service. */
function checkedPort(value, fallback) {
  if (value !== undefined && !/^\d{4,5}$/.test(value))
    throw new Error('test ports must be decimal integers');
  const port = value === undefined ? fallback : Number(value);
  if (!Number.isSafeInteger(port) || port < 1024 || port > 65535)
    throw new Error('test port must be 1024..65535');
  return port;
}

/** Hold a temporary bind probe until both ports are known to be free, then release only our probes. */
async function ensureUnused() {
  const probes = [];
  try {
    for (const port of [apiPort, uiPort]) {
      const probe = createPortProbe();
      probes.push(probe);
      await new Promise((resolveReady, reject) => {
        probe.once('error', reject);
        probe.listen(port, '127.0.0.1', resolveReady);
      });
    }
  } finally {
    await Promise.all(
      probes.map(
        (probe) =>
          new Promise((done) => {
            if (!probe.listening) done();
            else probe.close(done);
          }),
      ),
    );
  }
}

/** Keep only bounded public fixture diagnostics and recognize the spawned process's actual server bind. */
function record(chunk) {
  logs = (logs + stripVTControlCharacters(chunk.toString())).slice(-16000);
  if (new RegExp(`Tomcat started on ports?:? ${apiPort}\\b`).test(logs)) ownedBinding = true;
}

/** Check readiness only after our live child reported its bind; never adopt an existing API. */
async function waitForOwnedApi() {
  for (let attempt = 0; attempt < 240; attempt += 1) {
    if (child.exitCode !== null || child.signalCode !== null)
      throw new Error('owned Java API exited during startup');
    if (ownedBinding) {
      const ready = await new Promise((done) => {
        const health = get(
          { hostname: '127.0.0.1', port: apiPort, path: '/actuator/health', timeout: 1000 },
          (response) => {
            let body = '';
            response.on('data', (part) => {
              body = (body + part.toString()).slice(0, 2048);
            });
            response.on('end', () => {
              try {
                done(response.statusCode === 200 && JSON.parse(body).status === 'UP');
              } catch {
                done(false);
              }
            });
          },
        );
        health.on('timeout', () => health.destroy());
        health.on('error', () => done(false));
      });
      if (ready) return;
    }
    await sleep(250);
  }
  throw new Error('owned Java API readiness timed out');
}

/** Serve only compiled files inside dist; proxy real API traffic to the spawned loopback Java process. */
function serve(incoming, outgoing) {
  if ((incoming.url?.length ?? 0) > 2048) {
    outgoing.writeHead(414);
    outgoing.end();
    return;
  }
  const pathname = new URL(incoming.url ?? '/', `http://127.0.0.1:${uiPort}`).pathname;
  if (pathname.startsWith('/api/') || pathname === '/actuator/health') {
    const upstream = request(
      {
        hostname: '127.0.0.1',
        port: apiPort,
        method: incoming.method,
        path: incoming.url,
        headers: { ...incoming.headers, host: `127.0.0.1:${apiPort}`, connection: 'close' },
      },
      (response) => {
        outgoing.writeHead(response.statusCode ?? 502, response.headers);
        response.pipe(outgoing);
      },
    );
    upstream.on('error', () => {
      if (!outgoing.headersSent) outgoing.writeHead(502);
      outgoing.end('Owned test API unavailable');
    });
    incoming.on('aborted', () => upstream.destroy());
    incoming.pipe(upstream);
    return;
  }
  if (incoming.method !== 'GET' && incoming.method !== 'HEAD') {
    outgoing.writeHead(405);
    outgoing.end();
    return;
  }
  let decoded;
  try {
    decoded = decodeURIComponent(pathname);
  } catch {
    outgoing.writeHead(400);
    outgoing.end();
    return;
  }
  const file = resolve(dist, decoded === '/' ? 'index.html' : `.${decoded}`);
  const offset = relative(dist, file);
  if (
    offset.startsWith('..') ||
    isAbsolute(offset) ||
    !existsSync(file) ||
    !statSync(file).isFile()
  ) {
    outgoing.writeHead(404);
    outgoing.end();
    return;
  }
  const mime = {
    '.html': 'text/html; charset=utf-8',
    '.js': 'text/javascript; charset=utf-8',
    '.css': 'text/css; charset=utf-8',
    '.svg': 'image/svg+xml',
    '.json': 'application/json',
  };
  outgoing.writeHead(200, {
    'Content-Type': mime[extname(file)] ?? 'application/octet-stream',
    'X-Content-Type-Options': 'nosniff',
    'Cache-Control': 'no-store',
  });
  outgoing.end(incoming.method === 'HEAD' ? undefined : readFileSync(file));
}

/** Shut down only handles created by this launcher; no global process or port cleanup is performed. */
async function stop(code) {
  if (stopping) return;
  stopping = true;
  server?.closeAllConnections();
  server?.close();
  if (child && child.exitCode === null && child.signalCode === null) {
    child.kill('SIGTERM');
    for (
      let attempt = 0;
      attempt < 20 && child.exitCode === null && child.signalCode === null;
      attempt += 1
    )
      await sleep(100);
    if (child.exitCode === null && child.signalCode === null) child.kill('SIGKILL');
  }
  process.exit(code);
}

/** Launch a new in-memory database and require actual generated assets/JAR before accepting browser traffic. */
async function main() {
  if (apiPort === uiPort) throw new Error('API and UI ports must differ');
  if (!existsSync(jar) || !existsSync(join(dist, 'index.html')))
    throw new Error('Build backend JAR and frontend/dist before running browser tests');
  await ensureUnused();
  const excludedEnvironment = new Set([
    'JAVA_TOOL_OPTIONS',
    '_JAVA_OPTIONS',
    'JDK_JAVA_OPTIONS',
    'SPRING_APPLICATION_JSON',
    'SPRING_CONFIG_LOCATION',
    'SPRING_CONFIG_ADDITIONAL_LOCATION',
    'SPRING_PROFILES_ACTIVE',
    'MANAGEMENT_SERVER_ADDRESS',
  ]);
  const environment = Object.fromEntries(
    Object.entries(process.env).filter(([key]) => !excludedEnvironment.has(key)),
  );
  child = spawn(
    java,
    [
      '-jar',
      jar,
      '--server.address=127.0.0.1',
      `--server.port=${apiPort}`,
      '--spring.config.location=classpath:/application.yml',
      '--spring.profiles.active=browser-fixture',
      `--spring.datasource.url=jdbc:h2:mem:browser_${process.pid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1`,
      '--spring.datasource.username=sa',
      '--spring.datasource.password=',
      '--spring.datasource.hikari.maximum-pool-size=4',
      `--operations.allowed-origin=http://127.0.0.1:${uiPort}`,
      `--management.server.port=${apiPort}`,
    ],
    { cwd: frontend, env: environment, stdio: ['ignore', 'pipe', 'pipe'], windowsHide: true },
  );
  child.stdout.on('data', record);
  child.stderr.on('data', record);
  child.once('error', (error) => {
    console.error(`Owned Java process failed: ${error.message}`);
    void stop(1);
  });
  child.once('exit', () => {
    if (!stopping) {
      console.error(`Owned Java API exited.\n${logs}`);
      void stop(1);
    }
  });
  await waitForOwnedApi();
  server = createServer(serve);
  await new Promise((ready, reject) => {
    server.once('error', reject);
    server.listen(uiPort, '127.0.0.1', ready);
  });
  console.log(
    `Owned browser fixture ready: UI 127.0.0.1:${uiPort}, Spring/H2 127.0.0.1:${apiPort}`,
  );
}
process.on('SIGTERM', () => {
  void stop(0);
});
process.on('SIGINT', () => {
  void stop(0);
});
void main().catch((error) => {
  console.error(`${error.message}\n${logs}`);
  void stop(1);
});
