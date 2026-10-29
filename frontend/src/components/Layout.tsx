import { useEffect } from 'react';
import { Link, Outlet, useLocation } from 'react-router-dom';
import { useI18n } from '../i18n';
import LanguageSwitch from './LanguageSwitch';
import { getToken } from '../api/session';
export default function Layout() {
  const { t, locale } = useI18n();
  const { pathname } = useLocation();
  const landing = pathname === '/';
  useEffect(() => { document.documentElement.lang = locale; }, [locale]);
  useEffect(() => {
    window.scrollTo(0, 0);
    document.getElementById('main')?.focus({ preventScroll: true });
  }, [pathname]);
  return <div className={`mx-auto flex min-h-dvh w-full flex-col px-4 sm:px-6 ${landing ? 'max-w-5xl' : 'max-w-md'}`}>
    <a href="#main" onClick={event => { event.preventDefault(); document.getElementById('main')?.focus(); }} className="sr-only focus:not-sr-only focus:rounded-xl focus:bg-brand focus:p-3 focus:text-white">{t('nav.skip')}</a>
    <header className="flex items-center justify-between gap-2 py-4">
      <Link to="/" aria-label={t('app.name')} className="flex min-h-11 items-center gap-1.5 text-xl font-black tracking-tighter"><span aria-hidden="true" className="grid h-10 w-10 place-items-center rounded-2xl bg-brand text-xl text-white">²</span>{t('app.name')}</Link>
      <div className="flex items-center gap-2"><LanguageSwitch />{getToken() && <Link to="/me" aria-label={t('nav.me')} className="inline-flex h-11 min-w-11 items-center justify-center rounded-full border border-line px-2 text-sm font-semibold"><span aria-hidden="true" className="sm:hidden">↗</span><span className="hidden sm:inline">{t('nav.me')}</span></Link>}</div>
    </header>
    <main id="main" tabIndex={-1} className={`min-w-0 flex-1 outline-none ${landing ? 'flex flex-col pb-6' : 'pb-10'}`}><Outlet /></main>
    <footer className="flex items-center justify-between gap-4 border-t border-line py-3 text-xs text-ink-muted"><span>{t('app.name')} · THWS</span><Link to="/privacy" className="inline-flex min-h-11 items-center underline underline-offset-4">{t('nav.privacy')}</Link></footer>
  </div>;
}
