import { useState } from 'react';
import { restoreUrl } from '../api/session';
import { useI18n } from '../i18n';
import Button from './Button';
import Card from './Card';
export default function ReturnLink() {
  const { t } = useI18n();
  const [copied, setCopied] = useState(false);
  const [fallback, setFallback] = useState(false);
  const url = restoreUrl();
  if (!url) return null;
  async function copy() { try { await navigator.clipboard.writeText(url!); setCopied(true); setFallback(false); } catch { setFallback(true); } }
  return <Card className="space-y-3"><h2>{t('me.returnTitle')}</h2><p className="text-sm leading-relaxed text-ink-muted">{t('me.returnBody')}</p><Button variant="secondary" onClick={() => void copy()}>{t('common.copy')}</Button><p role="status" className="text-sm">{copied ? t('common.copied') : ''}</p>{fallback && <div><label htmlFor="return-link" className="mb-2 block text-sm">{t('common.copyFallback')}</label><input id="return-link" readOnly value={url} className="field" onFocus={e => e.currentTarget.select()} /></div>}</Card>;
}
