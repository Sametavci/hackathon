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
  await api.getGroup('group'); await api.getCards('group'); await api.getConnections('group');
  await api.setConnection('group', 'person', 'YES'); await api.shareContact('group', 'person', 'discord demo'); await api.withdrawContact('group', 'person');
  await api.seedDemo('key'); await api.runMatch('key'); await api.getStats('key');
  expect(fetcher.mock.calls.map(([path, options]) => [options.method, path])).toEqual([
    ['GET', '/api/meta'], ['POST', '/api/participants'], ['GET', '/api/me'], ['DELETE', '/api/me'], ['POST', '/api/me/invite'], ['GET', '/api/invites/CODE'], ['POST', '/api/me/friend/unlink'],
    ['GET', '/api/groups/group'], ['GET', '/api/groups/group/cards'], ['GET', '/api/groups/group/connections'], ['PUT', '/api/groups/group/connections/person'], ['PUT', '/api/groups/group/connections/person/contact'], ['DELETE', '/api/groups/group/connections/person/contact'],
    ['POST', '/api/admin/seed'], ['POST', '/api/admin/match'], ['GET', '/api/admin/stats'],
  ]);
  expect(JSON.parse(fetcher.mock.calls[10][1].body)).toEqual({ choice: 'YES' });
  expect(JSON.parse(fetcher.mock.calls[11][1].body)).toEqual({ contact: 'discord demo' });
  expect(JSON.parse(fetcher.mock.calls[1][1].body).inviteCode).toBeNull();
});
it('encodes dynamic path segments', async () => {
  const fetcher = vi.fn().mockResolvedValue(new Response('{}')); vi.stubGlobal('fetch', fetcher);
  await api.getInvite('a/b?x'); expect(fetcher.mock.calls[0][0]).toBe('/api/invites/a%2Fb%3Fx');
});
it('blocks reset without sending an undefined-contract mutation', async () => {
  const fetcher = vi.fn(); vi.stubGlobal('fetch', fetcher);
  await expect(api.resetDemo()).rejects.toMatchObject({ code: 'CONTRACT_PENDING' }); expect(fetcher).not.toHaveBeenCalled();
});
