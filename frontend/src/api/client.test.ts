import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, request } from './client';
import { memoryStorage } from './testStorage';
import { clearSession, getToken, setToken } from './session';

beforeEach(() => { vi.stubGlobal('localStorage', memoryStorage()); });
afterEach(() => { vi.unstubAllGlobals(); });

describe('HTTP client contract', () => {
  it('sends camelCase JSON and participant Bearer only on authenticated requests', async () => {
    setToken('private-token');
    const fetcher = vi.fn().mockImplementation(() => Promise.resolve(new Response('{"id":"p1"}')));
    vi.stubGlobal('fetch', fetcher);
    await expect(request('/me', { auth: 'participant' })).resolves.toEqual({ id: 'p1' });
    const [url, options] = fetcher.mock.calls[0];
    expect(url).toBe('/api/me'); expect(options.headers.get('Authorization')).toBe('Bearer private-token');
    await request('/participants', { method: 'POST', body: { displayName: 'Lena', inviteCode: null } });
    const publicOptions = fetcher.mock.calls[1][1];
    expect(publicOptions.headers.has('Authorization')).toBe(false);
    expect(publicOptions.headers.get('Content-Type')).toBe('application/json');
    expect(JSON.parse(publicOptions.body)).toEqual({ displayName: 'Lena', inviteCode: null });
  });
  it('maps a 409 error envelope and field details to ApiError', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ error: { code: 'INVITE_USED', message: 'Already used.', details: [{ field: 'inviteCode', message: 'Choose another code.' }] } }), { status: 409 })));
    await expect(request('/participants', { method: 'POST' })).rejects.toMatchObject({ status: 409, code: 'INVITE_USED', message: 'Already used.', details: [{ field: 'inviteCode', message: 'Choose another code.' }] });
  });
  it('supports 204 responses without parsing JSON', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })));
    await expect(request('/me', { method: 'DELETE', auth: 'participant' })).resolves.toBeUndefined();
  });
  it('clears participant credentials on 401, including malformed responses', async () => {
    setToken('invalid');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('not json', { status: 401 })));
    await expect(request('/me', { auth: 'participant' })).rejects.toBeInstanceOf(ApiError);
    expect(getToken()).toBeNull();
  });
  it('sends admin credentials without participant credentials and preserves session on admin 401', async () => {
    setToken('participant-token');
    const fetcher = vi.fn().mockResolvedValue(new Response('{"error":{"code":"UNAUTHORIZED","message":"Invalid key"}}', { status: 401 }));
    vi.stubGlobal('fetch', fetcher);
    await expect(request('/admin/seed', { method: 'POST', auth: 'admin', adminKey: 'wrong' })).rejects.toMatchObject({ status: 401 });
    const headers = fetcher.mock.calls[0][1].headers as Headers;
    expect(headers.get('X-Admin-Key')).toBe('wrong'); expect(headers.has('Authorization')).toBe(false); expect(getToken()).toBe('participant-token');
  });
  it('reports network failures without changing to mock transport', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')));
    await expect(request('/meta')).rejects.toMatchObject({ status: 0, code: 'NETWORK' });
  });
  it('rejects non-JSON successful responses', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('<html>proxy</html>')));
    await expect(request('/meta')).rejects.toMatchObject({ code: 'INVALID_RESPONSE' });
  });
  it('clears only its own session key', () => {
    localStorage.setItem('unrelated', 'keep'); setToken('token'); clearSession();
    expect(getToken()).toBeNull(); expect(localStorage.getItem('unrelated')).toBe('keep');
  });
});
