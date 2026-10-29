import { describe, expect, it } from 'vitest';
import { t } from '.';

describe('t', () => {
  it('returns the English string for a key', () => {
    expect(t('app.slogan')).toBe('Bring a Friend. Meet Two.');
  });
});
