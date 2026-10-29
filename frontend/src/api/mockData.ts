import type { MetaResponse, ReasonCode } from './types';
import { createMensaCatalog } from './mensaCatalog';
export const reasonMessages: Record<ReasonCode, string> = {
  WAITING_FOR_NEXT_ROUND: 'Your registration is saved. The next matching round has not run yet.',
  NOT_ENOUGH_DOMESTIC: 'Not enough locally-based students share your campus and lunch slots yet.',
  NOT_ENOUGH_INTERNATIONAL: 'Not enough international students share your campus and lunch slots yet.',
  NO_COMMON_LANGUAGE: 'A group sharing your language and lunch slots is not available yet.',
  FRIEND_DIFFERENT_MENSA: 'You and your friend chose different Mensas.',
  FRIEND_DIFFERENT_CAMPUS: 'You and your friend chose different campuses.',
  FRIEND_NO_COMMON_SLOT: 'You and your friend have no lunch slot in common.',
  FRIEND_NO_COMMON_LANGUAGE: 'You and your friend have no language in common.',
  INVITER_ALREADY_MATCHED: 'Your friend was already matched. You will be matched on your own.',
};
export const mockMeta: MetaResponse = {
  campuses: [
    { id: 'WUERZBURG', label: 'Würzburg', location: 'Mensa Würzburg – Lunch² table (yellow sign)' },
    { id: 'SCHWEINFURT', label: 'Schweinfurt', location: 'Mensa Schweinfurt – Lunch² table (yellow sign)' },
  ],
  languages: ['en:English', 'de:Deutsch', 'tr:Türkçe', 'ar:العربية', 'es:Español', 'fr:Français', 'it:Italiano', 'pt:Português', 'ru:Русский', 'uk:Українська', 'zh:中文', 'hi:हिन्दी'].map(value => { const [code, label] = value.split(':'); return { code, label }; }),
  interests: [
    ['gaming', 'Gaming', '🎮'], ['travel', 'Travel', '✈️'], ['tech', 'Tech', '💻'], ['football', 'Football', '⚽'],
    ['music', 'Music', '🎵'], ['cooking', 'Cooking', '🍳'], ['hiking', 'Hiking', '🥾'], ['movies', 'Movies', '🎬'],
    ['photography', 'Photography', '📷'], ['art', 'Art', '🎨'], ['reading', 'Reading', '📚'], ['fitness', 'Fitness', '🏃'],
    ['startups', 'Startups', '💡'], ['languages', 'Languages', '💬'], ['anime', 'Anime', '🌸'], ['boardgames', 'Board games', '🎲'],
  ].map(([id, label, emoji]) => ({ id, label, emoji })),
  studyFields: ['COMPUTER_SCIENCE:Computer Science', 'ENGINEERING:Engineering', 'BUSINESS:Business', 'DESIGN:Design', 'SOCIAL_SCIENCES:Social Sciences', 'HEALTH:Health', 'OTHER:Other'].map(value => { const [id, label] = value.split(':'); return { id, label }; }),
  ...createMensaCatalog(),
};
