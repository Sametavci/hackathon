/** Calendar arithmetic uses UTC noon; today's civil date is always Berlin's. */
export function berlinToday(now = new Date()): string {
  const parts = new Intl.DateTimeFormat('en-GB', { timeZone: 'Europe/Berlin', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(now);
  const part = (type: string) => parts.find(value => value.type === type)!.value;
  return `${part('year')}-${part('month')}-${part('day')}`;
}
export function addDays(date: string, amount: number): string {
  const value = new Date(`${date}T12:00:00Z`);
  value.setUTCDate(value.getUTCDate() + amount);
  return value.toISOString().slice(0, 10);
}
export function weekStart(date: string): string {
  return addDays(date, -((new Date(`${date}T12:00:00Z`).getUTCDay() + 6) % 7));
}
export function weekDates(date: string): string[] {
  return Array.from({ length: 7 }, (_, index) => addDays(weekStart(date), index));
}
export function monthStart(date: string): string { return `${date.slice(0, 7)}-01`; }
export function addMonths(date: string, amount: number): string {
  const value = new Date(`${monthStart(date)}T12:00:00Z`);
  value.setUTCMonth(value.getUTCMonth() + amount);
  return value.toISOString().slice(0, 10);
}
export function monthDates(date: string): string[] {
  const first = weekStart(monthStart(date));
  const next = addMonths(date, 1);
  const days = Math.round((Date.parse(`${next}T12:00:00Z`) - Date.parse(`${first}T12:00:00Z`)) / 86400000);
  return Array.from({ length: Math.ceil(days / 7) * 7 }, (_, index) => addDays(first, index));
}

export function isWeekday(date: string): boolean {
  const day = new Date(`${date}T12:00:00Z`).getUTCDay();
  return day !== 0 && day !== 6;
}
export function nextWeekday(date: string, direction = 1): string {
  let next = date;
  while (!isWeekday(next)) next = addDays(next, direction);
  return next;
}
