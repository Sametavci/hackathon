import Button from './Button';
import { t } from '../i18n/en';
export default function ErrorBanner({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  return <div role="alert" className="space-y-3 rounded-2xl border border-red-200 bg-red-50 p-4 text-sm text-red-900"><p className="break-words">{error instanceof Error ? error.message : t('error.generic')}</p>{onRetry && <Button variant="secondary" onClick={onRetry}>{t('common.retry')}</Button>}</div>;
}
