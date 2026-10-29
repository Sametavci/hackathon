import { Link } from 'react-router-dom';
import Button, { buttonClasses } from '../Button';
import Card from '../Card';
import { gameCatalog } from './gameCatalog';
import { useI18n } from '../../i18n';

/** Game list. `basePath` is the hub URL; `standalone` means there is no lunch group yet. */
export default function GameHub({ basePath, standalone = false }: { basePath: string; standalone?: boolean }) {
  const { t } = useI18n();
  return <div className="space-y-5">
    <header className="space-y-3">
      <p className="eyebrow">{t('games.eyebrow')}</p>
      <h1>{t('games.title')}</h1>
      <p className="text-sm leading-relaxed text-ink-muted">{t(standalone ? 'games.tryIntro' : 'games.intro')}</p>
    </header>
    {gameCatalog.map(game => <Card key={game.id} className="space-y-4" aria-labelledby={`game-${game.id}`}>
      <span aria-hidden="true" className="inline-grid h-12 w-12 place-items-center rounded-2xl bg-brand-soft text-2xl">{game.icon}</span>
      <h2 id={`game-${game.id}`}>{t(game.title)}</h2>
      <p className="text-sm leading-relaxed text-ink-muted">{t(game.description)}</p>
      {standalone && game.needsGroup ? <Button className="w-full" variant="secondary" disabled>{t('games.needsGroup')}</Button> : <>
        <p className="text-xs text-ink-muted">{t('games.duration')}</p>
        <Link className={`${buttonClasses} w-full`} to={`${basePath}/${game.id}`}>{t('games.start')}</Link>
      </>}
    </Card>)}
  </div>;
}
