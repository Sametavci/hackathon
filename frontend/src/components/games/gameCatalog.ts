import ConversationCards from '../ConversationCards';

// Add a component and translated metadata here to introduce another local game.
// Shared sessions, scoring and server-side game state are outside the prototype.
export const gameCatalog = [
  { id: 'icebreakers', title: 'games.icebreakers', description: 'games.icebreakersBody', icon: '✦', component: ConversationCards },
  { id: 'would-you-rather', title: 'games.wouldRather', description: 'games.wouldRatherBody', icon: '↔', component: null },
  { id: 'common-ground', title: 'games.commonGround', description: 'games.commonGroundBody', icon: '◎', component: null },
] as const;
