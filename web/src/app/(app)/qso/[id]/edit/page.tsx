"use client";

import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { AppShell } from "@/components/AppShell";
import { QsoForm } from "@/components/QsoForm";
import { useLocale } from "@/components/LocaleProvider";
import { Alert, Card } from "@/components/ui";
import { api } from "@/lib/api";
import type { Qso } from "@/lib/types";

export default function EditQsoPage() {
  const { tr } = useLocale();
  const router = useRouter();
  const params = useParams<{ id: string }>();
  const id = Array.isArray(params.id) ? params.id[0] : params.id;
  const [qso, setQso] = useState<Qso | null>(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!id || !/^\d+$/.test(id)) return;
    void api
      .getQso(id)
      .then(setQso)
      .catch((err) =>
        setError(err instanceof Error ? err.message : "Load failed"),
      );
  }, [id]);

  return (
    <AppShell>
      <Card title={tr("editQso")}>
        {error && (
          <div className="mb-3">
            <Alert>{error}</Alert>
          </div>
        )}
        {notice && (
          <p className="mb-3 rounded-md border border-[var(--sand)]/40 bg-[var(--sand)]/10 px-3 py-2 text-sm text-[var(--sand)]">
            {notice}
          </p>
        )}
        {!qso ? (
          <p className="text-[var(--mist)]">{error ? "" : tr("loading")}</p>
        ) : (
          <QsoForm
            value={qso}
            onChange={(next) => {
              setNotice("");
              setQso(next);
            }}
            saving={saving}
            onSubmit={async () => {
              setSaving(true);
              setError("");
              setNotice("");
              try {
                if (!id) return;
                const updated = await api.updateQso(id, qso);
                setQso(updated);
                setNotice(tr("saved"));
              } catch (err) {
                setError(err instanceof Error ? err.message : "Save failed");
              } finally {
                setSaving(false);
              }
            }}
            onDelete={async () => {
              setSaving(true);
              try {
                if (!id) return;
                await api.deleteQso(id);
                router.replace("/log");
              } catch (err) {
                setError(err instanceof Error ? err.message : "Delete failed");
                setSaving(false);
              }
            }}
          />
        )}
      </Card>
    </AppShell>
  );
}
