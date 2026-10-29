import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import GameHub from './GameHub';
import ImpostorGame from './ImpostorGame';
import WouldYouRatherGame from './WouldYouRatherGame';
import CommonGroundGame from './CommonGroundGame';
import { IMPOSTOR_WORDS, WOULD_YOU_RATHER } from './tableGameContent';

const inRouter = (node: JSX.Element) => renderToStaticMarkup(<MemoryRouter>{node}</MemoryRouter>);

describe('game hub', () => {
  it('links all five games inside a lunch group', () => {
    const html = inRouter(<GameHub basePath="/group/g1/play" />);
    for (const id of ['icebreakers', 'impostor', 'two-truths', 'would-you-rather', 'common-ground']) {
      expect(html).toContain(`href="/group/g1/play/${id}"`);
    }
    expect(html).not.toContain('Coming soon');
  });
  it('disables group-only games on the standalone page', () => {
    const html = inRouter(<GameHub basePath="/games" standalone />);
    expect(html).not.toContain('href="/games/icebreakers"');
    expect(html).toContain('href="/games/impostor"');
    expect(html).toContain('Available once you have a lunch group.');
  });
});

describe('impostor setup', () => {
  it('pre-fills the group names and never shows a secret word before the game starts', () => {
    const html = renderToStaticMarkup(<ImpostorGame players={['Lena', 'Sam', 'Aiko', 'Omar']} />);
    for (const name of ['Lena', 'Sam', 'Aiko', 'Omar']) expect(html).toContain(`value="${name}"`);
    const words = Object.values(IMPOSTOR_WORDS).flat().map(word => word.en);
    expect(words.some(word => html.includes(`>${word}<`))).toBe(false);
  });
  it('fills up to three players when a table has fewer names', () => {
    const html = renderToStaticMarkup(<ImpostorGame players={['Lena']} />);
    expect(html).toContain('value="Lena"');
    expect(html).toContain('value="Player 3"');
  });
});

describe('content games', () => {
  it('shows a would-you-rather question in English with a German subtitle', () => {
    const html = renderToStaticMarkup(<WouldYouRatherGame />);
    expect(WOULD_YOU_RATHER.some(([a, b]) => html.includes(a.en) && html.includes(b.de))).toBe(true);
    expect(html).toContain('lang="de"');
  });
  it('starts common ground at three minutes with labelled inputs', () => {
    const html = renderToStaticMarkup(<CommonGroundGame />);
    expect(html).toContain('3:00');
    expect(html).toContain('for="common-0"');
  });
});
