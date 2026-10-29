export function createLandingCycle(onScene: (scene: number) => void) {
  let scene = 0;
  let timer: ReturnType<typeof setInterval> | undefined;
  function stop() { clearInterval(timer); timer = undefined; }
  return {
    setRunning(running: boolean) {
      if (!running) { stop(); return; }
      if (timer !== undefined) return;
      timer = setInterval(() => { scene = (scene + 1) % 3; onScene(scene); }, 4000);
    },
    dispose: stop,
  };
}
