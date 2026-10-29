import type { InvitePreview, MetaResponse } from './types';

// Older servers can preview invites, but cannot safely skip plan selection.
export function hasBuddyPlan(invite: InvitePreview, meta: MetaResponse): boolean {
  const mensa = meta.mensas?.find(m => m.id === invite.mensaId && m.campus === invite.campus);
  return invite.valid && invite.inviterMatchStatus === 'WAITING' && !!mensa &&
    !!invite.slotIds?.length && invite.slotIds.every(id => mensa.slotIds.includes(id) && meta.slots.some(s => s.id === id));
}

export function buddySteps(inheritedPlan: boolean): number[] {
  return inheritedPlan ? [1, 2, 3, 4, 6, 7, 8] : [0, 1, 2, 3, 4, 5, 6, 7, 8];
}
