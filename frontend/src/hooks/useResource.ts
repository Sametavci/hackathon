import { useCallback, useEffect, useRef, useState } from 'react';
export function useResource<T>(key: string, loader: () => Promise<T>, enabled = true) {
  const loaderRef = useRef(loader);
  loaderRef.current = loader;
  const [revision, setRevision] = useState(0);
  const [state, setState] = useState<{ key: string; data: T | null; loading: boolean; error: unknown }>({ key, data: null, loading: enabled, error: null });
  const reload = useCallback(() => setRevision(n => n + 1), []);
  useEffect(() => {
    if (!enabled) return;
    let active = true;
    setState({ key, data: null, loading: true, error: null });
    loaderRef.current().then(data => { if (active) setState({ key, data, loading: false, error: null }); }, error => { if (active) setState({ key, data: null, loading: false, error }); });
    return () => { active = false; };
  }, [key, revision, enabled]);
  return { ...(state.key === key ? state : { key, data: null, loading: enabled, error: null }), reload };
}
