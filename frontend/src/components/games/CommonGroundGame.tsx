import { useEffect, useState } from 'react';
import { useI18n } from '../../i18n';
import Button from '../Button';
import { COMMON_GROUND_SECONDS, formatClock } from './tableGameLogic';

export default function CommonGroundGame() {
  const { t } = useI18n();
  const [left, setLeft] = useState(COMMON_GROUND_SECONDS);
  const [running, setRunning] = useState(false);
  const [found, setFound] = useState(['', '', '']);
  const done = found.every(item => item.trim());

  useEffect(() => {
    if (!running) return;
    const timer = window.setInterval(() => setLeft(value => Math.max(0, value - 1)), 1000);
    return () => window.clearInterval(timer);
  }, [running]);
  useEffect(() => { if (left === 0) setRunning(false); }, [left]);

  function toggle() {
    if (left === 0) setLeft(COMMON_GROUND_SECONDS);
    setRunning(value => !value);
  }
  return <div className="space-y-4">
    <p className="leading-relaxed">{t('games.common.intro')}</p>
    <div className="rounded-3xl border border-line bg-accent p-6 text-center">
      <p className="text-xs font-semibold text-ink-muted">{t('games.common.timer')}</p>
      {/* Not announced every second; the time-up message below is announced once. */}
      <p className="mt-1 text-5xl font-bold tabular-nums" role="timer" aria-live="off">{left ? formatClock(left) : t('games.common.timeUp')}</p>
    </div>
    <p role="status" className="sr-only">{left ? '' : t('games.common.timeUp')}</p>
    <div className="flex gap-2">
      <Button className="flex-1" onClick={toggle}>{t(running ? 'games.common.pause' : 'games.common.start')}</Button>
      <Button variant="secondary" onClick={() => { setRunning(false); setLeft(COMMON_GROUND_SECONDS); setFound(['', '', '']); }}>{t('games.common.reset')}</Button>
    </div>
    <fieldset className="space-y-2">
      <legend className="font-bold">{t('games.common.found')}</legend>
      {found.map((item, i) => <div key={i}>
        <label htmlFor={`common-${i}`} className="sr-only">{t('games.common.thing', { n: i + 1 })}</label>
        <input id={`common-${i}`} className="field" maxLength={60} value={item} placeholder={t('games.common.thing', { n: i + 1 })} autoComplete="off"
          onChange={e => setFound(list => list.map((v, j) => j === i ? e.target.value : v))} />
      </div>)}
    </fieldset>
    <p role="status" className="text-sm font-semibold text-brand">{done ? t('games.common.done') : ''}</p>
    <p className="rounded-2xl bg-brand-soft p-3 text-sm">{t('games.common.stuck')}</p>
  </div>;
}
