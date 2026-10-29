import { useRef, useState } from 'react';
import { getCards } from '../api/endpoints';
import { useResource } from '../hooks/useResource';
import { t } from '../i18n/en';
import Button from './Button';
import ErrorBanner from './ErrorBanner';
import LoadState from './LoadState';
export default function ConversationCards({ groupId }: { groupId: string }) {
  const cards = useResource(`cards-${groupId}`, () => getCards(groupId));
  const [index, setIndex] = useState(0);
  const start = useRef<{ x: number; y: number } | null>(null);
  const deck = cards.data?.cards ?? [];
  const activeIndex = Math.min(index, Math.max(0, deck.length - 1));
  return <section id="conversation-cards" className="scroll-mt-6 space-y-4" aria-labelledby="cards-title"><div><p className="eyebrow">{t('group.cards')}</p><h2 id="cards-title" className="mt-2 text-3xl">{t('cards.title')}</h2><p className="mt-2 text-sm leading-relaxed text-ink-muted">{t('cards.subtitle')}</p></div>
    {!cards.data ? <LoadState {...cards} /> : !deck.length ? <ErrorBanner error={new Error(t('cards.empty'))} onRetry={cards.reload} /> : <>
      <div className="relative min-h-64 touch-pan-y rounded-3xl border border-line bg-accent p-6" onPointerDown={e => { start.current = { x: e.clientX, y: e.clientY }; }} onPointerCancel={() => { start.current = null; }} onPointerUp={e => { const origin = start.current; start.current = null; if (!origin) return; const delta = e.clientX - origin.x; if (Math.abs(delta) > 50 && Math.abs(delta) > Math.abs(e.clientY - origin.y)) setIndex(n => Math.max(0, Math.min(deck.length - 1, n + (delta < 0 ? 1 : -1)))); }}>
        <span className="inline-block rounded-full bg-white/80 px-3 py-1 text-xs font-bold">{t(cards.data.source === 'AI' ? 'cards.ai' : 'cards.classic')}</span><p className="mt-8 text-2xl font-bold leading-snug" aria-live="polite" aria-atomic="true">{deck[activeIndex].text}</p>
      </div>
      <div className="flex flex-wrap items-center justify-between gap-2"><Button variant="secondary" aria-label={t('cards.previous')} disabled={activeIndex === 0} onClick={() => setIndex(n => n - 1)}>←</Button><span className="text-xs font-semibold" role="status">{t('cards.progress')} {activeIndex + 1} {t('cards.of')} {deck.length}</span><Button variant="secondary" aria-label={t('cards.next')} disabled={activeIndex === deck.length - 1} onClick={() => setIndex(n => n + 1)}>→</Button></div>
      <div aria-hidden="true" className="flex justify-center gap-2">{deck.map((card, i) => <span key={card.id} className={`h-2 w-2 rounded-full ${i === activeIndex ? 'bg-ink' : 'bg-line'}`} />)}</div>
    </>}
  </section>;
}
