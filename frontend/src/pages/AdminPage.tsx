import { useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { getStats, resetDemo, runMatch, seedDemo } from '../api/endpoints';
import { ApiError } from '../api/client';
import { clearSession, getAdminKey, getToken, setAdminKey } from '../api/session';
import type { MatchRunResponse, SeedResponse, StatsResponse } from '../api/types';
import Button from '../components/Button';
import Card from '../components/Card';
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
  return <div className="space-y-5 pt-3"><p className="eyebrow">{t('app.name')} · THWS</p><h1>{t('admin.title')}</h1><p className="text-sm leading-relaxed text-ink-muted">{t('admin.body')}</p>
    <Card className="space-y-4"><label htmlFor="admin-key" className="block text-sm font-bold">{t('admin.key')}</label><input id="admin-key" className="field" type="password" autoComplete="off" value={key} onChange={e => setKey(e.target.value)} disabled={busy || confirmReset} /><div className="grid gap-3"><Button disabled={busy || confirmReset || !key.trim()} onClick={() => void execute('seed')}>{t('admin.seed')}</Button><Button disabled={busy || confirmReset || !key.trim()} onClick={() => void execute('match')}>{t('admin.match')}</Button><Button id="admin-reset-trigger" variant="danger" disabled={busy || confirmReset || !key.trim()} onClick={() => { setConfirmReset(true); setError(null); setMessage(''); }}>{t('admin.reset')}</Button><Button variant="secondary" disabled={busy || confirmReset || !key.trim()} onClick={() => void execute('stats')}>{t('admin.stats')}</Button></div></Card>
    {confirmReset && <Card role="region" aria-labelledby="admin-reset-title" className="space-y-4 !border-red-200"><h2 id="admin-reset-title">{tAdmin('resetTitle')}</h2><p className="text-sm leading-relaxed text-ink-muted">{tAdmin('resetBody')}</p><div className="flex flex-wrap gap-3"><Button autoFocus variant="secondary" disabled={busy} onClick={() => { setConfirmReset(false); document.getElementById('admin-reset-trigger')?.focus(); }}>{t('common.cancel')}</Button><Button variant="danger" disabled={busy} onClick={() => void execute('reset')}>{tAdmin('resetConfirm')}</Button></div></Card>}
    <Card className="space-y-3"><Button className="w-full" variant="secondary" disabled={busy || confirmReset || !key.trim()} onClick={() => void execute('demoMatch')}>{tAdmin('demoMatch')}</Button><p className="text-xs leading-relaxed text-ink-muted">{tAdmin('demoMatchHint')}</p></Card>
    {!!error && <ErrorBanner error={error} />}<p role="status" className="text-sm">{busy ? t('common.loading') : message ? t(message) : ''}</p>
    {match && getToken() && <Link to="/me" className="inline-flex min-h-11 items-center rounded-2xl border border-line px-5 py-3 text-sm font-bold underline">{tAdmin('openMyLunch')}</Link>}
    {match && <section className="space-y-4"><h2>{t('admin.results')}</h2>{!match.groupsCreated.length && <p className="text-sm text-ink-muted">{t('admin.noGroups')}</p>}{match.groupsCreated.map((group, index) => <Card key={group.groupId} className="space-y-2"><h3 className="font-bold">{t('admin.group')} {index + 1}</h3><p className="text-sm">{group.memberNames.join(' · ')}</p><p className="text-xs text-ink-muted">{campusLabel(group.campus)} · {formatSlot(group.slotId)}</p></Card>)}{match.waiting.length > 0 && <Card className="space-y-3"><h3 className="font-bold">{t('admin.waiting')}</h3><ul className="space-y-3 text-sm">{match.waiting.map(p => <li key={p.participantId}><span className="font-semibold">{p.displayName}</span><p className="break-all text-xs text-ink-muted">{reasonMessage(p.reasonCode)}</p></li>)}</ul></Card>}</section>}
    {seed && <section className="space-y-3"><h2>{t('admin.seedTitle')}</h2><p className="text-xs leading-relaxed text-ink-muted">{t('admin.seedHint')}</p>{seed.participants.map(p => <Card key={p.id} className="space-y-2"><h3 className="font-bold">{p.displayName}</h3><p className="text-xs text-ink-muted">{campusLabel(p.campus)} · {t(p.category === 'DOMESTIC' ? 'register.domestic' : 'register.international')}</p><a href={p.restoreUrl} target="_blank" rel="noreferrer" className="inline-flex min-h-11 items-center text-sm underline">{t('admin.open')} ↗</a></Card>)}</section>}
    {stats && <Card className="space-y-4"><h2>{t('admin.metrics')}</h2><dl className="grid grid-cols-2 gap-4">{([
      ['admin.registrations', stats.registrations.DOMESTIC + stats.registrations.INTERNATIONAL], ['admin.matched', stats.matched], ['admin.waitingCount', stats.waiting], ['admin.groups', stats.groups], ['admin.rate', `${Math.round(stats.matchRate * 100)}%`], ['admin.pairs', stats.friendPairs], ['admin.connections', stats.mutualConnections],
    ] as const).map(([label, value]) => <div key={label}><dt className="text-xs text-ink-muted">{t(label)}</dt><dd className="mt-1 text-2xl font-bold">{value}</dd></div>)}</dl><p className="text-xs text-ink-muted">{t('register.domestic')} {stats.registrations.DOMESTIC} · {t('register.international')} {stats.registrations.INTERNATIONAL}</p><p className="text-xs text-ink-muted">Würzburg {stats.byCampus.WUERZBURG} · Schweinfurt {stats.byCampus.SCHWEINFURT}</p><details><summary className="flex min-h-11 cursor-pointer items-center text-sm underline">{tAdmin('statsJson')}</summary><pre className="max-w-full overflow-x-auto rounded-xl bg-brand-soft p-3 text-xs">{JSON.stringify(stats, null, 2)}</pre></details></Card>}
  </div>;
}
