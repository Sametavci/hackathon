import { useState } from 'react';
import { QRCodeSVG } from 'qrcode.react';
import { createInvite } from '../api/endpoints';
import type { InviteResponse } from '../api/types';
import { useI18n } from '../i18n';
import Button from './Button';
import Card from './Card';
import ErrorBanner from './ErrorBanner';

export default function QrShare({ pendingInvite }: { pendingInvite: InviteResponse | null }) {
  const { t } = useI18n();
  const [invite, setInvite] = useState(pendingInvite);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [copied, setCopied] = useState(false);
  async function create() {
    setBusy(true); setError(null);
    try { setInvite(await createInvite()); } catch (e) { setError(e); } finally { setBusy(false); }
  }
  async function share(copy: boolean) {
    if (!invite) return;
    setError(null);
    try {
      if (!copy && navigator.share) await navigator.share({ title: t('buddy.title'), url: invite.url });
      else { await navigator.clipboard.writeText(invite.url); setCopied(true); }
    } catch (e) { if (!(e instanceof DOMException && e.name === 'AbortError')) setError(new Error(t('buddy.copyFallback'))); }
  }
  return <Card className="space-y-4"><h2>{t('buddy.title')}</h2><p className="text-sm text-ink-muted">{t('buddy.timing')}</p>
    {!invite ? <Button disabled={busy} onClick={() => void create()}>{t(busy ? 'common.saving' : 'buddy.create')}</Button> : <>
      <div className="mx-auto w-fit rounded-2xl bg-white p-4"><QRCodeSVG value={invite.url} size={192} title={t('buddy.title')} /></div>
      <label className="block space-y-2 text-sm"><span>{t('buddy.link')}</span><input className="field" readOnly value={invite.url} onFocus={e => e.target.select()} /></label>
      <div className="flex flex-wrap gap-2"><Button onClick={() => void share(false)}>{t('buddy.share')}</Button><Button variant="secondary" onClick={() => void share(true)}>{t('buddy.copy')}</Button></div>
      {copied && <p role="status" className="text-sm">{t('buddy.copied')}</p>}
    </>}{!!error && <ErrorBanner error={error} />}
  </Card>;
}
