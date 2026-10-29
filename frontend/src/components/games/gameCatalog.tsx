import type { ComponentType } from 'react';
import type { MessageKey } from '../../i18n';
import ConversationCards from '../ConversationCards';
import CommonGroundGame from './CommonGroundGame';
import ImpostorGame from './ImpostorGame';
import TwoTruthsGame from './TwoTruthsGame';
import WouldYouRatherGame from './WouldYouRatherGame';

export interface GameProps { groupId?: string; players: string[] }
export interface GameEntry {
  id: string; title: MessageKey; description: MessageKey; icon: string;
  /** Needs a lunch group (server data); hidden from the standalone games page. */
  needsGroup: boolean;
  component: ComponentType<GameProps>;
}

const GroupIcebreakers = ({ groupId }: GameProps) => groupId ? <ConversationCards groupId={groupId} /> : null;

// Add a component and translated metadata here to introduce another local game.
// Shared sessions, scoring and server-side game state are outside the prototype.
export const gameCatalog: readonly GameEntry[] = [
  { id: 'icebreakers', title: 'games.icebreakers', description: 'games.icebreakersBody', icon: '✦', needsGroup: true, component: GroupIcebreakers },
  { id: 'impostor', title: 'games.impostor', description: 'games.impostorBody', icon: '◐', needsGroup: false, component: ImpostorGame },
  { id: 'two-truths', title: 'games.truths', description: 'games.truthsBody', icon: '✎', needsGroup: false, component: TwoTruthsGame },
  { id: 'would-you-rather', title: 'games.wouldRather', description: 'games.wouldRatherBody', icon: '↔', needsGroup: false, component: WouldYouRatherGame },
  { id: 'common-ground', title: 'games.commonGround', description: 'games.commonGroundBody', icon: '◎', needsGroup: false, component: CommonGroundGame },
];
