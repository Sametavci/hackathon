type Props = { title: string; ticket: string; owner: string };

/** Temporary page body until the owning ticket replaces it. */
export default function Placeholder({ title, ticket, owner }: Props) {
  return (
    <section className="rounded-2xl bg-white p-6 shadow-sm">
      <h1 className="text-2xl font-bold">{title}</h1>
      <p className="mt-2 text-ink-muted">
        Placeholder – implemented in {ticket} ({owner}).
      </p>
    </section>
  );
}
