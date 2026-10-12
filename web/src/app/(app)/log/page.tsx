"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { AppShell } from "@/components/AppShell";
import { useLocale } from "@/components/LocaleProvider";
import { Alert, Button, Card, Field, Input } from "@/components/ui";
import { ApiError, api } from "@/lib/api";
import type { LogFilter, Qso } from "@/lib/types";

const PAGE_SIZE = 50;

type AppliedQuery = { filter: LogFilter; nonce: number };
type LoadSession = { gen: number; ac: AbortController; filter: LogFilter };

function normalizeFilter(filter: LogFilter): LogFilter {
  const next: LogFilter = {};
  for (const [key, value] of Object.entries(filter) as [keyof LogFilter, string | undefined][]) {
    if (value == null) continue;
    const trimmed = value.trim();
    if (!trimmed) continue;
    next[key] = trimmed;
  }
  return next;
}

function filterKey(filter: LogFilter): string {
  return JSON.stringify(
    Object.keys(filter)
      .sort()
      .map((key) => [key, filter[key as keyof LogFilter]]),
  );
}

function mergeQsos(prev: Qso[], incoming: Qso[]): Qso[] {
  const seen = new Set<string>();
  for (const row of prev) {
    if (row.qsoid != null) seen.add(row.qsoid);
  }
  const extra: Qso[] = [];
  for (const row of incoming) {
    if (row.qsoid != null) {
      if (seen.has(row.qsoid)) continue;
      seen.add(row.qsoid);
    }
    extra.push(row);
  }
  return extra.length ? prev.concat(extra) : prev;
}

function isAbort(err: unknown): boolean {
  return err instanceof Error && err.name === "AbortError";
}

/** True when the loaded end is within one viewport of the visible area. */
function isNearEnd(sentinel: HTMLElement | null): boolean {
  if (!sentinel || typeof window === "undefined") return false;
  const viewport = window.innerHeight || 800;
  return sentinel.getBoundingClientRect().top <= viewport * 2;
}

