import { Link } from 'react-router-dom';
import { useI18n } from '../i18n';
import Card from '../components/Card';
export default function PrivacyPage() {
  const { t } = useI18n();
  return <div className="space-y-5 pt-3"><h1>{t('privacy.title')}</h1><p className="leading-relaxed text-ink-muted">{t('privacy.intro')}</p>{(['data', 'purpose', 'contact', 'delete', 'retention'] as const).map(key => <Card key={key} className="space-y-3"><h2>{t(`privacy.${key}Title`)}</h2><p className="text-sm leading-relaxed text-ink-muted">{t(`privacy.${key}`)}</p></Card>)}<p className="text-sm leading-relaxed text-ink-muted">{t('privacy.mock')}</p><Link to="/me" className="inline-flex min-h-11 items-center underline">{t('nav.me')}</Link></div>;
}
