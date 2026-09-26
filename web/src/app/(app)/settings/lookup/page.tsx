"use client";

import { FormEvent, useEffect, useState } from "react";
import { useLocale } from "@/components/LocaleProvider";
import { Alert, Button, Card, Field, Input } from "@/components/ui";
import { api } from "@/lib/api";

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
        <div className="sm:col-span-2">
          <Button type="submit" disabled={busy}>
            {tr("save")}
          </Button>
        </div>
      </form>
    </Card>
  );
}