export default function LogPage() {
  const { tr } = useLocale();
  const trRef = useRef(tr);
  trRef.current = tr;

  const [filter, setFilter] = useState<LogFilter>({});
  const [applied, setApplied] = useState<AppliedQuery>({ filter: {}, nonce: 0 });
  const [rows, setRows] = useState<Qso[]>([]);
  const [count, setCount] = useState(0);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [error, setError] = useState("");
  const [initialLoading, setInitialLoading] = useState(true);
  const [fetchingMore, setFetchingMore] = useState(false);
  const [hasMore, setHasMore] = useState(true);
  const [pageTick, setPageTick] = useState(0);

  const sentinelRef = useRef<HTMLDivElement>(null);
  const sessionRef = useRef<LoadSession | null>(null);
  const generationRef = useRef(0);
  const offsetRef = useRef(0);
  const countRef = useRef(0);
  const hasMoreRef = useRef(true);
  const inFlightRef = useRef(false);
  const mountedRef = useRef(true);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  useEffect(() => {
    const next = normalizeFilter(filter);
    const handle = window.setTimeout(() => {
      setApplied((prev) =>
        filterKey(prev.filter) === filterKey(next) ? prev : { filter: next, nonce: prev.nonce },
      );
    }, 250);
    return () => window.clearTimeout(handle);
  }, [filter]);

  const appendPage = useCallback(async (force: boolean) => {
    const session = sessionRef.current;
    if (!session) return;
    if (inFlightRef.current || !hasMoreRef.current) return;
    if (!force && !isNearEnd(sentinelRef.current)) return;

    const { gen, ac, filter: query } = session;
    const offset = offsetRef.current;
    inFlightRef.current = true;
    setFetchingMore(true);
    try {
      const list = await api.listQsos(query, PAGE_SIZE, offset, ac.signal);
      if (!mountedRef.current || ac.signal.aborted || gen !== generationRef.current) return;
      setRows((prev) => mergeQsos(prev, list));
      const nextOffset = offset + list.length;
      offsetRef.current = nextOffset;
      const more = list.length === PAGE_SIZE && nextOffset < countRef.current;
      hasMoreRef.current = more;
      setHasMore(more);
      setPageTick((n) => n + 1);
    } catch (err) {
      if (!mountedRef.current || ac.signal.aborted || isAbort(err) || gen !== generationRef.current) {
        return;
      }
      setError(err instanceof Error ? err.message : "Failed");
      hasMoreRef.current = false;
      setHasMore(false);
    } finally {
      if (mountedRef.current && gen === generationRef.current) {
        inFlightRef.current = false;
        setFetchingMore(false);
      }
    }
  }, []);

  const appendPageRef = useRef(appendPage);
  appendPageRef.current = appendPage;

  useEffect(() => {
    const ac = new AbortController();
    const gen = ++generationRef.current;
    const query = applied.filter;
    sessionRef.current = { gen, ac, filter: query };
    inFlightRef.current = true;
    hasMoreRef.current = true;
    offsetRef.current = 0;
    countRef.current = 0;
    setInitialLoading(true);
    setFetchingMore(false);
    setError("");
    setRows([]);
    setCount(0);
    setHasMore(true);
    setPageTick(0);
    window.scrollTo(0, 0);

    let alive = true;
    void (async () => {
      try {
        const [list, counted] = await Promise.all([
          api.listQsos(query, PAGE_SIZE, 0, ac.signal),
          api.countQsos(query, ac.signal),
        ]);
        if (!alive || !mountedRef.current || ac.signal.aborted || gen !== generationRef.current) return;
        const total = counted.count;
        countRef.current = total;
        setCount(total);
        setRows(list);
        offsetRef.current = list.length;
        const more = list.length === PAGE_SIZE && list.length < total;
        hasMoreRef.current = more;
        setHasMore(more);
        setInitialLoading(false);
        inFlightRef.current = false;
        if (more) await appendPageRef.current(true);
      } catch (err) {
        if (!alive || !mountedRef.current || ac.signal.aborted || isAbort(err)) return;
        inFlightRef.current = false;
        hasMoreRef.current = false;
        setHasMore(false);
        setInitialLoading(false);
        setRows([]);
        setCount(0);
        if (err instanceof ApiError && err.status === 404) {
          setError(trRef.current("needDb"));
        } else {
          setError(err instanceof Error ? err.message : "Failed");
        }
      }
    })();

    return () => {
      alive = false;
      ac.abort();
    };
  }, [applied]);

  useEffect(() => {
    if (initialLoading || pageTick === 0) return;
    void appendPage(false);
  }, [appendPage, initialLoading, pageTick]);

  useEffect(() => {
    let raf = 0;
    const schedule = () => {
      if (raf) return;
      raf = window.requestAnimationFrame(() => {
        raf = 0;
        void appendPageRef.current(false);
      });
    };
    window.addEventListener("scroll", schedule, { passive: true });
    window.addEventListener("resize", schedule);
    return () => {
      window.cancelAnimationFrame(raf);
      window.removeEventListener("scroll", schedule);
      window.removeEventListener("resize", schedule);
    };
  }, []);

  const applyFilterNow = () => {
    const next = normalizeFilter(filter);
    setApplied((prev) => ({ filter: next, nonce: prev.nonce + 1 }));
  };

  const toggle = (id: string) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const onExport = async () => {
    const ids = [...selected];
    if (!ids.length) return;
    const blob = await api.exportAdif(ids);
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = "export.adi";
    a.click();
    URL.revokeObjectURL(url);
  };

  const onExportAll = async () => {
    setError("");
    try {
      const books = await api.listLogbooks();
      const active = books.find((b) => b.active) || books[0];
      if (!active) {
        setError(tr("needDb"));
        return;
      }
      const { blob, filename } = await api.exportLogbookAdif(active.id);
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = filename;
      a.click();
      URL.revokeObjectURL(url);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Export failed");
    }
  };

  const onImport = async (file: File | null) => {
    if (!file) return;
    setError("");
    try {
      const result = await api.importAdif(file);
      setError(`ADIF: inserted ${result.inserted}, skipped ${result.skipped}`);
      setApplied((prev) => ({ ...prev, nonce: prev.nonce + 1 }));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Import failed");
    }
  };

  return (
    <AppShell>
      <div className="grid gap-4">
        <Card
          title={tr("logbook")}
          actions={
            <div className="flex flex-wrap gap-2">
              <Button variant="sand" onClick={applyFilterNow}>
                {tr("filter")}
              </Button>
              <label className="inline-flex cursor-pointer items-center rounded-md border border-[var(--line)] px-3 py-2 text-sm font-semibold hover:bg-white/5">
                {tr("importAdif")}
                <input
                  type="file"
                  accept=".adi,.adif,.txt"
                  className="hidden"
                  onChange={(e) => void onImport(e.target.files?.[0] ?? null)}
                />
              </label>
              <Button
                variant="ghost"
                disabled={!selected.size}
                onClick={() => void onExport()}
              >
                {tr("exportAdif")} ({selected.size} {tr("selected")})
              </Button>
              <Button variant="ghost" onClick={() => void onExportAll()}>
                {tr("exportAll")}
              </Button>
              <Link
                href="/qso/new"
                className="inline-flex items-center justify-center rounded-md bg-[var(--signal)] px-3 py-2 text-sm font-semibold text-white hover:bg-[var(--signal-hover)]"
              >
                {tr("newQso")}
              </Link>
            </div>
          }
        >
          <div className="mb-4 grid gap-2 sm:grid-cols-2 lg:grid-cols-4">
            <Field label={tr("callsign")}>
              <Input
                value={filter.callsign ?? ""}
                onChange={(e) =>
                  setFilter((f) => ({ ...f, callsign: e.target.value }))
                }
              />
            </Field>
            <Field label={tr("band")}>
              <Input
                value={filter.band ?? ""}
                onChange={(e) =>
                  setFilter((f) => ({ ...f, band: e.target.value }))
                }
              />
            </Field>
            <Field label={tr("mode")}>
              <Input
                value={filter.mode ?? ""}
                onChange={(e) =>
                  setFilter((f) => ({ ...f, mode: e.target.value }))
                }
              />
            </Field>
            <Field label={tr("country")}>
              <Input
                value={filter.country ?? ""}
                onChange={(e) =>
                  setFilter((f) => ({ ...f, country: e.target.value }))
                }
              />
            </Field>
            <Field label={tr("dateFrom")}>
              <Input
                type="date"
                value={filter.dateFrom ?? ""}
                onChange={(e) =>
                  setFilter((f) => ({ ...f, dateFrom: e.target.value }))
                }
              />
            </Field>
            <Field label={tr("dateTo")}>
              <Input
                type="date"
                value={filter.dateTo ?? ""}
                onChange={(e) =>
                  setFilter((f) => ({ ...f, dateTo: e.target.value }))
                }
              />
            </Field>
            <Field label={tr("dxcc")}>
              <Input
                value={filter.dxcc ?? ""}
                onChange={(e) =>
                  setFilter((f) => ({ ...f, dxcc: e.target.value }))
                }
              />
            </Field>
            <Field label={tr("sota")}>
              <Input
                value={filter.sotaRef ?? ""}
                onChange={(e) =>
                  setFilter((f) => ({ ...f, sotaRef: e.target.value }))
                }
              />
            </Field>
            <Field label={tr("iota")}>
              <Input
                value={filter.iota ?? ""}
                onChange={(e) =>
                  setFilter((f) => ({ ...f, iota: e.target.value }))
                }
              />
            </Field>
            <Field label={tr("pota")}>
              <Input
                value={filter.potaRef ?? ""}
                onChange={(e) =>
                  setFilter((f) => ({ ...f, potaRef: e.target.value }))
                }
              />
            </Field>
            <Field label={tr("wwff")}>
              <Input
                value={filter.wwffRef ?? ""}
                onChange={(e) =>
                  setFilter((f) => ({ ...f, wwffRef: e.target.value }))
                }
              />
            </Field>
            <Field label={tr("cota")}>
              <Input
                value={filter.cotaRef ?? ""}
                onChange={(e) =>
                  setFilter((f) => ({ ...f, cotaRef: e.target.value }))
                }
              />
            </Field>
          </div>
          {error && <Alert>{error}</Alert>}
          <p
            className="mb-2 flex items-center gap-2 text-sm text-[var(--mist)]"
            data-qso-loaded={rows.length}
            data-qso-total={count}
            data-has-more={hasMore ? "1" : "0"}
          >
            <span>
              {tr("total")}: {count}
            </span>
            <span
              className={`inline-block h-1.5 w-1.5 shrink-0 rounded-full bg-[var(--sand)] ${
                fetchingMore ? "animate-pulse" : "invisible"
              }`}
              aria-hidden={!fetchingMore}
            />
            <span className="sr-only" aria-live="polite">
              {fetchingMore ? tr("loadingMore") : ""}
            </span>
          </p>
          {initialLoading && rows.length === 0 ? (
            <p className="text-[var(--mist)]">{tr("loading")}</p>
          ) : rows.length === 0 ? (
            <p className="text-[var(--mist)]">{tr("noRows")}</p>
          ) : (
            <>
              {/* Mobile: stacked cards */}
              <div className="grid gap-2 md:hidden">
                {rows.map((q) => {
                  const id = q.qsoid ?? "";
                  return (
                    <div
                      key={id}
                      className="flex gap-3 rounded-lg border border-[var(--line)] bg-black/20 p-3"
                    >
                      <input
                        type="checkbox"
                        className="mt-1"
                        checked={selected.has(id)}
                        onChange={() => toggle(id)}
                      />
                      <div className="min-w-0 flex-1">
                        <div className="flex flex-wrap items-baseline justify-between gap-2">
                          <Link
                            className="truncate font-semibold text-[var(--sand)] underline"
                            href={`/qso/${id}/edit`}
                          >
                            {q.callsign}
                          </Link>
                          <span className="font-mono text-xs text-[var(--mist)]">
                            {q.qsodate?.replace("T", " ").slice(0, 16)}
                          </span>
                        </div>
                        <p className="mt-1 text-sm text-[var(--mist)]">
                          {q.band} · {q.mode} · {q.rstsent}/{q.rstrcvd}
                          {q.country ? ` · ${q.country}` : ""}
                        </p>
                        <Link
                          className="mt-1 inline-block text-sm font-semibold text-[var(--sand)] underline"
                          href={`/qso/${id}/edit`}
                        >
                          {tr("edit")}
                        </Link>
                      </div>
                    </div>
                  );
                })}
              </div>

              {/* Desktop: table */}
              <div className="hidden overflow-x-auto md:block">
                <table className="w-full min-w-[720px] border-collapse text-left text-sm">
                  <thead>
                    <tr className="border-b border-[var(--line)] text-[var(--mist)]">
                      <th className="p-2" />
                      <th className="p-2">{tr("date")}</th>
                      <th className="p-2">{tr("callsign")}</th>
                      <th className="p-2">{tr("band")}</th>
                      <th className="p-2">{tr("mode")}</th>
                      <th className="p-2">{tr("rst")}</th>
                      <th className="p-2">{tr("country")}</th>
                      <th className="p-2" />
                    </tr>
                  </thead>
                  <tbody>
                    {rows.map((q) => {
                      const id = q.qsoid ?? "";
                      return (
                        <tr
                          key={id}
                          className="border-b border-[var(--line)]/60 hover:bg-white/5"
                        >
                          <td className="p-2">
                            <input
                              type="checkbox"
                              checked={selected.has(id)}
                              onChange={() => toggle(id)}
                            />
                          </td>
                          <td className="p-2 font-mono text-xs">
                            {q.qsodate?.replace("T", " ").slice(0, 16)}
                          </td>
                          <td className="p-2">
                            <Link
                              className="font-semibold text-[var(--sand)] underline"
                              href={`/qso/${id}/edit`}
                            >
                              {q.callsign}
                            </Link>
                          </td>
                          <td className="p-2">{q.band}</td>
                          <td className="p-2">{q.mode}</td>
                          <td className="p-2">
                            {q.rstsent}/{q.rstrcvd}
                          </td>
                          <td className="p-2">{q.country}</td>
                          <td className="p-2">
                            <Link
                              className="font-semibold text-[var(--sand)] underline"
                              href={`/qso/${id}/edit`}
                            >
                              {tr("edit")}
                            </Link>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
              <div ref={sentinelRef} className="h-0" aria-hidden />
            </>
          )}
        </Card>
      </div>
    </AppShell>
  );
}
