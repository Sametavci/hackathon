import { describe, expect, it } from 'vitest';
import { createMensaCatalog, defaultMensaId } from './mensaCatalog';
import { errorsForStep, stepForField } from './registrationFlow';
import { mockMeta } from './mockData';
import { validateRegistration } from './validation';
import type { RegistrationRequest } from './types';
const { mensas, slots } = createMensaCatalog('2026-11-02');
const valid: RegistrationRequest = { displayName: 'İpek', category: 'DOMESTIC', campus: 'WUERZBURG', mensaId: 'WUERZBURG_ROENTGENRING', languages: ['en'], interests: ['travel'], slotIds: ['2026-11-02T11:30'], studyField: null, inviteCode: null, consent: true };
const minutes = (time: string) => Number(time.slice(0, 2)) * 60 + Number(time.slice(3));
describe('Mensa availability', () => {
  it('contains four Würzburg Mensas and one Schweinfurt Mensa, each with one campus default', () => {
    expect(mensas.filter(m => m.campus === 'WUERZBURG')).toHaveLength(4);
    expect(mensas.filter(m => m.campus === 'SCHWEINFURT')).toHaveLength(1);
    expect(defaultMensaId('WUERZBURG')).toBe('WUERZBURG_ROENTGENRING');
    expect(defaultMensaId('SCHWEINFURT')).toBe('SCHWEINFURT_THWS');
  });
  it('uses unique shared slot IDs and keeps five weekdays even when the window begins on Saturday', () => {
    const catalog = createMensaCatalog('2026-11-07');
    expect([...new Set(catalog.slots.map(s => s.date))]).toEqual(['2026-11-09', '2026-11-10', '2026-11-11', '2026-11-12', '2026-11-13']);
    expect(new Set(slots.map(s => s.id)).size).toBe(slots.length);
  });
  it.each(mensas.map(m => [m.id, m] as const))('%s provides only 45-minute lunches within service hours', (_, mensa) => {
    for (const id of mensa.slotIds) {
      const slot = slots.find(s => s.id === id)!;
      const h = mensa.hours.find(h => h.dayOfWeek === new Date(`${slot.date}T12:00:00Z`).getUTCDay())!;
      expect(minutes(slot.end) - minutes(slot.start)).toBe(45);
      expect((minutes(slot.start) - minutes(h.lunchStart)) % 45).toBe(0);
      expect(slot.start < h.foodUntil).toBe(true);
      expect(slot.end <= h.closes).toBe(true);
    }
  });
  it('uses lunch service instead of the Schweinfurt cafeteria opening and respects Friday closing', () => {
    const ids = mensas.find(m => m.id === 'SCHWEINFURT_THWS')!.slotIds;
    expect(ids).toContain('2026-11-02T11:00');
    expect(ids.some(id => id.endsWith('T08:00'))).toBe(false);
    expect(ids).toContain('2026-11-02T13:15');
    expect(ids).toContain('2026-11-06T13:15');
    expect(slots.find(s => s.id === '2026-11-06T13:15')?.end).toBe('14:00');
    expect(ids).not.toContain('2026-11-06T14:00');
  });
  it('accepts the last full lunch ending at closing and rejects one that would overrun', () => {
    const ids = mensas.find(m => m.id === 'WUERZBURG_STUDENTENHAUS')!.slotIds;
    expect(ids).toContain('2026-11-02T13:30');
    expect(ids).not.toContain('2026-11-02T14:15');
    const roentgenring = mensas.find(m => m.id === 'WUERZBURG_ROENTGENRING')!.slotIds;
    expect(roentgenring).toContain('2026-11-02T13:45');
    expect(roentgenring).not.toContain('2026-11-02T14:30');
  });
});
describe('wizard validation', () => {
  it('only validates the current question while the rest of the form is incomplete', () => {
    const partial = { ...valid, displayName: '', consent: false, languages: [], interests: [], slotIds: [], mensaId: '' };
    expect(errorsForStep(partial, mockMeta, 0)).toEqual([]);
    expect(errorsForStep(partial, mockMeta, 2).map(e => e.field)).toEqual(['languages']);
    expect(errorsForStep(partial, mockMeta, 5).map(e => e.field)).toEqual(['mensaId']);
    expect(stepForField('slotIds')).toBe(6);
    expect(stepForField('consent')).toBe(8);
  });
  it('accepts a complete registration and rejects cross-campus or unsupported Mensa/time combinations', () => {
    expect(validateRegistration(valid, mockMeta)).toEqual([]);
    expect(validateRegistration({ ...valid, mensaId: 'SCHWEINFURT_THWS' }, mockMeta).map(e => e.field)).toContain('mensaId');
    expect(validateRegistration({ ...valid, slotIds: ['2026-11-02T11:00'] }, mockMeta).map(e => e.field)).toContain('slotIds');
  });
});
