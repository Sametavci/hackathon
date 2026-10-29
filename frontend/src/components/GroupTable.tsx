import type { GroupResponse, MetaResponse } from '../api/types';
import { useI18n } from '../i18n';
import MemberCard from './MemberCard';

const seats = ['col-start-2 row-start-1', 'col-start-3 row-start-2', 'col-start-2 row-start-3', 'col-start-1 row-start-2'];

export default function GroupTable({ members, number, meta }: {
  members: GroupResponse['members']; number: number | null; meta: MetaResponse | null;
}) {
  const { t } = useI18n();
  return <section aria-label={t('group.seating')} className="group-table mx-auto grid w-full max-w-lg grid-cols-3 items-center gap-x-1 gap-y-2 py-2">
    <div className="relative col-start-2 row-start-2 flex min-h-36 flex-col items-center justify-center gap-1 rounded-[45%] border-4 border-brand-soft bg-brand px-1 py-7 text-center text-white shadow-[0_6px_0_0_#dce9df]">
      <span aria-hidden="true" className="absolute left-2 top-3 h-5 w-5 rounded-full border-4 border-white/80 bg-accent" />
      <span aria-hidden="true" className="absolute bottom-3 right-2 h-5 w-5 rounded-full border-4 border-white/80 bg-accent" />
      <span className="text-xs font-semibold">{t(number === null ? 'group.yourTable' : 'group.tableLabel')}</span>
      {number !== null && <span className="text-3xl font-bold tabular-nums">{number}</span>}
      <span className="mt-1 text-xs">Lunch²</span>
    </div>
    {members.map((member, index) => <div key={member.id} className={seats[index] ?? ''}>
      <MemberCard member={member} index={index} meta={meta} compact />
    </div>)}
  </section>;
}
