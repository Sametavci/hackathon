import { describe, expect, it } from 'vitest';
import { sortByLabel } from './presentation';
describe('localized option order', () => {
  it('sorts visible labels without mutating catalog order or selected IDs', () => {
    const items = [{ id: 'fr', label: 'Französisch' }, { id: 'ar', label: 'Arabisch' }, { id: 'de', label: 'Deutsch' }];
    const selected = ['fr'];
    expect(sortByLabel(items, item => item.label, 'de').map(item => item.id)).toEqual(['ar', 'de', 'fr']);
    expect(items[0].id).toBe('fr');
    expect(selected).toEqual(['fr']);
  });
  it('handles localized accented labels', () => {
    expect(sortByLabel(['Z', 'Ä', 'B'], label => label, 'de')).toEqual(['Ä', 'B', 'Z']);
  });
});
