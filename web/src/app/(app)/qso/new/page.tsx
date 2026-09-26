"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { AppShell } from "@/components/AppShell";
import { QsoForm } from "@/components/QsoForm";
import { useLocale } from "@/components/LocaleProvider";
import { Alert, Card } from "@/components/ui";
import { api } from "@/lib/api";
import { emptyQso, type Qso, type StationProfile } from "@/lib/types";

export default function NewQsoPage() {
  const { tr } = useLocale();
  const router = useRouter();
  const [qso, setQso] = useState<Qso>(emptyQso());
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    void api
      .getStation()
      .then((s: StationProfile) => {
        setQso(
          emptyQso({
            band: s.defaultBand || "20m",
            mode: s.defaultMode || "SSB",
            rstsent: s.defaultRstSent || "59",
            rstrcvd: s.defaultRstRcvd || "59",
            stationcallsign: s.callsign,
            mygridsquare: s.gridsquare,
            myname: s.name,
            myrig: s.rig,
            txpwr: s.defaultTxpwr ? Number(s.defaultTxpwr) : null,
          }),
        );
      })
      .catch(() => undefined);
  }, []);

  return (
    <AppShell>
      <Card title={tr("newQso")}>
        {error && <div className="mb-3"><Alert>{error}</Alert></div>}
        <QsoForm
          value={qso}
          onChange={setQso}
          saving={saving}
          onSubmit={async () => {
            setSaving(true);
            setError("");
            try {
              const created = await api.createQso(qso);
              router.replace(`/qso/${created.qsoid}/edit`);
            } catch (err) {
              setError(err instanceof Error ? err.message : "Save failed");
            } finally {
              setSaving(false);
            }
          }}
        />
      </Card>
    </AppShell>
  );
}
