import { describe, expect, it } from 'vitest';
import { buddySteps, hasBuddyPlan } from './buddyFlow';
import { mockMeta } from './mockData';
import type { InvitePreview } from './types';

const mensa = mockMeta.mensas![0];
const invite: InvitePreview = { code: 'DEMO', inviterDisplayName: 'Lena', campus: mensa.campus,
  valid: true, mensaId: mensa.id, slotIds: [mensa.slotIds[0]], inviterMatchStatus: 'WAITING' };

describe('buddy registration plan', () => {
  it('skips only campus and Mensa, keeping personal answers and time confirmation', () => {
    expect(hasBuddyPlan(invite, mockMeta)).toBe(true);
    expect(buddySteps(true)).toEqual([1, 2, 3, 4, 6, 7, 8]);
    expect(buddySteps(false)).toEqual([0, 1, 2, 3, 4, 5, 6, 7, 8]);
  });
  it('never inherits used, matched, missing or inconsistent plans', () => {
    for (const change of [{ valid: false }, { inviterMatchStatus: 'MATCHED' as const },
      { inviterMatchStatus: undefined }, { mensaId: undefined }, { slotIds: [] },
      { slotIds: ['unknown'] }, { campus: mensa.campus === 'WUERZBURG' ? 'SCHWEINFURT' as const : 'WUERZBURG' as const }]) {
      expect(hasBuddyPlan({ ...invite, ...change }, mockMeta)).toBe(false);
    }
  });
});
