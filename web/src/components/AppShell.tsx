"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";
import { useAuth } from "@/components/AuthProvider";
import { useLocale } from "@/components/LocaleProvider";
import { Button } from "@/components/ui";

export function AppShell({ children }: { children: ReactNode }) {
  const { ready, session, logout } = useAuth();
  const { locale, setLocale, tr } = useLocale();
  const pathname = usePathname();
  const router = useRouter();

  useEffect(() => {
    if (!ready) return;
    if (!session) router.replace("/login");
  }, [ready, session, router]);

  if (!ready) {
    return (
      <div className="grid min-h-screen place-items-center text-[var(--mist)]">
        {tr("loading")}
      </div>
    );
  }
  if (!session) return null;

  const nav = [
    { href: "/log", label: tr("logbook") },
    { href: "/qso/new", label: tr("newQso") },
    { href: "/settings/station", label: tr("settings") },
  ];

  return (
    <div className="relative min-h-screen">
      <div className="sky" aria-hidden />
      <header className="brand-bar relative z-10">
        <div className="mx-auto flex w-full max-w-6xl flex-wrap items-center justify-between gap-3">
          <strong>{tr("brand")}</strong>
          <span className="font-mono text-xs opacity-80">{session.email}</span>
        </div>
      </header>
      <nav className="relative z-10 border-b border-[var(--line)] bg-[var(--night)]/70 backdrop-blur">
        <div className="mx-auto flex max-w-6xl flex-col gap-2 px-4 py-2 sm:flex-row sm:flex-wrap sm:items-center">
          <div className="flex flex-wrap items-center gap-2">
            {nav.map((item) => (
              <Link
                key={item.href}
                href={item.href}
                className={`rounded-md px-3 py-1.5 text-sm font-semibold ${
                  pathname.startsWith(item.href)
                    ? "bg-[var(--signal)] text-white"
                    : "text-[var(--mist)] hover:bg-white/5 hover:text-[var(--paper)]"
                }`}
              >
                {item.label}
              </Link>
            ))}
          </div>
          <div className="flex items-center gap-2 sm:ml-auto">
            <select
              aria-label={tr("language")}
              className="rounded-md border border-[var(--line)] bg-transparent px-2 py-1 text-sm"
              value={locale}
              onChange={(e) => setLocale(e.target.value as "de" | "en")}
            >
              <option value="en">EN</option>
              <option value="de">DE</option>
            </select>
            <Button
              variant="ghost"
              onClick={async () => {
                await logout();
                router.replace("/login");
              }}
            >
              {tr("logout")}
            </Button>
          </div>
        </div>
      </nav>
      <main className="relative z-10 mx-auto max-w-6xl px-4 py-6">{children}</main>
    </div>
  );
}

export function AuthShell({ children }: { children: ReactNode }) {
  const { ready, session } = useAuth();
  const { tr } = useLocale();
  const router = useRouter();

  useEffect(() => {
    if (ready && session) router.replace("/log");
  }, [ready, session, router]);

  return (
    <div className="relative flex min-h-[100dvh] items-center justify-center px-4 py-6">
      <div className="sky" aria-hidden />
      <div className="relative z-10 w-full max-w-md">
        <div className="brand-bar rounded-t-xl">
          <strong>{tr("brand")}</strong>
        </div>
        <div className="rounded-b-xl border border-[var(--line)] border-t-0 bg-[var(--night-mid)]/90 p-4 shadow-xl backdrop-blur sm:p-6">
          <p className="mb-4 text-sm text-[var(--mist)]">{tr("tagline")}</p>
          {children}
        </div>
      </div>
    </div>
  );
}
