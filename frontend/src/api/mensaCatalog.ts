import catalog from '../../../backend/src/main/resources/catalog/mensas.json';
import type { Campus, Mensa, Slot } from './types';

const minutes = (time: string) => Number(time.slice(0, 2)) * 60 + Number(time.slice(3));
const clock = (value: number) => `${Math.floor(value / 60).toString().padStart(2, '0')}:${(value % 60).toString().padStart(2, '0')}`;
export function createMensaCatalog(startDate: string): { mensas: Mensa[]; slots: Slot[] } {
  const slots = new Map<string, Slot>();
  const dates: string[] = [];
  const cursor = new Date(`${startDate}T12:00:00Z`);
  while (dates.length < 5) {
    if (cursor.getUTCDay() !== 0 && cursor.getUTCDay() !== 6) dates.push(cursor.toISOString().slice(0, 10));
    cursor.setUTCDate(cursor.getUTCDate() + 1);
  }
  function add(date: string, time: number) {
    const start = clock(time), id = `${date}T${start}`;
    const day = new Intl.DateTimeFormat('en-GB', { weekday: 'short', day: 'numeric', month: 'short', timeZone: 'UTC' }).format(new Date(`${date}T12:00:00Z`));
    slots.set(id, { id, date, start, end: clock(time + 45), label: `${day} · ${start}` });
    return id;
  }
  // Keep the original slots discoverable for legacy clients and saved profiles.
  for (const date of dates) for (const start of ['11:30', '12:15', '13:00']) add(date, minutes(start));
  const mensas = catalog.map(row => {
    const slotIds: string[] = [];
    for (const date of dates) {
      const hours = row.hours.find(h => h.dayOfWeek === new Date(`${date}T12:00:00Z`).getUTCDay());
      if (!hours) continue;
      for (let time = minutes(hours.lunchStart); time < minutes(hours.foodUntil) && time + 45 <= minutes(hours.closes); time += 45) slotIds.push(add(date, time));
    }
    return { ...row, campus: row.campus as Campus, slotIds };
  });
  return { mensas, slots: [...slots.values()] };
}
export function defaultMensaId(campus: Campus): string {
  return catalog.find(m => m.campus === campus && m.defaultForCampus)!.id;
}
