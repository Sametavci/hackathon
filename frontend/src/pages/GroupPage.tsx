import { Link, useParams } from 'react-router-dom';
import { getGroup, getMeta } from '../api/endpoints';
import { buttonClasses } from '../components/Button';
import Card from '../components/Card';
import PageHeading from '../components/PageHeading';
import Disclosure from '../components/Disclosure';
import ErrorBanner from '../components/ErrorBanner';
import LoadState from '../components/LoadState';
import GroupTable from '../components/GroupTable';
import { meetingTime, tableLocation } from '../components/tablePlan';
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
  const location = tableLocation(data.location);
  const meeting = meetingTime(data.slot.date, data.slot.start);
  return <div className="space-y-5 pt-3"><PageHeading title={t('group.title')} description={t('group.intro')} />
    {!!meta.error && <ErrorBanner error={meta.error} onRetry={meta.reload} />}
    {!data.members.length && <p>{t('group.empty')}</p>}
    <GroupTable members={data.members} number={location.number} meta={meta.data} />
    <Card className="space-y-4"><div className="rounded-2xl bg-brand-soft p-4"><p className="eyebrow">{t('group.meetingPoint')}</p><p className="mt-2 text-lg font-semibold">{t('group.meetAt', { time: meeting.time })}</p><p className="mt-1 text-sm">{t('group.entrance')}</p><p className="mt-1 text-sm text-ink-muted">{t('group.fiveBefore')}</p>{meeting.date !== data.slot.date && <p className="mt-1 text-sm">{formatDate(meeting.date)}</p>}</div><div><p className="eyebrow">{t('group.when')}</p><p className="mt-2 text-lg font-semibold">{formatDate(data.slot.date)}</p><p className="text-sm text-ink-muted">{data.slot.start}–{data.slot.end}</p></div><div className="border-t border-line pt-4"><p className="eyebrow">{t('group.where')}</p><p className="mt-2 font-semibold">{location.venue}</p><p className="mt-1 text-sm text-ink-muted">{t('group.walkTogether')}</p></div></Card>
    {data.sharedInterests.length > 0 && <section className="space-y-3"><h2 className="text-sm font-semibold">{t('group.shared')}</h2><div className="flex flex-wrap gap-2">{data.sharedInterests.map(id => <span key={id} className="rounded-full bg-brand-soft px-3 py-1.5 text-xs">{catalogLabel('interest', id, meta.data?.interests.find(i => i.id === id)?.label)}</span>)}</div></section>}
    {data.whyThisGroup.length > 0 && <Disclosure title={t('group.why')}><ul className="space-y-3 text-sm leading-relaxed text-ink-muted">{data.whyThisGroup.map((text, index) => <li key={index} className="flex gap-2"><span aria-hidden="true">✦</span>{groupExplanation(text, meta.data)}</li>)}</ul></Disclosure>}
    <Link className={`${buttonClasses} w-full`} to={`/group/${encodeURIComponent(groupId)}/play`}>{t('games.open')} ↗</Link>
    <Link className={`${buttonClasses} w-full !border-line !bg-white !text-ink`} to={`/group/${groupId}/connect`}>{t('group.connect')} ↗</Link><Link to="/me" className="inline-flex min-h-11 items-center text-sm underline">{t('common.back')}</Link>
  </div>;
}
export default function GroupPage() { const { groupId = '' } = useParams(); return <SessionGate><GroupContent key={groupId} groupId={groupId} /></SessionGate>; }
