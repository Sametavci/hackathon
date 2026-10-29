import type { ReactNode } from 'react';

export default function Disclosure({ title, children }: { title: string; children: ReactNode }) {
  return <details className="group rounded-2xl border border-line bg-white px-4">
    <summary className="flex min-h-14 cursor-pointer list-none items-center justify-between gap-3 text-sm font-medium">
      <span>{title}</span>
      <svg aria-hidden="true" viewBox="0 0 20 20" width="18" height="18" fill="none" className="shrink-0 text-ink-muted transition-transform group-open:rotate-180 motion-reduce:transition-none"><path d="m5 7.5 5 5 5-5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" /></svg>
    </summary>
    <div className="space-y-3 border-t border-line pb-4 pt-3 text-sm leading-relaxed text-ink-muted">{children}</div>
  </details>;
}
