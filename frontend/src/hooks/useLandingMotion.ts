import { useEffect, useMemo, useState } from 'react';
import { createLandingCycle } from './landingCycle';
export function useLandingMotion() {
  const [scene, setScene] = useState(0);
  const [paused, setPaused] = useState(false);
  const [reduced, setReduced] = useState(() => window.matchMedia('(prefers-reduced-motion: reduce)').matches);
  const [visible, setVisible] = useState(() => document.visibilityState !== 'hidden');
  const cycle = useMemo(() => createLandingCycle(setScene), []);
  const running = !paused && !reduced && visible;
  useEffect(() => {
    const media = window.matchMedia('(prefers-reduced-motion: reduce)');
    const motion = () => setReduced(media.matches);
    const visibility = () => setVisible(document.visibilityState !== 'hidden');
    media.addEventListener('change', motion); document.addEventListener('visibilitychange', visibility);
    return () => { media.removeEventListener('change', motion); document.removeEventListener('visibilitychange', visibility); cycle.dispose(); };
  }, [cycle]);
  useEffect(() => { cycle.setRunning(running); return () => cycle.setRunning(false); }, [cycle, running]);
  return { scene, paused, reduced, running, toggle: () => setPaused(value => !value) };
}
