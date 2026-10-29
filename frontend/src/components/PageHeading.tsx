interface Props {
  title: string;
  description?: string;
  eyebrow?: string;
}

export default function PageHeading({ title, description, eyebrow }: Props) {
  return <header className="space-y-2 pb-1">
    {eyebrow && <p className="text-xs font-medium text-ink-muted">{eyebrow}</p>}
    <h1 className="text-[1.75rem] font-bold leading-[1.15] tracking-tight sm:text-3xl">{title}</h1>
    {description && <p className="max-w-prose text-sm leading-relaxed text-ink-muted">{description}</p>}
  </header>;
}
