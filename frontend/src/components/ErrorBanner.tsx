import Button from './Button';
import { useI18n } from '../i18n';
import { errorMessage } from '../i18n/presentation';
export default function ErrorBanner({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const { t } = useI18n();
  return <div role="alert" className="space-y-3 rounded-2xl border border-red-200 bg-red-50 p-4 text-sm text-red-900"><p className="break-words">{errorMessage(error)}</p>{onRetry && <Button variant="secondary" onClick={onRetry}>{t('common.retry')}</Button>}</div>;
}
