"use client";

import { useEffect, useState } from "react";
import { useLocale } from "@/components/LocaleProvider";
import { api } from "@/lib/api";
import type { Logbook } from "@/lib/types";

export function LogbookSwitcher() {
  const { tr } = useLocale();
  const [books, setBooks] = useState<Logbook[]>([]);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    void api.listLogbooks().then(setBooks).catch(() => setBooks([]));
  }, []);

  if (books.length === 0) return null;
  const active = books.find((b) => b.active)?.id || books[0].id;

  return (
    <label className="flex items-center gap-2 text-sm text-[var(--mist)]">
      <span className="sr-only">{tr("logbooks")}</span>
      <select
        aria-label={tr("logbooks")}
        className="max-w-[16rem] rounded-md border border-[var(--line)] bg-transparent px-2 py-1 text-sm text-[var(--paper)]"
        value={active}
        disabled={busy}
        onChange={(e) => {
          const id = e.target.value;
          if (!id || id === active) return;
          setBusy(true);
          void api
            .activateLogbook(id)
            .then(() => window.location.reload())
            .catch(() => setBusy(false));
        }}
      >
        {books.map((book) => (
          <option key={book.id} value={book.id}>
            {book.displayName}
            {book.stationCallsign ? ` (${book.stationCallsign})` : ""}
          </option>
        ))}
      </select>
    </label>
  );
}
