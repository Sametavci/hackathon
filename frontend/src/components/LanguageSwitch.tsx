import { useI18n } from '../i18n';
export default function LanguageSwitch() {
  const { locale, setLocale, t } = useI18n();
  return <div role="group" aria-label={t('locale.label')} className="inline-flex shrink-0 items-center rounded-full bg-brand-soft p-1">
    {(['en', 'de'] as const).map(value => <button key={value} type="button" lang={value} aria-pressed={locale === value} onClick={() => setLocale(value)}
      className={`min-h-11 rounded-full px-3 text-xs font-semibold transition-colors motion-reduce:transition-none ${locale === value ? 'bg-brand text-white shadow-sm' : 'text-ink-muted hover:text-ink'}`}>{value === 'en' ? 'English' : 'Deutsch'}</button>)}
  </div>;
}
