import { describe, expect, it } from 'vitest';
import { buddyRegistrationPath, buddySteps, hasBuddyPlan, nextRegistrationStep } from './buddyFlow';
import { mockMeta } from './mockData';
import type { InvitePreview } from './types';

const mensa = mockMeta.mensas![0];
const invite: InvitePreview = { code: 'DEMO', inviterDisplayName: 'Lena', campus: mensa.campus,
  valid: true, mensaId: mensa.id, slotIds: [mensa.slotIds[0]], inviterMatchStatus: 'WAITING' };

describe('buddy registration plan', () => {
  it('skips only campus and Mensa, keeping personal answers and time confirmation', () => {
    expect(hasBuddyPlan(invite, mockMeta)).toBe(true);
    expect(buddySteps(true)).toEqual([7, 1, 2, 3, 4, 6, 8]);
    expect(buddySteps(false)).toEqual([0, 1, 2, 3, 4, 5, 6, 7, 8]);
  });
  it('asks for the buddy name first and visits every personal step before consent', () => {
    const visited = [buddySteps(true)[0]];
    while (visited[visited.length - 1] !== 8 && visited.length < 10) {
      visited.push(nextRegistrationStep(visited[visited.length - 1], true));
    }
    expect(visited).toEqual([7, 1, 2, 3, 4, 6, 8]);
    expect(nextRegistrationStep(7, false)).toBe(8);
    expect(nextRegistrationStep(4, false)).toBe(5);
  });
  it('preserves the invitation when explicitly starting a different participant', () => {
    expect(buddyRegistrationPath('ABC')).toBe('/register?invite=ABC');
    expect(buddyRegistrationPath('ABC', true)).toBe('/register?invite=ABC&new=1');
    expect(buddyRegistrationPath('a&b', true)).toBe('/register?invite=a%26b&new=1');
  });
  it('never inherits used, matched, missing or inconsistent plans', () => {
    for (const change of [{ valid: false }, { inviterMatchStatus: 'MATCHED' as const },
      { inviterMatchStatus: undefined }, { mensaId: undefined }, { slotIds: [] },
      { slotIds: ['unknown'] }, { campus: mensa.campus === 'WUERZBURG' ? 'SCHWEINFURT' as const : 'WUERZBURG' as const }]) {
      expect(hasBuddyPlan({ ...invite, ...change }, mockMeta)).toBe(false);
    }
  });
});
