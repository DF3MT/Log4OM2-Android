"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";
import { AuthShell } from "@/components/AppShell";
import { useAuth } from "@/components/AuthProvider";
import { useLocale } from "@/components/LocaleProvider";
import { Alert, Button, Field, Input } from "@/components/ui";

export default function RegisterPage() {
  const { register } = useAuth();
  const { tr } = useLocale();
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      await register(email, password, displayName || undefined);
      router.replace("/settings/database");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Register failed");
    } finally {
      setBusy(false);
    }
  };

  return (
    <AuthShell>
      <form onSubmit={onSubmit} className="grid gap-3">
        {error && <Alert>{error}</Alert>}
        <Field label={tr("email")}>
          <Input
            type="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
        </Field>
        <Field label={tr("password")}>
          <Input
            type="password"
            required
            minLength={8}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </Field>
        <Field label={tr("displayName")}>
          <Input
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
          />
        </Field>
        <Button type="submit" disabled={busy}>
          {tr("register")}
        </Button>
        <p className="text-center text-sm text-[var(--mist)]">
          <Link className="text-[var(--sand)] underline" href="/login">
            {tr("login")}
          </Link>
        </p>
      </form>
    </AuthShell>
  );
}
