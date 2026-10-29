import { useState } from 'react';
import { QRCodeSVG } from 'qrcode.react';
import { createInvite, unlinkFriend } from '../api/endpoints';
import type { InviteResponse, MeResponse } from '../api/types';
import { t } from '../i18n/en';
import Button from './Button';
import Card from './Card';
import ErrorBanner from './ErrorBanner';

/** Bring a Friend on My lunch: create/share an invite, or show the linked friend and its status. */
export default function BuddyCard({ me, onChange }: { me: MeResponse; onChange: () => void }) {
  const [invite, setInvite] = useState<InviteResponse | null>(me.pendingInvite);
  const [busy, setBusy] = useState(false);
  const [copied, setCopied] = useState(false);
  const [error, setError] = useState<unknown>(null);

  async function run(action: () => Promise<void>) {
    setBusy(true); setError(null);
    try { await action(); } catch (e) { setError(e); } finally { setBusy(false); }
  }

  if (me.friend) {
    const incompatible = me.friend.pairStatus === 'INCOMPATIBLE';
    return <Card className="space-y-3">
      <h2>{t('me.friend')}</h2>
      <p className="font-semibold">🤝 {me.friend.displayName}</p>
      <p role="status" className="text-sm leading-relaxed text-ink-muted">{incompatible ? me.friend.issue?.message : t('buddy.active')}</p>
      {incompatible && <><p className="text-sm text-ink-muted">{t('buddy.splitHint')}</p>
        <Button variant="secondary" disabled={busy} onClick={() => void run(async () => { await unlinkFriend(); onChange(); })}>{t(busy ? 'buddy.splitting' : 'buddy.split')}</Button></>}
      {!!error && <ErrorBanner error={error} />}
    </Card>;
  }

  if (me.matchStatus !== 'WAITING') return null;

  async function copy(url: string) {
    try { await navigator.clipboard.writeText(url); setCopied(true); } catch { setCopied(false); }
  }
  const canShare = typeof navigator !== 'undefined' && typeof navigator.share === 'function';

  return <Card className="space-y-4">
    <h2>{t('buddy.title')}</h2>
    <p className="text-sm leading-relaxed text-ink-muted">{t('buddy.body')}</p>
    {!invite ? <Button className="w-full" disabled={busy} onClick={() => void run(async () => setInvite(await createInvite()))}>{t(busy ? 'buddy.creating' : 'buddy.create')}</Button> : <>
      <p className="text-sm">{t('buddy.scan')}</p>
      <div className="flex justify-center rounded-2xl bg-white p-3"><QRCodeSVG value={invite.url} size={192} marginSize={2} title={t('buddy.link')} /></div>
      {/* Always visible: plain-HTTP deployments block the clipboard and share APIs. */}
      <label htmlFor="invite-link" className="block text-sm font-bold">{t('buddy.link')}</label>
      <input id="invite-link" readOnly value={invite.url} className="field" onFocus={e => e.currentTarget.select()} />
      <div className="flex flex-wrap gap-2">
        <Button variant="secondary" onClick={() => void copy(invite.url)}>{t('buddy.copy')}</Button>
        {canShare && <Button variant="secondary" onClick={() => void navigator.share({ text: t('buddy.shareText'), url: invite.url }).catch(() => undefined)}>{t('buddy.share')}</Button>}
      </div>
      <p role="status" className="text-sm">{copied ? t('buddy.copied') : ''}</p>
      <p className="text-xs text-ink-muted">{t('buddy.oneFriend')}</p>
    </>}
    {!!error && <ErrorBanner error={error} />}
  </Card>;
}
