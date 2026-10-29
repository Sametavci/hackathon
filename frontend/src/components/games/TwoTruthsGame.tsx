import { useState } from 'react';
import { useI18n } from '../../i18n';
import Button from '../Button';
import Card from '../Card';
import BilingualText from './BilingualText';
import { shuffleDeck } from './icebreakerDeck';
import { TRUTH_IDEAS, type Bilingual } from './tableGameContent';
import { nextTurn, pick, validateStatements, type TruthsError } from './tableGameLogic';

type Phase = 'write' | 'loud' | 'guess' | 'reveal';

export default function TwoTruthsGame({ players }: { players: string[] }) {
  const { t } = useI18n();
  const names = players.length ? players : [1, 2, 3, 4].map(n => t('games.player', { n }));
  const [turn, setTurn] = useState(0);
  const [phase, setPhase] = useState<Phase>('write');
  const [items, setItems] = useState(['', '', '']);
  const [lie, setLie] = useState<number | null>(null);
  const [order, setOrder] = useState([0, 1, 2]);
  const [guess, setGuess] = useState<number | null>(null);
  const [error, setError] = useState<TruthsError | null>(null);
  const [idea, setIdea] = useState<Bilingual | null>(null);
  const name = names[turn % names.length];
  const next = names[nextTurn(turn, names.length)];

  // Statements stay in this component only; nothing is sent or stored.
  function advance(keepPhase: Phase) {
    setTurn(value => nextTurn(value, names.length)); setItems(['', '', '']); setLie(null); setGuess(null);
    setError(null); setIdea(keepPhase === 'loud' ? pick(TRUTH_IDEAS) : null); setPhase(keepPhase);
  }
  function ready() {
    const problem = validateStatements(items, lie);
    setError(problem);
    if (!problem) { setItems(list => list.map(item => item.trim())); setOrder(shuffleDeck([0, 1, 2])); setPhase('guess'); }
  }

  if (phase === 'write') return <div className="space-y-4">
    <p className="leading-relaxed">{t('games.truths.intro')}</p>
    <Card className="space-y-4">
      <h2>{t('games.truths.turn', { name })}</h2>
      <p className="text-sm text-ink-muted">{t('games.truths.lookAway')}</p>
      {items.map((item, i) => <div key={i} className="space-y-2">
        <label htmlFor={`truth-${i}`} className="block text-sm font-bold">{t('games.truths.statement', { n: i + 1 })}</label>
        <input id={`truth-${i}`} className="field" maxLength={90} value={item} autoComplete="off"
          onChange={e => { setItems(list => list.map((v, j) => j === i ? e.target.value : v)); setError(null); }} />
        <label className="flex min-h-11 cursor-pointer items-center gap-2 text-sm">
          <input type="radio" name="truths-lie" className="h-5 w-5 accent-brand" checked={lie === i} onChange={() => { setLie(i); setError(null); }} />
          {t('games.truths.isLie')}
        </label>
      </div>)}
      {error && <p role="alert" className="text-sm font-semibold text-red-800">{t(error)}</p>}
      <Button className="w-full" onClick={ready}>{t('games.truths.ready')}</Button>
    </Card>
    <div className="flex flex-wrap gap-2">
      <Button variant="secondary" onClick={() => setIdea(pick(TRUTH_IDEAS))}>{t('games.truths.idea')}</Button>
      <Button variant="secondary" onClick={() => { setIdea(pick(TRUTH_IDEAS)); setPhase('loud'); }}>{t('games.truths.loud')}</Button>
    </div>
    {idea && <p role="status" className="rounded-2xl bg-brand-soft p-3 text-sm">{t('games.truths.ideaText')} <BilingualText text={idea} className="font-semibold" /></p>}
  </div>;

  if (phase === 'loud') return <div className="space-y-4">
    <Card className="space-y-3">
      <h2>{t('games.truths.turn', { name })}</h2>
      <p className="leading-relaxed">{t('games.truths.loudBody', { name })}</p>
      {idea && <p className="rounded-2xl bg-brand-soft p-3 text-sm">{t('games.truths.ideaText')} <BilingualText text={idea} className="font-semibold" /></p>}
    </Card>
    <Button className="w-full" onClick={() => advance('loud')}>{t('games.truths.next', { name: next })}</Button>
  </div>;

  const revealed = phase === 'reveal';
  return <div className="space-y-4">
    <p role={revealed ? 'status' : undefined} className="font-semibold">
      {!revealed ? t('games.truths.guess', { name }) : guess === lie ? t('games.truths.found') : t('games.truths.fooled', { name })}
    </p>
    <div className="grid gap-2">{order.map(i => <button key={i} type="button" disabled={revealed} onClick={() => { setGuess(i); setPhase('reveal'); }}
      className={`min-h-14 rounded-2xl border p-4 text-left text-sm ${revealed ? (i === lie ? 'border-red-300 bg-red-50' : 'border-brand bg-brand-soft') : 'border-line bg-white hover:border-brand'}`}>
      {revealed && <strong>{t(i === lie ? 'games.truths.lie' : 'games.truths.true')}: </strong>}{items[i]}
    </button>)}</div>
    {revealed && <Button className="w-full" onClick={() => advance('write')}>{t('games.truths.next', { name: next })}</Button>}
  </div>;
}
