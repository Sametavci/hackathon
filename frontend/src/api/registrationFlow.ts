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
