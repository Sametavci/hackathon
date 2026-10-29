import { useState } from 'react';
import type { GroupResponse, MetaResponse } from '../api/types';
import { catalogLabel } from '../i18n/presentation';
import { useI18n } from '../i18n';

export default function MemberCard({ member, index, meta, compact = false }: {
  member: GroupResponse['members'][number]; index: number; meta: MetaResponse | null; compact?: boolean;
}) {
  const { t, locale } = useI18n();
  const displayName = member.displayName === 'Former participant' ? t('connect.deleted') : member.displayName;
  const [revealed, setRevealed] = useState(false);
  return (
    <button type="button" onClick={() => setRevealed(true)} aria-expanded={revealed}
      aria-label={revealed ? displayName : `${t('group.reveal')} ${index + 1}`}
      className={`w-full min-w-0 rounded-2xl text-center [perspective:800px] ${compact ? 'group-seat' : ''}`}>
      <span className={`relative grid ${compact ? 'min-h-32' : 'min-h-40'} w-full rounded-2xl [transform-style:preserve-3d] motion-safe:transition-transform motion-safe:duration-500 ${revealed ? '[transform:rotateY(180deg)]' : ''}`}>
        <span aria-hidden="true" className={`col-start-1 row-start-1 flex ${compact ? 'min-h-32' : 'min-h-40'} flex-col items-center justify-center gap-2 rounded-2xl border border-line bg-brand-soft p-2 text-brand [backface-visibility:hidden]`}>
          <span className="text-3xl font-semibold">{index + 1}</span>
          <span className="text-xs font-semibold">{t('group.reveal')}</span>
        </span>
        <span aria-hidden={!revealed} className={`col-start-1 row-start-1 flex ${compact ? 'min-h-32' : 'min-h-40'} min-w-0 flex-col items-center justify-center gap-2 rounded-2xl border border-line bg-white p-2 [backface-visibility:hidden] [transform:rotateY(180deg)]`}>
          <span aria-hidden="true" className="grid h-12 w-12 place-items-center rounded-full bg-brand-soft text-xl font-semibold text-ink">{displayName.slice(0, 1).toLocaleUpperCase(locale)}</span>
          <span className="max-w-full break-words text-sm font-semibold">{displayName}</span>
          {(member.isMe || member.isMyFriend) && <span className="text-xs text-ink-muted">{t(member.isMe ? 'group.you' : 'group.friend')}</span>}
          <span className="text-xs leading-relaxed text-ink-muted">{member.interests.slice(0, 2).map(id => catalogLabel('interest', id, meta?.interests.find(i => i.id === id)?.label)).join(' · ')}</span>
        </span>
      </span>
    </button>
  );
}
