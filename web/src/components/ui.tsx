import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes } from "react";

export function Field({
  label,
  children,
}: {
  label: string;
  children: ReactNode;
}) {
  return (
    <label className="flex flex-col gap-1 text-sm">
      <span className="font-semibold text-[var(--mist)]">{label}</span>
      {children}
    </label>
  );
}

const control =
  "w-full rounded-md border border-[var(--line)] bg-[var(--night-mid)] px-3 py-2 text-[var(--paper)] outline-none focus:border-[var(--signal)]";

export function Input(props: InputHTMLAttributes<HTMLInputElement>) {
  return <input className={control} {...props} />;
}

export function Select(props: SelectHTMLAttributes<HTMLSelectElement>) {
  return <select className={control} {...props} />;
}

export function Textarea(props: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return <textarea className={`${control} min-h-24`} {...props} />;
}

export function Button({
  variant = "primary",
  className = "",
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: "primary" | "ghost" | "danger" | "sand";
}) {
  const styles = {
    primary:
      "bg-[var(--signal)] text-white hover:bg-[var(--signal-hover)] disabled:opacity-50",
    sand: "bg-[var(--sand)] text-[var(--ink)] hover:bg-[var(--sand-deep)] disabled:opacity-50",
    ghost:
      "border border-[var(--line)] text-[var(--paper)] hover:bg-white/5 disabled:opacity-50",
    danger:
      "bg-[var(--marker)] text-white hover:opacity-90 disabled:opacity-50",
  }[variant];
  return (
    <button
      className={`inline-flex items-center justify-center gap-2 rounded-md px-3 py-2 text-sm font-semibold transition ${styles} ${className}`}
      {...props}
    />
  );
}

export function Card({
  title,
  children,
  actions,
}: {
  title?: string;
  children: ReactNode;
  actions?: ReactNode;
}) {
  return (
    <section className="rounded-xl border border-[var(--line)] bg-[var(--night-mid)]/80 p-4 shadow-lg backdrop-blur">
      {(title || actions) && (
        <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
          {title ? <h2 className="text-lg font-bold text-[var(--sand)]">{title}</h2> : <span />}
          {actions}
        </div>
      )}
      {children}
    </section>
  );
}

export function Alert({ children }: { children: ReactNode }) {
  return (
    <div className="rounded-md border border-[var(--marker)]/40 bg-[var(--marker)]/10 px-3 py-2 text-sm text-[var(--sand)]">
      {children}
    </div>
  );
}
