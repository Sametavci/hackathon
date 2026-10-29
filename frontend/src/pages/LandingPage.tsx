import { Link } from 'react-router-dom';
import { getToken } from '../api/session';
import { buttonClasses } from '../components/Button';
import Card from '../components/Card';
import { t } from '../i18n/en';
export default function LandingPage() {
  return <div className="space-y-8 pb-3">
    <section className="space-y-5 pt-5"><p className="eyebrow">{t('landing.eyebrow')}</p><h1 className="whitespace-pre-line text-6xl leading-[1.03]">{t('landing.title')}</h1><p className="max-w-sm text-base leading-relaxed text-ink-muted">{t('landing.body')}</p>
      <div aria-hidden="true" className="relative mx-auto my-6 grid h-52 w-full max-w-xs grid-cols-2 gap-3 rounded-[3rem] bg-accent p-5"><span className="grid place-items-center rounded-3xl bg-white/90 text-4xl">☺</span><span className="grid place-items-center rounded-3xl bg-ink text-4xl text-accent">☺</span><span className="grid place-items-center rounded-3xl bg-ink text-4xl text-accent">☺</span><span className="grid place-items-center rounded-3xl bg-white/90 text-4xl">☺</span><span className="absolute left-1/2 top-1/2 grid h-14 w-14 -translate-x-1/2 -translate-y-1/2 place-items-center rounded-full border-4 border-accent bg-white text-lg font-black">4</span></div>
      <Link to={getToken() ? '/me' : '/register'} className={`${buttonClasses} w-full`}>{t(getToken() ? 'landing.return' : 'landing.join')} <span aria-hidden="true">↗</span></Link><p className="text-center text-xs text-ink-muted">{t('landing.note')}</p>
    </section>
    <Card className="space-y-5">{(['1', '2', '3'] as const).map(n => <div key={n} className="flex gap-3"><span aria-hidden="true" className="grid h-8 w-8 shrink-0 place-items-center rounded-full bg-brand-soft font-bold">{n}</span><div><h2 className="text-base">{t(`landing.step${n}`)}</h2><p className="mt-1 text-sm leading-relaxed text-ink-muted">{t(`landing.step${n}Body`)}</p></div></div>)}</Card>
    <p className="text-center text-xs leading-relaxed text-ink-muted">{t('landing.demo')}</p>
  </div>;
}
