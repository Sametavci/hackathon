const strings = {
  resetTitle: 'Reset all Lunch² data?',
  resetBody: 'This deletes all registrations, groups, conversation cards, invitations, friend links and shared contacts. Existing return links will stop working. This cannot be undone.',
  resetConfirm: 'Yes, delete all data',
  resetDone: 'All Lunch² data was reset. You can seed a fresh demo now.',
  statsJson: 'View metrics as JSON',
  demoMatch: 'Match with demo students',
  demoMatchHint: 'Keep existing registrations and fill missing seats with fictional students. Demo mode only; matching rules still apply.',
  demoMatchDone: 'Demo matching finished. Open My lunch and refresh your status to reveal your group.',
  openMyLunch: 'Open My lunch',
} as const;

export function t(key: keyof typeof strings): string { return strings[key]; }
