import { Link } from 'react-router-dom';
import { getToken } from '../api/session';
import { buttonClasses } from '../components/Button';
import LunchTable from '../components/LunchTable';
import { useLandingMotion } from '../hooks/useLandingMotion';
import { useI18n } from '../i18n';

const lines = [['landing.line1a', 'landing.line1b', 'landing.line1c'], ['landing.line2a', 'landing.line2b', 'landing.line2c']] as const;
export default function LandingPage() {
  const { t, locale } = useI18n();
  const { scene, scenes, running } = useLandingMotion();
  const returning = !!getToken();
  return <section className="flex flex-1 flex-col items-center justify-center gap-5 py-5 text-center sm:gap-6">
    <h1 aria-live="off" className={`landing-heading font-black grid w-full min-w-0 place-items-center leading-[1.12] tracking-[-0.045em] ${locale === 'de' ? 'text-[clamp(1.75rem,7.8vw,4.25rem)]' : 'text-[clamp(2.25rem,10vw,4.5rem)]'}`}>
      <span className="flex w-full flex-col items-center">
        {lines.map((variants, row) => <span key={row} className="inline-grid grid-cols-[max-content_max-content] items-baseline gap-x-[0.22em]">
          <span className="inline-grid text-right">{variants.map((key, index) => <span key={key} aria-hidden={index !== scenes[row]} className={`col-start-1 row-start-1 ${index === scenes[row] ? '' : 'invisible'}`}>{t(key).split(' ')[0]}</span>)}</span>
          <span className="landing-word inline-grid overflow-hidden text-left text-brand">{variants.map((key, index) => <span key={`${key}-${index === scenes[row]}`} aria-hidden={index !== scenes[row]} className={`col-start-1 row-start-1 whitespace-nowrap ${index === scenes[row] ? 'landing-word-enter' : 'invisible'}`}>{t(key).split(' ').slice(1).join(' ')}</span>)}</span>
        </span>)}
      </span>
    </h1>
    <p className="max-w-sm text-base text-ink-muted">{t('landing.body')}</p>
    <div className="relative w-[min(76vw,20rem)] sm:w-[22rem]">
      <LunchTable scene={scene} running={running} />
    </div>
    <Link to={returning ? '/me' : '/register'} className={`${buttonClasses} mt-1 min-h-14 w-full max-w-xs text-base`}>
      {t(returning ? 'landing.return' : 'landing.join')}<span aria-hidden="true" className="ml-4 text-xl">↗</span>
    </Link>
  </section>;
}
