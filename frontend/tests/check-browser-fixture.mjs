/**
 * File: check-browser-fixture.mjs
 * Purpose: Prove the real browser launcher refuses invalid/shared ports without adopting or killing a service.
 * Symbols: runGuard and main; temporary listener/child handles are owned by this validation only.
 * Variables: response nonce is public synthetic evidence; no Java/database starts on rejected configuration.
 */
import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import console from 'node:console';
import { createServer, get } from 'node:http';
import { dirname, resolve } from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';
const directory = resolve(dirname(fileURLToPath(import.meta.url)), '..');

/** Execute the actual fixture launcher as a separate process with bounded diagnostics/time. */
function runGuard(overrides) {
  return new Promise((done) => {
    execFile(
      process.execPath,
      ['tests/browser-server.mjs'],
      {
        cwd: directory,
        env: { ...process.env, ...overrides },
        timeout: 10000,
        maxBuffer: 24000,
        windowsHide: true,
      },
      (error, stdout, stderr) => done({ error, output: stdout + stderr }),
    );
  });
}
/** Refuse malformed/overlapping ports and preserve an already bound test-owned HTTP service. */
async function main() {
  const invalid = await runGuard({ IOC_E2E_API_PORT: 'bad-port' });
  assert(invalid.error);
  assert.match(invalid.output, /test ports must be decimal integers/);
  const same = await runGuard({ IOC_E2E_API_PORT: '18962', IOC_E2E_UI_PORT: '18962' });
  assert(same.error);
  assert.match(same.output, /API and UI ports must differ/);
  const listener = createServer((_request, response) => response.end('owned-fixture-collision'));
  await new Promise((ready) => listener.listen(0, '127.0.0.1', ready));
  try {
    const address = listener.address();
    assert(address && typeof address === 'object');
    const occupied = await runGuard({
      IOC_E2E_API_PORT: String(address.port),
      IOC_E2E_UI_PORT: address.port === 65534 ? '65533' : '65534',
    });
    assert(occupied.error);
    assert.match(occupied.output, /EADDRINUSE/);
    assert.doesNotMatch(occupied.output, /Tomcat initialized|Owned browser fixture ready/);
    const body = await new Promise((done, reject) => {
      const request = get(
        { hostname: '127.0.0.1', port: address.port, path: '/', timeout: 2000 },
        (response) => {
          let value = '';
          response.on('data', (chunk) => {
            value += chunk.toString();
          });
          response.on('end', () => done(value));
        },
      );
      request.on('error', reject);
      request.on('timeout', () => request.destroy(new Error('guard listener timeout')));
    });
    assert.equal(body, 'owned-fixture-collision');
  } finally {
    await new Promise((done) => listener.close(done));
  }
  console.log(
    'Browser fixture guards: malformed port, overlapping ports, occupied-port refusal and listener preservation passed.',
  );
}
await main();
