import type { InvitePreview, MetaResponse } from './types';

// Older servers can preview invites, but cannot safely skip plan selection.
export function hasBuddyPlan(invite: InvitePreview, meta: MetaResponse): boolean {
  const mensa = meta.mensas?.find(m => m.id === invite.mensaId && m.campus === invite.campus);
  return invite.valid && invite.inviterMatchStatus === 'WAITING' && !!mensa &&
    !!invite.slotIds?.length && invite.slotIds.every(id => mensa.slotIds.includes(id) && meta.slots.some(s => s.id === id));
}

export function buddySteps(inheritedPlan: boolean): number[] {
  return inheritedPlan ? [7, 1, 2, 3, 4, 6, 8] : [0, 1, 2, 3, 4, 5, 6, 7, 8];
}

export function nextRegistrationStep(step: number, inheritedPlan: boolean): number {
  const steps = buddySteps(inheritedPlan);
  return steps[steps.indexOf(step) + 1] ?? 8;
}

export function buddyRegistrationPath(code: string, newParticipant = false): string {
  return `/register?invite=${encodeURIComponent(code)}${newParticipant ? '&new=1' : ''}`;
}
