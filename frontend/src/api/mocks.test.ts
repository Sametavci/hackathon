import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { mockRequest } from './mocks';
import { mockMeta } from './mockData';
import { getToken, setToken } from './session';
import { memoryStorage } from './testStorage';
import type { CardsResponse, ConnectionItem, GroupResponse, InvitePreview, InviteResponse, MatchRunResponse, MeResponse, RegistrationRequest, RegistrationResponse, SeedResponse, StatsResponse } from './types';
import classics from '../../../backend/src/main/resources/cards/icebreakers.en.json';
const auth = { auth: 'participant' } as const;
const admin = { auth: 'admin', adminKey: 'dev-admin-key', method: 'POST' } as const;
function signup(name: string, changes: Partial<RegistrationRequest> = {}) {
  return mockRequest<RegistrationResponse>('/participants', { method: 'POST', body: { displayName: name, category: 'DOMESTIC', campus: 'WUERZBURG', languages: ['en'], interests: ['travel'], slotIds: [mockMeta.slots[4].id], studyField: null, inviteCode: null, consent: true, ...changes } });
}
async function lunch() {
  const a = await signup('Lena'); const b = await signup('Sam');
  const c = await signup('Aiko', { category: 'INTERNATIONAL' }); const d = await signup('Omar', { category: 'INTERNATIONAL' });
  const matched = await mockRequest<MatchRunResponse>('/admin/match', admin);
  setToken(a.accessToken);
  return { a, b, c, d, group: matched.groupsCreated[0].groupId };
}
beforeEach(() => { vi.stubGlobal('localStorage', memoryStorage()); vi.stubGlobal('window', { location: { origin: 'https://lunch.test', pathname: '/' } }); });
afterEach(() => vi.unstubAllGlobals());

