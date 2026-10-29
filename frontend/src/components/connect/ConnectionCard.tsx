import { useEffect, useState } from 'react';
import type { ConnectionItem } from '../../api/types';
import { setConnection, shareContact, withdrawContact } from '../../api/endpoints';
import { t } from '../../i18n/en';
import Card from '../Card';
import Button from '../Button';
import ErrorBanner from '../ErrorBanner';
export default function ConnectionCard({ item, groupId, onUpdate, onExpired }: { item: ConnectionItem; groupId: string; onUpdate: (value: ConnectionItem) => void; onExpired: () => void }) {
  const [contact, setContact] = useState(item.myContact ?? '');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  useEffect(() => { setContact(item.myContact ?? ''); }, [item.myContact, item.status]);
  async function update(action: () => Promise<ConnectionItem>) {
    if (busy) return;
    setBusy(true); setError(null);
    try { onUpdate(await action()); }
    catch (e) { setError(e); onExpired(); }
    finally { setBusy(false); }
  }
  const deleted = item.displayName === 'Former participant';
  return <Card className="space-y-4"><div className="flex flex-wrap items-center justify-between gap-3"><h2 className="break-all">{item.displayName}</h2><span className={`rounded-full px-3 py-2 text-xs font-semibold ${item.status === 'MUTUAL' ? 'bg-accent' : 'bg-brand-soft'}`}>{t(`connect.${item.status}`)}</span></div>
    {deleted ? <p className="text-sm text-ink-muted">{t('connect.deletedHint')}</p> : <><Button aria-pressed={item.myChoice === 'YES'} variant={item.myChoice === 'YES' ? 'secondary' : 'primary'} disabled={busy} className="w-full" onClick={() => void update(() => setConnection(groupId, item.participantId, item.myChoice === 'YES' ? 'NO' : 'YES'))}>{t(item.myChoice === 'YES' ? 'connect.no' : 'connect.yes')}</Button>
      {item.status === 'MUTUAL' && <div className="space-y-4 border-t border-line pt-4">
        {item.theirContact && <div className="rounded-2xl bg-brand-soft p-4"><p className="mb-2 text-xs font-bold">{t('connect.theirContact')}</p><p className="whitespace-pre-wrap text-sm">{item.theirContact}</p></div>}
        {item.myContact && <p className="text-sm"><span className="font-bold">{t('connect.myContact')}: </span>{item.myContact}</p>}
        <form className="space-y-3" onSubmit={e => { e.preventDefault(); if (contact.trim()) void update(() => shareContact(groupId, item.participantId, contact.trim())); }}><label htmlFor={`contact-${item.participantId}`} className="block text-sm font-semibold">{t('connect.contact')}</label><input id={`contact-${item.participantId}`} className="field" value={contact} maxLength={100} required onChange={e => setContact(e.target.value)} aria-describedby={`hint-${item.participantId}`} /><p id={`hint-${item.participantId}`} className="text-xs text-ink-muted">{t('connect.contactHint')}</p><div className="flex flex-wrap gap-2"><Button type="submit" disabled={busy || !contact.trim()}>{t('connect.share')}</Button>{item.myContact && <Button disabled={busy} variant="secondary" onClick={() => void update(() => withdrawContact(groupId, item.participantId))}>{t('connect.withdraw')}</Button>}</div></form>
      </div>}
    </>}{!!error && <ErrorBanner error={error} />}
  </Card>;
}
