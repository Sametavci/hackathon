import { Link } from 'react-router-dom';
import { t } from '../i18n/en';
import Card from '../components/Card';
import { buttonClasses } from '../components/Button';
export default function JoinPage() { return <Card className="mt-6 space-y-5"><h1>{t('join.title')}</h1><p className="leading-relaxed text-ink-muted">{t('join.body')}</p><Link to="/register" className={`${buttonClasses} w-full`}>{t('join.cta')}</Link></Card>; }
