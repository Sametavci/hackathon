import type { CSSProperties } from 'react';
export default function LunchTable({ scene = 0, running = false }: { scene?: number; running?: boolean }) {
  return <div aria-hidden="true" data-scene={scene} data-running={running} className="lunch-scene mx-auto w-full" style={{ '--motion-state': running ? 'running' : 'paused' } as CSSProperties}>
    <div className="lunch-orbit" />
    <div className="lunch-rotor">
      <div className="lunch-seat lunch-seat-top"><span className="lunch-face" /></div>
      <div className="lunch-seat lunch-seat-right"><span className="lunch-face" /></div>
      <div className="lunch-seat lunch-seat-bottom"><span className="lunch-face" /></div>
      <div className="lunch-seat lunch-seat-left"><span className="lunch-face" /></div>
      <div className="lunch-table"><span className="lunch-plate lunch-plate-one" /><span className="lunch-plate lunch-plate-two" /><span className="lunch-plate lunch-plate-three" /><span className="lunch-plate lunch-plate-four" /><span className="lunch-flower">✳</span></div>
    </div>
  </div>;
}
