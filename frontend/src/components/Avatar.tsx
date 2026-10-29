import { useI18n } from '../i18n';

export default function Avatar({ name, small = false }: { name: string; small?: boolean }) {
  const { locale } = useI18n();
  return <span aria-hidden="true" className={`inline-grid shrink-0 place-items-center rounded-full bg-brand-soft font-semibold text-brand ${small ? 'h-10 w-10 text-sm' : 'h-14 w-14 text-xl'}`}>
    {Array.from(name.trim())[0]?.toLocaleUpperCase(locale) ?? '·'}
  </span>;
}
