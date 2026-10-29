import { Link } from 'react-router-dom';
import Button, { buttonClasses } from '../Button';
import Card from '../Card';
import PageHeading from '../PageHeading';
import { gameCatalog } from './gameCatalog';
import { useI18n } from '../../i18n';

export default function GameHub({ groupId }: { groupId: string }) {
  const { t } = useI18n();
  return <div className="space-y-5">
    <PageHeading title={t('games.title')} />
    {gameCatalog.map(game => <Card key={game.id} className="space-y-4" aria-labelledby={`game-${game.id}`}>
      <div className="flex items-center gap-3"><span aria-hidden="true" className="inline-grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-brand-soft text-xl">{game.icon}</span><h2 id={`game-${game.id}`} className="text-base">{t(game.title)}</h2></div>
      {game.component ? <>
        <p className="text-sm text-ink-muted">{t(game.description)}</p>
        <Link className={`${buttonClasses} w-full`} to={`/group/${encodeURIComponent(groupId)}/play/${game.id}`}>{t('games.start')}</Link>
      </> : <Button className="w-full" variant="secondary" disabled>{t('games.soon')}</Button>}
    </Card>)}
  </div>;
}
