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
import Avatar from '../components/Avatar';
import SessionGate from '../components/SessionGate';
import { useResource } from '../hooks/useResource';
import { catalogLabel, formatSlot, reasonMessage } from '../i18n/presentation';
import { useI18n } from '../i18n';
function MeContent() {
  const { t } = useI18n();
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
  return <div className="space-y-5 pt-3">
    <header className="flex items-center gap-3 pb-1"><Avatar name={me.displayName} /><div className="min-w-0 space-y-1"><p className="text-xs text-ink-muted">{t('me.eyebrow')}</p><h1 className="break-words text-2xl font-bold">{me.displayName}</h1></div></header>
    {me.matchStatus === 'WAITING' ? <section className="rounded-2xl bg-brand-soft px-4 py-3">
      <div className="flex items-center justify-between gap-3"><p className="text-sm font-medium">{t('me.waiting')}</p><button type="button" aria-label={t('common.refresh')} className="grid h-11 w-11 shrink-0 place-items-center rounded-full bg-white text-brand hover:bg-white/70" onClick={profile.reload}><svg aria-hidden="true" width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6"><path d="M20 7v5h-5M4 17v-5h5M5.2 8a7.5 7.5 0 0 1 12.3-3L20 8M4 16l2.5 3A7.5 7.5 0 0 0 18.8 16" strokeLinecap="round" strokeLinejoin="round" /></svg></button></div>
      {me.waitingReason && me.waitingReason.code !== 'WAITING_FOR_NEXT_ROUND' && <details className="mt-1"><summary className="flex min-h-11 cursor-pointer items-center text-xs text-ink-muted">{t('me.statusDetails')} +</summary><p className="pb-2 text-sm leading-relaxed text-ink-muted">{reasonMessage(me.waitingReason.code)}</p></details>}
      <Link className="inline-flex min-h-11 items-center text-sm font-semibold text-brand underline" to="/games">{t('games.try')} ↗</Link>
    </section> : me.groupId && <Link className={`${buttonClasses} w-full`} to={`/group/${me.groupId}`}>{t('me.reveal')} ↗</Link>}
    <section className="space-y-4 rounded-2xl border border-line p-4">
      {!!catalog.error && <ErrorBanner error={catalog.error} onRetry={catalog.reload} />}
      <div className="space-y-1"><p className="text-xs text-ink-muted">{meta?.campuses.find(c => c.id === me.campus)?.label ?? me.campus}</p><h2 className="text-base font-semibold">{meta?.mensas?.find(m => m.id === me.mensaId)?.name ?? t('me.summary')}</h2></div>
      <div className="space-y-2 border-t border-line pt-3"><p className="text-xs text-ink-muted">{t('me.slots')}</p>{me.slotIds.slice(0, 2).map(id => <p key={id} className="text-sm font-medium">{formatSlot(meta?.slots.find(slot => slot.id === id) ?? id)}</p>)}</div>
      <details className="border-t border-line pt-1"><summary className="flex min-h-11 cursor-pointer items-center justify-between text-sm text-ink-muted">{t('me.summary')}<span aria-hidden="true">+</span></summary>
        <div className="space-y-3 pb-1 pt-2"><div className="flex flex-wrap gap-2">{me.interests.map(id => <span key={id} className="rounded-full bg-brand-soft px-3 py-1.5 text-xs">{catalogLabel('interest', id, meta?.interests.find(i => i.id === id)?.label)}</span>)}</div>
          {me.slotIds.length > 2 && <ul className="space-y-2 text-sm text-ink-muted">{me.slotIds.slice(2).map(id => <li key={id}>{formatSlot(meta?.slots.find(slot => slot.id === id) ?? id)}</li>)}</ul>}
        </div>
      </details>
    </section>
    {me.friend && <section className="rounded-2xl border border-line p-4"><p className="text-xs text-ink-muted">{t('me.friend')}</p><div className="mt-3 flex items-center gap-3"><Avatar name={me.friend.displayName} small /><p className="text-sm font-semibold">{me.friend.displayName}</p></div>{me.friend.issue && <p className="mt-2 text-sm text-ink-muted">{reasonMessage(me.friend.issue.code)}</p>}</section>}
    <ReturnLink />
    {!!error && <ErrorBanner error={error} />}
    {confirm ? <Card role="region" aria-label={t('me.deleteTitle')} className="space-y-4 !rounded-2xl !border-red-200 !shadow-none"><h2 className="text-base">{t('me.deleteTitle')}</h2><p className="text-sm leading-relaxed text-ink-muted">{t('me.deleteBody')}</p><div className="flex flex-wrap gap-2"><Button variant="danger" disabled={busy} onClick={() => void remove()}>{t(busy ? 'common.saving' : 'me.deleteConfirm')}</Button><Button variant="secondary" disabled={busy} onClick={() => setConfirm(false)}>{t('common.cancel')}</Button></div></Card> : <button type="button" className="min-h-11 text-xs text-ink-muted underline underline-offset-4" onClick={() => setConfirm(true)}>{t('me.delete')}</button>}
  </div>;
}
export default function MePage() { return <SessionGate><MeContent /></SessionGate>; }
