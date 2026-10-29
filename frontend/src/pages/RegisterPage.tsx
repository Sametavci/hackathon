import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { ApiError } from '../api/client';
import { getInvite, getMeta, registerParticipant } from '../api/endpoints';
import { getToken, setToken } from '../api/session';
import type { Category, Campus, FieldError, InvitePreview, RegistrationRequest } from '../api/types';
import { buddySteps, hasBuddyPlan, nextRegistrationStep } from '../api/buddyFlow';
import BuddySessionChoice from '../components/BuddySessionChoice';
import ReturnLink from '../components/ReturnLink';
import { validateRegistration } from '../api/validation';
import { createAutoAdvance, errorsForStep, isAutomaticStep, registrationSteps, stepForField, toggleDaySlot } from '../api/registrationFlow';
import Button, { buttonClasses } from '../components/Button';
import Chip from '../components/Chip';
import Card from '../components/Card';
import DateCarousel from '../components/DateCarousel';
import { berlinToday, isWeekday, nextWeekday } from '../components/calendarDates';
import ErrorBanner from '../components/ErrorBanner';
import LoadState from '../components/LoadState';
import { useResource } from '../hooks/useResource';
import { catalogLabel, sortByLabel, fieldMessage, formatDate as dateLabel, formatSlot } from '../i18n/presentation';
import { useI18n, type MessageKey } from '../i18n';

const titles: MessageKey[] = ['register.campus', 'register.category', 'register.languages', 'register.interests', 'register.study', 'register.mensa', 'register.slots', 'register.name', 'register.review'];
const summaryLabels: MessageKey[] = ['register.summaryCampus', 'register.summaryCategory', 'register.summaryLanguages', 'register.summaryInterests', 'register.summaryStudy', 'register.summaryMensa', 'register.summaryWhen', 'register.nickname'];

export default function RegisterPage() {
  const [params] = useSearchParams();
  const code = params.get('invite');
  const newParticipant = params.get('new') === '1';
  const { t } = useI18n();
  const invite = useResource(`invite:${code}`, () => getInvite(code!), !!code);
  const meta = useResource('buddy-meta', getMeta, !!code);
  if (!code) return <RegistrationForm key="solo" />;
  if (invite.error || (invite.data && (!invite.data.valid || invite.data.inviterMatchStatus === 'MATCHED'))) return <Card className="space-y-4"><p>{t(invite.data?.inviterMatchStatus === 'MATCHED' ? 'buddy.matched' : invite.data?.valid === false ? 'buddy.used' : 'buddy.unavailable')}</p>{!!invite.error && <ErrorBanner error={invite.error} onRetry={invite.reload} />}<Link className={buttonClasses} to="/register">{t('buddy.solo')}</Link></Card>;
  if (!invite.data) return <LoadState {...invite} />;
  if (!meta.data) return <LoadState {...meta} />;
  if (!hasBuddyPlan(invite.data, meta.data)) return <Card className="space-y-4"><p>{t('buddy.unavailable')}</p><Link className={buttonClasses} to="/register">{t('buddy.solo')}</Link></Card>;
  if (getToken() && !newParticipant) return <Card className="space-y-4"><h1>{t('buddy.invited', { name: invite.data.inviterDisplayName })}</h1><BuddySessionChoice code={code} /></Card>;
  return <RegistrationForm key={code} invite={invite.data} />;
}

