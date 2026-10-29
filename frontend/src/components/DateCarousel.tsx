import { useEffect, useRef, useState, type KeyboardEvent } from 'react';
import { useI18n } from '../i18n';
import { formatDate as format } from '../i18n/presentation';
import { addDays, addMonths, berlinToday, isWeekday, nextWeekday, monthDates, monthStart, weekDates, weekStart } from './calendarDates';

interface Props {
  dates: string[];
  value: string;
  selectedDates: string[];
  onChange: (date: string) => void;
}
export default function DateCarousel({ dates, value, selectedDates, onChange }: Props) {
  const { t } = useI18n();
  const today = berlinToday();
  const availableDates = dates.filter(date => date >= today && isWeekday(date)).sort();
  const firstDate = availableDates[0] ?? nextWeekday(today);
  const lastDate = availableDates[availableDates.length - 1] ?? firstDate;
  const active = availableDates.includes(value) ? value : firstDate;
  const [open, setOpen] = useState(false);
  const [month, setMonth] = useState(monthStart(active));
  const trigger = useRef<HTMLButtonElement>(null);
  const panel = useRef<HTMLDivElement>(null);
  const root = useRef<HTMLDivElement>(null);
  const touch = useRef<number | null>(null);
  const days = weekDates(active).slice(0, 5);
  useEffect(() => {
    if (!open) return;
    panel.current?.querySelector<HTMLButtonElement>(`[data-date="${active}"]`)?.focus();
    function outside(event: PointerEvent) {
      if (event.target instanceof Node && !root.current?.contains(event.target)) { setOpen(false); trigger.current?.focus(); }
    }
    document.addEventListener('pointerdown', outside);
    return () => document.removeEventListener('pointerdown', outside);
  }, [open, active]);
  function choose(date: string) { if (availableDates.includes(date)) onChange(date); }
  function moveWeek(amount: number) {
    const target = addDays(active, amount * 7);
    const next = target < firstDate ? firstDate : target > lastDate ? lastDate : target;
    const week = availableDates.filter(date => weekStart(date) === weekStart(next));
    choose(week.includes(next) ? next : week[0] ?? active);
  }
  function keyboard(event: KeyboardEvent, date: string, monthly: boolean) {
    const shift = event.key === 'ArrowLeft' ? -1 : event.key === 'ArrowRight' ? 1 : event.key === 'ArrowUp' ? -7 : event.key === 'ArrowDown' ? 7 : event.key === 'Home' ? weekDates(date).indexOf(date) * -1 : event.key === 'End' ? 4 - weekDates(date).indexOf(date) : null;
    if (shift === null) return;
    event.preventDefault();
    const next = nextWeekday(addDays(date, shift), shift < 0 ? -1 : 1);
    if (!availableDates.includes(next)) return;
    choose(next);
    if (monthly) setMonth(monthStart(next));
    requestAnimationFrame(() => (monthly ? panel.current : root.current)?.querySelector<HTMLButtonElement>(`[data-date="${next}"]`)?.focus());
  }
  function dayButton(date: string, monthly = false) {
    const current = date === active;
    const selected = selectedDates.includes(date);
    return <button key={date} data-date={date} type="button" disabled={!availableDates.includes(date)} aria-pressed={current} aria-current={date === today ? 'date' : undefined}
      aria-label={`${format(date, { weekday: 'long', day: 'numeric', month: 'long' })}${selected ? ` · ${t('register.dayHasSlots')}` : ''}${!dates.includes(date) ? ` · ${t('register.noSlots')}` : ''}`}
      onKeyDown={event => keyboard(event, date, monthly)} onClick={() => { choose(date); if (monthly) { setOpen(false); trigger.current?.focus(); } }}
      className={`relative flex min-h-11 min-w-0 flex-col items-center justify-center rounded-xl py-1 text-sm transition-colors motion-reduce:transition-none disabled:opacity-30 ${monthly ? 'h-11' : 'h-16'} ${current ? 'bg-brand text-white shadow-sm' : 'text-ink hover:bg-white'} ${monthly && date.slice(0, 7) !== month.slice(0, 7) ? 'opacity-40' : ''}`}>
      {!monthly && <span className="text-[10px] font-medium uppercase">{format(date, { weekday: 'short' })}</span>}
      <span className={`text-sm font-bold ${date === today ? 'underline decoration-2 underline-offset-4' : ''}`}>{Number(date.slice(-2))}</span>
      <span aria-hidden="true" className={`absolute bottom-1 h-1 w-1 rounded-full ${selected ? current ? 'bg-white' : 'bg-brand' : 'bg-transparent'}`} />
    </button>;
  }
  const arrowClass = 'grid h-11 w-11 shrink-0 place-items-center rounded-full text-lg text-ink hover:bg-brand-soft disabled:opacity-25';
  return <div ref={root} className="space-y-2" onKeyDown={event => { if (event.key === 'Escape' && open) { event.preventDefault(); setOpen(false); trigger.current?.focus(); } }}>
    <div className="flex items-center justify-between gap-2">
      <button type="button" aria-label={t('calendar.previousWeek')} disabled={weekStart(active) <= weekStart(firstDate)} onClick={() => moveWeek(-1)} className={arrowClass}>‹</button>
      <div className="flex items-center gap-1"><span aria-live="polite" className="text-sm font-semibold capitalize">{format(active, { month: 'long', year: 'numeric' })}</span>
      <button ref={trigger} type="button" aria-label={t('calendar.open')} aria-expanded={open} aria-controls="month-calendar" onClick={() => { setMonth(monthStart(active)); setOpen(value => !value); }} className={arrowClass}>
        <svg aria-hidden="true" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6"><rect x="3" y="5" width="18" height="16" rx="3" /><path d="M7 3v4m10-4v4M3 11h18m-13 4h2m4 0h2" /></svg>
      </button></div>
      <button type="button" aria-label={t('calendar.nextWeek')} disabled={!availableDates.length || weekStart(active) >= weekStart(lastDate)} onClick={() => moveWeek(1)} className={arrowClass}>›</button>
    </div>
    <div className="flex items-center gap-1 rounded-2xl bg-brand-soft p-1" onTouchStart={event => { touch.current = event.touches[0].clientX; }} onTouchEnd={event => {
      if (touch.current !== null) { const distance = event.changedTouches[0].clientX - touch.current; if (Math.abs(distance) > 45) moveWeek(distance < 0 ? 1 : -1); } touch.current = null;
    }}>
      <div role="group" aria-label={t('register.day')} className="grid min-w-0 flex-1 grid-cols-5 gap-1">{days.map(date => dayButton(date))}</div>
    </div>
    {open && <div id="month-calendar" ref={panel} role="region" aria-label={t('calendar.open')} className="rounded-2xl border border-line bg-white p-2 shadow-sm">
      <div className="mb-2 flex items-center justify-between"><button type="button" disabled={month <= monthStart(firstDate)} aria-label={t('calendar.previousMonth')} onClick={() => setMonth(addMonths(month, -1))} className={arrowClass}>‹</button>
        <span aria-live="polite" className="text-sm font-semibold capitalize">{format(month, { month: 'long', year: 'numeric' })}</span>
        <button type="button" aria-label={t('calendar.nextMonth')} disabled={!availableDates.length || month >= monthStart(lastDate)} onClick={() => setMonth(addMonths(month, 1))} className={arrowClass}>›</button></div>
      <div className="grid grid-cols-7 gap-0.5">{weekDates(month).map(date => <span key={date} className="py-2 text-center text-[10px] font-semibold uppercase text-ink-muted">{format(date, { weekday: 'short' })}</span>)}{monthDates(month).map(date => dayButton(date, true))}</div>
    </div>}
  </div>;
}
