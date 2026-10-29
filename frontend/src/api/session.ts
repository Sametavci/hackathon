const TOKEN_KEY = 'lunch2.token';
const SESSION_EVENT = 'lunch2:session';
export const getToken = (): string | null => localStorage.getItem(TOKEN_KEY);
function notifySession(): void {
  if (typeof window !== 'undefined' && typeof window.dispatchEvent === 'function') {
    window.dispatchEvent(new Event(SESSION_EVENT));
  }
}
export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token);
  notifySession();
}
export function clearSession(): void {
  localStorage.removeItem(TOKEN_KEY);
  notifySession();
}
export function subscribeSession(listener: () => void): () => void {
  const onStorage = (event: StorageEvent) => {
    if (event.key === TOKEN_KEY || event.key === null) listener();
  };
  window.addEventListener('storage', onStorage);
  window.addEventListener(SESSION_EVENT, listener);
  return () => {
    window.removeEventListener('storage', onStorage);
    window.removeEventListener(SESSION_EVENT, listener);
  };
}
export function restoreUrl(): string | null {
  const token = getToken();
  return token ? `${window.location.origin}${window.location.pathname}#/back?t=${encodeURIComponent(token)}` : null;
}
export const getAdminKey = (): string => sessionStorage.getItem('lunch2.adminKey') ?? '';
export function setAdminKey(key: string): void { sessionStorage.setItem('lunch2.adminKey', key); }
