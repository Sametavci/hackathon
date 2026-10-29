import { Link } from 'react-router-dom';
import { useI18n } from '../i18n';
import PageHeading from '../components/PageHeading';
import { buttonClasses } from '../components/Button';
export default function JoinPage() {
  const { t } = useI18n();
  return <section className="space-y-6 pt-6"><PageHeading title={t('join.title')} description={t('join.body')} /><Link to="/register" className={`${buttonClasses} w-full`}>{t('join.cta')}</Link></section>;
}
