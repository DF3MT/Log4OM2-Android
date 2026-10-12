"use client";

import { FormEvent, useEffect, useState } from "react";
import { useLocale } from "@/components/LocaleProvider";
import { Alert, Button, Card, Field, Input } from "@/components/ui";
import { ApiError, api } from "@/lib/api";
import type { Logbook, LogbookWrite } from "@/lib/types";

type Form = {
  id: string | null;
  displayName: string;
  stationCallsign: string;
  host: string;
  port: number;
  database: string;
  username: string;
  password: string;
  sslEnabled: boolean;
  passwordSet: boolean;
  qrzLogbookKey: string;
  qrzLogbookKeySet: boolean;
  eqslUser: string;
  eqslPassword: string;
  eqslPasswordSet: boolean;
  active: boolean;
  lastTestOkAt: string | null;
};

const empty: Form = {
  id: null,
  displayName: "",
  stationCallsign: "",
  host: "",
  port: 3306,
  database: "",
  username: "",
  password: "",
  sslEnabled: false,
  passwordSet: false,
  qrzLogbookKey: "",
  qrzLogbookKeySet: false,
  eqslUser: "",
  eqslPassword: "",
  eqslPasswordSet: false,
  active: false,
  lastTestOkAt: null,
};

function fromBook(book: Logbook): Form {
  return {
    id: book.id,
    displayName: book.displayName,
    stationCallsign: book.stationCallsign,
    host: book.host,
    port: book.port,
    database: book.database,
    username: book.username,
    password: "",
    sslEnabled: book.sslEnabled,
    passwordSet: book.passwordSet,
    qrzLogbookKey: "",
    qrzLogbookKeySet: book.qrzLogbookKeySet,
    eqslUser: book.eqslUser,
    eqslPassword: "",
    eqslPasswordSet: book.eqslPasswordSet,
    active: book.active,
    lastTestOkAt: book.lastTestOkAt,
  };
}

function toWrite(form: Form): LogbookWrite {
  return {
    displayName: form.displayName.trim(),
    stationCallsign: form.stationCallsign.trim(),
    host: form.host.trim(),
    port: form.port || 3306,
    database: form.database.trim(),
    username: form.username.trim(),
    password: form.password.trim() ? form.password : null,
    sslEnabled: form.sslEnabled,
    qrzLogbookKey: form.qrzLogbookKey.trim() ? form.qrzLogbookKey.trim() : null,
    eqslUser: form.eqslUser.trim(),
    eqslPassword: form.eqslPassword.trim() ? form.eqslPassword : null,
  };
}

