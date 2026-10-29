import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createLandingCycle } from './landingCycle';
beforeEach(() => vi.useFakeTimers());
afterEach(() => vi.useRealTimers());
describe('landing scene cycle', () => {
  it('uses a single timer, changes paired scenes at 1800ms, and never repeats the current scene', () => {
    const update = vi.fn(); const cycle = createLandingCycle(update, () => 0);
    cycle.setRunning(true); cycle.setRunning(true);
    expect(vi.getTimerCount()).toBe(1);
    vi.advanceTimersByTime(1799); expect(update).not.toHaveBeenCalled();
    vi.advanceTimersByTime(1); expect(update).toHaveBeenLastCalledWith(1);
    vi.advanceTimersByTime(1800); expect(update).toHaveBeenLastCalledWith(2);
    vi.advanceTimersByTime(1800); expect(update).toHaveBeenLastCalledWith(0);
    cycle.dispose(); expect(vi.getTimerCount()).toBe(0);
  });
  it('supports mixed scene order and the upper delay boundary', () => {
    const update = vi.fn(); const cycle = createLandingCycle(update, () => .999999);
    cycle.setRunning(true);
    vi.advanceTimersByTime(2799); expect(update).not.toHaveBeenCalled();
    vi.advanceTimersByTime(1); expect(update).toHaveBeenLastCalledWith(2);
    vi.advanceTimersByTime(2800); expect(update).toHaveBeenLastCalledWith(1);
    cycle.dispose();
  });
  it('stops while hidden or reduced-motion is active, resumes once, and cleans up', () => {
    const update = vi.fn(); const cycle = createLandingCycle(update, () => 0);
    cycle.setRunning(true); vi.advanceTimersByTime(1800);
    cycle.setRunning(false); vi.advanceTimersByTime(12000); expect(update).toHaveBeenCalledTimes(1);
    cycle.setRunning(true); vi.advanceTimersByTime(1800);
    expect(update).toHaveBeenLastCalledWith(2); expect(update).toHaveBeenCalledTimes(2);
    cycle.dispose(); vi.runAllTimers(); expect(update).toHaveBeenCalledTimes(2);
  });
});
