import { Link, useParams } from 'react-router-dom';
import { ApiError } from '../api/client';
import { getInvite, getMeta } from '../api/endpoints';
import { getToken } from '../api/session';
import { buttonClasses } from '../components/Button';
import Card from '../components/Card';
import LoadState from '../components/LoadState';
import { useResource } from '../hooks/useResource';
import { t } from '../i18n/en';

export default function JoinPage() {
  const { code = '' } = useParams();
  const invite = useResource(`invite:${code}`, () => getInvite(code));
  const meta = useResource('meta', getMeta);
  const alone = <Link to="/register" className={`${buttonClasses} w-full !border-line !bg-white !text-ink`}>{t('join.cta')}</Link>;
  if (getToken()) return <Card className="mt-6 space-y-4"><p>{t('join.registered')}</p><Link className={`${buttonClasses} w-full`} to="/me">{t('landing.return')}</Link></Card>;
  if (invite.error instanceof ApiError && invite.error.status === 404) return <Card className="mt-6 space-y-5"><h1>{t('join.title')}</h1><p role="alert" className="text-ink-muted">{t('join.invalid')}</p>{alone}</Card>;
  if (!invite.data) return <LoadState {...invite} />;
  const preview = invite.data;
  const campus = meta.data?.campuses.find(c => c.id === preview.campus)?.label ?? preview.campus;
  if (!preview.valid) return <Card className="mt-6 space-y-5"><h1>{t('join.title')}</h1><p role="alert" className="text-ink-muted">{t('join.used')}</p>{alone}</Card>;
  return <Card className="mt-6 space-y-5">
    <span aria-hidden="true" className="text-4xl">🤝</span>
    <h1>{preview.inviterDisplayName} {t('join.invitedYou')}</h1>
    <p className="text-sm font-semibold">{t('join.at')}: {campus}</p>
    <p className="leading-relaxed text-ink-muted">{t('join.body')}</p>
    <p className="rounded-2xl bg-brand-soft p-3 text-sm">{t('join.sameChoices')}</p>
    <Link to={`/register?invite=${encodeURIComponent(preview.code)}`} className={`${buttonClasses} w-full`}>{t('join.accept')}</Link>
    {alone}
  </Card>;
}
