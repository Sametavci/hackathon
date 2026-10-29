import { Link } from 'react-router-dom';
import { useI18n } from '../i18n';
import Disclosure from '../components/Disclosure';
import PageHeading from '../components/PageHeading';

export default function PrivacyPage() {
  const { t } = useI18n();
  return <div className="space-y-5 pt-3">
    <PageHeading title={t('privacy.title')} description={t('privacy.intro')} />
    <div className="space-y-2">{(['data', 'purpose', 'contact', 'delete', 'retention'] as const).map(key => <Disclosure key={key} title={t(`privacy.${key}Title`)}><p>{t(`privacy.${key}`)}</p></Disclosure>)}</div>
    <p className="text-xs leading-relaxed text-ink-muted">{t('privacy.mock')}</p>
    <Link to="/me" className="inline-flex min-h-11 items-center text-sm text-brand underline underline-offset-4">{t('nav.me')}</Link>
  </div>;
}
