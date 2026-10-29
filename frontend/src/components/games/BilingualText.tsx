import { useI18n } from '../../i18n';
import type { Bilingual } from './tableGameContent';

/** Game content in the active UI language, with the other language as a small subtitle. */
export default function BilingualText({ text, className = '', suffix = '' }: { text: Bilingual; className?: string; suffix?: string }) {
  const { locale } = useI18n();
  const other = locale === 'de' ? 'en' : 'de';
  return <span className="block">
    <span lang={locale} className={`block ${className}`}>{text[locale]}{suffix}</span>
    <span lang={other} className="mt-1 block text-sm font-normal text-ink-muted">{text[other]}{suffix}</span>
  </span>;
}
