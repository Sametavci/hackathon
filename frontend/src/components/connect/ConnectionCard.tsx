import { useEffect, useRef, useState } from 'react';
import type { ConnectionItem } from '../../api/types';
import { setConnection, shareContact, withdrawContact } from '../../api/endpoints';
import { ApiError } from '../../api/client';
import { useI18n, type MessageKey } from '../../i18n';
import Card from '../Card';
import Avatar from '../Avatar';
import Button from '../Button';
import ErrorBanner from '../ErrorBanner';
interface ConnectionCardProps {
  item: ConnectionItem;
  groupId: string;
  disabled: boolean;
  onUpdate: (value: ConnectionItem) => void;
  onBusyChange: (busy: boolean) => void;
  onStale: () => void;
}

export default function ConnectionCard({ item, groupId, disabled, onUpdate, onBusyChange, onStale }: ConnectionCardProps) {
  const { t } = useI18n();
  const [contact, setContact] = useState(item.myContact ?? '');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [announcement, setAnnouncement] = useState<MessageKey | ''>('');
  const pending = useRef(false);
  useEffect(() => { setContact(item.myContact ?? ''); }, [item.myContact]);

  async function update(action: () => Promise<ConnectionItem>, message: MessageKey) {
    if (pending.current || disabled) return;
    pending.current = true;
    setBusy(true);
    onBusyChange(true);
    setError(null);
    setAnnouncement('');
    try {
      onUpdate(await action());
      setAnnouncement(message);
    } catch (failure) {
      if (failure instanceof ApiError && [401, 403, 404, 409].includes(failure.status)) {
        onStale();
      } else {
        setError(failure);
      }
    } finally {
      pending.current = false;
      setBusy(false);
      onBusyChange(false);
    }
  }
  const deleted = item.displayName === 'Former participant';
  const locked = busy || disabled;
  const headingId = `member-${item.participantId}`;

  return (
    <Card role="region" aria-labelledby={headingId} aria-busy={busy} className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex min-w-0 items-center gap-3"><Avatar name={deleted ? t('connect.deleted') : item.displayName} small /><h2 id={headingId} className="min-w-0 break-words text-base">{deleted ? t('connect.deleted') : item.displayName}</h2></div>
        {!deleted && <span role="status" className={`rounded-full px-2.5 py-1 text-[11px] font-medium ${item.status === 'MUTUAL' ? 'bg-brand-soft text-brand' : 'bg-brand-soft text-ink-muted'}`}>
          {t(`connect.${item.status}`)}
        </span>}
      </div>
      {deleted ? <p className="text-sm text-ink-muted">{t('connect.deletedHint')}</p> : (
        <>
          <Button
            aria-pressed={item.myChoice === 'YES'}
            aria-describedby={item.myChoice === 'YES' ? `withdraw-hint-${item.participantId}` : undefined}
            variant={item.myChoice === 'YES' ? 'secondary' : 'primary'}
            disabled={locked}
            className="w-full"
            onClick={() => void update(
              () => setConnection(groupId, item.participantId, item.myChoice === 'YES' ? 'NO' : 'YES'),
              'connect.choiceSaved',
            )}
          >{t(item.myChoice === 'YES' ? 'connect.no' : 'connect.yes')}</Button>
          {item.myChoice === 'YES' && <p id={`withdraw-hint-${item.participantId}`} className="text-xs leading-relaxed text-ink-muted">{t('connect.withdrawHint')}</p>}
          {item.myContact !== null && (
            <div className="space-y-3 rounded-2xl border border-line p-4">
              <p className="text-xs font-bold">{t('connect.myContact')}</p>
              <p className="whitespace-pre-wrap break-words text-sm">{item.myContact}</p>
              {item.status !== 'MUTUAL' && <p className="text-xs leading-relaxed text-ink-muted">{t('connect.hiddenContact')}</p>}
              <Button disabled={locked} variant="secondary" className="w-full" onClick={() => void update(
                () => withdrawContact(groupId, item.participantId), 'connect.contactWithdrawn',
              )}>{t('connect.withdraw')}</Button>
            </div>
          )}
          {item.status === 'MUTUAL' && (
            <div className="space-y-4 border-t border-line pt-4">
              {item.theirContact !== null && <div className="rounded-2xl bg-brand-soft p-4">
                <p className="mb-2 text-xs font-bold">{t('connect.theirContact')}</p>
                <p className="whitespace-pre-wrap break-words text-sm">{item.theirContact}</p>
              </div>}
              <form className="space-y-3" onSubmit={event => {
                event.preventDefault();
                if (contact.trim() && contact.length <= 100) {
                  void update(() => shareContact(groupId, item.participantId, contact), 'connect.contactSaved');
                }
              }}>
                <label htmlFor={`contact-${item.participantId}`} className="block text-sm font-semibold">{t('connect.contact')}</label>
                <input id={`contact-${item.participantId}`} className="field" value={contact} minLength={1} maxLength={100}
                  required disabled={locked} autoComplete="off" onChange={event => setContact(event.target.value)}
                  aria-describedby={`hint-${item.participantId}`} />
                <p id={`hint-${item.participantId}`} className="text-xs text-ink-muted">{t('connect.contactHint')}</p>
                <Button type="submit" className="w-full" disabled={locked || !contact.trim() || contact.length > 100}>
                  {busy ? t('common.saving') : t('connect.share')}
                </Button>
              </form>
            </div>
          )}
        </>
      )}
      {!!error && <ErrorBanner error={error} />}
      <p role="status" className={announcement ? 'text-xs text-ink-muted' : 'sr-only'}>{announcement ? t(announcement) : ''}</p>
    </Card>
  );
}
