"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { AppShell } from "@/components/AppShell";
import { useLocale } from "@/components/LocaleProvider";
import { Alert, Button, Card, Field, Input } from "@/components/ui";
import { ApiError, api } from "@/lib/api";
import type { LogFilter, Qso } from "@/lib/types";

export default function LogPage() {
  const { tr } = useLocale();
  const [filter, setFilter] = useState<LogFilter>({});
  const [rows, setRows] = useState<Qso[]>([]);
  const [count, setCount] = useState(0);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [list, c] = await Promise.all([
        api.listQsos(filter, 100, 0),
        api.countQsos(filter),
      ]);
      setRows(list);
      setCount(c.count);
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) {
        setError(tr("needDb"));
        setRows([]);
        setCount(0);
      } else {
        setError(err instanceof Error ? err.message : "Failed");
      }
    } finally {
      setLoading(false);
    }
  }, [filter, tr]);

  useEffect(() => {
    void load();
  }, [load]);

  const toggle = (id: number) => {
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

  const onImport = async (file: File | null) => {
    if (!file) return;
    setError("");
    try {
      const result = await api.importAdif(file);
      setError(`ADIF: inserted ${result.inserted}, skipped ${result.skipped}`);
      await load();
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
              <Button variant="sand" onClick={() => void load()}>
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
              <Link href="/qso/new">
                <Button>{tr("newQso")}</Button>
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
          </div>
          {error && <Alert>{error}</Alert>}
          <p className="mb-2 text-sm text-[var(--mist)]">
            {tr("total")}: {count}
          </p>
          {loading ? (
            <p className="text-[var(--mist)]">{tr("loading")}</p>
          ) : rows.length === 0 ? (
            <p className="text-[var(--mist)]">{tr("noRows")}</p>
          ) : (
            <>
              {/* Mobile: stacked cards */}
              <div className="grid gap-2 md:hidden">
                {rows.map((q) => {
                  const id = q.qsoid ?? 0;
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
                    </tr>
                  </thead>
                  <tbody>
                    {rows.map((q) => {
                      const id = q.qsoid ?? 0;
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
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </>
          )}
        </Card>
      </div>
    </AppShell>
  );
}
