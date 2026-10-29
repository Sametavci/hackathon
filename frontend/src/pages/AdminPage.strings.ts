const strings = {
  resetTitle: 'Reset all Lunch² data?',
  resetBody: 'This deletes all registrations, groups, conversation cards, invitations, friend links and shared contacts. Existing return links will stop working. This cannot be undone.',
  resetConfirm: 'Yes, delete all data',
  resetDone: 'All Lunch² data was reset. You can seed a fresh demo now.',
  statsJson: 'View metrics as JSON',
} as const;

export function t(key: keyof typeof strings): string { return strings[key]; }
