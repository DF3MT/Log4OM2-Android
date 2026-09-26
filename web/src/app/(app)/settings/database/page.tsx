"use client";

import { FormEvent, useEffect, useState } from "react";
import { useLocale } from "@/components/LocaleProvider";
import { Alert, Button, Card, Field, Input } from "@/components/ui";
import { ApiError, api } from "@/lib/api";

type Form = {
  host: string;
  port: number;
  database: string;
  username: string;
  password: string;
  sslEnabled: boolean;
  passwordSet: boolean;
  lastTestOkAt: string | null;
};

const empty: Form = {
  host: "",
  port: 3306,
  database: "",
  username: "",
  password: "",
  sslEnabled: true,
  passwordSet: false,
  lastTestOkAt: null,
};

export default function DatabaseSettingsPage() {
  const { tr } = useLocale();
  const [form, setForm] = useState<Form>(empty);
  const [msg, setMsg] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    void api
      .getDbConfig()
      .then((c) =>
        setForm({
          host: c.host,
          port: c.port,
          database: c.database,
          username: c.username,
          password: "",
          sslEnabled: c.sslEnabled,
          passwordSet: c.passwordSet,
          lastTestOkAt: c.lastTestOkAt,
        }),
      )
      .catch((err) => {
        if (!(err instanceof ApiError && err.status === 404)) {
          setError(err instanceof Error ? err.message : "Failed");
        }
      });
  }, []);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError("");
    setMsg("");
    try {
      const saved = await api.putDbConfig({
        host: form.host,
        port: form.port,
        database: form.database,
        username: form.username,
        password: form.password || undefined,
        sslEnabled: form.sslEnabled,
      });
      setForm((f) => ({
        ...f,
        password: "",
        passwordSet: saved.passwordSet,
        lastTestOkAt: saved.lastTestOkAt,
      }));
      setMsg("OK");
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
      const r = await api.testDbConfig();
      setMsg(r.ok ? `OK: ${r.message}` : r.message);
      if (!r.ok) setError(r.message);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Test failed");
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card title={tr("database")}>
      <form onSubmit={onSubmit} className="grid gap-3 sm:grid-cols-2">
        {error && (
          <div className="sm:col-span-2">
            <Alert>{error}</Alert>
          </div>
        )}
        {msg && <p className="sm:col-span-2 text-sm text-[var(--sand)]">{msg}</p>}
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
            onChange={(e) =>
              setForm((f) => ({ ...f, port: Number(e.target.value) || 3306 }))
            }
          />
        </Field>
        <Field label={tr("databaseName")}>
          <Input
            required
            value={form.database}
            onChange={(e) =>
              setForm((f) => ({ ...f, database: e.target.value }))
            }
          />
        </Field>
        <Field label={tr("username")}>
          <Input
            required
            value={form.username}
            onChange={(e) =>
              setForm((f) => ({ ...f, username: e.target.value }))
            }
          />
        </Field>
        <Field label={tr("password")}>
          <Input
            type="password"
            placeholder={
              form.passwordSet ? tr("passwordHint") : undefined
            }
            value={form.password}
            onChange={(e) =>
              setForm((f) => ({ ...f, password: e.target.value }))
            }
          />
        </Field>
        <label className="flex items-center gap-2 self-end text-sm">
          <input
            type="checkbox"
            checked={form.sslEnabled}
            onChange={(e) =>
              setForm((f) => ({ ...f, sslEnabled: e.target.checked }))
            }
          />
          {tr("ssl")}
        </label>
        {form.lastTestOkAt && (
          <p className="sm:col-span-2 text-xs text-[var(--mist)]">
            lastTestOkAt: {form.lastTestOkAt}
          </p>
        )}
        <div className="flex flex-wrap gap-2 sm:col-span-2">
          <Button type="submit" disabled={busy}>
            {tr("save")}
          </Button>
          <Button
            type="button"
            variant="ghost"
            disabled={busy}
            onClick={() => void onTest()}
          >
            {tr("test")}
          </Button>
        </div>
      </form>
    </Card>
  );
}
