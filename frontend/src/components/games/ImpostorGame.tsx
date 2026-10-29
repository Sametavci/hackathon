import { useState } from 'react';
import { useI18n } from '../../i18n';
import Button from '../Button';
import Card from '../Card';
import Chip from '../Chip';
import BilingualText from './BilingualText';
import { IMPOSTOR_CATEGORIES, IMPOSTOR_CATEGORY_NAMES, type ImpostorChoice } from './tableGameContent';
import { MAX_PLAYERS, MIN_PLAYERS, newImpostorRound, startingPlayers, type ImpostorRound } from './tableGameLogic';

type Phase = 'setup' | 'pass' | 'talk' | 'reveal';
const CHOICES: ImpostorChoice[] = ['Mixed', ...IMPOSTOR_CATEGORIES];

export default function ImpostorGame({ players }: { players: string[] }) {
  const { t, locale } = useI18n();
  const [phase, setPhase] = useState<Phase>('setup');
  const [names, setNames] = useState(() => startingPlayers(players, n => t('games.player', { n })));
  const [choice, setChoice] = useState<ImpostorChoice>('Mixed');
  const [round, setRound] = useState<ImpostorRound | null>(null);
  const [index, setIndex] = useState(0);
  const [shown, setShown] = useState(false);
  const [error, setError] = useState(false);

  function start() {
    const trimmed = names.map(name => name.trim());
    if (trimmed.some(name => !name)) { setError(true); return; }
    setNames(trimmed); setError(false);
    setRound(newImpostorRound(trimmed.length, choice)); setIndex(0); setShown(false); setPhase('pass');
  }

  if (phase === 'setup') return <div className="space-y-5">
    <p className="leading-relaxed">{t('games.impostorBody')}</p>
    <fieldset className="space-y-2">
      <legend className="font-bold">{t('games.impostor.players')}</legend>
      <p className="text-sm text-ink-muted">{t('games.impostor.playersHint')}</p>
      {names.map((name, i) => <div key={i}>
        <label htmlFor={`impostor-player-${i}`} className="sr-only">{t('games.impostor.playerLabel', { n: i + 1 })}</label>
        <input id={`impostor-player-${i}`} className="field" maxLength={20} value={name}
          onChange={e => { setNames(list => list.map((v, j) => j === i ? e.target.value : v)); setError(false); }} />
      </div>)}
      <div className="flex flex-wrap gap-2">
        <Button variant="secondary" disabled={names.length >= MAX_PLAYERS} onClick={() => setNames(list => [...list, t('games.player', { n: list.length + 1 })])}>{t('games.impostor.add')}</Button>
        <Button variant="secondary" disabled={names.length <= MIN_PLAYERS} onClick={() => setNames(list => list.slice(0, -1))}>{t('games.impostor.remove')}</Button>
      </div>
    </fieldset>
    <fieldset className="space-y-2">
      <legend className="font-bold">{t('games.impostor.category')}</legend>
      <div className="flex flex-wrap gap-2">{CHOICES.map(c => <Chip key={c} selected={choice === c} onClick={() => setChoice(c)}>{IMPOSTOR_CATEGORY_NAMES[c][locale]}</Chip>)}</div>
    </fieldset>
    {error && <p role="alert" className="text-sm font-semibold text-red-800">{t('games.impostor.errorNames')}</p>}
    <Button className="w-full" onClick={start}>{t('games.impostor.start')}</Button>
  </div>;

  if (!round) return null;
  const name = names[index];
  const isImpostor = index === round.impostorIndex;
  const last = index === names.length - 1;

  if (phase === 'pass') return <div className="space-y-4">
    <p>{t('games.impostor.pass', { name })}</p>
    <button type="button" aria-live="polite" onClick={() => setShown(value => !value)}
      className={`grid min-h-56 w-full place-items-center rounded-3xl border p-6 text-center transition-colors motion-reduce:transition-none ${shown ? (isImpostor ? 'border-ink bg-ink text-white' : 'border-brand bg-accent') : 'border-line bg-white'}`}>
      {!shown ? <span><span className="block text-2xl font-bold">{name}</span><span className="mt-2 block text-sm text-ink-muted">{t('games.impostor.tap')}</span></span>
        : isImpostor ? <span><span className="block text-2xl font-bold">{t('games.impostor.youAre')}</span>
          <span className="mt-2 block">{t('games.impostor.categoryLabel', { category: IMPOSTOR_CATEGORY_NAMES[round.category][locale] })}</span>
          <span className="mt-2 block text-sm opacity-80">{t('games.impostor.blend')}</span></span>
        : <span><span className="block text-sm text-ink-muted">{t('games.impostor.secret')}</span><BilingualText text={round.word} className="mt-1 text-3xl font-bold" /></span>}
    </button>
    <p className="text-center text-xs text-ink-muted">{t('games.impostor.progress', { n: index + 1, total: names.length })}</p>
    <Button className="w-full" disabled={!shown} onClick={() => { setShown(false); if (last) setPhase('talk'); else setIndex(i => i + 1); }}>
      {last ? t('games.impostor.hideStart') : t('games.impostor.hideNext', { name: names[index + 1] })}
    </Button>
  </div>;

  if (phase === 'talk') return <div className="space-y-4">
    <Card className="space-y-3">
      <h2>{t('games.impostor.roundTitle')}</h2>
      <ol className="list-decimal space-y-2 pl-5 text-sm leading-relaxed">
        <li>{t('games.impostor.rule1', { name: names[round.starterIndex] })}</li>
        <li>{t('games.impostor.rule2')}</li>
        <li>{t('games.impostor.rule3')}</li>
      </ol>
    </Card>
    <Button className="w-full" onClick={() => setPhase('reveal')}>{t('games.impostor.reveal')}</Button>
  </div>;

  return <div className="space-y-4">
    <Card className="space-y-3 !border-ink !bg-ink text-center !text-white">
      <p className="text-sm opacity-80">{t('games.impostor.was')}</p>
      <p className="text-3xl font-bold">{names[round.impostorIndex]}</p>
      <p className="text-sm opacity-80">{t('games.impostor.wordWas')}</p>
      <p className="text-xl font-bold">{round.word[locale]} <span lang={locale === 'de' ? 'en' : 'de'} className="font-normal opacity-80">({round.word[locale === 'de' ? 'en' : 'de']})</span></p>
    </Card>
    <p className="text-sm leading-relaxed">{t('games.impostor.lastChance')}</p>
    <div className="flex flex-wrap gap-2">
      <Button onClick={start}>{t('games.impostor.again')}</Button>
      <Button variant="secondary" onClick={() => setPhase('setup')}>{t('games.impostor.change')}</Button>
    </div>
  </div>;
}
