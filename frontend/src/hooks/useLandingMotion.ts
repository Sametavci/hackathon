import { useEffect, useMemo, useState } from 'react';
import { createLandingCycle } from './landingCycle';
export function useLandingMotion() {
  const [scene, setScene] = useState(0);
  const [secondScene, setSecondScene] = useState(0);
  const [reduced, setReduced] = useState(() => window.matchMedia('(prefers-reduced-motion: reduce)').matches);
  const [visible, setVisible] = useState(() => document.visibilityState !== 'hidden');
  const cycle = useMemo(() => createLandingCycle(setScene), []);
  const secondCycle = useMemo(() => createLandingCycle(setSecondScene), []);
  const running = !reduced && visible;
  useEffect(() => {
    const media = window.matchMedia('(prefers-reduced-motion: reduce)');
    const motion = () => setReduced(media.matches);
    const visibility = () => setVisible(document.visibilityState !== 'hidden');
    media.addEventListener('change', motion); document.addEventListener('visibilitychange', visibility);
    return () => { media.removeEventListener('change', motion); document.removeEventListener('visibilitychange', visibility); cycle.dispose(); secondCycle.dispose(); };
  }, [cycle, secondCycle]);
  useEffect(() => { cycle.setRunning(running); return () => cycle.setRunning(false); }, [cycle, running]);
  useEffect(() => {
    if (!running) { secondCycle.setRunning(false); return; }
    const offset = setTimeout(() => secondCycle.setRunning(true), 900);
    return () => { clearTimeout(offset); secondCycle.setRunning(false); };
  }, [secondCycle, running]);
  return { scene, scenes: [scene, secondScene] as const, running };
}
