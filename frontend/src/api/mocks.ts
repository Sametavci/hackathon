import { ApiError, type RequestOptions } from './client';
import { clearSession, getToken } from './session';
import { defaultMensaId } from './mensaCatalog';
import { mockMeta, reasonMessages } from './mockData';
import classicQuestions from '../../../backend/src/main/resources/cards/icebreakers.en.json';
import translatedQuestions from '../../../backend/src/main/resources/cards/generic-translations.json';
import { validateRegistration } from './validation';
import type { CardsResponse, ConnectionItem, ConnectChoice, GroupResponse, InviteResponse, MatchRunResponse, MeResponse, ReasonCode, RegistrationRequest, RegistrationResponse, SeedResponse, StatsResponse } from './types';

interface Choice { groupId: string; from: string; to: string; choice: ConnectChoice; contact: string | null }
interface MockState {
  participants: Record<string, MeResponse>; tokens: Record<string, string>;
  groups: Record<string, GroupResponse>; choices: Choice[];
  invites: Record<string, { inviterId: string; usedById: string | null }>;
  pairs: [string, string][];
}
const STORE = 'lunch2.mock.v1';
const emptyState = (): MockState => ({ participants: {}, tokens: {}, groups: {}, choices: [], invites: {}, pairs: [] });
const fail = (status: number, code: string, message: string): never => { throw new ApiError(status, code, message); };
const pairOf = (state: MockState, id: string) => state.pairs.find(pair => pair.includes(id));
const mensaOf = (p: MeResponse) => p.mensaId ?? defaultMensaId(p.campus);
const common = (lists: string[][]): string[] => lists[0]?.filter(x => lists.every(list => list.includes(x))) ?? [];
function load(): MockState {
  const saved = localStorage.getItem(STORE);
  return saved ? JSON.parse(saved) as MockState : emptyState();
}
function save(state: MockState): void { localStorage.setItem(STORE, JSON.stringify(state)); }
function baseUrl(): string { return `${window.location.origin}${window.location.pathname}`; }
function reason(code: ReasonCode) { return { code, message: reasonMessages[code] }; }
function me(state: MockState, id: string): MeResponse {
  const participant = state.participants[id];
  const pair = pairOf(state, id);
  const friend = pair ? state.participants[pair.find(p => p !== id)!] : null;
  let issue: ReasonCode | null = null;
  if (friend) {
    if (friend.campus !== participant.campus) issue = 'FRIEND_DIFFERENT_CAMPUS';
    else if (mensaOf(friend) !== mensaOf(participant)) issue = 'FRIEND_DIFFERENT_MENSA';
    else if (!common([friend.slotIds, participant.slotIds]).length) issue = 'FRIEND_NO_COMMON_SLOT';
    else if (!common([friend.languages, participant.languages]).length) issue = 'FRIEND_NO_COMMON_LANGUAGE';
    else if (friend.groupId && friend.groupId !== participant.groupId) issue = 'INVITER_ALREADY_MATCHED';
  }
  const invite = Object.entries(state.invites).find(([, value]) => value.inviterId === id && !value.usedById);
  return { ...participant, mensaId: mensaOf(participant), friend: friend ? { displayName: friend.displayName, pairStatus: issue ? 'INCOMPATIBLE' : 'ACTIVE', issue: issue ? reason(issue) : null } : null,
    pendingInvite: invite ? { code: invite[0], url: `${baseUrl()}#/join/${invite[0]}` } : null };
}
function register(state: MockState, input: RegistrationRequest): RegistrationResponse {
  const errors = validateRegistration(input, mockMeta);
  if (errors.length) throw new ApiError(400, input.consent ? 'VALIDATION_FAILED' : 'CONSENT_REQUIRED', 'Please check the highlighted fields.', errors);
  const invite = input.inviteCode ? state.invites[input.inviteCode] : null;
  if (input.inviteCode && !invite) fail(404, 'INVITE_INVALID', 'This invitation does not exist.');
  if (invite?.usedById) fail(409, 'INVITE_USED', 'This invitation has already been used.');
  const id = crypto.randomUUID();
  const token = btoa(String.fromCharCode(...crypto.getRandomValues(new Uint8Array(32)))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '');
  state.participants[id] = { id, mensaId: input.mensaId ?? defaultMensaId(input.campus), displayName: input.displayName, category: input.category, campus: input.campus, languages: input.languages, interests: input.interests, slotIds: input.slotIds, studyField: input.studyField, matchStatus: 'WAITING', groupId: null, waitingReason: reason('WAITING_FOR_NEXT_ROUND'), friend: null, pendingInvite: null };
  state.tokens[token] = id;
  if (invite) { invite.usedById = id; state.pairs.push([invite.inviterId, id]); }
  return { accessToken: token, restoreUrl: `${baseUrl()}#/back?t=${token}`, me: me(state, id) };
}
function memberGroup(state: MockState, groupId: string, id: string): GroupResponse {
  const group = state.groups[groupId];
  if (!group) return fail(404, 'NOT_FOUND', 'This lunch group could not be found.');
  if (!group.members.some(member => member.id === id)) return fail(403, 'FORBIDDEN', 'You are not a member of this lunch group.');
  return group;
}
function connection(state: MockState, groupId: string, from: string, to: string): ConnectionItem {
  const group = memberGroup(state, groupId, from);
  if (from === to) fail(400, 'VALIDATION_FAILED', 'Choose another group member.');
  const person = group.members.find(p => p.id === to);
  if (!person) return fail(403, 'FORBIDDEN', 'This person is not in your group.');
  const mine = state.choices.find(c => c.groupId === groupId && c.from === from && c.to === to);
  const theirs = state.choices.find(c => c.groupId === groupId && c.from === to && c.to === from);
  const mutual = mine?.choice === 'YES' && theirs?.choice === 'YES';
  return { participantId: to, displayName: state.participants[to]?.displayName ?? 'Former participant', myChoice: mine?.choice ?? null, status: mutual ? 'MUTUAL' : mine?.choice === 'YES' ? 'WAITING_FOR_THEM' : 'NONE', myContact: mutual ? mine?.contact ?? null : null, theirContact: mutual ? theirs?.contact ?? null : null };
}
function match(state: MockState): MatchRunResponse {
  // Small deterministic demo matcher, not the backend scoring engine.
  const result: MatchRunResponse = { groupsCreated: [], waiting: [] };
  for (const campus of mockMeta.campuses) for (const mensa of (mockMeta.mensas ?? []).filter(m => m.campus === campus.id)) for (const slot of mockMeta.slots) {
    let found = true;
    while (found) {
      found = false;
      const pool = Object.values(state.participants).filter(p => p.matchStatus === 'WAITING' && p.campus === campus.id && mensaOf(p) === mensa.id && p.slotIds.includes(slot.id) && me(state, p.id).friend?.pairStatus !== 'INCOMPATIBLE');
      const domestic = pool.filter(p => p.category === 'DOMESTIC');
      const international = pool.filter(p => p.category === 'INTERNATIONAL');
      search: for (let a = 0; a < domestic.length; a++) for (let b = a + 1; b < domestic.length; b++) for (let c = 0; c < international.length; c++) for (let d = c + 1; d < international.length; d++) {
        const people = [domestic[a], domestic[b], international[c], international[d]];
        const ids = people.map(p => p.id);
        const languages = common(people.map(p => p.languages));
        if (!languages.length || people.some(p => pairOf(state, p.id)?.some(id => !ids.includes(id)))) continue;
        const interests = mockMeta.interests.map(i => i.id).filter(id => people.filter(p => p.interests.includes(id)).length >= 2).sort((x, y) => people.filter(p => p.interests.includes(y)).length - people.filter(p => p.interests.includes(x)).length || x.localeCompare(y, 'en'));
        const groupId = crypto.randomUUID();
        state.groups[groupId] = { id: groupId, campus: campus.id, slot, startsAt: `${slot.date}T${slot.start}:00+01:00`, location: `${mensa.name} · ${mensa.address}`,
          members: people.map(p => ({ id: p.id, displayName: p.displayName, interests: p.interests, isMe: false, isMyFriend: false })), sharedInterests: interests, commonLanguages: languages,
          whyThisGroup: ['A lunch slot that works for all four of you.', `Everyone speaks ${mockMeta.languages.find(l => l.code === languages[0])?.label ?? languages[0]}.`] };
        people.forEach(p => { p.matchStatus = 'MATCHED'; p.groupId = groupId; p.waitingReason = null; });
        result.groupsCreated.push({ groupId, campus: campus.id, slotId: slot.id, memberNames: people.map(p => p.displayName), score: 0 });
        found = true; break search;
      }
    }
  }
  for (const p of Object.values(state.participants).filter(p => p.matchStatus === 'WAITING')) {
    const candidates = Object.values(state.participants).filter(other => other.matchStatus === 'WAITING' && other.campus === p.campus && mensaOf(other) === mensaOf(p) && common([other.slotIds, p.slotIds]).length);
    const code = me(state, p.id).friend?.issue?.code ?? (candidates.filter(x => x.category === 'DOMESTIC').length < 2 ? 'NOT_ENOUGH_DOMESTIC' : candidates.filter(x => x.category === 'INTERNATIONAL').length < 2 ? 'NOT_ENOUGH_INTERNATIONAL' : 'NO_COMMON_LANGUAGE');
    p.waitingReason = reason(code);
    result.waiting.push({ participantId: p.id, displayName: p.displayName, reasonCode: code });
  }
  return result;
}
export async function mockRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const state = load();
  const method = options.method ?? 'GET';
  let id = '';
  if (options.auth === 'admin' && options.adminKey !== 'dev-admin-key') fail(401, 'UNAUTHORIZED', 'The admin key is invalid.');
  if (options.auth === 'participant') {
    id = state.tokens[getToken() ?? ''] ?? '';
    if (!state.participants[id]) { clearSession(); fail(401, 'UNAUTHORIZED', 'Your session has expired. Please register or use your saved return link.'); }
  }
  let output: unknown;
  if (path === '/meta') output = mockMeta;
  else if (path === '/participants' && method === 'POST') output = register(state, options.body as RegistrationRequest);
  else if (path === '/me' && method === 'GET') output = me(state, id);
  else if (path === '/me' && method === 'DELETE') {
    delete state.participants[id];
    for (const [token, person] of Object.entries(state.tokens)) if (person === id) delete state.tokens[token];
    for (const [code, invite] of Object.entries(state.invites)) if (invite.inviterId === id) delete state.invites[code];
    state.pairs = state.pairs.filter(pair => !pair.includes(id));
    state.choices = state.choices.filter(c => c.from !== id && c.to !== id);
    for (const group of Object.values(state.groups)) group.members = group.members.map(p => p.id === id ? { ...p, displayName: 'Former participant', interests: [] } : p);
  } else if (path === '/me/invite' && method === 'POST') {
    if (pairOf(state, id)) fail(409, 'ALREADY_PAIRED', 'You already have a friend linked.');
    const current = me(state, id).pendingInvite;
    if (current) output = current;
    else {
      const alphabet = '0123456789ABCDEFGHJKMNPQRSTVWXYZ';
      const code = Array.from(crypto.getRandomValues(new Uint8Array(10)), n => alphabet[n % 32]).join('');
      state.invites[code] = { inviterId: id, usedById: null };
      output = { code, url: `${baseUrl()}#/join/${code}` } satisfies InviteResponse;
    }
  } else if (path.startsWith('/invites/')) {
    const code = decodeURIComponent(path.split('/')[2]);
    const invite = state.invites[code];
    if (!invite) fail(404, 'INVITE_INVALID', 'This invitation could not be found.');
    const inviter = state.participants[invite.inviterId];
    output = { code, inviterDisplayName: inviter.displayName, campus: inviter.campus, valid: !invite.usedById };
  } else if (path === '/me/friend/unlink') { state.pairs = state.pairs.filter(pair => !pair.includes(id)); output = me(state, id); }
  else if (path === '/admin/seed') {
    // Seed once; never overwrite live registrations or invalidate return links.
    const existing = Object.values(state.participants).filter(p => p.displayName.endsWith(' demo'));
    const people = existing.length ? existing : ['Mia', 'Ben', 'Noor', 'Kai', 'Aiko', 'Omar'].map((name, index) => register(state, {
      displayName: `${name} demo`, category: index < 2 ? 'DOMESTIC' : 'INTERNATIONAL', campus: 'WUERZBURG', languages: ['en', 'de'], interests: ['travel', 'gaming'], slotIds: [index < 4 ? '2026-11-02T11:30' : '2026-11-03T12:15'], studyField: null, inviteCode: null, consent: true,
    }).me);
    output = { participants: people.map(p => { const token = Object.keys(state.tokens).find(token => state.tokens[token] === p.id)!; return { id: p.id, displayName: p.displayName, campus: p.campus, category: p.category, restoreUrl: `${baseUrl()}#/back?t=${token}` }; }) } satisfies SeedResponse;
  } else if (path === '/admin/match') output = match(state);
  else if (path === '/admin/stats') {
    const people = Object.values(state.participants); const matched = people.filter(p => p.matchStatus === 'MATCHED').length;
    output = { registrations: { DOMESTIC: people.filter(p => p.category === 'DOMESTIC').length, INTERNATIONAL: people.filter(p => p.category === 'INTERNATIONAL').length }, byCampus: { WUERZBURG: people.filter(p => p.campus === 'WUERZBURG').length, SCHWEINFURT: people.filter(p => p.campus === 'SCHWEINFURT').length }, matched, waiting: people.length - matched, matchRate: people.length ? matched / people.length : 0, groups: Object.keys(state.groups).length, friendPairs: state.pairs.length, mutualConnections: state.choices.filter(c => connection(state, c.groupId, c.from, c.to).status === 'MUTUAL').length / 2 } satisfies StatsResponse;
  } else if (path.startsWith('/groups/')) {
    const [, , groupId, sub, target, contact] = path.split('/');
    const group = memberGroup(state, groupId, id);
    if (!sub) output = { ...group, members: group.members.map(p => ({ ...p, isMe: p.id === id, isMyFriend: pairOf(state, id)?.includes(p.id) === true && p.id !== id })) };
    else if (sub === 'cards') {
      const language = group.commonLanguages.includes('en') ? 'en' : group.commonLanguages[0];
      const banks: Readonly<Record<string, readonly string[]>> = translatedQuestions;
      const questions = language === 'en' ? classicQuestions : banks[language] ?? classicQuestions;
      output = { groupId, language, source: 'FALLBACK', cards: questions.slice(0, 5).map((text, i) => ({ id: `c${i + 1}`, text, topic: 'generic' })) } satisfies CardsResponse;
    }
    else if (sub === 'connections') {
      if (!target) output = group.members.filter(p => p.id !== id).map(p => connection(state, groupId, id, p.id));
      else {
        const current = connection(state, groupId, id, target);
        let choice = state.choices.find(c => c.groupId === groupId && c.from === id && c.to === target);
        if (contact === 'contact') {
          if (method === 'PUT') {
            if (current.status !== 'MUTUAL') fail(409, 'NOT_MUTUAL', 'Contact sharing is available after you both choose to connect.');
            const value = (options.body as { contact: string }).contact.trim();
            if (!value || value.length > 100) fail(400, 'VALIDATION_FAILED', 'Enter between 1 and 100 characters.');
            choice!.contact = value;
          } else if (method === 'DELETE' && choice) choice.contact = null;
        } else if (method === 'PUT') {
          const value = (options.body as { choice: ConnectChoice }).choice;
          if (value !== 'YES' && value !== 'NO') fail(400, 'VALIDATION_FAILED', 'Choose YES or NO.');
          if (!choice) { choice = { groupId, from: id, to: target, choice: value, contact: null }; state.choices.push(choice); }
          choice.choice = value;
          if (value === 'NO') choice.contact = null;
        }
        output = connection(state, groupId, id, target);
      }
    } else fail(404, 'NOT_FOUND', 'This page could not be found.');
  } else fail(404, 'NOT_FOUND', 'This endpoint is not available.');
  save(state);
  return structuredClone(output) as T;
}
