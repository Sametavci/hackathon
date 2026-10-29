import { Fragment, useSyncExternalStore, type ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { getToken, subscribeSession } from '../api/session';
export default function SessionGate({ children }: { children: ReactNode }) {
  const token = useSyncExternalStore(subscribeSession, getToken);
  // Switching return links in another tab must discard the previous person's UI.
  return token ? <Fragment key={token}>{children}</Fragment> : <Navigate to="/" replace />;
}
