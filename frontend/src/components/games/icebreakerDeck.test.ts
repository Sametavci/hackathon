import { describe, expect, it } from 'vitest';
import classics from '../../../../backend/src/main/resources/cards/icebreakers.en.json';
import type { CardsResponse } from '../../api/types';
import { buildIcebreakerDeck, interestFirstDeck, shuffleDeck } from './icebreakerDeck';

const response: CardsResponse = {
  groupId: 'group', language: 'en', source: 'AI',
  cards: [{ id: 'c1', text: 'Which game would you recommend?', topic: 'gaming' }],
};

describe('prototype icebreaker deck', () => {
  it('includes all ten supplied classics alongside the server questions', () => {
    const deck = buildIcebreakerDeck(response);
    expect(deck).toHaveLength(11);
    expect(deck.map(card => card.text)).toEqual(expect.arrayContaining(classics));
    expect(deck).toContainEqual(response.cards[0]);
    expect(new Set(deck.map(card => card.id)).size).toBe(deck.length);
    expect(response.cards).toHaveLength(1);
  });

  it('deduplicates a classic that the server already returned', () => {
    const deck = buildIcebreakerDeck({ ...response, cards: [{ id: 'c1', text: `  ${classics[0].toUpperCase()}  `, topic: 'generic' }] });
    expect(deck).toHaveLength(10);
    expect(deck[0].id).toBe('c1');
  });

  it('does not mix English classics into a different shared language', () => {
    const translated: CardsResponse = { ...response, language: 'tr', source: 'FALLBACK',
      cards: [{ id: 'c1', text: 'En sevdiğin kampüs köşesi neresi?', topic: 'generic' }] };
    expect(buildIcebreakerDeck(translated)).toEqual(translated.cards);
  });

  it('reshuffles the same questions without losing cards or mutating the cache', () => {
    const deck = buildIcebreakerDeck(response);
    const original = [...deck];
    const shuffled = shuffleDeck(deck, () => 0);
    expect(shuffled).not.toEqual(deck);
    expect(new Set(shuffled)).toEqual(new Set(deck));
    expect(deck).toEqual(original);
  });

  it('makes shuffle visibly change the order even if the random draw retained it', () => {
    expect(shuffleDeck([1, 2, 3], () => 0.999)).not.toEqual([1, 2, 3]);
    expect(shuffleDeck([])).toEqual([]);
    expect(shuffleDeck([1])).toEqual([1]);
  });

  it('puts the interest-based server questions before the classics', () => {
    const ai: CardsResponse = { ...response, cards: [
      { id: 'c1', text: 'Best trip so far?', topic: 'travel' }, { id: 'c2', text: 'Favourite co-op game?', topic: 'gaming' }] };
    const deck = interestFirstDeck(ai, () => 0.5);
    expect(new Set(deck.slice(0, 2).map(card => card.id))).toEqual(new Set(['c1', 'c2']));
    expect(deck.slice(2).map(card => card.text)).toEqual(expect.arrayContaining(classics));
    expect(deck).toHaveLength(12);
  });
});