export default function DatabaseSettingsPage() {
  const { tr } = useLocale();
  const [books, setBooks] = useState<Logbook[]>([]);
  const [form, setForm] = useState<Form>(empty);
  const [msg, setMsg] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const load = async (selectId?: string | null) => {
    const list = await api.listLogbooks();
    setBooks(list);
    const pick =
      list.find((b) => b.id === selectId) ||
      list.find((b) => b.active) ||
      list[0];
    setForm(pick ? fromBook(pick) : empty);
  };

  useEffect(() => {
    void load().catch((err) => setError(err instanceof Error ? err.message : "Failed"));
  }, []);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError("");
    setMsg("");
    try {
      const body = toWrite(form);
      const saved = form.id
        ? await api.updateLogbook(form.id, body)
        : await api.createLogbook(body);
      await load(saved.id);
      setMsg(tr("saved"));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Save failed");
    } finally {
      setBusy(false);
    }
  };

  const onTest = async () => {
    setBusy(true);
    setError("");
    setMsg("");
    try {
      const r = await api.testLogbook(form.id, toWrite(form));
      if (r.ok) setMsg(`OK: ${r.message}`);
      else setError(r.message);
      if (form.id) await load(form.id);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Test failed");
    } finally {
      setBusy(false);
    }
  };

  const onActivate = async () => {
    if (!form.id) return;
    setBusy(true);
    setError("");
    try {
      await api.activateLogbook(form.id);
      await load(form.id);
      setMsg(tr("saved"));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed");
    } finally {
      setBusy(false);
    }
  };

  const onDelete = async () => {
    if (!form.id) return;
    if (!window.confirm(tr("confirmDeleteLogbook"))) return;
    setBusy(true);
    setError("");
    try {
      await api.deleteLogbook(form.id);
      await load(null);
      setMsg(tr("saved"));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed");
    } finally {
      setBusy(false);
    }
  };

  const onExport = async () => {
    if (!form.id) return;
    setBusy(true);
    setError("");
    try {
      const { blob, filename } = await api.exportLogbookAdif(form.id);
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = filename;
      a.click();
      URL.revokeObjectURL(url);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Export failed");
    } finally {
      setBusy(false);
    }
  };

  const onSync = async (kind: "qrz" | "eqsl") => {
    if (!form.id) return;
    if (!window.confirm(tr("syncConfirm"))) return;
    setBusy(true);
    setError("");
    setMsg("");
    try {
      const result = kind === "qrz" ? await api.syncQrz(form.id) : await api.syncEqsl(form.id);
      const text = `${result.message} (↑${result.uploaded} ↓${result.downloaded}, ${tr("skipped")} ${result.skipped})`;
      if (result.ok) setMsg(text);
      else setError(result.code === "MISSING_CREDENTIALS" ? `${tr("syncRefused")}: ${result.message}` : result.message);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : err instanceof Error ? err.message : "Sync failed");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="grid gap-4">
      <Card title={tr("logbooks")}>
        <div className="mb-3 flex flex-wrap gap-2">
          {books.map((book) => (
            <button
              key={book.id}
              type="button"
              onClick={() => setForm(fromBook(book))}
              className={`rounded-md px-3 py-1.5 text-sm font-semibold ${
                form.id === book.id
                  ? "bg-[var(--sand)] text-[var(--ink)]"
                  : "border border-[var(--line)] text-[var(--mist)]"
              }`}
            >
              {book.displayName}
              {book.stationCallsign ? ` · ${book.stationCallsign}` : ""}
              {book.active ? ` · ${tr("activeLogbook")}` : ""}
            </button>
          ))}
          <Button type="button" variant="ghost" onClick={() => setForm(empty)}>
            {tr("addLogbook")}
          </Button>
        </div>
        <form onSubmit={onSubmit} className="grid gap-3 sm:grid-cols-2">
          {error && (
            <div className="sm:col-span-2">
              <Alert>{error}</Alert>
            </div>
          )}
          {msg && <p className="sm:col-span-2 text-sm text-[var(--sand)]">{msg}</p>}
          <Field label={tr("displayName")}>
            <Input
              required
              value={form.displayName}
              onChange={(e) => setForm((f) => ({ ...f, displayName: e.target.value }))}
            />
          </Field>
          <Field label={tr("stationCallsign")}>
            <Input
              value={form.stationCallsign}
              onChange={(e) =>
                setForm((f) => ({ ...f, stationCallsign: e.target.value.toUpperCase() }))
              }
            />
          </Field>
          <Field label={tr("host")}>
            <Input
              required
              value={form.host}
              onChange={(e) => setForm((f) => ({ ...f, host: e.target.value }))}
            />
          </Field>
          <Field label={tr("port")}>
            <Input
              type="number"
              required
              value={form.port}
              onChange={(e) => setForm((f) => ({ ...f, port: Number(e.target.value) || 3306 }))}
            />
          </Field>
          <Field label={tr("databaseName")}>
            <Input
              required
              value={form.database}
              onChange={(e) => setForm((f) => ({ ...f, database: e.target.value }))}
            />
          </Field>
          <Field label={tr("username")}>
            <Input
              required
              value={form.username}
              onChange={(e) => setForm((f) => ({ ...f, username: e.target.value }))}
            />
          </Field>
          <Field label={tr("password")}>
            <Input
              type="password"
              autoComplete="new-password"
              placeholder={form.passwordSet ? tr("passwordHint") : undefined}
              value={form.password}
              onChange={(e) => setForm((f) => ({ ...f, password: e.target.value }))}
            />
          </Field>
          <label className="flex items-center gap-2 self-end text-sm">
            <input
              type="checkbox"
              checked={form.sslEnabled}
              onChange={(e) => setForm((f) => ({ ...f, sslEnabled: e.target.checked }))}
            />
            {tr("ssl")}
          </label>
          <Field label={tr("qrzLogbookKey")}>
            <Input
              type="password"
              autoComplete="new-password"
              placeholder={form.qrzLogbookKeySet ? tr("passwordHint") : undefined}
              value={form.qrzLogbookKey}
              onChange={(e) => setForm((f) => ({ ...f, qrzLogbookKey: e.target.value }))}
            />
          </Field>
          <p className="self-end text-xs text-[var(--mist)]">{tr("qrzLogbookHint")}</p>
          <Field label={tr("eqslUser")}>
            <Input
              value={form.eqslUser}
              onChange={(e) => setForm((f) => ({ ...f, eqslUser: e.target.value }))}
            />
          </Field>
          <Field label={tr("eqslPassword")}>
            <Input
              type="password"
              autoComplete="new-password"
              placeholder={form.eqslPasswordSet ? tr("passwordHint") : undefined}
              value={form.eqslPassword}
              onChange={(e) => setForm((f) => ({ ...f, eqslPassword: e.target.value }))}
            />
          </Field>
          {form.lastTestOkAt && (
            <p className="sm:col-span-2 text-xs text-[var(--mist)]">
              lastTestOkAt: {form.lastTestOkAt}
            </p>
          )}
          <div className="flex flex-wrap gap-2 sm:col-span-2">
            <Button type="submit" disabled={busy}>
              {form.id ? tr("save") : tr("addLogbook")}
            </Button>
            <Button type="button" variant="ghost" disabled={busy} onClick={() => void onTest()}>
              {tr("test")}
            </Button>
            {form.id && !form.active && (
              <Button type="button" variant="sand" disabled={busy} onClick={() => void onActivate()}>
                {tr("useLogbook")}
              </Button>
            )}
            {form.id && (
              <Button type="button" variant="ghost" disabled={busy} onClick={() => void onExport()}>
                {tr("exportAll")}
              </Button>
            )}
            {form.id && (
              <Button type="button" variant="ghost" disabled={busy} onClick={() => void onSync("qrz")}>
                {tr("syncQrz")}
              </Button>
            )}
            {form.id && (
              <Button type="button" variant="ghost" disabled={busy} onClick={() => void onSync("eqsl")}>
                {tr("syncEqsl")}
              </Button>
            )}
            {form.id && (
              <Button type="button" variant="danger" disabled={busy} onClick={() => void onDelete()}>
                {tr("deleteLogbook")}
              </Button>
            )}
          </div>
        </form>
      </Card>
    </div>
  );
}
