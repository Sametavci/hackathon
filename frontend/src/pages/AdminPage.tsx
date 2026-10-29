import { useRef, useState } from 'react';
import { getStats, resetDemo, runMatch, seedDemo } from '../api/endpoints';
import { ApiError } from '../api/client';
import { clearSession, getAdminKey, setAdminKey } from '../api/session';
import type { MatchRunResponse, SeedResponse, StatsResponse } from '../api/types';
import Button from '../components/Button';
import Card from '../components/Card';
import ErrorBanner from '../components/ErrorBanner';
import { t } from '../i18n/en';
import { t as tAdmin } from './AdminPage.strings';
export default function AdminPage() {
  const [key, setKey] = useState(getAdminKey);
  const [seed, setSeed] = useState<SeedResponse | null>(null);
  const [match, setMatch] = useState<MatchRunResponse | null>(null);
  const [stats, setStats] = useState<StatsResponse | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [message, setMessage] = useState('');
  const [confirmReset, setConfirmReset] = useState(false);
  const pending = useRef(false);
  async function execute(action: 'seed' | 'match' | 'stats' | 'reset') {
    if (pending.current) return;
    pending.current = true; setBusy(true); setError(null); setMessage('');
    try {
      setAdminKey(key);
      if (action === 'seed') { setSeed(await seedDemo(key)); setMatch(null); setStats(null); }
      else if (action === 'match') { setMatch(await runMatch(key)); setStats(null); }
      else if (action === 'reset') {
        await resetDemo(key);
        setSeed(null); setMatch(null); setStats(null); setConfirmReset(false);
        clearSession();
      } else setStats(await getStats(key));
      setMessage(action === 'reset' ? tAdmin('resetDone') : t('admin.done'));
    } catch (e) { if (action === 'stats' && e instanceof ApiError && e.status === 404) { setStats(null); setMessage(t('admin.statsMissing')); } else setError(e); }
    finally { pending.current = false; setBusy(false); }
  }
  return <div className="space-y-5 pt-3"><p className="eyebrow">{t('app.name')} · THWS</p><h1>{t('admin.title')}</h1><p className="text-sm leading-relaxed text-ink-muted">{t('admin.body')}</p>
    <Card className="space-y-4"><label htmlFor="admin-key" className="block text-sm font-bold">{t('admin.key')}</label><input id="admin-key" className="field" type="password" autoComplete="off" value={key} onChange={e => setKey(e.target.value)} disabled={busy || confirmReset} /><div className="grid gap-3"><Button disabled={busy || confirmReset || !key.trim()} onClick={() => void execute('seed')}>{t('admin.seed')}</Button><Button disabled={busy || confirmReset || !key.trim()} onClick={() => void execute('match')}>{t('admin.match')}</Button><Button id="admin-reset-trigger" variant="danger" disabled={busy || confirmReset || !key.trim()} onClick={() => { setConfirmReset(true); setError(null); setMessage(''); }}>{t('admin.reset')}</Button><Button variant="secondary" disabled={busy || confirmReset || !key.trim()} onClick={() => void execute('stats')}>{t('admin.stats')}</Button></div></Card>
    {confirmReset && <Card role="region" aria-labelledby="admin-reset-title" className="space-y-4 !border-red-200"><h2 id="admin-reset-title">{tAdmin('resetTitle')}</h2><p className="text-sm leading-relaxed text-ink-muted">{tAdmin('resetBody')}</p><div className="flex flex-wrap gap-3"><Button autoFocus variant="secondary" disabled={busy} onClick={() => { setConfirmReset(false); document.getElementById('admin-reset-trigger')?.focus(); }}>{t('common.cancel')}</Button><Button variant="danger" disabled={busy} onClick={() => void execute('reset')}>{tAdmin('resetConfirm')}</Button></div></Card>}
    {!!error && <ErrorBanner error={error} />}<p role="status" className="text-sm">{busy ? t('common.loading') : message}</p>
    {match && <section className="space-y-4"><h2>{t('admin.results')}</h2>{!match.groupsCreated.length && <p className="text-sm text-ink-muted">{t('admin.noGroups')}</p>}{match.groupsCreated.map((group, index) => <Card key={group.groupId} className="space-y-2"><h3 className="font-bold">{t('admin.group')} {index + 1}</h3><p className="text-sm">{group.memberNames.join(' · ')}</p><p className="text-xs text-ink-muted">{group.campus} · {group.slotId}</p></Card>)}{match.waiting.length > 0 && <Card className="space-y-3"><h3 className="font-bold">{t('admin.waiting')}</h3><ul className="space-y-3 text-sm">{match.waiting.map(p => <li key={p.participantId}><span className="font-semibold">{p.displayName}</span><p className="break-all text-xs text-ink-muted">{p.reasonCode}</p></li>)}</ul></Card>}</section>}
    {seed && <section className="space-y-3"><h2>{t('admin.seedTitle')}</h2><p className="text-xs leading-relaxed text-ink-muted">{t('admin.seedHint')}</p>{seed.participants.map(p => <Card key={p.id} className="space-y-2"><h3 className="font-bold">{p.displayName}</h3><p className="text-xs text-ink-muted">{p.campus} · {p.category}</p><a href={p.restoreUrl} target="_blank" rel="noreferrer" className="inline-flex min-h-11 items-center text-sm underline">{t('admin.open')} ↗</a></Card>)}</section>}
    {stats && <Card className="space-y-4"><h2>{t('admin.metrics')}</h2><dl className="grid grid-cols-2 gap-4">{([
      ['admin.registrations', stats.registrations.DOMESTIC + stats.registrations.INTERNATIONAL], ['admin.matched', stats.matched], ['admin.waitingCount', stats.waiting], ['admin.groups', stats.groups], ['admin.rate', `${Math.round(stats.matchRate * 100)}%`], ['admin.pairs', stats.friendPairs], ['admin.connections', stats.mutualConnections],
    ] as const).map(([label, value]) => <div key={label}><dt className="text-xs text-ink-muted">{t(label)}</dt><dd className="mt-1 text-2xl font-bold">{value}</dd></div>)}</dl><p className="text-xs text-ink-muted">DOMESTIC {stats.registrations.DOMESTIC} · INTERNATIONAL {stats.registrations.INTERNATIONAL}</p><p className="text-xs text-ink-muted">WUERZBURG {stats.byCampus.WUERZBURG} · SCHWEINFURT {stats.byCampus.SCHWEINFURT}</p><details><summary className="flex min-h-11 cursor-pointer items-center text-sm underline">{tAdmin('statsJson')}</summary><pre className="max-w-full overflow-x-auto rounded-xl bg-brand-soft p-3 text-xs">{JSON.stringify(stats, null, 2)}</pre></details></Card>}
  </div>;
}
