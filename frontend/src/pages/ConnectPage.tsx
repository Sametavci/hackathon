import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { getConnections } from '../api/endpoints';
import type { ConnectionItem } from '../api/types';
import { useResource } from '../hooks/useResource';
import { useI18n } from '../i18n';
import Button, { buttonClasses } from '../components/Button';
import LoadState from '../components/LoadState';
import SessionGate from '../components/SessionGate';
import ConnectionCard from '../components/connect/ConnectionCard';
function ConnectContent({ groupId }: { groupId: string }) {
  const { t } = useI18n();
  const resource = useResource(`connections-${groupId}`, () => getConnections(groupId));
  const [updates, setUpdates] = useState<Record<string, ConnectionItem>>({});
  const [pendingId, setPendingId] = useState<string | null>(null);
  const [changed, setChanged] = useState(false);
  const [refreshing, setRefreshing] = useState(false);

  useEffect(() => {
    if (!resource.loading) setRefreshing(false);
  }, [resource.data, resource.loading, resource.error]);

  function refresh(stale = false) {
    setUpdates({});
    setChanged(stale);
    setRefreshing(true);
    resource.reload();
  }

  return (
    <div className="space-y-5 pt-3">
      <p className="eyebrow">{t('group.connect')}</p>
      <h1>{t('connect.title')}</h1>
      <p className="text-sm leading-relaxed text-ink-muted">{t('connect.body')}</p>
      {changed && <p role="status" className="rounded-2xl bg-brand-soft p-4 text-sm">{t('connect.changed')}</p>}
      {!resource.data || refreshing ? <LoadState {...resource} /> : (
        <>
          {!resource.data.length && <p>{t('connect.empty')}</p>}
          {resource.data.map(item => (
            <ConnectionCard
              key={item.participantId}
              item={updates[item.participantId] ?? item}
              groupId={groupId}
              disabled={pendingId !== null}
              onBusyChange={busy => setPendingId(busy ? item.participantId : null)}
              onUpdate={value => setUpdates(previous => ({ ...previous, [value.participantId]: value }))}
              onStale={() => refresh(true)}
            />
          ))}
          <Button variant="secondary" disabled={pendingId !== null} onClick={() => refresh()} className="w-full">
            {t('common.refresh')}
          </Button>
        </>
      )}
      <Link to={`/group/${encodeURIComponent(groupId)}`} className={`${buttonClasses} w-full`}>{t('connect.backToGroup')}</Link>
    </div>
  );
}
export default function ConnectPage() { const { groupId = '' } = useParams(); return <SessionGate><ConnectContent key={groupId} groupId={groupId} /></SessionGate>; }
