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
  const id = Number(params.id);
  const [qso, setQso] = useState<Qso | null>(null);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!id) return;
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
        {!qso ? (
          <p className="text-[var(--mist)]">{tr("loading")}</p>
        ) : (
          <QsoForm
            value={qso}
            onChange={setQso}
            saving={saving}
            onSubmit={async () => {
              setSaving(true);
              setError("");
              try {
                await api.updateQso(id, qso);
              } catch (err) {
                setError(err instanceof Error ? err.message : "Save failed");
              } finally {
                setSaving(false);
              }
            }}
            onDelete={async () => {
              setSaving(true);
              try {
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
