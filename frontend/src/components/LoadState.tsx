import { Navigate } from 'react-router-dom';
import { ApiError } from '../api/client';
import ErrorBanner from './ErrorBanner';
import Spinner from './Spinner';
export default function LoadState({ loading, error, reload }: { loading: boolean; error: unknown; reload: () => void }) {
  if (error instanceof ApiError && error.status === 401) return <Navigate to="/" replace />;
  if (error) return <ErrorBanner error={error} onRetry={reload} />;
  return loading ? <Spinner /> : null;
}