function RegistrationForm({ invite }: { invite?: InvitePreview }) {
  const { t } = useI18n();
  const meta = useResource('meta', getMeta);
  const navigate = useNavigate();
  const steps = buddySteps(!!invite);
  const [step, setStep] = useState(invite ? 7 : 0);
  const [name, setName] = useState('');
  const [category, setCategory] = useState<Category | ''>('');
  const [campus, setCampus] = useState<Campus | ''>(invite?.campus ?? '');
  const [mensaId, setMensaId] = useState(invite?.mensaId ?? '');
  const [languages, setLanguages] = useState<string[]>([]);
  const [interests, setInterests] = useState<string[]>([]);
  const [slots, setSlots] = useState<string[]>([]);
  const [day, setDay] = useState(() => nextWeekday(berlinToday()));
  const [studyField, setStudyField] = useState('');
  const [consent, setConsent] = useState(false);
  const [errors, setErrors] = useState<FieldError[]>([]);
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const [scheduleChanged, setScheduleChanged] = useState(false);
  const heading = useRef<HTMLHeadingElement>(null);
  const errorHeading = useRef<HTMLDivElement>(null);
  const pending = useRef(false);
  const autoAdvance = useMemo(() => createAutoAdvance(setStep), []);
  useEffect(() => {
    heading.current?.focus({ preventScroll: true });
    return () => autoAdvance.cancel();
  }, [step, autoAdvance, meta.data]);

  const toggle = (values: string[], value: string, set: (next: string[]) => void) => {
    set(values.includes(value) ? values.filter(v => v !== value) : [...values, value]); setErrors([]);
  };
  function go(next: number) { autoAdvance.cancel(); setErrors([]); setError(null); setStep(next); }
  function selectAndAdvance(select: () => void) {
    if (autoAdvance.pending()) return;
    select(); setErrors([]); setError(null); autoAdvance.schedule(nextRegistrationStep(step, !!invite));
  }
  function clearSchedule() { if (slots.length) setScheduleChanged(true); setSlots([]); setDay(nextWeekday(berlinToday())); }
  function chooseCampus(value: Campus) {
    selectAndAdvance(() => { if (campus !== value) { setCampus(value); setMensaId(''); clearSchedule(); } });
  }
  function chooseMensa(value: string) {
    selectAndAdvance(() => { if (mensaId !== value) { setMensaId(value); clearSchedule(); } });
  }
  const input: RegistrationRequest = { displayName: name.trim(), category: category as Category, campus: campus as Campus, mensaId, languages, interests, slotIds: slots, studyField: studyField || null, inviteCode: invite?.code ?? null, consent };
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!meta.data || pending.current || isAutomaticStep(step)) return;
    const validation = step === 8 ? validateRegistration(input, meta.data) : errorsForStep(input, meta.data, step);
    setErrors(validation); setError(null);
    if (validation.length) {
      if (step === 8) setStep(stepForField(validation[0].field));
      requestAnimationFrame(() => errorHeading.current?.focus()); return;
    }
    if (step < 8) { go(nextRegistrationStep(step, !!invite)); return; }
    pending.current = true; setBusy(true);
    try {
      if (invite) {
        const latest = await getInvite(invite.code);
        if (!latest.valid) throw new ApiError(409, 'INVITE_USED', t('buddy.used'));
        if (latest.inviterMatchStatus === 'MATCHED') throw new Error(t('buddy.matched'));
        if (!hasBuddyPlan(latest, meta.data)) throw new Error(t('buddy.unavailable'));
      }
      const result = await registerParticipant(input); setToken(result.accessToken); navigate('/me', { replace: true }); }
    catch (e) {
      setError(e);
      if (e instanceof ApiError) { setErrors(e.details); if (e.details.length) setStep(stepForField(e.details[0].field)); }
      requestAnimationFrame(() => errorHeading.current?.focus());
    } finally { pending.current = false; setBusy(false); }
  }
  if (getToken() && !invite) return <Card className="space-y-4"><p>{t('register.existing')}</p><Link className={buttonClasses} to="/me">{t('landing.return')}</Link></Card>;
  if (!meta.data) return <LoadState {...meta} />;
  const catalog = meta.data;
  const mensas = (catalog.mensas ?? []).filter(m => m.campus === campus);
  const mensa = mensas.find(m => m.id === mensaId);
  const today = berlinToday();
  const available = catalog.slots.filter(s => s.date >= today && isWeekday(s.date) && mensa?.slotIds.includes(s.id) && (!invite || invite.slotIds?.includes(s.id))).sort((a, b) => a.id.localeCompare(b.id));
  const dates = [...new Set(available.map(s => s.date))];
  const selectedDay = dates.includes(day) ? day : dates[0] ?? nextWeekday(today);
  const selectedSlots = catalog.slots.filter(s => slots.includes(s.id)).sort((a, b) => a.id.localeCompare(b.id));
  const selectedDates = [...new Set(selectedSlots.map(s => s.date))];
  const summary = [
    catalog.campuses.find(c => c.id === campus)?.label,
    category ? t(category === 'DOMESTIC' ? 'register.domestic' : 'register.international') : '',
    catalog.languages.filter(l => languages.includes(l.code)).map(l => catalogLabel('language', l.code, l.label)).join(', '),
    catalog.interests.filter(i => interests.includes(i.id)).map(i => `${i.emoji} ${catalogLabel('interest', i.id, i.label)}`).join(', '),
    studyField ? catalogLabel('study', studyField, catalog.studyFields.find(f => f.id === studyField)?.label) : t('register.skipped'),
    mensa ? `${mensa.name} · ${mensa.address}` : '',
    selectedSlots.map(s => `${dateLabel(s.date)} · ${s.start}–${s.end}`).join(' / '), name.trim(),
  ];
  return <div className="space-y-5">
    {invite && <Card className="space-y-2 !bg-brand-soft"><p className="font-semibold">{t('buddy.invited', { name: invite.inviterDisplayName })}</p><p className="text-sm text-ink-muted">{t('buddy.plan')}</p><Link to="/register" className="inline-flex min-h-11 items-center text-sm underline">{t('buddy.solo')}</Link></Card>}
    {invite && getToken() && <><p className="text-sm text-ink-muted">{t('buddy.sessionKept')}</p><ReturnLink /></>}
    <div className="flex min-h-11 items-center gap-3">
      {steps.indexOf(step) > 0 ? <button type="button" disabled={busy} onClick={() => go(steps[steps.indexOf(step) - 1])} aria-label={t('register.back')} className="grid h-11 w-11 shrink-0 place-items-center rounded-full border border-line text-xl hover:bg-brand-soft disabled:opacity-50">←</button> : <span className="h-11 w-2" aria-hidden="true" />}
      <div role="progressbar" aria-label={t('register.title')} aria-valuemin={0} aria-valuemax={steps.length} aria-valuenow={steps.indexOf(step) + 1} aria-valuetext={`${t('register.step')} ${steps.indexOf(step) + 1} ${t('register.of')} ${steps.length}`} className="h-1.5 flex-1 overflow-hidden rounded-full bg-brand-soft">
        <div className="h-full rounded-full bg-brand transition-all duration-300 motion-reduce:transition-none" style={{ width: `${(steps.indexOf(step) + 1) / steps.length * 100}%` }} />
      </div><span aria-hidden="true" className="text-xs tabular-nums text-ink-muted">{steps.indexOf(step) + 1}/{steps.length}</span>
    </div>
    <form onSubmit={submit} noValidate className="space-y-5" aria-busy={busy}>
      <section key={step} className="wizard-enter space-y-5" aria-labelledby="step-title">
        <h1 id="step-title" ref={heading} tabIndex={-1} className="text-[1.75rem] font-bold leading-tight focus:outline-none">{t(titles[step])}</h1>
        {(error || errors.length > 0) && <div ref={errorHeading} tabIndex={-1} className="rounded-2xl"><ErrorBanner error={error ?? new Error(errors.map(fieldMessage).join(' '))} /></div>}
        {step === 0 && <div className="grid gap-3">{catalog.campuses.map(c => <Chip key={c.id} selected={campus === c.id} onClick={() => chooseCampus(c.id)} className="min-h-24 text-left text-xl">{c.label}</Chip>)}</div>}
        {step === 1 && <div className="grid gap-3">{(['DOMESTIC', 'INTERNATIONAL'] as const).map(value => <Chip key={value} selected={category === value} onClick={() => selectAndAdvance(() => setCategory(value))} className="min-h-24 text-left text-base">{t(value === 'DOMESTIC' ? 'register.domestic' : 'register.international')}</Chip>)}</div>}
        {step === 2 && <div className="grid grid-cols-2 items-stretch gap-2">{sortByLabel(catalog.languages, l => catalogLabel('language', l.code, l.label)).map(l => <Chip key={l.code} className="min-w-0 px-3 text-left" selected={languages.includes(l.code)} disabled={languages.length >= 4 && !languages.includes(l.code)} onClick={() => toggle(languages, l.code, setLanguages)}><span className="min-w-0 break-words">{catalogLabel('language', l.code, l.label)}</span></Chip>)}</div>}
        {step === 3 && <div className="grid grid-cols-2 items-stretch gap-2">{sortByLabel(catalog.interests, i => catalogLabel('interest', i.id, i.label)).map(i => <Chip key={i.id} className="min-w-0 px-3 text-left" selected={interests.includes(i.id)} disabled={interests.length >= 5 && !interests.includes(i.id)} onClick={() => toggle(interests, i.id, setInterests)}><span aria-hidden="true" className="shrink-0">{i.emoji}</span><span className="min-w-0 break-words">{catalogLabel('interest', i.id, i.label)}</span></Chip>)}</div>}
        {(step === 2 || step === 3) && <p role="status" className="text-xs tabular-nums text-ink-muted">{step === 2 ? languages.length : interests.length}/{step === 2 ? 4 : 5} {t('register.selected')}</p>}
        {step === 4 && <div className="grid gap-2"><Chip selected={!studyField} onClick={() => selectAndAdvance(() => setStudyField(''))}>{t('register.studyNone')}</Chip>{catalog.studyFields.map(f => <Chip key={f.id} selected={studyField === f.id} onClick={() => selectAndAdvance(() => setStudyField(f.id))} className="text-left">{catalogLabel('study', f.id, f.label)}</Chip>)}</div>}
        {step === 5 && <div className="grid gap-3">{!mensas.length && <div className="space-y-3"><p role="alert">{t('register.noMensas')}</p><Button variant="secondary" onClick={meta.reload}>{t('common.retry')}</Button></div>}{mensas.map(m => <Chip key={m.id} selected={mensaId === m.id} onClick={() => chooseMensa(m.id)} className="min-h-20 items-start text-left"><span className="space-y-1"><span className="block text-base">{m.name}</span><span className="block text-xs font-normal text-ink-muted">{m.address}</span></span></Chip>)}</div>}
        {step === 6 && <div className="space-y-4">
          {scheduleChanged && <p role="status" className="text-xs text-ink-muted">{t('register.scheduleChanged')}</p>}
          <div className="space-y-1"><p className="text-sm text-ink-muted">{mensa?.name}</p><p className="text-xs text-ink-muted">{t('register.oneDayHint')}</p></div>
          {invite && <Link to="/register" className="inline-flex min-h-11 items-center text-sm underline">{t('buddy.noTimes')}</Link>}
          <DateCarousel dates={dates} value={selectedDay} selectedDates={selectedDates} onChange={setDay} />
          <fieldset><legend className="sr-only">{t('register.time')}</legend><div className="grid grid-cols-2 gap-2">{available.filter(s => s.date === selectedDay).map(s => <Chip key={s.id} selected={slots.includes(s.id)} disabled={slots.filter(id => id.slice(0, 10) === s.date).length >= 15 && !slots.includes(s.id)} onClick={() => { setSlots(current => toggleDaySlot(current, s.id)); setErrors([]); setScheduleChanged(false); }} aria-label={formatSlot(s)}>{s.start}–{s.end}</Chip>)}</div>{!available.some(s => s.date === selectedDay) && <p className="py-3 text-sm text-ink-muted" role="status">{t('register.noSlots')}</p>}</fieldset>
          <p role="status" className="text-xs tabular-nums text-ink-muted">{slots.length}/15 {t('register.selected')} · {selectedDates.length} {t(selectedDates.length === 1 ? 'register.daySingle' : 'register.daysCount')}</p>
        </div>}
        {step === 7 && <div><label htmlFor="displayName" className="sr-only">{t('register.nickname')}</label><input id="displayName" name="displayName" autoComplete="nickname" enterKeyHint="next" maxLength={30} placeholder={t('register.nickname')} value={name} onChange={e => { setName(e.target.value); setErrors([]); }} className="field" aria-invalid={errors.some(e => e.field === 'displayName')} /></div>}
        {step === 8 && <div className="space-y-4"><Card className="divide-y divide-line !py-1">{summary.map((answer, index) => <div key={registrationSteps[index]} className="flex items-start justify-between gap-3 py-3"><div className="min-w-0 space-y-1"><p className="text-xs text-ink-muted">{t(summaryLabels[index])}</p><p className="break-words text-sm font-semibold">{answer}</p></div>{!(invite && (index === 0 || index === 5)) && <button type="button" onClick={() => go(index)} className="min-h-11 shrink-0 text-sm font-semibold text-brand underline" aria-label={`${t('register.edit')}: ${t(summaryLabels[index])}`}>{t('register.edit')}</button>}</div>)}</Card><label className="flex min-h-11 cursor-pointer items-start gap-3 rounded-2xl border border-line bg-white p-4"><input type="checkbox" checked={consent} onChange={e => { setConsent(e.target.checked); setErrors([]); }} className="mt-1 h-5 w-5 shrink-0 accent-brand" /><span className="text-sm leading-relaxed">{t('register.consent')}</span></label><Link to="/privacy" target="_blank" rel="noopener noreferrer" className="inline-flex min-h-11 items-center text-sm underline underline-offset-4">{t('register.privacy')}</Link></div>}
      </section>
      {!isAutomaticStep(step) && <Button type="submit" disabled={busy} aria-label={step === 7 ? t('register.next') : undefined} className="min-h-14 w-full text-base">
        {step === 7 ? <span aria-hidden="true" className="text-xl">→</span> : t(busy ? 'common.saving' : step === 8 ? 'register.submit' : 'register.done')}
      </Button>}
    </form>
  </div>;
}
