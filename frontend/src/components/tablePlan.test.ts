import { describe, expect, it } from 'vitest';
import { meetingTime, tableLocation } from './tablePlan';

describe('table arrival instructions', () => {
  it('meets five minutes before lunch in local civil time', () => {
    expect(meetingTime('2026-10-29', '12:15')).toEqual({ date: '2026-10-29', time: '12:10' });
    expect(meetingTime('2026-07-01', '11:30')).toEqual({ date: '2026-07-01', time: '11:25' });
  });
  it('handles midnight and year boundaries', () => {
    expect(meetingTime('2027-01-01', '00:03')).toEqual({ date: '2026-12-31', time: '23:58' });
  });
  it('keeps venue addresses intact and accepts legacy locations', () => {
    expect(tableLocation('Mensa · Street 12 · Lunch² Table 2')).toEqual({ venue: 'Mensa · Street 12', number: 2 });
    expect(tableLocation('Mensa · Street 12')).toEqual({ venue: 'Mensa · Street 12', number: null });
  });
});
