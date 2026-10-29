import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router-dom';
import BuddySessionChoice from './BuddySessionChoice';
import { getToken, setToken } from '../api/session';
import { memoryStorage } from '../api/testStorage';
import { setLocale } from '../i18n';

beforeEach(() => {
  vi.stubGlobal('localStorage', memoryStorage());
  vi.stubGlobal('window', { location: { origin: 'https://lunch.test', pathname: '/' } });
  setLocale('en');
});
afterEach(() => vi.unstubAllGlobals());

it('offers a separate buddy registration alongside the existing lunch and private return link', () => {
  setToken('existing-participant');
  const html = renderToStaticMarkup(<MemoryRouter><BuddySessionChoice code="INVITE" /></MemoryRouter>);
  expect(html).toContain('Join as a different person');
  expect(html).toContain('href="/register?invite=INVITE&amp;new=1"');
  expect(html).toContain('href="/me"');
  expect(html).toContain('Save');
  expect(getToken()).toBe('existing-participant');
  expect(html).not.toContain('invite=existing-participant');
});
