import { useMemo, useRef, useState } from 'react';
import { getCards } from '../api/endpoints';
import { useResource } from '../hooks/useResource';
import { useI18n } from '../i18n';
import { buildIcebreakerDeck, shuffleDeck } from './games/icebreakerDeck';
import type { CardsResponse } from '../api/types';
import Button from './Button';
import ErrorBanner from './ErrorBanner';
import LoadState from './LoadState';
export default function ConversationCards({ groupId }: { groupId: string }) {
  const { t } = useI18n();
  const cards = useResource(`cards-${groupId}`, () => getCards(groupId));
  const [index, setIndex] = useState(0);
  const [finished, setFinished] = useState(false);
  const [shuffled, setShuffled] = useState<CardsResponse['cards'] | null>(null);
  const start = useRef<{ x: number; y: number } | null>(null);
  const initialDeck = useMemo(() => cards.data ? shuffleDeck(buildIcebreakerDeck(cards.data)) : [], [cards.data]);
  const deck = shuffled ?? initialDeck;
  const activeIndex = Math.min(index, Math.max(0, deck.length - 1));

  function shuffle() {
    setShuffled(shuffleDeck(deck));
    setIndex(0);
    setFinished(false);
    start.current = null;
  }

  function next() {
    if (activeIndex === deck.length - 1) setFinished(true);
    else setIndex(previous => Math.min(deck.length - 1, previous + 1));
  }

  return (
    <section id="conversation-cards" className="space-y-5" aria-labelledby="cards-title">
      <header><h1 id="cards-title" className="text-[1.75rem] font-bold">{t('games.icebreakers')}</h1></header>
      {!cards.data ? <LoadState {...cards} /> : !deck.length
        ? <ErrorBanner error={new Error(t('cards.empty'))} onRetry={cards.reload} />
        : finished ? <div className="space-y-4 rounded-2xl border border-line bg-brand-soft p-6">
          <h2>{t('games.finished')}</h2>
          <p className="text-sm leading-relaxed">{t('games.finishedBody')}</p>
          <Button className="w-full" onClick={shuffle}>{t('games.replay')}</Button>
        </div> : <>
          <div className="relative min-h-64 touch-pan-y rounded-2xl border border-line bg-brand-soft p-6"
            onPointerDown={event => { start.current = { x: event.clientX, y: event.clientY }; }}
            onPointerCancel={() => { start.current = null; }}
            onPointerUp={event => {
              const origin = start.current;
              start.current = null;
              if (!origin) return;
              const delta = event.clientX - origin.x;
              if (Math.abs(delta) > 50 && Math.abs(delta) > Math.abs(event.clientY - origin.y)) {
                setIndex(previous => Math.max(0, Math.min(deck.length - 1, previous + (delta < 0 ? 1 : -1))));
              }
            }}>
            <span className="inline-block rounded-full bg-white/80 px-3 py-1 text-xs font-bold">
              {t(cards.data.source === 'AI' ? 'games.mixed' : 'cards.classic')}
            </span>
            <p className="mt-8 break-words text-2xl font-semibold leading-snug" aria-live="polite" aria-atomic="true">{deck[activeIndex].text}</p>
          </div>
          <p className="text-center text-sm font-semibold" role="status">{t('cards.progress')} {activeIndex + 1} {t('cards.of')} {deck.length}</p>
          <div className="grid grid-cols-2 gap-3">
            <Button variant="secondary" disabled={activeIndex === 0} onClick={() => setIndex(previous => Math.max(0, previous - 1))}>
              {t('cards.previous')}
            </Button>
            <Button onClick={next}>{t(activeIndex === deck.length - 1 ? 'games.finish' : 'cards.next')}</Button>
            <Button variant="secondary" onClick={next}>{t('games.skip')}</Button>
            <Button variant="secondary" onClick={shuffle}>{t('games.shuffle')}</Button>
          </div>
        </>}
    </section>
  );
}
