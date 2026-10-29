import { t as translate } from '../i18n';

type AdminKey = 'resetTitle' | 'resetBody' | 'resetConfirm' | 'resetDone' | 'statsJson' | 'demoMatch' | 'demoMatchHint' | 'demoMatchDone' | 'openMyLunch';
export function t(key: AdminKey): string { return translate(`admin.${key}`); }
