import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { getMe } from '../api/endpoints';
import { ApiError } from '../api/client';
import { clearSession, getToken, setToken } from '../api/session';
import { buttonClasses } from '../components/Button';
import ErrorBanner from '../components/ErrorBanner';
import Spinner from '../components/Spinner';
import { t } from '../i18n/en';
export default function RestorePage() {
  const [params] = useSearchParams();
  const token = useRef(params.get('t'));
  const started = useRef(false);
  const [error, setError] = useState<unknown>(null);
  const navigate = useNavigate();
  useEffect(() => {
    if (started.current) return;
    started.current = true;
    const value = token.current;
    // Strip the credential before any asynchronous work; keep the HashRouter route.
    window.history.replaceState(window.history.state, '', `${window.location.pathname}${window.location.search}#/back`);
    if (!value) { setError(new Error(t('restore.missing'))); return; }
    const previous = getToken();
    setToken(value);
    getMe().then(() => navigate('/me', { replace: true })).catch((e: unknown) => {
      if (previous) setToken(previous); else clearSession();
      setError(e instanceof ApiError && e.status === 401 ? new Error(t('restore.invalid')) : e);
    });
  }, [navigate]);
  return <div className="space-y-5 pt-4"><h1>{t('restore.title')}</h1>{error ? <><ErrorBanner error={error} /><Link to="/register" className={buttonClasses}>{t('restore.register')}</Link></> : <Spinner />}</div>;
}
