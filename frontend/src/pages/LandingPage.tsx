import { Link } from 'react-router-dom';
import { getToken } from '../api/session';
import { buttonClasses } from '../components/Button';
import LunchTable from '../components/LunchTable';
import { t } from '../i18n/en';

export default function LandingPage() {
  const returning = !!getToken();
  return <section className="grid flex-1 items-center gap-6 py-5 lg:grid-cols-2 lg:gap-x-16 lg:gap-y-7 lg:py-16">
    <div className="space-y-4 lg:col-start-1 lg:row-start-1 lg:self-end">
      <h1 className="whitespace-pre-line text-[3.75rem] leading-[0.98] tracking-[-0.055em] sm:text-7xl lg:text-[5.25rem]">{t('landing.title')}</h1>
      <p className="text-lg text-ink-muted">{t('landing.body')}</p>
    </div>
    <div className="lg:col-start-2 lg:row-span-2 lg:row-start-1"><LunchTable /></div>
    <Link to={returning ? '/me' : '/register'} className={`${buttonClasses} min-h-14 w-full text-base lg:col-start-1 lg:row-start-2 lg:w-auto lg:justify-self-start lg:self-start lg:px-8`}>
      {t(returning ? 'landing.return' : 'landing.join')}<span aria-hidden="true" className="ml-4 text-xl">↗</span>
    </Link>
  </section>;
}
