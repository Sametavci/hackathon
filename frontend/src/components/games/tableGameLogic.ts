import { IMPOSTOR_CATEGORIES, IMPOSTOR_WORDS, type Bilingual, type ImpostorCategory, type ImpostorChoice } from './tableGameContent';

export const MIN_PLAYERS = 3;
export const MAX_PLAYERS = 6;
export const COMMON_GROUND_SECONDS = 180;

export type Random = () => number;
export const pick = <T,>(list: readonly T[], random: Random = Math.random): T => list[Math.floor(random() * list.length)];

export interface ImpostorRound { word: Bilingual; category: ImpostorCategory; impostorIndex: number; starterIndex: number }

/** Everyone but the impostor gets the same secret word; a random player starts the talking round. */
export function newImpostorRound(playerCount: number, choice: ImpostorChoice, random: Random = Math.random): ImpostorRound {
  if (playerCount < MIN_PLAYERS || playerCount > MAX_PLAYERS) throw new RangeError(`Impostor needs ${MIN_PLAYERS}-${MAX_PLAYERS} players`);
  const category = choice === 'Mixed' ? pick(IMPOSTOR_CATEGORIES, random) : choice;
  return {
    category, word: pick(IMPOSTOR_WORDS[category], random),
    impostorIndex: Math.floor(random() * playerCount), starterIndex: Math.floor(random() * playerCount),
  };
}

/** Names for the setup screen: the given names, padded or cut to fit the player limits. */
export function startingPlayers(names: readonly string[], fallback: (n: number) => string): string[] {
  const list = names.map(name => name.trim()).filter(Boolean).slice(0, MAX_PLAYERS);
  while (list.length < MIN_PLAYERS) list.push(fallback(list.length + 1));
  return list;
}

export type TruthsError = 'games.truths.errorMissing' | 'games.truths.errorLie';
export function validateStatements(items: readonly string[], lieIndex: number | null): TruthsError | null {
  if (items.length !== 3 || items.some(item => !item.trim())) return 'games.truths.errorMissing';
  if (lieIndex === null || lieIndex < 0 || lieIndex > 2) return 'games.truths.errorLie';
  return null;
}

export const nextTurn = (turn: number, playerCount: number) => (turn + 1) % Math.max(1, playerCount);

export const formatClock = (seconds: number) => `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
