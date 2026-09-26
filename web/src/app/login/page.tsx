"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";
import { AuthShell } from "@/components/AppShell";
import { useAuth } from "@/components/AuthProvider";
import { useLocale } from "@/components/LocaleProvider";
import { Alert, Button, Field, Input } from "@/components/ui";

export default function LoginPage() {
  const { login } = useAuth();
  const { tr } = useLocale();
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      await login(email, password);
      router.replace("/log");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Login failed");
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
            autoComplete="username"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
        </Field>
        <Field label={tr("password")}>
          <Input
            type="password"
            required
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </Field>
        <Button type="submit" disabled={busy}>
          {tr("login")}
        </Button>
        <p className="text-center text-sm text-[var(--mist)]">
          <Link className="text-[var(--sand)] underline" href="/register">
            {tr("register")}
          </Link>
        </p>
      </form>
    </AuthShell>
  );
}
