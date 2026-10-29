export function createLandingCycle(onScene: (scene: number) => void, random = Math.random) {
  let scene = 0;
  let running = false;
  let timer: ReturnType<typeof setTimeout> | undefined;
  function stop() { running = false; clearTimeout(timer); timer = undefined; }
  function schedule() {
    timer = setTimeout(() => {
      timer = undefined;
      scene = (scene + 1 + Math.floor(random() * 2)) % 3;
      onScene(scene);
      if (running) schedule();
    }, 1800 + Math.floor(random() * 1001));
  }
  return {
    setRunning(value: boolean) {
      if (!value) { stop(); return; }
      if (running) return;
      running = true;
      schedule();
    },
    dispose: stop,
  };
}
