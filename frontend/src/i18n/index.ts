import { useSyncExternalStore } from 'react';
import { en, type MessageKey } from './en';
import { de } from './de';
export type { MessageKey } from './en';
export type Locale = 'en' | 'de';
const STORAGE_KEY = 'lunch2.locale';
const dictionaries = { en, de };
const listeners = new Set<() => void>();
let locale: Locale | undefined;

export function resolveLocale(saved: string | null, browserLanguage: string): Locale {
  return saved === 'en' || saved === 'de' ? saved : browserLanguage.toLowerCase().split('-')[0] === 'de' ? 'de' : 'en';
}
export function getLocale(): Locale {
  if (!locale) {
    let saved: string | null = null;
    try { if (typeof window !== 'undefined') saved = window.localStorage.getItem(STORAGE_KEY); } catch { /* In-memory preference still works. */ }
    locale = resolveLocale(saved, typeof navigator === 'undefined' ? 'en' : navigator.language);
  }
  return locale;
}
export function setLocale(next: Locale): void {
  locale = next;
  try { localStorage.setItem(STORAGE_KEY, next); } catch { /* Storage may be disabled. */ }
  if (typeof document !== 'undefined') document.documentElement.lang = next;
  listeners.forEach(listener => listener());
}
export function t(key: MessageKey, values: Record<string, string | number> = {}): string {
  const message: string = dictionaries[getLocale()][key];
  return message.replace(/\{(\w+)\}/g, (placeholder, name: string) => String(values[name] ?? placeholder));
}
function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => { listeners.delete(listener); };
}
export function useI18n() {
  const current = useSyncExternalStore(subscribe, getLocale, (): Locale => 'en');
  return { locale: current, setLocale, t };
}
