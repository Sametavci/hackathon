import { describe, expect, it } from 'vitest';
import { IMPOSTOR_CATEGORIES, IMPOSTOR_CATEGORY_NAMES, IMPOSTOR_WORDS, TRUTH_IDEAS, WOULD_YOU_RATHER, type Bilingual } from './tableGameContent';
import { COMMON_GROUND_SECONDS, formatClock, MAX_PLAYERS, MIN_PLAYERS, newImpostorRound, nextTurn, pick, startingPlayers, validateStatements } from './tableGameLogic';

const filled = (text: Bilingual) => text.en.trim().length > 0 && text.de.trim().length > 0;
/** Deterministic random source cycling through the given values. */
const sequence = (...values: number[]) => { let i = 0; return () => values[i++ % values.length]; };

describe('table game content', () => {
  it('has English and German text everywhere', () => {
    const all = [...Object.values(IMPOSTOR_CATEGORY_NAMES), ...Object.values(IMPOSTOR_WORDS).flat(), ...TRUTH_IDEAS, ...WOULD_YOU_RATHER.flat()];
    expect(all.every(filled)).toBe(true);
  });
  it('gives every impostor category at least 8 distinct words', () => {
    for (const category of IMPOSTOR_CATEGORIES) {
      const words = IMPOSTOR_WORDS[category].map(word => word.en);
      expect(words.length).toBeGreaterThanOrEqual(8);
      expect(new Set(words).size).toBe(words.length);
    }
  });
  it('has distinct would-you-rather questions', () => {
    const keys = WOULD_YOU_RATHER.map(([a, b]) => `${a.en}|${b.en}`);
    expect(new Set(keys).size).toBe(keys.length);
    expect(keys.length).toBeGreaterThanOrEqual(12);
  });
});

describe('impostor rounds', () => {
  it('picks the word from the chosen category and indices within the table', () => {
    for (let players = MIN_PLAYERS; players <= MAX_PLAYERS; players++) {
      const round = newImpostorRound(players, 'Food', sequence(0.99, 0.5, 0.99));
      expect(round.category).toBe('Food');
      expect(IMPOSTOR_WORDS.Food).toContain(round.word);
      expect(round.impostorIndex).toBeGreaterThanOrEqual(0); expect(round.impostorIndex).toBeLessThan(players);
      expect(round.starterIndex).toBeGreaterThanOrEqual(0); expect(round.starterIndex).toBeLessThan(players);
    }
  });
  it('lets Mixed choose any category, including the last one', () => {
    const round = newImpostorRound(4, 'Mixed', sequence(0.999, 0, 0, 0));
    expect(round.category).toBe(IMPOSTOR_CATEGORIES[IMPOSTOR_CATEGORIES.length - 1]);
    expect(IMPOSTOR_WORDS[round.category]).toContain(round.word);
  });
  it('rejects tables that are too small or too big', () => {
    expect(() => newImpostorRound(MIN_PLAYERS - 1, 'Food')).toThrow(RangeError);
    expect(() => newImpostorRound(MAX_PLAYERS + 1, 'Food')).toThrow(RangeError);
  });
  it('pads and trims the starting player list', () => {
    expect(startingPlayers(['Lena', ' ', 'Sam'], n => `P${n}`)).toEqual(['Lena', 'Sam', 'P3']);
    expect(startingPlayers(['a', 'b', 'c', 'd', 'e', 'f', 'g'], n => `P${n}`)).toHaveLength(MAX_PLAYERS);
    expect(startingPlayers(['Lena', 'Sam', 'Aiko', 'Omar'], n => `P${n}`)).toEqual(['Lena', 'Sam', 'Aiko', 'Omar']);
  });
  it('picks from the whole list', () => {
    expect(pick(['a', 'b', 'c'], () => 0)).toBe('a');
    expect(pick(['a', 'b', 'c'], () => 0.999)).toBe('c');
  });
});

describe('two truths, one lie', () => {
  it('needs three statements and a marked lie', () => {
    expect(validateStatements(['a', '', 'c'], 1)).toBe('games.truths.errorMissing');
    expect(validateStatements(['a', 'b', 'c'], null)).toBe('games.truths.errorLie');
    expect(validateStatements(['a', 'b', 'c'], 2)).toBeNull();
  });
  it('passes the turn round the table', () => {
    expect([0, 1, 2, 3].map(turn => nextTurn(turn, 4))).toEqual([1, 2, 3, 0]);
  });
});

describe('common ground clock', () => {
  it('formats minutes and seconds', () => {
    expect(formatClock(COMMON_GROUND_SECONDS)).toBe('3:00');
    expect(formatClock(65)).toBe('1:05');
    expect(formatClock(5)).toBe('0:05');
  });
});
