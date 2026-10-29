import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ApiError } from '../api/client';
import { getMeta, registerParticipant } from '../api/endpoints';
import { getToken, setToken } from '../api/session';
import type { Category, Campus, FieldError, RegistrationRequest } from '../api/types';
import { validateRegistration } from '../api/validation';
import Button, { buttonClasses } from '../components/Button';
import Chip from '../components/Chip';
import Card from '../components/Card';
import ErrorBanner from '../components/ErrorBanner';
import LoadState from '../components/LoadState';
import { useResource } from '../hooks/useResource';
import { t, type MessageKey } from '../i18n/en';
import { registrationSteps, errorsForStep, stepForField } from '../api/registrationFlow';

const dateLabel = (date: string) => new Intl.DateTimeFormat('en-GB', { weekday: 'short', day: 'numeric', month: 'short', timeZone: 'Europe/Berlin' }).format(new Date(`${date}T12:00:00Z`));
const titles: MessageKey[] = ['register.campus', 'register.category', 'register.languages', 'register.interests', 'register.study', 'register.mensa', 'register.slots', 'register.name', 'register.review'];
const hints: Partial<Record<number, MessageKey>> = { 1: 'register.categoryHint', 2: 'register.languagesHint', 3: 'register.interestsHint', 5: 'register.mensaHint', 6: 'register.slotsHint', 7: 'register.nameHint', 8: 'register.reviewHint' };

