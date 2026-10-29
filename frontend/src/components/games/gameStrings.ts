import { t as appT, type MessageKey } from '../../i18n/en';

const strings = {
  'games.title': 'Play together.',
  'games.eyebrow': 'Good food. Good conversation.',
  'games.intro': 'At your table? Pick something to get the conversation going. One phone is enough.',
  'games.open': 'Play together',
  'games.backToGroup': 'Back to my group',
  'games.backToHub': 'All games',
  'games.start': 'Start playing',
  'games.soon': 'Coming soon',
  'games.icebreakers': 'Conversation Shuffle',
  'games.icebreakersBody': 'A mix of classic questions and suggestions inspired by your shared interests.',
  'games.wouldRather': 'Would You Rather?',
  'games.wouldRatherBody': 'Two possibilities. Pick one and tell the table why.',
  'games.commonGround': 'Find Common Ground',
  'games.commonGroundBody': 'Small challenges to discover what you all have in common.',
  'games.duration': 'About 5 minutes · Take turns',
  'games.rules': 'Take turns answering out loud. Anyone can pass. Your answers are not recorded.',
  'games.mixed': 'AI + classic questions',
  'games.shuffle': 'Shuffle questions',
  'games.skip': 'Skip question',
  'games.finish': 'Finish game',
  'games.finished': 'Keep the conversation going.',
  'games.finishedBody': 'No scores. No right answers. Keep chatting, play again, or explore the other games.',
  'games.replay': 'Play again',
  'games.notAvailable': 'This game is not available yet. Choose Conversation Shuffle to start playing.',
} as const;

export function t(key: MessageKey | keyof typeof strings): string {
  return key in strings ? strings[key as keyof typeof strings] : appT(key as MessageKey);
}
