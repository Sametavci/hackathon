import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { deleteMe, getMe, getMeta } from '../api/endpoints';
import { clearSession } from '../api/session';
import { ApiError } from '../api/client';
import Button, { buttonClasses } from '../components/Button';
import Card from '../components/Card';
import ErrorBanner from '../components/ErrorBanner';
import LoadState from '../components/LoadState';
import ReturnLink from '../components/ReturnLink';
import SessionGate from '../components/SessionGate';
import { useResource } from '../hooks/useResource';
import { t } from '../i18n/en';
function MeContent() {
  const profile = useResource('me', getMe);
  const catalog = useResource('meta', getMeta);
  const [confirm, setConfirm] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const navigate = useNavigate();
  async function remove() { setBusy(true); setError(null); try { await deleteMe(); clearSession(); navigate('/', { replace: true }); } catch (e) { if (e instanceof ApiError && e.status === 401) navigate('/', { replace: true }); else setError(e); } finally { setBusy(false); } }
  if (!profile.data) return <LoadState {...profile} />;
  const me = profile.data;
  const meta = catalog.data;
  return <div className="space-y-5 pt-3"><p className="eyebrow">{t('me.eyebrow')} · {me.displayName}</p><h1>{t(me.matchStatus === 'MATCHED' ? 'me.matched' : 'me.waiting')}</h1>
    {me.matchStatus === 'WAITING' ? <Card className="space-y-4 !border-brand !bg-brand-soft"><span aria-hidden="true" className="text-4xl">☀</span><p className="font-semibold">{me.waitingReason?.message ?? t('me.waitingBody')}</p><p className="text-sm leading-relaxed text-ink-muted">{t('me.waitingTip')}</p><Button variant="secondary" onClick={profile.reload}>{t('common.refresh')}</Button></Card> : me.groupId && <Card className="space-y-4 !bg-ink !text-white"><p>{t('group.eyebrow')}</p><Link className={`${buttonClasses} w-full`} to={`/group/${me.groupId}`}>{t('me.reveal')} ↗</Link></Card>}
    <Card className="space-y-4"><h2>{t('me.summary')}</h2>{!!catalog.error && <ErrorBanner error={catalog.error} onRetry={catalog.reload} />}<p className="text-sm font-semibold">{meta?.campuses.find(c => c.id === me.campus)?.label ?? me.campus}</p><p className="text-sm text-ink-muted">{meta?.mensas?.find(m => m.id === me.mensaId)?.name}</p><div className="flex flex-wrap gap-2">{me.interests.map(id => <span key={id} className="rounded-full bg-brand-soft px-3 py-2 text-sm">{meta?.interests.find(i => i.id === id)?.label ?? id}</span>)}</div><h3 className="text-sm font-bold">{t('me.slots')}</h3><ul className="space-y-2 text-sm text-ink-muted">{me.slotIds.map(id => <li key={id}>{meta?.slots.find(slot => slot.id === id)?.label ?? id}</li>)}</ul></Card>
    {me.friend && <Card className="space-y-2"><h2>{t('me.friend')}</h2><p>{me.friend.displayName}</p><p className="text-sm text-ink-muted">{me.friend.issue?.message ?? t('me.friendLater')}</p></Card>}
    <ReturnLink />
    {!!error && <ErrorBanner error={error} />}
    {confirm ? <Card role="region" aria-label={t('me.deleteTitle')} className="space-y-4 !border-red-200"><h2>{t('me.deleteTitle')}</h2><p className="text-sm leading-relaxed text-ink-muted">{t('me.deleteBody')}</p><div className="flex flex-wrap gap-2"><Button variant="danger" disabled={busy} onClick={() => void remove()}>{t(busy ? 'common.saving' : 'me.deleteConfirm')}</Button><Button variant="secondary" disabled={busy} onClick={() => setConfirm(false)}>{t('common.cancel')}</Button></div></Card> : <Button variant="secondary" className="w-full !text-red-800" onClick={() => setConfirm(true)}>{t('me.delete')}</Button>}
  </div>;
}
export default function MePage() { return <SessionGate><MeContent /></SessionGate>; }
