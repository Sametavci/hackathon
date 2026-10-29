import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createLandingCycle } from './landingCycle';
beforeEach(() => vi.useFakeTimers());
afterEach(() => vi.useRealTimers());
describe('landing scene cycle', () => {
  it('changes both headline rows and table scene every four seconds and wraps after three scenes', () => {
    const update = vi.fn(); const cycle = createLandingCycle(update);
    cycle.setRunning(true); cycle.setRunning(true);
    expect(vi.getTimerCount()).toBe(1);
    vi.advanceTimersByTime(3999); expect(update).not.toHaveBeenCalled();
    vi.advanceTimersByTime(1); expect(update).toHaveBeenLastCalledWith(1);
    vi.advanceTimersByTime(4000); expect(update).toHaveBeenLastCalledWith(2);
    vi.advanceTimersByTime(4000); expect(update).toHaveBeenLastCalledWith(0);
    cycle.dispose(); expect(vi.getTimerCount()).toBe(0);
  });
  it('does not advance while paused, hidden or reduced-motion is active, then resumes once', () => {
    const update = vi.fn(); const cycle = createLandingCycle(update);
    cycle.setRunning(true); vi.advanceTimersByTime(4000);
    cycle.setRunning(false); vi.advanceTimersByTime(12000); expect(update).toHaveBeenCalledTimes(1);
    cycle.setRunning(true); vi.advanceTimersByTime(4000);
    expect(update).toHaveBeenLastCalledWith(2); expect(update).toHaveBeenCalledTimes(2);
    cycle.dispose(); vi.runAllTimers(); expect(update).toHaveBeenCalledTimes(2);
  });
});
