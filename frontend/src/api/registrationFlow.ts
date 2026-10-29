import type { MetaResponse, RegistrationRequest } from './types';
import { validateRegistration } from './validation';
export const registrationSteps = ['campus', 'category', 'languages', 'interests', 'studyField', 'mensaId', 'slotIds', 'displayName', 'consent'] as const;
export function stepForField(field: string): number {
  const index = registrationSteps.findIndex(value => value === field);
  return index < 0 ? 8 : index;
}
export function errorsForStep(input: RegistrationRequest, meta: MetaResponse, step: number) {
  return validateRegistration(input, meta).filter(error => stepForField(error.field) === step);
}

export function isAutomaticStep(step: number): boolean {
  return [0, 1, 4, 5].includes(step);
}

// One pending selection at a time. Navigation and unmount both cancel the timer.
export function createAutoAdvance(advance: (next: number) => void, delay = 220) {
  let timer: ReturnType<typeof setTimeout> | undefined;
  return {
    schedule(next: number): void {
      if (timer !== undefined) return;
      timer = setTimeout(() => { timer = undefined; advance(next); }, delay);
    },
    pending: () => timer !== undefined,
    cancel(): void { clearTimeout(timer); timer = undefined; },
  };
}
