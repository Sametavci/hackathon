import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createAutoAdvance, isAutomaticStep } from './registrationFlow';

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
