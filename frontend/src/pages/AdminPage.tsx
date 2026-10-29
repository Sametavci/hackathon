import { useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { getStats, resetDemo, runMatch, seedDemo } from '../api/endpoints';
import { ApiError } from '../api/client';
import { clearSession, getAdminKey, getToken, setAdminKey } from '../api/session';
import type { MatchRunResponse, SeedResponse, StatsResponse } from '../api/types';
import Button from '../components/Button';
import Card from '../components/Card';
import PageHeading from '../components/PageHeading';
import Avatar from '../components/Avatar';
import ErrorBanner from '../components/ErrorBanner';
import { useI18n, type MessageKey } from '../i18n';
import { campusLabel, formatSlot, reasonMessage } from '../i18n/presentation';
import { t as tAdmin } from './AdminPage.strings';
export default function AdminPage() {
  const { t } = useI18n();
  const [key, setKey] = useState(getAdminKey);
  const [seed, setSeed] = useState<SeedResponse | null>(null);
  const [match, setMatch] = useState<MatchRunResponse | null>(null);
  const [stats, setStats] = useState<StatsResponse | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [message, setMessage] = useState<MessageKey | ''>('');
  const [confirmReset, setConfirmReset] = useState(false);
  const pending = useRef(false);
  async function execute(action: 'seed' | 'match' | 'demoMatch' | 'stats' | 'reset') {
    if (pending.current) return;
    pending.current = true; setBusy(true); setError(null); setMessage('');
    try {
      setAdminKey(key);
      if (action === 'seed') { setSeed(await seedDemo(key)); setMatch(null); setStats(null); }
      else if (action === 'match' || action === 'demoMatch') { setMatch(await runMatch(key, { fillWithDemo: action === 'demoMatch' })); setStats(null); }
      else if (action === 'reset') {
        await resetDemo(key);
        setSeed(null); setMatch(null); setStats(null); setConfirmReset(false);
        clearSession();
      } else setStats(await getStats(key));
      setMessage(action === 'reset' ? 'admin.resetDone' : action === 'demoMatch' ? 'admin.demoMatchDone' : 'admin.done');
    } catch (e) { if (action === 'stats' && e instanceof ApiError && e.status === 404) { setStats(null); setMessage('admin.statsMissing'); } else setError(e); }
    finally { pending.current = false; setBusy(false); }
  }
  const disabled = busy || confirmReset || !key.trim();
  return <div className="space-y-5 pt-3" aria-busy={busy}>
    <PageHeading title={t('admin.title')} eyebrow={t('ui.admin')} />
    <section className="space-y-4 rounded-2xl border border-line p-4">
      <div className="space-y-2"><label htmlFor="admin-key" className="block text-xs font-medium text-ink-muted">{t('admin.key')}</label><input id="admin-key" className="field !rounded-xl !py-2" type="password" autoComplete="off" value={key} onChange={e => setKey(e.target.value)} disabled={busy || confirmReset} /></div>
      <Button className="w-full" disabled={disabled} onClick={() => void execute('match')}>{t('admin.match')}</Button>
      <div className="grid grid-cols-2 gap-2"><Button variant="secondary" className="!px-2 !text-xs" disabled={disabled} onClick={() => void execute('seed')}>{t('admin.seed')}</Button><Button variant="secondary" className="!px-2 !text-xs" disabled={disabled} onClick={() => void execute('stats')}>{t('admin.stats')}</Button></div>
      <details className="border-t border-line pt-1"><summary className="flex min-h-11 cursor-pointer items-center justify-between text-sm text-ink-muted">{t('admin.more')}<span aria-hidden="true">+</span></summary>
        <div className="space-y-4 pb-1 pt-2"><div className="space-y-2"><Button className="w-full" variant="secondary" disabled={disabled} onClick={() => void execute('demoMatch')}>{tAdmin('demoMatch')}</Button><p className="text-xs leading-relaxed text-ink-muted">{tAdmin('demoMatchHint')}</p></div>
          <button id="admin-reset-trigger" type="button" disabled={disabled} className="min-h-11 text-xs text-red-800 underline underline-offset-4 disabled:opacity-50" onClick={() => { setConfirmReset(true); setError(null); setMessage(''); }}>{t('admin.reset')}</button>
        </div>
      </details>
    </section>
    {confirmReset && <Card role="region" aria-labelledby="admin-reset-title" className="space-y-4 !rounded-2xl !border-red-200 !shadow-none"><h2 id="admin-reset-title" className="text-base">{tAdmin('resetTitle')}</h2><p className="text-sm leading-relaxed text-ink-muted">{tAdmin('resetBody')}</p><div className="flex flex-wrap gap-3"><Button autoFocus variant="secondary" disabled={busy} onClick={() => { setConfirmReset(false); document.getElementById('admin-reset-trigger')?.focus(); }}>{t('common.cancel')}</Button><Button variant="danger" disabled={busy} onClick={() => void execute('reset')}>{tAdmin('resetConfirm')}</Button></div></Card>}
    {!!error && <ErrorBanner error={error} />}
    {(busy || message) && <p role="status" className="rounded-xl bg-brand-soft px-3 py-2 text-xs leading-relaxed">{busy ? t('common.loading') : message ? t(message) : ''}</p>}
    {match && getToken() && <Link to="/me" className="inline-flex min-h-11 items-center text-sm font-medium text-brand underline underline-offset-4">{tAdmin('openMyLunch')} ↗</Link>}
    {match && <section className="space-y-3">
      <div className="grid grid-cols-2 gap-3 rounded-2xl bg-brand-soft p-4"><div><p className="text-xs text-ink-muted">{t('admin.groups')}</p><p className="mt-1 text-2xl font-semibold">{match.groupsCreated.length}</p></div><div><p className="text-xs text-ink-muted">{t('admin.waitingCount')}</p><p className="mt-1 text-2xl font-semibold">{match.waiting.length}</p></div></div>
      {!match.groupsCreated.length && <p className="text-xs text-ink-muted">{t('admin.noGroups')}</p>}
      {match.groupsCreated.length > 0 && <details className="rounded-2xl border border-line px-4"><summary className="flex min-h-14 cursor-pointer items-center justify-between text-sm font-medium">{t('admin.results')}<span aria-hidden="true">+</span></summary><div className="divide-y divide-line pb-2">{match.groupsCreated.map((group, index) => <div key={group.groupId} className="space-y-1 py-3"><h3 className="text-sm font-semibold">{t('admin.group')} {index + 1}</h3><p className="text-sm">{group.memberNames.join(' · ')}</p><p className="text-xs text-ink-muted">{campusLabel(group.campus)} · {formatSlot(group.slotId)}</p></div>)}</div></details>}
      {match.waiting.length > 0 && <details className="rounded-2xl border border-line px-4"><summary className="flex min-h-14 cursor-pointer items-center justify-between text-sm font-medium">{t('admin.waiting')}<span aria-hidden="true">+</span></summary><ul className="divide-y divide-line pb-2">{match.waiting.map(person => <li key={person.participantId} className="space-y-1 py-3"><div className="flex items-center gap-3"><Avatar name={person.displayName} small /><span className="text-sm font-medium">{person.displayName}</span></div><p className="pl-[3.25rem] text-xs leading-relaxed text-ink-muted">{reasonMessage(person.reasonCode)}</p></li>)}</ul></details>}
    </section>}
    {seed && <details className="rounded-2xl border border-line px-4"><summary className="flex min-h-14 cursor-pointer items-center justify-between gap-3 text-sm font-medium"><span>{t('admin.seedTitle')} · {seed.participants.length}</span><span aria-hidden="true">+</span></summary><p className="pb-3 text-xs leading-relaxed text-ink-muted">{t('admin.seedHint')}</p><div className="divide-y divide-line pb-2">{seed.participants.map(person => <div key={person.id} className="flex items-center justify-between gap-3 py-2"><div className="flex min-w-0 items-center gap-3"><Avatar name={person.displayName} small /><div className="min-w-0"><p className="break-words text-sm font-medium">{person.displayName}</p><p className="text-xs text-ink-muted">{campusLabel(person.campus)}</p></div></div><a href={person.restoreUrl} target="_blank" rel="noreferrer" aria-label={`${t('admin.open')}: ${person.displayName}`} className="grid h-11 w-11 shrink-0 place-items-center rounded-full bg-brand-soft text-brand"><svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6"><path d="M7 17 17 7M7 7h10v10" strokeLinecap="round" strokeLinejoin="round" /></svg></a></div>)}</div></details>}
    {stats && <section className="space-y-3 rounded-2xl border border-line p-4"><h2 className="text-base font-semibold">{t('admin.metrics')}</h2><dl className="grid grid-cols-2 gap-4">{([
      ['admin.registrations', stats.registrations.DOMESTIC + stats.registrations.INTERNATIONAL], ['admin.groups', stats.groups], ['admin.waitingCount', stats.waiting], ['admin.rate', `${Math.round(stats.matchRate * 100)}%`],
    ] as const).map(([label, value]) => <div key={label}><dt className="text-xs text-ink-muted">{t(label)}</dt><dd className="mt-1 text-2xl font-semibold">{value}</dd></div>)}</dl>
      <details className="border-t border-line pt-1"><summary className="flex min-h-11 cursor-pointer items-center justify-between text-sm text-ink-muted">{t('admin.more')}<span aria-hidden="true">+</span></summary><dl className="grid grid-cols-2 gap-3 py-3">{([
        ['admin.matched', stats.matched], ['admin.pairs', stats.friendPairs], ['admin.connections', stats.mutualConnections], ['register.domestic', stats.registrations.DOMESTIC], ['register.international', stats.registrations.INTERNATIONAL],
      ] as const).map(([label, value]) => <div key={label}><dt className="text-xs text-ink-muted">{t(label)}</dt><dd className="mt-1 text-sm font-semibold">{value}</dd></div>)}</dl><p className="pb-3 text-xs text-ink-muted">Würzburg {stats.byCampus.WUERZBURG} · Schweinfurt {stats.byCampus.SCHWEINFURT}</p></details>
    </section>}
  </div>;
}