describe('stateful demo API', () => {
  it('assigns sequential tables across rounds and keeps the same number for all members', async () => {
    const first = await lunch();
    const second = await lunch();
    setToken(first.a.accessToken);
    expect((await mockRequest<GroupResponse>(`/groups/${first.group}`, auth)).location).toMatch(/ · Lunch² Table 1$/);
    setToken(second.a.accessToken);
    expect((await mockRequest<GroupResponse>(`/groups/${second.group}`, auth)).location).toMatch(/ · Lunch² Table 2$/);
    setToken(first.b.accessToken);
    expect((await mockRequest<GroupResponse>(`/groups/${first.group}`, auth)).location).toMatch(/ · Lunch² Table 1$/);
  });
  it('serves five stable classic cards and blocks non-members', async () => {
    const { group } = await lunch();
    const first = await mockRequest<CardsResponse>(`/groups/${group}/cards`, auth);
    expect(first.cards).toHaveLength(5);
    expect(first.cards.every(card => classics.includes(card.text))).toBe(true);
    expect(await mockRequest(`/groups/${group}/cards`, auth)).toEqual(first);
    const outsider = await signup('Outside');
    setToken(outsider.accessToken);
    await expect(mockRequest(`/groups/${group}/cards`, auth)).rejects.toMatchObject({ status: 403 });
  });

  it('keeps non-English mock cards in the shared language', async () => {
    const first = await signup('First', { languages: ['tr'] });
    for (const [index, category] of (['DOMESTIC', 'INTERNATIONAL', 'INTERNATIONAL'] as const).entries()) {
      await signup(`Person ${index}`, { category, languages: ['tr'] });
    }
    const result = await mockRequest<MatchRunResponse>('/admin/match', admin);
    expect(result.groupsCreated).toHaveLength(1);
    setToken(first.accessToken);
    const cards = await mockRequest<CardsResponse>(`/groups/${result.groupsCreated[0].groupId}/cards`, auth);
    expect(cards.language).toBe('tr');
    expect(cards.cards).toHaveLength(5);
    expect(cards.cards.every(card => !classics.includes(card.text))).toBe(true);
    expect(cards.cards[0].text).toBe('Kampüste bir günü senin için daha keyifli yapan nedir?');
  });
  it('registers, restores a token, matches 2+2 once, and returns member-safe group fields', async () => {
    const { a, group } = await lunch();
    expect(a.restoreUrl).toBe(`https://lunch.test/#/back?t=${a.accessToken}`); expect(a.accessToken).toMatch(/^[\w-]{43}$/);
    const me = await mockRequest<MeResponse>('/me', auth); expect(me.groupId).toBe(group); expect(me.waitingReason).toBeNull();
    const data = await mockRequest<GroupResponse>(`/groups/${group}`, auth);
    expect(data.members).toHaveLength(4); expect(data.members.filter(p => p.isMe)).toHaveLength(1);
    expect(data.members[0]).not.toHaveProperty('category'); expect(data.members[0]).not.toHaveProperty('languages'); expect(data.members[0]).not.toHaveProperty('accessToken');
    expect((await mockRequest<MatchRunResponse>('/admin/match', admin)).groupsCreated).toHaveLength(0);
  });
  it('never shows a one-sided YES or NO to the recipient and gates contact sharing', async () => {
    const { a, b, group } = await lunch(); const path = `/groups/${group}/connections`;
    await mockRequest(`${path}/${b.me.id}`, { ...auth, method: 'PUT', body: { choice: 'YES' } });
    setToken(b.accessToken);
    expect((await mockRequest<ConnectionItem[]>(path, auth)).find(p => p.participantId === a.me.id)).toMatchObject({ status: 'NONE', myChoice: null, theirContact: null });
    await mockRequest(`${path}/${a.me.id}`, { ...auth, method: 'PUT', body: { choice: 'NO' } });
    setToken(a.accessToken);
    expect((await mockRequest<ConnectionItem[]>(path, auth)).find(p => p.participantId === b.me.id)).toMatchObject({ status: 'WAITING_FOR_THEM', theirContact: null });
    await expect(mockRequest(`${path}/${b.me.id}/contact`, { ...auth, method: 'PUT', body: { contact: 'discord demo' } })).rejects.toMatchObject({ status: 409, code: 'NOT_MUTUAL' });
  });
  it('shares only after MUTUAL, withdraws contact, and removes own contact on NO', async () => {
    const { a, b, group } = await lunch(); const path = `/groups/${group}/connections`;
    await mockRequest(`${path}/${b.me.id}`, { ...auth, method: 'PUT', body: { choice: 'YES' } });
    setToken(b.accessToken); await mockRequest(`${path}/${a.me.id}`, { ...auth, method: 'PUT', body: { choice: 'YES' } });
    setToken(a.accessToken); await mockRequest(`${path}/${b.me.id}/contact`, { ...auth, method: 'PUT', body: { contact: 'discord demo' } });
    setToken(b.accessToken); expect((await mockRequest<ConnectionItem[]>(path, auth)).find(p => p.participantId === a.me.id)?.theirContact).toBe('discord demo');
    setToken(a.accessToken); await mockRequest(`${path}/${b.me.id}/contact`, { ...auth, method: 'DELETE' });
    setToken(b.accessToken); expect((await mockRequest<ConnectionItem[]>(path, auth)).find(p => p.participantId === a.me.id)?.theirContact).toBeNull();
    setToken(a.accessToken); await mockRequest(`${path}/${b.me.id}/contact`, { ...auth, method: 'PUT', body: { contact: 'discord demo' } });
    await mockRequest(`${path}/${b.me.id}`, { ...auth, method: 'PUT', body: { choice: 'NO' } });
    setToken(b.accessToken); expect((await mockRequest<ConnectionItem[]>(path, auth)).find(p => p.participantId === a.me.id)).toMatchObject({ status: 'WAITING_FOR_THEM', theirContact: null });
  });
  it('denies non-members and self-connections', async () => {
    const { group, a } = await lunch();
    await expect(mockRequest(`/groups/${group}/connections/${a.me.id}`, { ...auth, method: 'PUT', body: { choice: 'YES' } })).rejects.toMatchObject({ status: 400 });
    const outsider = await signup('Outside'); setToken(outsider.accessToken);
    await expect(mockRequest(`/groups/${group}`, auth)).rejects.toMatchObject({ status: 403 });
  });
  it('deletes a registration, revokes its token and leaves a tombstone', async () => {
    const { a, b, group } = await lunch(); await mockRequest('/me', { ...auth, method: 'DELETE' });
    await expect(mockRequest('/me', auth)).rejects.toMatchObject({ status: 401 }); expect(getToken()).toBeNull();
    setToken(b.accessToken); const data = await mockRequest<GroupResponse>(`/groups/${group}`, auth);
    expect(data.members.find(p => p.id === a.me.id)).toMatchObject({ displayName: 'Former participant', interests: [] });
  });
  it('preserves invitations for future integration: idempotency, preview, incompatibility and unlink', async () => {
    const a = await signup('Lena'); setToken(a.accessToken);
    const invite = await mockRequest<InviteResponse>('/me/invite', { ...auth, method: 'POST' });
    expect(await mockRequest('/me/invite', { ...auth, method: 'POST' })).toEqual(invite);
    expect(await mockRequest<InvitePreview>(`/invites/${invite.code}`)).toMatchObject({ valid: true, inviterDisplayName: 'Lena' });
    const b = await signup('Sam', { inviteCode: invite.code, campus: 'SCHWEINFURT' });
    expect(b.me.friend?.issue?.code).toBe('FRIEND_DIFFERENT_CAMPUS');
    await expect(signup('Again', { inviteCode: invite.code })).rejects.toMatchObject({ status: 409, code: 'INVITE_USED' });
    setToken(b.accessToken); await mockRequest('/me/friend/unlink', { ...auth, method: 'POST' });
    expect((await mockRequest<MeResponse>('/me', auth)).friend).toBeNull();
    setToken(a.accessToken); expect((await mockRequest<MeResponse>('/me', auth)).friend).toBeNull();
  });
  it('does not split an active friend pair to complete a group', async () => {
    const a = await signup('Lena'); setToken(a.accessToken);
    const invite = await mockRequest<InviteResponse>('/me/invite', { ...auth, method: 'POST' });
    await signup('Friend', { inviteCode: invite.code, slotIds: [mockMeta.slots[4].id, mockMeta.slots[5].id] });
    await signup('One', { category: 'INTERNATIONAL', slotIds: [mockMeta.slots[5].id] });
    await signup('Two', { category: 'INTERNATIONAL', slotIds: [mockMeta.slots[5].id] });
    await signup('Other', { slotIds: [mockMeta.slots[5].id] });
    expect((await mockRequest<MatchRunResponse>('/admin/match', admin)).groupsCreated).toHaveLength(0);
  });
  it('seeds idempotently and leaves live-demo slots waiting for two registrations', async () => {
    const first = await mockRequest<SeedResponse>('/admin/seed', admin);
    const second = await mockRequest<SeedResponse>('/admin/seed', admin);
    expect(second).toEqual(first); expect(first.participants).toHaveLength(6);
    const result = await mockRequest<MatchRunResponse>('/admin/match', admin);
    expect(result.groupsCreated).toHaveLength(1); expect(result.waiting).toHaveLength(2);
    await signup('Lena'); await signup('Sam');
    expect((await mockRequest<MatchRunResponse>('/admin/match', admin)).groupsCreated).toHaveLength(1);
    expect((await mockRequest<StatsResponse>('/admin/stats', admin)).matched).toBe(8);
  });
  it('returns no group when campus, slot or common language is incompatible', async () => {
    await signup('One'); await signup('Two', { campus: 'SCHWEINFURT' });
    await signup('Three', { category: 'INTERNATIONAL', languages: ['tr'] }); await signup('Four', { category: 'INTERNATIONAL' });
    expect((await mockRequest<MatchRunResponse>('/admin/match', admin)).groupsCreated).toHaveLength(0);
  });
  it('rejects missing participant and incorrect admin credentials', async () => {
    await expect(mockRequest('/me', auth)).rejects.toMatchObject({ status: 401 });
    await expect(mockRequest('/admin/seed', { ...admin, adminKey: 'wrong' })).rejects.toMatchObject({ status: 401 });
  });
  it('completes a single live registration with compatible demo students without changing the session', async () => {
    const live = await signup('Live student'); setToken(live.accessToken);
    expect((await mockRequest<MatchRunResponse>('/admin/match', admin)).groupsCreated).toHaveLength(0);
    const result = await mockRequest<MatchRunResponse>('/admin/match?fillWithDemo=true', admin);
    expect(result.groupsCreated).toHaveLength(1);
    expect(getToken()).toBe(live.accessToken);
    expect((await mockRequest<MeResponse>('/me', auth)).matchStatus).toBe('MATCHED');
    const group = await mockRequest<GroupResponse>(`/groups/${result.groupsCreated[0].groupId}`, auth);
    expect(group.members).toHaveLength(4);
    expect(group.members.some(p => p.id === live.me.id && p.isMe)).toBe(true);
    expect((await mockRequest<MatchRunResponse>('/admin/match?fillWithDemo=true', admin)).groupsCreated).toHaveLength(0);
  });
  it('uses compatible existing participants before adding missing fictional seats', async () => {
    const a = await signup('Live domestic');
    const b = await signup('Live international', { category: 'INTERNATIONAL' });
    const result = await mockRequest<MatchRunResponse>('/admin/match?fillWithDemo=true', admin);
    expect(result.groupsCreated).toHaveLength(1);
    expect((await mockRequest<StatsResponse>('/admin/stats', admin)).matched).toBe(4);
    setToken(a.accessToken);
    const group = await mockRequest<GroupResponse>(`/groups/${result.groupsCreated[0].groupId}`, auth);
    expect(group.members.map(p => p.id)).toEqual(expect.arrayContaining([a.me.id, b.me.id]));
  });
  it('resets all mock data, revokes old links and preserves unrelated storage and admin credentials', async () => {
    vi.stubGlobal('sessionStorage', memoryStorage());
    sessionStorage.setItem('lunch2.adminKey', 'dev-admin-key');
    localStorage.setItem('unrelated', 'keep');
    const { a, b, group } = await lunch();
    const invite = await mockRequest<InviteResponse>('/me/invite', { ...auth, method: 'POST' });
    await signup('Linked', { inviteCode: invite.code });
    const path = `/groups/${group}/connections`;
    await mockRequest(`${path}/${b.me.id}`, { ...auth, method: 'PUT', body: { choice: 'YES' } });
    setToken(b.accessToken);
    await mockRequest(`${path}/${a.me.id}`, { ...auth, method: 'PUT', body: { choice: 'YES' } });
    await mockRequest(`${path}/${a.me.id}/contact`, { ...auth, method: 'PUT', body: { contact: 'discord demo' } });
    await expect(mockRequest<void>('/admin/reset', admin)).resolves.toBeUndefined();
    const stats = await mockRequest<StatsResponse>('/admin/stats', admin);
    expect(stats).toEqual({ registrations: { DOMESTIC: 0, INTERNATIONAL: 0 }, byCampus: { WUERZBURG: 0, SCHWEINFURT: 0 },
      matched: 0, waiting: 0, matchRate: 0, groups: 0, friendPairs: 0, mutualConnections: 0 });
    expect(localStorage.getItem('unrelated')).toBe('keep');
    expect(sessionStorage.getItem('lunch2.adminKey')).toBe('dev-admin-key');
    for (const token of [a.accessToken, b.accessToken]) {
      setToken(token);
      await expect(mockRequest('/me', auth)).rejects.toMatchObject({ status: 401, code: 'UNAUTHORIZED' });
    }
    await expect(mockRequest(`/invites/${invite.code}`)).rejects.toMatchObject({ status: 404 });
    const fresh = await signup('Fresh'); setToken(fresh.accessToken);
    await expect(mockRequest(`/groups/${group}`, auth)).rejects.toMatchObject({ status: 404 });
    expect(fresh.me.friend).toBeNull();
  });
  it('rejects unauthorized resets without changing data', async () => {
    const a = await signup('Lena'); setToken(a.accessToken);
    const before = localStorage.getItem('lunch2.mock.v1');
    for (const options of [{ method: 'POST' } as const, { ...admin, adminKey: 'wrong' }]) {
      await expect(mockRequest('/admin/reset', options)).rejects.toMatchObject({ status: 401, code: 'UNAUTHORIZED' });
      expect(localStorage.getItem('lunch2.mock.v1')).toBe(before);
    }
    expect((await mockRequest<MeResponse>('/me', auth)).id).toBe(a.me.id);
  });
  it('supports repeated reset of an empty store and seeding again afterwards', async () => {
    await expect(mockRequest<void>('/admin/reset', admin)).resolves.toBeUndefined();
    await expect(mockRequest<void>('/admin/reset', admin)).resolves.toBeUndefined();
    const seeded = await mockRequest<SeedResponse>('/admin/seed', admin);
    expect(seeded.participants.length).toBeGreaterThan(0);
    expect((await mockRequest<StatsResponse>('/admin/stats', admin)).waiting).toBe(seeded.participants.length);
  });
});


