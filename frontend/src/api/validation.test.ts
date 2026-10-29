import { describe, expect, it } from 'vitest';
import { mockMeta } from './mockData';
import { validateRegistration } from './validation';
import type { RegistrationRequest } from './types';
const valid: RegistrationRequest = { displayName: 'İpek', category: 'DOMESTIC', campus: 'WUERZBURG', languages: ['en'], interests: ['travel'], slotIds: [mockMeta.slots[0].id], studyField: null, inviteCode: null, consent: true };
describe('registration validation', () => {
  it('accepts Unicode nicknames and nullable optional fields', () => { expect(validateRegistration(valid, mockMeta)).toEqual([]); });
  it.each(['a', 'a@b.de', '123+456', 'A'.repeat(31)])('rejects an invalid nickname: %s', displayName => { expect(validateRegistration({ ...valid, displayName }, mockMeta)).toContainEqual(expect.objectContaining({ field: 'displayName' })); });
  it('enforces consent and required selections', () => {
    const errors = validateRegistration({ ...valid, consent: false, languages: [], interests: [], slotIds: [] }, mockMeta);
    expect(errors.map(e => e.field).sort()).toEqual(['consent', 'interests', 'languages', 'slotIds']);
  });
  it('rejects unknown, duplicate and over-limit catalog selections', () => {
    const errors = validateRegistration({ ...valid, languages: ['en', 'de', 'tr', 'fr', 'ar'], interests: ['travel', 'travel'], slotIds: ['invalid'], studyField: 'UNKNOWN' }, mockMeta);
    expect(errors.map(e => e.field).sort()).toEqual(['interests', 'languages', 'slotIds', 'studyField']);
  });
});
