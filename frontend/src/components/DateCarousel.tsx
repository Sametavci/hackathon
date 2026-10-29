import { useLayoutEffect, useRef, type KeyboardEvent } from 'react';
import { useI18n } from '../i18n';
import { formatDate as format } from '../i18n/presentation';

interface Props {
  dates: string[];
  value: string;
  selectedDates: string[];
  onChange: (date: string) => void;
}


export default function DateCarousel({ dates, value, selectedDates, onChange }: Props) {
  const { t } = useI18n();
  const track = useRef<HTMLDivElement>(null);
  const settleTimer = useRef<ReturnType<typeof setTimeout>>();
  const current = useRef({ value, onChange });
  current.current = { value, onChange };
  const datesKey = dates.join('|');

  function center(date: string, smooth = false) {
    const root = track.current;
    const button = root?.querySelector<HTMLButtonElement>(`[data-date="${date}"]`);
    if (!root || !button) return;
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    root.scrollTo({ left: button.offsetLeft + button.offsetWidth / 2 - root.clientWidth / 2, behavior: smooth && !reduced ? 'smooth' : 'instant' });
  }

  useLayoutEffect(() => {
    center(current.current.value);
    const observer = new ResizeObserver(() => center(current.current.value));
    if (track.current) observer.observe(track.current);
    return () => { observer.disconnect(); clearTimeout(settleTimer.current); };
  }, [datesKey]);

  function select(date: string, focus = false) {
    clearTimeout(settleTimer.current);
    current.current.onChange(date);
    center(date, true);
    if (focus) track.current?.querySelector<HTMLButtonElement>(`[data-date="${date}"]`)?.focus({ preventScroll: true });
  }

  function settle() {
    clearTimeout(settleTimer.current);
    settleTimer.current = setTimeout(() => {
      const root = track.current;
      if (!root) return;
      const middle = root.scrollLeft + root.clientWidth / 2;
      const buttons = [...root.querySelectorAll<HTMLButtonElement>('[data-date]')];
      const nearest = buttons.reduce<HTMLButtonElement | undefined>((best, button) => !best || Math.abs(button.offsetLeft + button.offsetWidth / 2 - middle) < Math.abs(best.offsetLeft + best.offsetWidth / 2 - middle) ? button : best, undefined);
      const date = nearest?.dataset.date;
      if (date && date !== current.current.value) current.current.onChange(date);
    }, 120);
  }

  function keyboard(event: KeyboardEvent<HTMLDivElement>) {
    const index = dates.indexOf(value);
    const next = event.key === 'ArrowLeft' ? Math.max(0, index - 1)
      : event.key === 'ArrowRight' ? Math.min(dates.length - 1, index + 1)
      : event.key === 'Home' ? 0 : event.key === 'End' ? dates.length - 1 : -1;
    if (next < 0) return;
    event.preventDefault(); select(dates[next], true);
  }

  return <div className="relative isolate overflow-hidden rounded-3xl bg-brand-soft">
    <div aria-hidden="true" className="pointer-events-none absolute inset-y-3 left-1/2 -z-10 w-28 -translate-x-1/2 rounded-2xl border border-line bg-white shadow-sm" />
    <div ref={track} role="radiogroup" aria-label={t('register.day')} aria-orientation="horizontal" onScroll={settle} onKeyDown={keyboard}
      className="day-carousel relative flex snap-x snap-mandatory gap-4 overflow-x-auto overscroll-x-contain py-3" style={{ paddingInline: 'calc(50% - 3.5rem)' }}>
      {dates.map(date => <button key={date} type="button" role="radio" data-date={date} aria-checked={date === value} tabIndex={date === value ? 0 : -1}
        aria-label={format(date, { weekday: 'long', day: 'numeric', month: 'long' })} onClick={() => select(date)}
        className={`flex min-h-24 w-28 shrink-0 snap-center flex-col items-center justify-center gap-1 rounded-2xl transition-colors motion-reduce:transition-none ${date === value ? 'text-ink' : 'text-ink-muted'}`}>
        <span className="text-xs font-semibold uppercase tracking-wider">{format(date, { weekday: 'short' })}</span>
        <span className={`text-2xl ${date === value ? 'font-black' : 'font-medium'}`}>{format(date, { day: 'numeric', month: 'short' })}</span>
        <span className={`h-1.5 w-1.5 rounded-full ${selectedDates.includes(date) ? 'bg-brand' : 'bg-transparent'}`} aria-hidden="true" />
        {selectedDates.includes(date) && <span className="sr-only">{t('register.dayHasSlots')}</span>}
      </button>)}
    </div>
    <div aria-hidden="true" className="pointer-events-none absolute inset-y-0 left-0 w-8 bg-gradient-to-r from-brand-soft to-transparent" />
    <div aria-hidden="true" className="pointer-events-none absolute inset-y-0 right-0 w-8 bg-gradient-to-l from-brand-soft to-transparent" />
  </div>;
}