it('persists Mensa choice and prevents matching across Mensas', async () => {
  const selected = { mensaId: 'WUERZBURG_JOSEF_SCHNEIDER', slotIds: ['2026-11-02T11:30'] };
  const a = await signup('One', selected);
  await signup('Two', selected);
  await signup('Three', { ...selected, category: 'INTERNATIONAL', mensaId: 'WUERZBURG_ROENTGENRING' });
  await signup('Four', { ...selected, category: 'INTERNATIONAL', mensaId: 'WUERZBURG_ROENTGENRING' });
  expect((await mockRequest<MatchRunResponse>('/admin/match', admin)).groupsCreated).toHaveLength(0);
  await signup('Five', { ...selected, category: 'INTERNATIONAL' });
  await signup('Six', { ...selected, category: 'INTERNATIONAL' });
  const result = await mockRequest<MatchRunResponse>('/admin/match', admin);
  expect(result.groupsCreated).toHaveLength(1);
  setToken(a.accessToken);
  expect((await mockRequest<MeResponse>('/me', auth)).mensaId).toBe(selected.mensaId);
  const group = await mockRequest<GroupResponse>(`/groups/${result.groupsCreated[0].groupId}`, auth);
  expect(group.location).toContain('Josef-Schneider-Straße 9');
});
it('keeps a friend pair at different Mensas waiting together', async () => {
  const a = await signup('One', { mensaId: 'WUERZBURG_ROENTGENRING', slotIds: ['2026-11-02T11:30'] });
  setToken(a.accessToken);
  const invite = await mockRequest<InviteResponse>('/me/invite', { ...auth, method: 'POST' });
  const b = await signup('Friend', { inviteCode: invite.code, mensaId: 'WUERZBURG_JOSEF_SCHNEIDER', slotIds: ['2026-11-02T11:30'] });
  expect(b.me.friend?.issue?.code).toBe('FRIEND_DIFFERENT_MENSA');
  const result = await mockRequest<MatchRunResponse>('/admin/match', admin);
  expect(result.groupsCreated).toHaveLength(0);
  expect(result.waiting.every(p => p.reasonCode === 'FRIEND_DIFFERENT_MENSA')).toBe(true);
});
