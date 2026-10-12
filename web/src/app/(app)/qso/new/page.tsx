"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { AppShell } from "@/components/AppShell";
import { freqKhzForBand, QsoForm } from "@/components/QsoForm";
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
    void Promise.all([api.getStation(), api.listLogbooks().catch(() => [])])
      .then(([s, books]: [StationProfile, { stationCallsign: string; active: boolean }[]]) => {
        const band = s.defaultBand || "20m";
        const activeCall =
          books.find((b) => b.active)?.stationCallsign || s.callsign;
        setQso(
          emptyQso({
            band,
            mode: s.defaultMode || "SSB",
            freq: freqKhzForBand(band) || 14225,
            rstsent: s.defaultRstSent || "59",
            rstrcvd: s.defaultRstRcvd || "59",
            stationcallsign: activeCall,
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
              const created = await api.createQso({
                ...qso,
                freqrx: qso.freqrx || qso.freq,
                bandrx: qso.bandrx || qso.band,
              });
              if (!created.qsoid) throw new Error("Save failed");
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
