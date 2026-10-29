import classicQuestions from '../../../../backend/src/main/resources/cards/icebreakers.en.json';
import type { CardsResponse } from '../../api/types';

type QuestionCard = CardsResponse['cards'][number];

export function buildIcebreakerDeck(response: CardsResponse): QuestionCard[] {
  const classics = response.language === 'en'
    ? classicQuestions.map((text, index) => ({ id: `classic-${index + 1}`, text, topic: 'generic' })) : [];
  const unique = new Map<string, QuestionCard>();
  for (const card of [...response.cards, ...classics]) {
    const key = card.text.trim().replace(/\s+/g, ' ').toLowerCase();
    if (!unique.has(key)) unique.set(key, card);
  }
  return [...unique.values()];
}

/** Display-only shuffle: never mutates the cached API response or calls the LLM. */
export function shuffleDeck<T>(cards: readonly T[], random: () => number = Math.random): T[] {
  const shuffled = [...cards];
  for (let index = shuffled.length - 1; index > 0; index--) {
    const other = Math.floor(random() * (index + 1));
    [shuffled[index], shuffled[other]] = [shuffled[other], shuffled[index]];
  }
  if (shuffled.length > 1 && shuffled.every((card, index) => card === cards[index])) {
    shuffled.push(shuffled.shift()!);
  }
  return shuffled;
}

/** The server's interest-based questions come first (shuffled), then the classics (shuffled). */
export function interestFirstDeck(response: CardsResponse, random: () => number = Math.random): QuestionCard[] {
  const deck = buildIcebreakerDeck(response);
  const own = new Set(response.cards.map(card => card.id));
  return [...shuffleDeck(deck.filter(card => own.has(card.id)), random), ...shuffleDeck(deck.filter(card => !own.has(card.id)), random)];
}
