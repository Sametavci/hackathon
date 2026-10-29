import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { deleteMe, getMe, getMeta, unlinkFriend } from '../api/endpoints';
import { clearSession } from '../api/session';
import { ApiError } from '../api/client';
import Button, { buttonClasses } from '../components/Button';
import Card from '../components/Card';
import ErrorBanner from '../components/ErrorBanner';
import LoadState from '../components/LoadState';
import QrShare from '../components/QrShare';
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
  async function unlink() {
    setBusy(true); setError(null);
    try { await unlinkFriend(); profile.reload(); } catch (e) { setError(e); } finally { setBusy(false); }
  }
  if (!profile.data) return <LoadState {...profile} />;
  const me = profile.data;
  const meta = catalog.data;
  const mensa = meta?.mensas?.find(m => m.id === me.mensaId);
  return <div className="space-y-5 pb-3 pt-3">
    <header className="flex items-center gap-3 pb-1"><Avatar name={me.displayName} /><div className="min-w-0 space-y-1"><p className="text-xs text-ink-muted">{t('me.eyebrow')}</p><h1 className="break-words text-2xl font-bold tracking-tight">{me.displayName}</h1></div></header>
    <section aria-labelledby="lunch-status" className="rounded-3xl bg-brand-dark p-5 text-white sm:p-6">
      <div className="flex items-center justify-between gap-3">
        <p className="inline-flex items-center gap-2 text-xs font-medium text-white/90"><span aria-hidden="true" className="h-2 w-2 rounded-full bg-accent" />{t(me.matchStatus === 'MATCHED' ? 'me.matched' : 'me.saved')}</p>
        <button type="button" aria-label={t('common.refresh')} onClick={profile.reload} className="grid h-11 w-11 shrink-0 place-items-center rounded-full border border-white/30 text-white hover:bg-white/10 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-white">
          <svg aria-hidden="true" width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6"><path d="M20 7v5h-5M4 17v-5h5M5.2 8a7.5 7.5 0 0 1 12.3-3L20 8M4 16l2.5 3A7.5 7.5 0 0 0 18.8 16" strokeLinecap="round" strokeLinejoin="round" /></svg>
        </button>
      </div>
      <h2 id="lunch-status" className="mt-3 max-w-xs text-2xl font-semibold leading-tight tracking-tight">{t(me.matchStatus === 'MATCHED' ? 'me.readyTitle' : 'me.waiting')}</h2>
      {me.matchStatus === 'WAITING' && <p className="mt-2 max-w-sm text-sm leading-relaxed text-white/90">{t('me.waitingBody')}</p>}
      {me.matchStatus === 'WAITING' && me.waitingReason && me.waitingReason.code !== 'WAITING_FOR_NEXT_ROUND' && <details className="mt-3 border-t border-white/20"><summary className="flex min-h-11 cursor-pointer items-center text-xs font-medium">{t('me.statusDetails')} +</summary><p className="pb-1 text-sm leading-relaxed text-white/90">{reasonMessage(me.waitingReason.code)}</p></details>}
      {me.matchStatus === 'MATCHED' && me.groupId && <Link className={`${buttonClasses} mt-5 w-full !bg-white !text-brand-dark hover:!bg-brand-soft focus-visible:!outline-white`} to={`/group/${me.groupId}`}>{t('me.reveal')} <span aria-hidden="true">↗</span></Link>}
    </section>
    <section aria-labelledby="lunch-location" className="space-y-4 rounded-3xl border border-line p-5">
      {!!catalog.error && <ErrorBanner error={catalog.error} onRetry={catalog.reload} />}
      <div className="flex items-start gap-3">
        <span aria-hidden="true" className="grid h-11 w-11 shrink-0 place-items-center rounded-2xl bg-brand-soft text-brand"><svg width="21" height="21" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5"><path d="M19 10c0 5-7 10-7 10S5 15 5 10a7 7 0 1 1 14 0Z" /><circle cx="12" cy="10" r="2.5" /></svg></span>
        <div className="min-w-0 space-y-1"><p className="text-xs text-ink-muted">{meta?.campuses.find(c => c.id === me.campus)?.label ?? me.campus}</p><h2 id="lunch-location" className="break-words text-base font-semibold">{mensa?.name ?? t('me.summary')}</h2>{mensa?.address && <p className="text-xs leading-relaxed text-ink-muted">{mensa.address}</p>}</div>
      </div>
      <div className="space-y-3 border-t border-line pt-4"><h3 className="text-xs font-medium text-ink-muted">{t('me.slots')}</h3><ul className="space-y-2">{me.slotIds.slice(0, 2).map(id => <li key={id} className="flex items-center gap-2.5 rounded-xl bg-brand-soft px-3 py-3 text-sm font-medium"><svg aria-hidden="true" className="shrink-0 text-brand" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5"><rect x="4" y="5" width="16" height="16" rx="3" /><path d="M8 3v4m8-4v4M4 11h16" /></svg><span>{formatSlot(meta?.slots.find(slot => slot.id === id) ?? id)}</span></li>)}</ul></div>
      <details className="border-t border-line pt-1"><summary className="flex min-h-11 cursor-pointer items-center justify-between text-sm text-ink-muted">{t('me.summary')}<span aria-hidden="true">+</span></summary>
        <div className="space-y-3 pb-1 pt-2"><div className="flex flex-wrap gap-2">{me.interests.map(id => <span key={id} className="rounded-full bg-brand-soft px-3 py-1.5 text-xs">{catalogLabel('interest', id, meta?.interests.find(i => i.id === id)?.label)}</span>)}</div>
          {me.slotIds.length > 2 && <ul className="space-y-2 text-sm text-ink-muted">{me.slotIds.slice(2).map(id => <li key={id}>{formatSlot(meta?.slots.find(slot => slot.id === id) ?? id)}</li>)}</ul>}
        </div>
      </details>
    </section>
    {me.friend && <section aria-labelledby="lunch-friend" className="rounded-3xl border border-line p-5">
      <h2 id="lunch-friend" className="mb-4 text-xs font-medium text-ink-muted">{t('me.friend')}</h2>
      <div className="flex items-center gap-3"><Avatar name={me.friend.displayName} small /><p className="min-w-0 break-words text-base font-semibold">{me.friend.displayName}</p><span aria-hidden="true" className="ml-auto grid h-7 w-7 shrink-0 place-items-center rounded-full bg-brand-soft text-brand">✓</span></div>
      <p className="mt-3 text-xs leading-relaxed text-ink-muted">{me.friend.issue ? reasonMessage(me.friend.issue.code) : t(me.matchStatus === 'MATCHED' ? 'buddy.together' : 'buddy.waiting')}</p>
      <details className="mt-3 border-t border-line"><summary className="flex min-h-11 cursor-pointer items-center text-xs font-medium text-ink-muted">{t('me.manageFriend')}</summary><p className="mb-3 text-xs leading-relaxed text-ink-muted">{t('buddy.unlinkInfo')}</p><Button variant="secondary" disabled={busy} className="w-full" onClick={() => void unlink()}>{t(busy ? 'common.saving' : 'buddy.unlink')}</Button></details>
    </section>}
    {!me.friend && me.matchStatus === 'WAITING' && <QrShare key={me.pendingInvite?.code ?? 'new'} pendingInvite={me.pendingInvite} />}
    <div className="space-y-3 border-t border-line pt-5"><p className="px-1 text-xs font-medium text-ink-muted">{t('me.account')}</p><ReturnLink /></div>
    {!!error && <ErrorBanner error={error} />}
    {confirm ? <Card role="region" aria-label={t('me.deleteTitle')} className="space-y-4 !rounded-2xl !border-red-200 !shadow-none"><h2 className="text-base">{t('me.deleteTitle')}</h2><p className="text-sm leading-relaxed text-ink-muted">{t('me.deleteBody')}</p><div className="flex flex-wrap gap-2"><Button variant="danger" disabled={busy} onClick={() => void remove()}>{t(busy ? 'common.saving' : 'me.deleteConfirm')}</Button><Button variant="secondary" disabled={busy} onClick={() => setConfirm(false)}>{t('common.cancel')}</Button></div></Card> : <button type="button" className="min-h-11 text-xs text-ink-muted underline underline-offset-4" onClick={() => setConfirm(true)}>{t('me.delete')}</button>}
  </div>;
}
export default function MePage() { return <SessionGate><MeContent /></SessionGate>; }
