import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createAutoAdvance, isAutomaticStep, toggleDaySlot } from './registrationFlow';

beforeEach(() => vi.useFakeTimers());
afterEach(() => vi.useRealTimers());

describe('automatic registration navigation', () => {
  it('only advances single-choice questions automatically', () => {
    expect(Array.from({ length: 9 }, (_, step) => step).filter(isAutomaticStep)).toEqual([0, 1, 4, 5]);
  });
  it('leaves time for selection feedback and ignores repeated taps', () => {
    const advance = vi.fn();
    const transition = createAutoAdvance(advance);
    transition.schedule(1); transition.schedule(2);
    expect(transition.pending()).toBe(true);
    vi.advanceTimersByTime(219); expect(advance).not.toHaveBeenCalled();
    vi.advanceTimersByTime(1); expect(advance).toHaveBeenCalledTimes(1); expect(advance).toHaveBeenCalledWith(1);
    expect(transition.pending()).toBe(false);
    vi.runAllTimers(); expect(advance).toHaveBeenCalledTimes(1);
  });
  it('cannot jump forward after navigation away or unmount', () => {
    const advance = vi.fn();
    const transition = createAutoAdvance(advance);
    transition.schedule(6); transition.cancel();
    vi.runAllTimers(); expect(advance).not.toHaveBeenCalled();
    transition.schedule(2); vi.runAllTimers();
    expect(advance).toHaveBeenCalledTimes(1); expect(advance).toHaveBeenCalledWith(2);
  });
});

describe('single-day lunch choices', () => {
  it('keeps multiple times on one day and allows removing a selected time', () => {
    const first = toggleDaySlot([], '2026-12-15T11:00');
    const both = toggleDaySlot(first, '2026-12-15T11:45');
    expect(both).toEqual(['2026-12-15T11:00', '2026-12-15T11:45']);
    expect(toggleDaySlot(both, '2026-12-15T11:00')).toEqual(['2026-12-15T11:45']);
    expect(first).toEqual(['2026-12-15T11:00']);
  });
  it('replaces all previous-day times when a different day is selected', () => {
    expect(toggleDaySlot(['2026-12-15T11:00', '2026-12-15T11:45'], '2026-12-16T11:00'))
      .toEqual(['2026-12-16T11:00']);
  });
});
