import { Link, useParams } from 'react-router-dom';
import { getInvite, getMeta } from '../api/endpoints';
import { getToken } from '../api/session';
import { buddyRegistrationPath, hasBuddyPlan } from '../api/buddyFlow';
import BuddySessionChoice from '../components/BuddySessionChoice';
import { useResource } from '../hooks/useResource';
import { formatSlot } from '../i18n/presentation';
import LoadState from '../components/LoadState';
import { useI18n } from '../i18n';
import Card from '../components/Card';
import { buttonClasses } from '../components/Button';
export default function JoinPage() {
  const { code = '' } = useParams();
  const { t } = useI18n();
  const invite = useResource(`invite:${code}`, () => getInvite(code));
  const meta = useResource('meta', getMeta);
  if (invite.error) return <Card className="mt-6 space-y-4"><h1>{t('buddy.unavailable')}</h1><LoadState {...invite} /><Link to="/register" className={buttonClasses}>{t('buddy.solo')}</Link></Card>;
  if (!invite.data) return <LoadState {...invite} />;
  if (!meta.data) return <LoadState {...meta} />;
  const data = invite.data;
  const ready = hasBuddyPlan(data, meta.data);
  return <Card className="mt-6 space-y-5"><h1>{t('buddy.invited', { name: data.inviterDisplayName })}</h1>
    <p>{meta.data.campuses.find(c => c.id === data.campus)?.label}</p>
    <p className="font-semibold">{meta.data.mensas?.find(m => m.id === data.mensaId)?.name}</p>
    <ul className="space-y-2 text-sm text-ink-muted">{(data.slotIds ?? []).map(id => <li key={id}>{formatSlot(meta.data!.slots.find(s => s.id === id) ?? id)}</li>)}</ul>
    <p className="text-sm text-ink-muted">{t(!data.valid ? 'buddy.used' : data.inviterMatchStatus === 'MATCHED' ? 'buddy.matched' : ready ? 'buddy.plan' : 'buddy.unavailable')}</p>
    {ready && (getToken() ? <BuddySessionChoice code={code} /> : <Link to={buddyRegistrationPath(code)} className={`${buttonClasses} w-full`}>{t('buddy.join', { name: data.inviterDisplayName })}</Link>)}
    <Link to="/register" className="inline-flex min-h-11 items-center text-sm font-semibold underline">{t('buddy.solo')}</Link>
  </Card>;
}
