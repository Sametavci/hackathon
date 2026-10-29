import { Link, Outlet } from 'react-router-dom';
import { t } from '../i18n/en';

export default function Layout() {
  return (
    <div className="mx-auto flex min-h-screen max-w-md flex-col px-4">
      <header className="py-4">
        <Link to="/" className="text-xl font-extrabold tracking-tight">
          {t('app.name')}
        </Link>
      </header>
      <main className="flex-1 pb-8">
        <Outlet />
      </main>
      <footer className="py-4 text-sm text-ink-muted">
        <Link to="/privacy" className="underline">
          {t('nav.privacy')}
        </Link>
      </footer>
    </div>
  );
}
