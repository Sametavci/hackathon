import { useEffect } from 'react';
import { Link, Outlet, useLocation } from 'react-router-dom';
import { t } from '../i18n/en';
export default function Layout() {
  const { pathname } = useLocation();
  useEffect(() => {
    window.scrollTo(0, 0);
    document.getElementById('main')?.focus({ preventScroll: true });
  }, [pathname]);
  return <div className="mx-auto flex min-h-dvh w-full max-w-md flex-col px-4 sm:px-6">
    <a href="#main" onClick={event => { event.preventDefault(); document.getElementById('main')?.focus(); }} className="sr-only focus:not-sr-only focus:rounded-xl focus:bg-brand focus:p-3 focus:text-white">{t('nav.skip')}</a>
    <header className="flex items-center justify-between gap-3 py-6">
      <Link to="/" aria-label={t('app.name')} className="flex min-h-11 items-center gap-2 text-2xl font-black tracking-tighter"><span aria-hidden="true" className="grid h-10 w-10 place-items-center rounded-2xl bg-brand text-xl text-white">²</span>{t('app.name')}</Link>
      <Link to="/me" className="inline-flex min-h-11 items-center rounded-full border border-line px-4 text-sm font-semibold">{t('nav.me')}</Link>
    </header>
    <main id="main" tabIndex={-1} className="min-w-0 flex-1 pb-10 outline-none"><Outlet /></main>
    <footer className="flex items-center justify-between gap-4 border-t border-line py-5 text-sm text-ink-muted"><span>{t('app.name')} · THWS</span><Link to="/privacy" className="inline-flex min-h-11 items-center underline underline-offset-4">{t('nav.privacy')}</Link></footer>
  </div>;
}
