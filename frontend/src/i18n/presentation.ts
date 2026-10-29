import { en, type MessageKey } from './en';
import { de } from './de';
import { getLocale, t } from '.';
import type { FieldError, MetaResponse, ReasonCode, Slot } from '../api/types';

export function catalogLabel(kind: 'interest' | 'language' | 'study', id: string, fallback = id): string {
  const key = `${kind}.${id}`;
  return key in en ? t(key as MessageKey) : fallback;
}
export const campusLabel = (campus: string) => campus === 'WUERZBURG' ? 'Würzburg' : campus === 'SCHWEINFURT' ? 'Schweinfurt' : campus;
export function formatDate(date: string, options: Intl.DateTimeFormatOptions = { weekday: 'short', day: 'numeric', month: 'short' }): string {
  return new Intl.DateTimeFormat(getLocale() === 'de' ? 'de-DE' : 'en-GB', { ...options, timeZone: 'Europe/Berlin' }).format(new Date(`${date}T12:00:00Z`));
}
export function formatSlot(value: string | Slot): string {
  if (typeof value !== 'string') return `${formatDate(value.date)} · ${value.start}–${value.end}`;
  const match = /^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2})$/.exec(value);
  return match ? `${formatDate(match[1])} · ${match[2]}` : value;
}
export function reasonMessage(code: ReasonCode): string { return t(`reason.${code}`); }
const fieldKeys: Record<string, MessageKey> = {
  displayName: 'validation.name', category: 'validation.category', campus: 'validation.campus', languages: 'validation.languages',
  interests: 'validation.interests', slotIds: 'validation.slots', studyField: 'validation.study', consent: 'validation.consent', mensaId: 'validation.mensa', contact: 'error.contact',
};
export function fieldMessage(error: FieldError): string {
  const key = fieldKeys[error.field.split(/[.\[]/)[0]];
  return key ? t(key) : error.message;
}
export function errorMessage(error: unknown): string {
  if (typeof error === 'object' && error !== null && 'code' in error && typeof error.code === 'string') {
    const aliases: Record<string, MessageKey> = { NETWORK: 'error.network', INVALID_RESPONSE: 'error.response', CONSENT_REQUIRED: 'validation.consent', UNKNOWN_SLOT: 'validation.slots', INTERNAL: 'error.generic' };
    const key = aliases[error.code] ?? `error.${error.code}`;
    return key in en ? t(key as MessageKey) : t('error.generic');
  }
  if (error instanceof Error) {
    const key = (Object.keys(en) as MessageKey[]).find(key => en[key] === error.message || de[key] === error.message);
    return key ? t(key) : error.message;
  }
  return t('error.generic');
}
export function groupExplanation(text: string, meta: MetaResponse | null): string {
  const interest = /^([1-4]) of you love (.+)$/.exec(text);
  if (interest) {
    const option = meta?.interests.find(i => i.label === interest[2]);
    if (option) return t('group.sharedCount', { count: interest[1], interest: catalogLabel('interest', option.id, option.label) });
  }
  if (text.startsWith('Everyone speaks ')) {
    const label = text.slice('Everyone speaks '.length).replace(/\.$/, '');
    const option = meta?.languages.find(l => l.label === label);
    if (option) return t('group.everyoneSpeaks', { language: catalogLabel('language', option.code, option.label) });
  }
  const suffix = ' signed up together';
  if (text.endsWith(suffix)) return t('group.friendsTogether', { names: text.slice(0, -suffix.length) });
  if (['A lunch slot that works for all four of you.', 'A lunch time and Mensa that work for all four of you.'].includes(text)) return t('group.sharedSlot');
  if (text === 'A language you can all share.') return t('group.sharedLanguage');
  return text;
}

export function sortByLabel<T>(items: readonly T[], label: (item: T) => string, locale = getLocale()): T[] {
  const collator = new Intl.Collator(locale, { sensitivity: 'base' });
  return [...items].sort((a, b) => collator.compare(label(a), label(b)));
}
