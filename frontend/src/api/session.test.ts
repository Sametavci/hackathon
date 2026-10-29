import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { clearSession, getAdminKey, getToken, restoreUrl, setAdminKey, setToken, subscribeSession } from './session';
import { memoryStorage } from './testStorage';
beforeEach(() => {
  vi.stubGlobal('localStorage', memoryStorage());
  vi.stubGlobal('sessionStorage', memoryStorage());
  vi.stubGlobal('window', Object.assign(new EventTarget(), { location: { origin: 'https://lunch.test', pathname: '/' } }));
});
afterEach(() => vi.unstubAllGlobals());
it('notifies protected screens about local changes and cross-tab identity changes', () => {
  const listener = vi.fn(); const unsubscribe = subscribeSession(listener);
  setToken('one'); expect(listener).toHaveBeenCalledTimes(1); expect(getToken()).toBe('one');
  window.dispatchEvent(Object.assign(new Event('storage'), { key: 'unrelated' })); expect(listener).toHaveBeenCalledTimes(1);
  window.dispatchEvent(Object.assign(new Event('storage'), { key: 'lunch2.token' })); expect(listener).toHaveBeenCalledTimes(2);
  window.dispatchEvent(Object.assign(new Event('storage'), { key: null })); expect(listener).toHaveBeenCalledTimes(3);
  clearSession(); expect(listener).toHaveBeenCalledTimes(4); expect(restoreUrl()).toBeNull();
  unsubscribe(); setToken('two'); expect(listener).toHaveBeenCalledTimes(4);
});
it('builds a private return link on the current origin and keeps admin credentials in sessionStorage', () => {
  setToken('test/token'); expect(restoreUrl()).toBe('https://lunch.test/#/back?t=test%2Ftoken');
  setAdminKey('admin'); expect(getAdminKey()).toBe('admin'); expect(localStorage.getItem('lunch2.adminKey')).toBeNull();
});
