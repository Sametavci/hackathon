import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { en, t as legacyT } from './en';
import { de } from './de';
import { getLocale, resolveLocale, setLocale, t } from '.';
import { catalogLabel, errorMessage, fieldMessage, formatDate, formatSlot, groupExplanation, reasonMessage } from './presentation';
import { memoryStorage } from '../api/testStorage';
import { mockMeta } from '../api/mockData';

beforeEach(() => { vi.stubGlobal('localStorage', memoryStorage()); setLocale('en'); });
afterEach(() => { setLocale('en'); vi.unstubAllGlobals(); });

describe('locale selection', () => {
  it.each([
    ['de', 'en-US', 'de'], ['en', 'de-DE', 'en'], [null, 'de-DE', 'de'], [null, 'de-AT', 'de'],
    [null, 'en-GB', 'en'], [null, 'tr-TR', 'en'], ['invalid', 'de-DE', 'de'],
  ])('resolves saved %s and browser %s to %s', (saved, browser, expected) => {
    expect(resolveLocale(saved, browser!)).toBe(expected);
  });
  it('persists only the UI preference and keeps participant/session data', () => {
    localStorage.setItem('lunch2.token', 'private');
    setLocale('de');
    expect(localStorage.getItem('lunch2.locale')).toBe('de');
    expect(localStorage.getItem('lunch2.token')).toBe('private');
    expect(getLocale()).toBe('de'); expect(t('register.done')).toBe('Fertig');
  });
  it('works with disabled storage', () => {
    vi.stubGlobal('localStorage', { setItem: () => { throw new Error('disabled'); } });
    expect(() => setLocale('de')).not.toThrow(); expect(getLocale()).toBe('de');
  });
});

describe('translations and presentation', () => {
  it('keeps the existing translation import compatible with locale changes', () => {
    setLocale('en');
    expect(legacyT('register.done')).toBe(t('register.done'));
    setLocale('de');
    expect(legacyT('register.done')).toBe('Fertig');
  });
  it('provides every English key in German with a nonempty value', () => {
    expect(Object.keys(de).sort()).toEqual(Object.keys(en).sort());
    expect(Object.values(de).every(value => value.trim())).toBe(true);
  });
  it('localizes catalogs, dates, reasons and templates without changing IDs', () => {
    setLocale('de');
    expect(catalogLabel('interest', 'travel')).toBe('Reisen');
    expect(catalogLabel('language', 'en')).toBe('Englisch');
    expect(catalogLabel('study', 'COMPUTER_SCIENCE')).toBe('Informatik');
    expect(catalogLabel('interest', 'unknown', 'Future interest')).toBe('Future interest');
    expect(formatDate('2026-11-02', { weekday: 'long' })).toBe('Montag');
    expect(formatSlot('2026-11-02T11:30')).toContain('11:30');
    expect(reasonMessage('FRIEND_DIFFERENT_MENSA')).toContain('verschiedene Mensen');
    expect(t('group.sharedCount', { count: 3, interest: 'Reisen' })).toBe('3 von euch mögen Reisen');
  });
  it('translates current group explanation templates while preserving names and supplied content', () => {
    setLocale('de');
    expect(groupExplanation('3 of you love Travel', mockMeta)).toBe('3 von euch mögen Reisen');
    expect(groupExplanation('Everyone speaks English', mockMeta)).toBe('Alle sprechen Englisch');
    expect(groupExplanation('İpek & Kai signed up together', mockMeta)).toBe('İpek & Kai haben sich zusammen angemeldet');
    expect(groupExplanation('A lunch slot that works for all four of you.', mockMeta)).toContain('Mensa');
    expect(groupExplanation('Unknown explanation', mockMeta)).toBe('Unknown explanation');
  });
  it('renders stored UI errors and API codes using the current locale', () => {
    const previous = new Error(t('restore.invalid'));
    setLocale('de');
    expect(errorMessage(previous)).toBe(t('restore.invalid'));
    expect(errorMessage({ code: 'CONSENT_REQUIRED', message: 'English server message' })).toBe(t('validation.consent'));
    expect(errorMessage({ code: 'FORBIDDEN', message: 'English server message' })).toBe(t('error.FORBIDDEN'));
    expect(fieldMessage({ field: 'displayName', message: 'English server validation' })).toBe(t('validation.name'));
    setLocale('en'); expect(errorMessage(previous)).toBe(en['restore.invalid']);
  });
});
