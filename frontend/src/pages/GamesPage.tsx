import { Link, useParams } from 'react-router-dom';
import { getToken } from '../api/session';
import { buttonClasses } from '../components/Button';
import GameHub from '../components/games/GameHub';
import { gameCatalog } from '../components/games/gameCatalog';
import { useI18n } from '../i18n';

/** Table games without a lunch group: no login needed, players start as "Player 1–4". */
export default function GamesPage() {
  const { t } = useI18n();
  const { gameId } = useParams();
  const game = gameCatalog.find(entry => entry.id === gameId && !entry.needsGroup);
  const Game = game?.component;
  const players = [1, 2, 3, 4].map(n => t('games.player', { n }));
  return <div className="space-y-6 pt-3">
    {!gameId ? <GameHub basePath="/games" standalone /> : Game ? <>
      <Link className="inline-flex min-h-11 items-center text-sm underline" to="/games">← {t('games.backToHub')}</Link>
      <h1>{t(game.title)}</h1>
      <p className="rounded-2xl bg-brand-soft p-4 text-sm leading-relaxed">{t('games.rules')}</p>
      <Game key={gameId} players={players} />
    </> : <>
      <p role="status">{t('games.needsGroup')}</p>
      <Link className={buttonClasses} to="/games">{t('games.backToHub')}</Link>
    </>}
    {getToken() ? <Link className={`${buttonClasses} w-full`} to="/me">{t('common.back')}</Link>
      : <Link className="inline-flex min-h-11 items-center text-sm underline" to="/">{t('nav.home')}</Link>}
  </div>;
}
