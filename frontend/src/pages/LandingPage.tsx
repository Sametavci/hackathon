import { Link } from 'react-router-dom';
import { getToken } from '../api/session';
import { buttonClasses } from '../components/Button';
import LunchTable from '../components/LunchTable';
import { useLandingMotion } from '../hooks/useLandingMotion';
import { useI18n } from '../i18n';

const lines = [['landing.line1a', 'landing.line1b', 'landing.line1c'], ['landing.line2a', 'landing.line2b', 'landing.line2c']] as const;
export default function LandingPage() {
  const { t, locale } = useI18n();
  const { scene, paused, reduced, running, toggle } = useLandingMotion();
  const returning = !!getToken();
  return <section className="flex flex-1 flex-col items-center justify-center gap-5 py-5 text-center sm:gap-6">
    <h1 aria-live="off" aria-label={`${t(lines[0][scene])} ${t(lines[1][scene])}`} className={`landing-heading flex w-full min-w-0 flex-col justify-center leading-[1.08] tracking-[-0.05em] ${locale === 'de' ? 'text-[clamp(2rem,9.5vw,4.25rem)]' : 'text-[clamp(2.75rem,12vw,4.5rem)]'}`}>
      {lines.map((variants, row) => <span key={row} aria-hidden="true" className="block whitespace-nowrap">
        <span className="inline-grid align-top">{variants.map((key, index) => <span key={key} className={`col-start-1 row-start-1 ${index === scene ? '' : 'invisible'}`}>{t(key).split(' ')[0]}&nbsp;</span>)}</span>
        <span className="landing-word inline-grid overflow-hidden align-top text-brand">{variants.map((key, index) => <span key={key} className={`col-start-1 row-start-1 ${index === scene ? 'landing-word-enter' : 'invisible'}`}>{t(key).split(' ').slice(1).join(' ')}</span>)}</span>
      </span>)}
    </h1>
    <p className="max-w-sm text-base text-ink-muted">{t('landing.body')}</p>
    <div className="relative w-[min(76vw,20rem)] sm:w-[22rem]">
      <LunchTable scene={scene} running={running} />
      {!reduced && <button type="button" onClick={toggle} aria-label={t(paused ? 'landing.resume' : 'landing.pause')} aria-pressed={paused} className="absolute -bottom-1 -right-1 grid h-11 w-11 place-items-center rounded-full border border-line bg-white text-brand shadow-sm hover:bg-brand-soft"><span aria-hidden="true" className="text-sm">{paused ? '▶' : 'Ⅱ'}</span></button>}
    </div>
    <Link to={returning ? '/me' : '/register'} className={`${buttonClasses} mt-1 min-h-14 w-full max-w-xs text-base`}>
      {t(returning ? 'landing.return' : 'landing.join')}<span aria-hidden="true" className="ml-4 text-xl">↗</span>
    </Link>
  </section>;
}
