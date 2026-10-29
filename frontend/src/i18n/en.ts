// English UI strings (owner: Dev 3). German can be added later as de.ts with the same keys.
// Dev 1 / Dev 2 keep strings for their pages in pages/<Page>.strings.ts instead of editing this file.
export const en = {
  'app.name': 'Lunch²',
  'app.slogan': 'Bring a Friend. Meet Two.',
  'nav.privacy': 'Privacy',
} as const;

export type MessageKey = keyof typeof en;

export function t(key: MessageKey): string {
  return en[key];
}
