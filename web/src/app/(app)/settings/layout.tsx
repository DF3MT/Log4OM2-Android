"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { AppShell } from "@/components/AppShell";
import { useLocale } from "@/components/LocaleProvider";

export default function SettingsLayout({ children }: { children: ReactNode }) {
  const { tr } = useLocale();
  const pathname = usePathname();
  const tabs = [
    { href: "/settings/station", label: tr("station") },
    { href: "/settings/database", label: tr("database") },
    { href: "/settings/lookup", label: tr("lookup") },
  ];

  return (
    <AppShell>
      <div className="mb-4 flex flex-wrap gap-2">
        {tabs.map((tab) => (
          <Link
            key={tab.href}
            href={tab.href}
            className={`rounded-md px-3 py-1.5 text-sm font-semibold ${
              pathname === tab.href
                ? "bg-[var(--sand)] text-[var(--ink)]"
                : "border border-[var(--line)] text-[var(--mist)] hover:bg-white/5"
            }`}
          >
            {tab.label}
          </Link>
        ))}
      </div>
      {children}
    </AppShell>
  );
}
