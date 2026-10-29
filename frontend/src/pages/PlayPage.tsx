import { Link, useParams } from 'react-router-dom';
import { getGroup } from '../api/endpoints';
import { buttonClasses } from '../components/Button';
import GameHub from '../components/games/GameHub';
import { gameCatalog } from '../components/games/gameCatalog';
import { t } from '../components/games/gameStrings';
import LoadState from '../components/LoadState';
import SessionGate from '../components/SessionGate';
import { useResource } from '../hooks/useResource';

function PlayContent({ groupId, gameId }: { groupId: string; gameId?: string }) {
  // The existing member-only group API is the access check; no attendance/time gate.
  const group = useResource(`play-group-${groupId}`, () => getGroup(groupId));
  const base = `/group/${encodeURIComponent(groupId)}`;
  if (!group.data) return <div className="space-y-4 pt-3">
    <LoadState {...group} />
    <Link className={buttonClasses} to="/me">{t('common.back')}</Link>
  </div>;
  const game = gameCatalog.find(entry => entry.id === gameId);
  const Game = game?.component;
  return <div className="space-y-6 pt-3">
    {!gameId ? <GameHub groupId={groupId} /> : Game ? <>
      <Link className="inline-flex min-h-11 items-center text-sm underline" to={`${base}/play`}>← {t('games.backToHub')}</Link>
      <p className="rounded-2xl bg-brand-soft p-4 text-sm leading-relaxed">{t('games.rules')}</p>
      <Game key={`${groupId}-${gameId}`} groupId={groupId} />
    </> : <>
      <p role="status">{t('games.notAvailable')}</p>
      <Link className={buttonClasses} to={`${base}/play`}>{t('games.backToHub')}</Link>
    </>}
    <Link className={`${buttonClasses} w-full`} to={base}>{t('games.backToGroup')}</Link>
  </div>;
}

export default function PlayPage() {
  const { groupId = '', gameId } = useParams();
  return <SessionGate><PlayContent key={`${groupId}-${gameId ?? 'hub'}`} groupId={groupId} gameId={gameId} /></SessionGate>;
}
