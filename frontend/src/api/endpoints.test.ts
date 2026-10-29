import { beforeEach, afterEach, expect, it, vi } from 'vitest';
import * as api from './endpoints';
import { memoryStorage } from './testStorage';
beforeEach(() => { vi.stubEnv('VITE_USE_MOCKS', 'false'); vi.stubGlobal('localStorage', memoryStorage()); });
afterEach(() => { vi.unstubAllEnvs(); vi.unstubAllGlobals(); });
it('uses the frozen endpoint paths, verbs and request bodies', async () => {
  const fetcher = vi.fn().mockImplementation(() => Promise.resolve(new Response('{}'))); vi.stubGlobal('fetch', fetcher);
  const registration = { displayName: 'Lena', category: 'DOMESTIC', campus: 'WUERZBURG', languages: ['en'], interests: ['travel'], slotIds: ['2026-11-03T12:15'], studyField: null, inviteCode: null, consent: true } as const;
  await api.getMeta(); await api.registerParticipant({ ...registration, languages: [...registration.languages], interests: [...registration.interests], slotIds: [...registration.slotIds] });
  await api.getMe(); await api.deleteMe(); await api.createInvite(); await api.getInvite('CODE'); await api.unlinkFriend();
  await api.getGroup('group'); await api.getCards('group'); await api.refreshCards('group'); await api.getConnections('group');
  await api.setConnection('group', 'person', 'YES'); await api.shareContact('group', 'person', 'discord demo'); await api.withdrawContact('group', 'person');
  await api.seedDemo('key'); await api.runMatch('key'); await api.getStats('key');
  expect(fetcher.mock.calls.map(([path, options]) => [options.method, path])).toEqual([
    ['GET', '/api/meta'], ['POST', '/api/participants'], ['GET', '/api/me'], ['DELETE', '/api/me'], ['POST', '/api/me/invite'], ['GET', '/api/invites/CODE'], ['POST', '/api/me/friend/unlink'],
    ['GET', '/api/groups/group'], ['GET', '/api/groups/group/cards'], ['POST', '/api/groups/group/cards/refresh'], ['GET', '/api/groups/group/connections'], ['PUT', '/api/groups/group/connections/person'], ['PUT', '/api/groups/group/connections/person/contact'], ['DELETE', '/api/groups/group/connections/person/contact'],
    ['POST', '/api/admin/seed'], ['POST', '/api/admin/match'], ['GET', '/api/admin/stats'],
  ]);
  expect(JSON.parse(fetcher.mock.calls[11][1].body)).toEqual({ choice: 'YES' });
  expect(JSON.parse(fetcher.mock.calls[12][1].body)).toEqual({ contact: 'discord demo' });
  expect(JSON.parse(fetcher.mock.calls[1][1].body).inviteCode).toBeNull();
});
it('encodes dynamic path segments', async () => {
  const fetcher = vi.fn().mockResolvedValue(new Response('{}')); vi.stubGlobal('fetch', fetcher);
  await api.getInvite('a/b?x'); expect(fetcher.mock.calls[0][0]).toBe('/api/invites/a%2Fb%3Fx');
});
it('opts into demo completion only when explicitly requested', async () => {
  const fetcher = vi.fn().mockImplementation(() => Promise.resolve(new Response('{}'))); vi.stubGlobal('fetch', fetcher);
  await api.runMatch('key', { fillWithDemo: true });
  expect(fetcher.mock.calls[0][0]).toBe('/api/admin/match?fillWithDemo=true');
  expect(fetcher.mock.calls[0][1].headers.get('X-Admin-Key')).toBe('key');
});
it('resets with the admin key, no participant credentials and an empty 204 response', async () => {
  localStorage.setItem('lunch2.token', 'participant-token');
  const fetcher = vi.fn().mockResolvedValue(new Response(null, { status: 204 })); vi.stubGlobal('fetch', fetcher);
  await expect(api.resetDemo('admin-secret')).resolves.toBeUndefined();
  const [path, options] = fetcher.mock.calls[0];
  expect(path).toBe('/api/admin/reset'); expect(options.method).toBe('POST');
  expect(options.headers.get('X-Admin-Key')).toBe('admin-secret');
  expect(options.headers.has('Authorization')).toBe(false); expect(options.body).toBeUndefined();
});
it('returns reset authorization failures instead of treating them as success', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ error: { code: 'UNAUTHORIZED', message: 'Invalid key.' } }), { status: 401 })));
  await expect(api.resetDemo('wrong')).rejects.toMatchObject({ status: 401, code: 'UNAUTHORIZED' });
});
it('routes reset through the mock API when mocks are enabled', async () => {
  vi.stubEnv('VITE_USE_MOCKS', 'true');
  const fetcher = vi.fn(); vi.stubGlobal('fetch', fetcher);
  await expect(api.resetDemo('dev-admin-key')).resolves.toBeUndefined();
  expect(fetcher).not.toHaveBeenCalled();
});
