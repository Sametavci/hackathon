import { describe, expect, it } from 'vitest';
import { addDays, addMonths, berlinToday, monthDates, weekDates, weekStart } from './calendarDates';
describe('Berlin registration calendar', () => {
  it('starts the current week on Monday, including weekends and year boundaries', () => {
    expect(weekStart('2026-10-29')).toBe('2026-10-26');
    expect(weekStart('2027-01-03')).toBe('2026-12-28');
    expect(weekDates('2026-10-29')).toEqual(['2026-10-26', '2026-10-27', '2026-10-28', '2026-10-29', '2026-10-30', '2026-10-31', '2026-11-01']);
  });
  it('uses Berlin midnight and daylight saving time rather than the host date', () => {
    expect(berlinToday(new Date('2026-10-28T23:30:00Z'))).toBe('2026-10-29');
    expect(berlinToday(new Date('2026-06-01T22:30:00Z'))).toBe('2026-06-02');
    expect(addDays('2026-03-28', 1)).toBe('2026-03-29');
    expect(addDays('2026-03-29', 1)).toBe('2026-03-30');
  });
  it('includes leap days and complete Monday–Sunday rows', () => {
    const days = monthDates('2028-02-01');
    expect(days).toContain('2028-02-29');
    expect(days.length % 7).toBe(0);
    expect(weekStart(days[0])).toBe(days[0]);
    expect(monthDates('2026-02-01')).toHaveLength(35);
    expect(addMonths('2026-12-31', 1)).toBe('2027-01-01');
    expect(addMonths('2027-01-31', -1)).toBe('2026-12-01');
  });
  it('shows today even when available slots only start next month', () => {
    const today = '2026-10-29';
    const available = ['2026-11-02'];
    expect(weekDates(today)).toContain(today);
    expect(available).not.toContain(today);
    expect(monthDates(today)).toContain('2026-10-01');
  });
});
