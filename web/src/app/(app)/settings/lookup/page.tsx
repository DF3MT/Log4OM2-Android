"use client";

import { FormEvent, useEffect, useState } from "react";
import { useLocale } from "@/components/LocaleProvider";
import { Alert, Button, Card, Field, Input } from "@/components/ui";
import { api } from "@/lib/api";
import type { LookupProvider, LookupTestRequest } from "@/lib/types";

type ProbeResult = { ok: boolean; text: string };

export default function LookupSettingsPage() {
  const { tr } = useLocale();
  const [qrzUser, setQrzUser] = useState("");
  const [qrzPassword, setQrzPassword] = useState("");
  const [hamqthUser, setHamqthUser] = useState("");
  const [hamqthPassword, setHamqthPassword] = useState("");
  const [clublogApiKey, setClublogApiKey] = useState("");
  const [flags, setFlags] = useState({
    qrzPasswordSet: false,
    hamqthPasswordSet: false,
    clublogApiKeySet: false,
  });
  const [msg, setMsg] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [testing, setTesting] = useState<LookupProvider | null>(null);
  const [results, setResults] = useState<Partial<Record<LookupProvider, ProbeResult>>>({});

  useEffect(() => {
    void api
      .getLookup()
      .then((c) => {
        setQrzUser(c.qrzUser);
        setHamqthUser(c.hamqthUser);
        setFlags({
          qrzPasswordSet: c.qrzPasswordSet,
          hamqthPasswordSet: c.hamqthPasswordSet,
          clublogApiKeySet: c.clublogApiKeySet,
        });
      })
      .catch((err) => setError(err instanceof Error ? err.message : "Failed"));
  }, []);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError("");
    setMsg("");
    try {
      const saved = await api.putLookup({
        qrzUser,
        hamqthUser,
        qrzPassword: qrzPassword || null,
        hamqthPassword: hamqthPassword || null,
        clublogApiKey: clublogApiKey || null,
      });
      setQrzPassword("");
      setHamqthPassword("");
      setClublogApiKey("");
      setFlags({
        qrzPasswordSet: saved.qrzPasswordSet,
        hamqthPasswordSet: saved.hamqthPasswordSet,
        clublogApiKeySet: saved.clublogApiKeySet,
      });
      setMsg("OK");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Save failed");
    } finally {
      setBusy(false);
    }
  };

  const onTest = async (provider: LookupProvider) => {
    setTesting(provider);
    setResults((current) => ({ ...current, [provider]: undefined }));
    const body: LookupTestRequest =
      provider === "qrz"
        ? { provider, qrzUser, qrzPassword: qrzPassword.trim() || null }
        : provider === "hamqth"
          ? {
              provider,
              hamqthUser,
              hamqthPassword: hamqthPassword.trim() || null,
            }
          : { provider, clublogApiKey: clublogApiKey.trim() || null };
    try {
      const res = await api.testLookup(body);
      setResults((current) => ({
        ...current,
        [provider]: {
          ok: res.ok,
          text: res.message || (res.ok ? "OK" : "Lookup failed"),
        },
      }));
    } catch (err) {
      setResults((current) => ({
        ...current,
        [provider]: {
          ok: false,
          text: err instanceof Error ? err.message : "Lookup failed",
        },
      }));
    } finally {
      setTesting(null);
    }
  };

  const probe = (provider: LookupProvider, label: string) => {
    const result = results[provider];
    return (
      <div className="sm:col-span-2 grid gap-2">
        <div>
          <Button
            type="button"
            variant="ghost"
            disabled={busy || testing !== null}
            onClick={() => void onTest(provider)}
          >
            {testing === provider ? tr("loading") : label}
          </Button>
        </div>
        {result &&
          (result.ok ? (
            <p className="text-sm text-[var(--sand)]">{result.text}</p>
          ) : (
            <Alert>{result.text}</Alert>
          ))}
      </div>
    );
  };

  return (
    <Card title={tr("lookup")}>
      <form onSubmit={onSubmit} className="grid gap-3 sm:grid-cols-2">
        {error && (
          <div className="sm:col-span-2">
            <Alert>{error}</Alert>
          </div>
        )}
        {msg && <p className="sm:col-span-2 text-sm text-[var(--sand)]">{msg}</p>}
        <Field label={tr("qrzUser")}>
          <Input value={qrzUser} onChange={(e) => setQrzUser(e.target.value)} />
        </Field>
        <Field label={tr("qrzPassword")}>
          <Input
            type="password"
            placeholder={flags.qrzPasswordSet ? tr("passwordHint") : undefined}
            value={qrzPassword}
            onChange={(e) => setQrzPassword(e.target.value)}
          />
        </Field>
        {probe("qrz", tr("testQrz"))}
        <Field label={tr("hamqthUser")}>
          <Input
            value={hamqthUser}
            onChange={(e) => setHamqthUser(e.target.value)}
          />
        </Field>
        <Field label={tr("hamqthPassword")}>
          <Input
            type="password"
            placeholder={
              flags.hamqthPasswordSet ? tr("passwordHint") : undefined
            }
            value={hamqthPassword}
            onChange={(e) => setHamqthPassword(e.target.value)}
          />
        </Field>
        {probe("hamqth", tr("testHamqth"))}
        <Field label={tr("clublogKey")}>
          <Input
            type="password"
            placeholder={
              flags.clublogApiKeySet ? tr("passwordHint") : undefined
            }
            value={clublogApiKey}
            onChange={(e) => setClublogApiKey(e.target.value)}
          />
        </Field>
        {probe("clublog", tr("testClublog"))}
        <div className="sm:col-span-2">
          <Button type="submit" disabled={busy || testing !== null}>
            {tr("save")}
          </Button>
        </div>
      </form>
    </Card>
  );
}
