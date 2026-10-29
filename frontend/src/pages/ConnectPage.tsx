import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { getConnections } from '../api/endpoints';
import type { ConnectionItem } from '../api/types';
import { getToken } from '../api/session';
import { useResource } from '../hooks/useResource';
import { t } from '../i18n/en';
import Button, { buttonClasses } from '../components/Button';
import LoadState from '../components/LoadState';
import SessionGate from '../components/SessionGate';
import ConnectionCard from '../components/connect/ConnectionCard';
function ConnectContent({ groupId }: { groupId: string }) {
  const resource = useResource(`connections-${groupId}`, () => getConnections(groupId));
  const [updates, setUpdates] = useState<Record<string, ConnectionItem>>({});
  const refresh = () => { setUpdates({}); resource.reload(); };
  return <div className="space-y-5 pt-3"><p className="eyebrow">{t('group.connect')}</p><h1>{t('connect.title')}</h1><p className="text-sm leading-relaxed text-ink-muted">{t('connect.body')}</p>
    {!resource.data ? <LoadState {...resource} /> : <>{!resource.data.length && <p>{t('connect.empty')}</p>}{resource.data.map(item => <ConnectionCard key={item.participantId} item={updates[item.participantId] ?? item} groupId={groupId} onUpdate={value => setUpdates(previous => ({ ...previous, [value.participantId]: value }))} onExpired={() => { if (!getToken()) refresh(); }} />)}<Button variant="secondary" onClick={refresh}>{t('common.refresh')}</Button></>}
    <Link to={`/group/${groupId}`} className={`${buttonClasses} w-full`}>{t('group.title')}</Link>
  </div>;
}
export default function ConnectPage() { const { groupId = '' } = useParams(); return <SessionGate><ConnectContent key={groupId} groupId={groupId} /></SessionGate>; }