export default function RegisterPage() {
  const meta = useResource('meta', getMeta);
  const navigate = useNavigate();
  const [step, setStep] = useState(0);
  const [name, setName] = useState('');
  const [category, setCategory] = useState<Category | ''>('');
  const [campus, setCampus] = useState<Campus | ''>('');
  const [mensaId, setMensaId] = useState('');
  const [languages, setLanguages] = useState<string[]>([]);
  const [interests, setInterests] = useState<string[]>([]);
  const [slots, setSlots] = useState<string[]>([]);
  const [day, setDay] = useState('');
  const [studyField, setStudyField] = useState('');
  const [consent, setConsent] = useState(false);
  const [errors, setErrors] = useState<FieldError[]>([]);
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const [scheduleChanged, setScheduleChanged] = useState(false);
  const heading = useRef<HTMLHeadingElement>(null);
  const errorHeading = useRef<HTMLDivElement>(null);
  const pending = useRef(false);
  useEffect(() => { heading.current?.focus({ preventScroll: true }); }, [step]);
  const toggle = (values: string[], value: string, set: (next: string[]) => void) => {
    set(values.includes(value) ? values.filter(v => v !== value) : [...values, value]); setErrors([]);
  };
  function go(next: number) { setErrors([]); setError(null); setStep(next); }
  function clearSchedule() { if (slots.length) setScheduleChanged(true); setSlots([]); setDay(''); }
  function chooseCampus(value: Campus) { if (campus !== value) { setCampus(value); setMensaId(''); clearSchedule(); } setErrors([]); }
  function chooseMensa(value: string) { if (mensaId !== value) { setMensaId(value); clearSchedule(); } setErrors([]); }
  const input: RegistrationRequest = { displayName: name.trim(), category: category as Category, campus: campus as Campus, mensaId, languages, interests, slotIds: slots, studyField: studyField || null, inviteCode: null, consent };
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!meta.data || pending.current) return;
    const validation = step === 8 ? validateRegistration(input, meta.data) : errorsForStep(input, meta.data, step);
    setErrors(validation); setError(null);
    if (validation.length) {
      if (step === 8) setStep(stepForField(validation[0].field));
      requestAnimationFrame(() => errorHeading.current?.focus()); return;
    }
    if (step < 8) { go(step + 1); return; }
    pending.current = true; setBusy(true);
    try { const result = await registerParticipant(input); setToken(result.accessToken); navigate('/me', { replace: true }); }
    catch (e) {
      setError(e);
      if (e instanceof ApiError) { setErrors(e.details); if (e.details.length) setStep(stepForField(e.details[0].field)); }
      requestAnimationFrame(() => errorHeading.current?.focus());
    } finally { pending.current = false; setBusy(false); }
  }
  if (getToken()) return <Card className="space-y-4"><p>{t('register.existing')}</p><Link className={buttonClasses} to="/me">{t('landing.return')}</Link></Card>;
  if (!meta.data) return <LoadState {...meta} />;
  const catalog = meta.data;
  const mensas = (catalog.mensas ?? []).filter(m => m.campus === campus);
  const mensa = mensas.find(m => m.id === mensaId);
  const available = catalog.slots.filter(s => mensa?.slotIds.includes(s.id)).sort((a, b) => a.id.localeCompare(b.id));
  const dates = [...new Set(available.map(s => s.date))];
  const selectedDay = dates.includes(day) ? day : '';
  const selectedSlots = catalog.slots.filter(s => slots.includes(s.id)).sort((a, b) => a.id.localeCompare(b.id));
  const summary = [
    catalog.campuses.find(c => c.id === campus)?.label,
    category ? t(category === 'DOMESTIC' ? 'register.domestic' : 'register.international') : '',
    catalog.languages.filter(l => languages.includes(l.code)).map(l => l.label).join(', '),
    catalog.interests.filter(i => interests.includes(i.id)).map(i => `${i.emoji} ${i.label}`).join(', '),
    catalog.studyFields.find(f => f.id === studyField)?.label ?? t('register.skipped'),
    mensa ? `${mensa.name} · ${mensa.address}` : '',
    selectedSlots.map(s => `${dateLabel(s.date)} · ${s.start}–${s.end}`).join(' / '), name.trim(),
  ];
  return <div className="space-y-5 pt-3">
    <div className="flex items-center justify-between text-xs font-semibold text-ink-muted"><span>{t('app.name')} · THWS</span><span>{t('register.step')} {step + 1} {t('register.of')} {registrationSteps.length}</span></div>
    <div role="progressbar" aria-label={t('register.title')} aria-valuemin={0} aria-valuemax={registrationSteps.length} aria-valuenow={step + 1} className="h-2 overflow-hidden rounded-full bg-brand-soft"><div className="h-full rounded-full bg-brand transition-all duration-300 motion-reduce:transition-none" style={{ width: `${(step + 1) / registrationSteps.length * 100}%` }} /></div>
    <form onSubmit={submit} noValidate className="space-y-5" aria-busy={busy}>
      <section key={step} className="wizard-enter space-y-5" aria-labelledby="step-title" aria-describedby={hints[step] ? 'step-hint' : undefined}>
        <div className="space-y-3"><span aria-hidden="true" className="inline-grid h-12 w-12 place-items-center rounded-2xl bg-accent text-2xl">{['📍', '👋', '💬', '✨', '🎓', '🍽️', '🗓️', '☺', '✓'][step]}</span><h1 id="step-title" ref={heading} tabIndex={-1} className="text-3xl focus:outline-none">{t(titles[step])}</h1>{hints[step] && <p id="step-hint" className="text-sm leading-relaxed text-ink-muted">{t(hints[step]!)}</p>}</div>
        {(error || errors.length > 0) && <div ref={errorHeading} tabIndex={-1} className="rounded-2xl"><ErrorBanner error={error ?? new Error(errors.map(e => e.message).join(' '))} /></div>}
        {step === 0 && <div className="grid gap-3">{catalog.campuses.map(c => <Chip key={c.id} selected={campus === c.id} onClick={() => chooseCampus(c.id)} className="min-h-20 text-left text-lg">{c.label}</Chip>)}</div>}
        {step === 1 && <div className="grid gap-3">{(['DOMESTIC', 'INTERNATIONAL'] as const).map(value => <Chip key={value} selected={category === value} onClick={() => { setCategory(value); setErrors([]); }} className="min-h-20 text-left">{t(value === 'DOMESTIC' ? 'register.domestic' : 'register.international')}</Chip>)}</div>}
        {step === 2 && <div className="flex flex-wrap gap-2">{catalog.languages.map(l => <Chip key={l.code} selected={languages.includes(l.code)} disabled={languages.length >= 4 && !languages.includes(l.code)} onClick={() => toggle(languages, l.code, setLanguages)}>{l.label}</Chip>)}</div>}
        {step === 3 && <div className="flex flex-wrap gap-2">{catalog.interests.map(i => <Chip key={i.id} selected={interests.includes(i.id)} disabled={interests.length >= 5 && !interests.includes(i.id)} onClick={() => toggle(interests, i.id, setInterests)}><span aria-hidden="true">{i.emoji}</span>{i.label}</Chip>)}</div>}
        {(step === 2 || step === 3) && <p role="status" className="text-sm text-ink-muted">{step === 2 ? languages.length : interests.length} / {step === 2 ? 4 : 5} {t('register.selected')}</p>}
        {step === 4 && <div className="grid gap-2"><Chip selected={!studyField} onClick={() => setStudyField('')}>{t('register.studyNone')}</Chip>{catalog.studyFields.map(f => <Chip key={f.id} selected={studyField === f.id} onClick={() => setStudyField(f.id)} className="text-left">{f.label}</Chip>)}</div>}
        {step === 5 && <div className="grid gap-3">{!mensas.length && <p role="alert">{t('register.noMensas')}</p>}{mensas.map(m => <Chip key={m.id} selected={mensaId === m.id} onClick={() => chooseMensa(m.id)} className="items-start text-left"><span className="space-y-1"><span className="block text-base">{m.name}</span><span className="block text-xs font-normal text-ink-muted">{m.address}</span><span className="block text-xs font-normal text-ink-muted">{t('register.service')}: {m.hours[0].lunchStart}–{m.hours[0].closes} · {t('register.foodUntil')} {m.hours[0].foodUntil}</span>{m.hours[4].closes !== m.hours[0].closes && <span className="block text-xs font-normal text-ink-muted">{t('register.friday')}: {m.hours[4].lunchStart}–{m.hours[4].closes} · {t('register.foodUntil')} {m.hours[4].foodUntil}</span>}</span></Chip>)}</div>}
        {step === 6 && <div className="space-y-5">
          {scheduleChanged && <p role="status" className="rounded-2xl bg-accent/30 p-3 text-sm">{t('register.scheduleChanged')}</p>}
          <fieldset className="space-y-3"><legend className="mb-2 font-bold">{t('register.day')}</legend><div className="flex flex-wrap gap-2">{dates.map(date => <Chip key={date} selected={selectedDay === date} onClick={() => setDay(date)}>{dateLabel(date)}{slots.some(id => id.startsWith(date)) && <span aria-hidden="true">•</span>}</Chip>)}</div></fieldset>
          <fieldset className="space-y-3"><legend className="mb-2 font-bold">{t('register.time')}</legend>{!selectedDay && <p className="text-sm text-ink-muted">{t('register.chooseDayFirst')}</p>}<p className="text-xs text-ink-muted">{mensa?.name} · {t('register.duration')}</p><div className="grid grid-cols-2 gap-2">{available.filter(s => s.date === selectedDay).map(s => <Chip key={s.id} selected={slots.includes(s.id)} disabled={slots.length >= 15 && !slots.includes(s.id)} onClick={() => toggle(slots, s.id, setSlots)} aria-label={s.label}>{s.start}–{s.end}</Chip>)}</div>{!available.length && <p>{t('register.noSlots')}</p>}</fieldset>
          {!!selectedSlots.length && <div className="space-y-2 rounded-2xl bg-brand-soft p-4"><p role="status" className="text-sm font-bold">{slots.length} / 15 {t('register.selected')}</p>{selectedSlots.map(s => <p key={s.id} className="text-xs">{dateLabel(s.date)} · {s.start}–{s.end}</p>)}</div>}
        </div>}
        {step === 7 && <div className="space-y-2"><label htmlFor="displayName" className="text-sm font-bold">{t('register.name')}</label><input id="displayName" name="displayName" autoComplete="nickname" maxLength={30} value={name} onChange={e => { setName(e.target.value); setErrors([]); }} className="field" aria-describedby="step-hint" aria-invalid={errors.some(e => e.field === 'displayName')} /><p className="text-xs text-ink-muted">{t('register.demo')}</p></div>}
        {step === 8 && <div className="space-y-4"><Card className="divide-y divide-line !py-1">{summary.map((answer, index) => <div key={registrationSteps[index]} className="flex items-start justify-between gap-3 py-3"><div className="min-w-0 space-y-1"><p className="text-xs text-ink-muted">{t(titles[index])}</p><p className="break-words text-sm font-semibold">{answer}</p></div><button type="button" onClick={() => go(index)} className="min-h-11 shrink-0 text-sm font-semibold text-brand underline" aria-label={`${t('register.edit')}: ${t(titles[index])}`}>{t('register.edit')}</button></div>)}</Card><label className="flex min-h-11 cursor-pointer items-start gap-3 rounded-2xl border border-line bg-brand-soft p-4"><input type="checkbox" checked={consent} onChange={e => { setConsent(e.target.checked); setErrors([]); }} className="mt-1 h-5 w-5 shrink-0 accent-brand" /><span className="text-sm leading-relaxed">{t('register.consent')}</span></label><Link to="/privacy" target="_blank" rel="noopener noreferrer" className="inline-flex min-h-11 items-center text-sm underline underline-offset-4">{t('register.privacy')}</Link></div>}
      </section>
      <div className="flex gap-3 border-t border-line pt-4">{step > 0 && <Button variant="secondary" disabled={busy} onClick={() => go(step - 1)}>{t('common.back')}</Button>}<Button type="submit" disabled={busy || (step === 5 && !mensas.length)} className="flex-1">{t(busy ? 'common.saving' : step === 8 ? 'register.submit' : 'register.next')}<span aria-hidden="true">→</span></Button></div>
    </form>
  </div>;
}
