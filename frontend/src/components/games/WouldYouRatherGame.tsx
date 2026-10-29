import { useState } from 'react';
import { useI18n } from '../../i18n';
import Button from '../Button';
import BilingualText from './BilingualText';
import { shuffleDeck } from './icebreakerDeck';
import { WOULD_YOU_RATHER } from './tableGameContent';

const allQuestions = () => shuffleDeck(WOULD_YOU_RATHER.map((_, i) => i));

export default function WouldYouRatherGame() {
  const { t } = useI18n();
  const [order, setOrder] = useState(allQuestions);
  const [index, setIndex] = useState(0);
  const [a, b] = WOULD_YOU_RATHER[order[index]];
  function next() {
    // A finished deck is reshuffled so the table can keep going.
    if (index + 1 >= order.length) { setOrder(allQuestions()); setIndex(0); } else setIndex(i => i + 1);
  }
  return <div className="space-y-4">
    <p className="text-sm leading-relaxed text-ink-muted">{t('games.rather.how')}</p>
    <article className="space-y-4 rounded-3xl border border-line bg-accent p-6" aria-live="polite" aria-atomic="true">
      <p className="eyebrow">{t('games.rather.prompt')}</p>
      <BilingualText text={a} className="text-xl font-bold" />
      <p className="text-center text-sm font-bold uppercase tracking-wide text-ink-muted">{t('games.rather.or')}</p>
      <BilingualText text={b} suffix="?" className="text-xl font-bold" />
    </article>
    <p className="text-center text-xs font-semibold" role="status">{t('games.rather.progress', { n: index + 1, total: order.length })}</p>
    <Button className="w-full" onClick={next}>{t('games.rather.next')}</Button>
  </div>;
}
