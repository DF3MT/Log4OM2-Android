"use client";

import { FormEvent, useEffect, useState } from "react";
import { useLocale } from "@/components/LocaleProvider";
import { Alert, Button, Card, Field, Input } from "@/components/ui";
import { api } from "@/lib/api";
import type { StationProfile } from "@/lib/types";

const empty: StationProfile = {
  callsign: "",
  gridsquare: "",
  name: "",
  rig: "",
  dxcc: "",
  defaultRstSent: "59",
  defaultRstRcvd: "59",
  defaultBand: "20m",
  defaultMode: "SSB",
  defaultTxpwr: "",
};

export default function StationSettingsPage() {
  const { tr } = useLocale();
  const [form, setForm] = useState<StationProfile>(empty);
  const [msg, setMsg] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    void api
      .getStation()
      .then(setForm)
      .catch((err) => setError(err instanceof Error ? err.message : "Failed"));
  }, []);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError("");
    setMsg("");
    try {
      const saved = await api.putStation(form);
      setForm(saved);
      setMsg("OK");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Save failed");
    } finally {
      setBusy(false);
    }
  };

  const set = <K extends keyof StationProfile>(k: K, v: StationProfile[K]) =>
    setForm((f) => ({ ...f, [k]: v }));

  return (
    <Card title={tr("station")}>
      <form onSubmit={onSubmit} className="grid gap-3 sm:grid-cols-2">
        {error && <div className="sm:col-span-2"><Alert>{error}</Alert></div>}
        {msg && <p className="sm:col-span-2 text-sm text-[var(--sand)]">{msg}</p>}
        <Field label={tr("callsign")}>
          <Input
            value={form.callsign}
            onChange={(e) => set("callsign", e.target.value.toUpperCase())}
          />
        </Field>
        <Field label={tr("grid")}>
          <Input
            value={form.gridsquare}
            onChange={(e) => set("gridsquare", e.target.value.toUpperCase())}
          />
        </Field>
        <Field label={tr("name")}>
          <Input value={form.name} onChange={(e) => set("name", e.target.value)} />
        </Field>
        <Field label="Rig">
          <Input value={form.rig} onChange={(e) => set("rig", e.target.value)} />
        </Field>
        <Field label="DXCC">
          <Input value={form.dxcc} onChange={(e) => set("dxcc", e.target.value)} />
        </Field>
        <div className="sm:col-span-2">
          <h3 className="mb-2 text-sm font-bold text-[var(--sand)]">{tr("defaults")}</h3>
        </div>
        <Field label={tr("band")}>
          <Input
            value={form.defaultBand}
            onChange={(e) => set("defaultBand", e.target.value)}
          />
        </Field>
        <Field label={tr("mode")}>
          <Input
            value={form.defaultMode}
            onChange={(e) => set("defaultMode", e.target.value)}
          />
        </Field>
        <Field label={`${tr("rst")} TX`}>
          <Input
            value={form.defaultRstSent}
            onChange={(e) => set("defaultRstSent", e.target.value)}
          />
        </Field>
        <Field label={`${tr("rst")} RX`}>
          <Input
            value={form.defaultRstRcvd}
            onChange={(e) => set("defaultRstRcvd", e.target.value)}
          />
        </Field>
        <Field label="TX PWR">
          <Input
            value={form.defaultTxpwr}
            onChange={(e) => set("defaultTxpwr", e.target.value)}
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
