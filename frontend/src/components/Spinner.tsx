import { t } from '../i18n/en';
export default function Spinner() { return <div role="status" className="flex items-center gap-3 py-8 text-sm text-ink-muted"><span aria-hidden="true" className="h-5 w-5 rounded-full border-2 border-line border-t-ink motion-safe:animate-spin" />{t('common.loading')}</div>; }
