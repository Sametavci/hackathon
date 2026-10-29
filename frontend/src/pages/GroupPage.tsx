import { Link, useParams } from 'react-router-dom';
import { getGroup, getMeta } from '../api/endpoints';
import { buttonClasses } from '../components/Button';
import Card from '../components/Card';
import ErrorBanner from '../components/ErrorBanner';
import LoadState from '../components/LoadState';
import MemberCard from '../components/MemberCard';
import SessionGate from '../components/SessionGate';
import { useResource } from '../hooks/useResource';
import { catalogLabel, formatDate, groupExplanation } from '../i18n/presentation';
import { useI18n } from '../i18n';
function GroupContent({ groupId }: { groupId: string }) {
  const { t } = useI18n();
  const group = useResource(`group-${groupId}`, () => getGroup(groupId));
  const meta = useResource('meta', getMeta);
  if (!group.data) return <div className="space-y-4"><LoadState {...group} /><Link to="/me" className={buttonClasses}>{t('common.back')}</Link></div>;
  const data = group.data;
  return <div className="space-y-7 pt-3"><div className="space-y-3"><p className="eyebrow">{t('group.eyebrow')}</p><h1>{t('group.title')}</h1><p className="text-sm text-ink-muted">{t('group.intro')}</p></div>
    {!!meta.error && <ErrorBanner error={meta.error} onRetry={meta.reload} />}
    {!data.members.length && <p>{t('group.empty')}</p>}
    <div className="grid grid-cols-2 gap-3">{data.members.map((member, index) => <MemberCard key={member.id} member={member} index={index} meta={meta.data} />)}</div>
    <Card className="space-y-4"><div><p className="eyebrow">{t('group.when')}</p><p className="mt-2 text-xl font-bold">{formatDate(data.slot.date)}</p><p className="text-sm text-ink-muted">{data.slot.start}–{data.slot.end} · Europe/Berlin</p></div><div className="border-t border-line pt-4"><p className="eyebrow">{t('group.where')}</p><p className="mt-2 font-semibold">{data.location}</p></div></Card>
    {data.sharedInterests.length > 0 && <section className="space-y-3"><h2>{t('group.shared')}</h2><div className="flex flex-wrap gap-2">{data.sharedInterests.map(id => <span key={id} className="rounded-full border border-line bg-white px-3 py-2 text-sm">{catalogLabel('interest', id, meta.data?.interests.find(i => i.id === id)?.label)}</span>)}</div></section>}
    {data.whyThisGroup.length > 0 && <Card className="space-y-3"><h2>{t('group.why')}</h2><ul className="space-y-3 text-sm leading-relaxed text-ink-muted">{data.whyThisGroup.map((text, index) => <li key={index} className="flex gap-2"><span aria-hidden="true">✦</span>{groupExplanation(text, meta.data)}</li>)}</ul></Card>}
    <Link className={`${buttonClasses} w-full`} to={`/group/${encodeURIComponent(groupId)}/play`}>{t('games.open')} ↗</Link>
    <Link className={`${buttonClasses} w-full`} to={`/group/${groupId}/connect`}>{t('group.connect')} ↗</Link><Link to="/me" className="inline-flex min-h-11 items-center text-sm underline">{t('common.back')}</Link>
  </div>;
}
export default function GroupPage() { const { groupId = '' } = useParams(); return <SessionGate><GroupContent key={groupId} groupId={groupId} /></SessionGate>; }
