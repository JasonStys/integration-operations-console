/**
 * File: desktop.test.ts
 * Purpose: Check fragment removal, tab reload, storage denial and exact-origin session confinement.
 * State: synthetic launch values only; declaration locations: docs/code-index.md.
 */
// @vitest-environment-options {"url":"http://127.0.0.1:18312/"}
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const token = 'b'.repeat(64);

beforeEach(() => {
  vi.resetModules();
  window.sessionStorage.clear();
  window.history.replaceState(null, '', '/');
});
afterEach(() => vi.restoreAllMocks());

describe('native launch boundary', () => {
  it('consumes the fragment and keeps the session only for its exact origin', async () => {
    window.history.replaceState(null, '', '/?view=jobs#ioc=' + token);
    const desktop = await import('./desktop');
    expect(window.location.hash).toBe('');
    expect(window.location.search).toBe('?view=jobs');
    expect(desktop.isDesktopSession()).toBe(true);
    expect(desktop.desktopHeaders()).toEqual({ 'X-IOC-Session': token });
    expect(desktop.desktopHeaders('http://127.0.0.1:18312/')).toEqual({ 'X-IOC-Session': token });
    for (const foreign of [
      'https://example.test',
      'http://localhost:18312',
      'http://127.0.0.1:18313',
    ])
      expect(() => desktop.desktopHeaders(foreign)).toThrow('another service');
  });

  it('restores a valid tab session across reload and rejects malformed stored values', async () => {
    window.sessionStorage.setItem('ioc-local-launch', token);
    const desktop = await import('./desktop');
    expect(desktop.desktopHeaders()).toEqual({ 'X-IOC-Session': token });
    window.sessionStorage.setItem('ioc-local-launch', 'invalid');
    desktop.initializeDesktopSession();
    expect(desktop.isDesktopSession()).toBe(false);
    expect(desktop.desktopHeaders()).toEqual({});
  });

  it('keeps a new session in memory when browser storage is denied', async () => {
    window.history.replaceState(null, '', '/#ioc=' + token);
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('denied');
    });
    const desktop = await import('./desktop');
    expect(desktop.desktopHeaders()).toEqual({ 'X-IOC-Session': token });
    expect(window.location.hash).toBe('');
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('denied');
    });
    desktop.initializeDesktopSession();
    expect(desktop.isDesktopSession()).toBe(false);
  });

  it('does not treat arbitrary fragments as a native session', async () => {
    window.history.replaceState(null, '', '/#ioc=short');
    const desktop = await import('./desktop');
    expect(desktop.isDesktopSession()).toBe(false);
    expect(window.location.hash).toBe('#ioc=short');
  });
});
