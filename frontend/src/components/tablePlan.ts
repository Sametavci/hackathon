/** Table labels are assigned once by matching and travel in the existing location. */
export function tableLocation(location: string): { venue: string; number: number | null } {
  const match = / · Lunch² Table ([1-9]\d*)$/.exec(location);
  return { venue: match ? location.slice(0, match.index) : location, number: match ? Number(match[1]) : null };
}

/** Civil time arithmetic keeps the meeting five minutes before the selected local slot. */
export function meetingTime(date: string, start: string): { date: string; time: string } {
  const meeting = new Date(`${date}T${start}:00Z`);
  meeting.setUTCMinutes(meeting.getUTCMinutes() - 5);
  return { date: meeting.toISOString().slice(0, 10), time: meeting.toISOString().slice(11, 16) };
}
