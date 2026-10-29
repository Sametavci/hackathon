import { t as appT, type MessageKey } from '../i18n/en';

const strings = {
  'connect.backToGroup': 'Back to my group',
  'connect.changed': 'This connection changed. We refreshed its status before showing contact details.',
  'connect.hiddenContact': 'Your contact is hidden while this connection is not mutual. You can withdraw it at any time.',
  'connect.choiceSaved': 'Your choice is saved.',
  'connect.contactSaved': 'Your contact was shared with this person.',
  'connect.contactWithdrawn': 'Your shared contact was withdrawn.',
  'connect.withdrawHint': 'Withdrawing your choice also removes the contact you shared with this person.',
} as const;

export function t(key: MessageKey | keyof typeof strings): string {
  return key in strings ? strings[key as keyof typeof strings] : appT(key as MessageKey);
}
