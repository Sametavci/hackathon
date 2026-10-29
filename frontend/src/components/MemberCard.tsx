import { useState } from 'react';
import type { GroupResponse, MetaResponse } from '../api/types';
import { t } from '../i18n/en';

export default function MemberCard({ member, index, meta }: {
  member: GroupResponse['members'][number]; index: number; meta: MetaResponse | null;
}) {
  const [revealed, setRevealed] = useState(false);
  return (
    <button type="button" onClick={() => setRevealed(true)} aria-expanded={revealed}
      aria-label={revealed ? member.displayName : `${t('group.reveal')} ${index + 1}`}
      className="min-h-48 min-w-0 rounded-3xl text-center [perspective:800px]">
      <span className={`relative grid min-h-48 w-full rounded-3xl [transform-style:preserve-3d] motion-safe:transition-transform motion-safe:duration-500 ${revealed ? '[transform:rotateY(180deg)]' : ''}`}>
        <span aria-hidden="true" className="col-start-1 row-start-1 flex min-h-48 flex-col items-center justify-center gap-3 rounded-3xl border border-ink bg-ink p-3 text-accent [backface-visibility:hidden]">
          <span className="text-4xl font-black">{index + 1}</span>
          <span className="text-xs font-semibold">{t('group.reveal')}</span>
        </span>
        <span aria-hidden={!revealed} className="col-start-1 row-start-1 flex min-h-48 min-w-0 flex-col items-center justify-center gap-3 rounded-3xl border border-line bg-white p-3 [backface-visibility:hidden] [transform:rotateY(180deg)]">
          <span aria-hidden="true" className="grid h-12 w-12 place-items-center rounded-2xl bg-brand-soft text-xl font-black text-ink">{member.displayName.slice(0, 1).toLocaleUpperCase('en')}</span>
          <span className="max-w-full break-words font-bold">{member.displayName}</span>
          {(member.isMe || member.isMyFriend) && <span className="text-xs text-ink-muted">{t(member.isMe ? 'group.you' : 'group.friend')}</span>}
          <span className="text-xs leading-relaxed text-ink-muted">{member.interests.slice(0, 2).map(id => meta?.interests.find(i => i.id === id)?.label ?? id).join(' · ')}</span>
        </span>
      </span>
    </button>
  );
}
