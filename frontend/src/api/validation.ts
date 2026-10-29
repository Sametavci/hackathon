import type { FieldError, MetaResponse, RegistrationRequest } from './types';
import { t } from '../i18n/en';
export function validateRegistration(input: RegistrationRequest, meta: MetaResponse): FieldError[] {
  const errors: FieldError[] = [];
  const add = (field: string, message: string) => errors.push({ field, message });
  if (input.displayName.length < 2 || input.displayName.length > 30 || !/^[\p{L}\p{N} ._-]+$/u.test(input.displayName)) add('displayName', t('validation.name'));
  if (!['DOMESTIC', 'INTERNATIONAL'].includes(input.category)) add('category', t('validation.category'));
  if (!meta.campuses.some(c => c.id === input.campus)) add('campus', t('validation.campus'));
  const check = (field: 'languages' | 'interests' | 'slotIds', max: number, ids: string[], message: string) => {
    const values = input[field];
    if (!values.length || values.length > max || new Set(values).size !== values.length || values.some(id => !ids.includes(id))) add(field, message);
  };
  check('languages', 4, meta.languages.map(x => x.code), t('validation.languages'));
  check('interests', 5, meta.interests.map(x => x.id), t('validation.interests'));
  check('slotIds', 15, meta.slots.map(x => x.id), t('validation.slots'));
  if (input.studyField !== null && !meta.studyFields.some(x => x.id === input.studyField)) add('studyField', t('validation.study'));
  if (input.mensaId != null) {
    const mensa = meta.mensas?.find(m => m.id === input.mensaId && m.campus === input.campus);
    if (!mensa) add('mensaId', t('validation.mensa'));
    else if (input.slotIds.some(id => !mensa.slotIds.includes(id))) add('slotIds', t('validation.slots'));
  }
  if (!input.consent) add('consent', t('validation.consent'));
  return errors;
}
