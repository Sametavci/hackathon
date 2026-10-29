import { useEffect, useRef, useState, type KeyboardEvent } from 'react';
import { useI18n, type Locale } from '../i18n';

const languages = [{ locale: 'en', flag: '🇬🇧', name: 'English' }, { locale: 'de', flag: '🇩🇪', name: 'Deutsch' }] as const;
export default function LanguageSwitch() {
  const { locale, setLocale, t } = useI18n();
  const [open, setOpen] = useState(false);
  const root = useRef<HTMLDivElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const menu = useRef<HTMLDivElement>(null);
  function close() { setOpen(false); trigger.current?.focus(); }
  function choose(value: Locale) { setLocale(value); close(); }
  useEffect(() => {
    if (!open) return;
    menu.current?.querySelector<HTMLButtonElement>(`[lang="${locale}"]`)?.focus();
    function outside(event: PointerEvent) {
      if (event.target instanceof Node && !root.current?.contains(event.target)) close();
    }
    document.addEventListener('pointerdown', outside);
    return () => document.removeEventListener('pointerdown', outside);
  }, [open, locale]);
  function keyboard(event: KeyboardEvent) {
    if (event.key === 'Escape') { event.preventDefault(); close(); }
    const buttons = [...(menu.current?.querySelectorAll<HTMLButtonElement>('button') ?? [])];
    const index = buttons.indexOf(document.activeElement as HTMLButtonElement);
    const next = event.key === 'Home' ? 0 : event.key === 'End' ? buttons.length - 1
      : event.key === 'ArrowDown' ? (index + 1) % buttons.length
      : event.key === 'ArrowUp' ? (index - 1 + buttons.length) % buttons.length : -1;
    if (next >= 0 && buttons[next]) { event.preventDefault(); buttons[next].focus(); }
  }
  return <div ref={root} className="relative shrink-0" onKeyDown={keyboard} onBlur={event => {
    if (event.relatedTarget && !event.currentTarget.contains(event.relatedTarget)) setOpen(false);
  }}>
    <button ref={trigger} type="button" aria-label={`${t('locale.label')}: ${languages.find(item => item.locale === locale)?.name}`} aria-haspopup="menu" aria-expanded={open}
      onClick={() => setOpen(value => !value)} onKeyDown={event => { if (event.key === 'ArrowDown' || event.key === 'ArrowUp') { event.preventDefault(); setOpen(true); } }}
      className="grid h-11 w-11 place-items-center rounded-full border border-line bg-brand-soft text-xl hover:border-brand">
      <span aria-hidden="true">{languages.find(item => item.locale === locale)?.flag}</span>
    </button>
    {open && <div ref={menu} role="menu" aria-label={t('locale.label')} className="absolute right-0 top-full z-30 mt-2 flex gap-1 rounded-2xl border border-line bg-white p-1.5 shadow-lg">
      {languages.map(item => <button key={item.locale} type="button" role="menuitemradio" aria-checked={locale === item.locale} aria-label={item.name} lang={item.locale}
        onPointerDown={event => { if (event.button === 0) { event.preventDefault(); choose(item.locale); } }}
        onClick={() => choose(item.locale)} className={`grid h-11 w-11 place-items-center rounded-xl text-xl ${locale === item.locale ? 'bg-brand-soft ring-1 ring-brand' : 'hover:bg-brand-soft'}`}>
        <span aria-hidden="true">{item.flag}</span>
      </button>)}
    </div>}
  </div>;
}
