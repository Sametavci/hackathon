import { Link } from 'react-router-dom';
import { buddyRegistrationPath } from '../api/buddyFlow';
import { useI18n } from '../i18n';
import { buttonClasses } from './Button';
import ReturnLink from './ReturnLink';

export default function BuddySessionChoice({ code }: { code: string }) {
  const { t } = useI18n();
  return <div className="space-y-4">
    <p className="text-sm text-ink-muted">{t('buddy.existing')}</p>
    <Link to={buddyRegistrationPath(code, true)} className={`${buttonClasses} w-full`}>{t('buddy.newParticipant')}</Link>
    <Link to="/me" className="inline-flex min-h-11 items-center text-sm font-semibold underline">{t('landing.return')}</Link>
    <ReturnLink />
  </div>;
}
