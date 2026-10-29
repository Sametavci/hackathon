import { useI18n } from '../i18n';

export default function BrandLogo() {
  const { t } = useI18n();
  const name = t('app.name');
  return <span aria-hidden="true" className="inline-flex shrink-0 items-center gap-2.5">
    <img src="/lunch2-icon.svg" alt="" width={44} height={44} className="h-11 w-11 shrink-0" />
    <span className="whitespace-nowrap text-[24px] font-black leading-none tracking-[-0.055em] text-ink">
      {name.replace(/²$/, '')}<sup className="relative -top-[0.5em] ml-[0.08em] text-[0.58em] leading-none">²</sup>
    </span>
  </span>;
}
