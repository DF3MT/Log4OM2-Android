"use client";

import { useLocale } from "@/components/LocaleProvider";
import { Button, Field, Input, Textarea } from "@/components/ui";
import type { Qso } from "@/lib/types";
import type { FormEvent } from "react";

const BANDS = ["160m", "80m", "60m", "40m", "30m", "20m", "17m", "15m", "12m", "10m", "6m", "2m", "70cm"];
const MODES = ["SSB", "CW", "FT8", "FT4", "RTTY", "FM", "AM", "DIGITAL"];

export function QsoForm({
  value,
  onChange,
  onSubmit,
  onDelete,
  saving,
}: {
  value: Qso;
  onChange: (next: Qso) => void;
  onSubmit: () => Promise<void>;
  onDelete?: () => Promise<void>;
  saving?: boolean;
}) {
  const { tr } = useLocale();

  const set = <K extends keyof Qso>(key: K, v: Qso[K]) =>
    onChange({ ...value, [key]: v });

  const handle = async (e: FormEvent) => {
    e.preventDefault();
    await onSubmit();
  };

  return (
    <form onSubmit={handle} className="grid gap-4">
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        <Field label={tr("callsign")}>
          <Input
            required
            value={value.callsign}
            onChange={(e) => set("callsign", e.target.value.toUpperCase())}
          />
        </Field>
        <Field label={tr("date")}>
          <Input
            type="datetime-local"
            required
            value={value.qsodate?.slice(0, 16) ?? ""}
            onChange={(e) => set("qsodate", e.target.value.length === 16 ? `${e.target.value}:00` : e.target.value)}
          />
        </Field>
        <Field label={tr("band")}>
          <select
            className="w-full rounded-md border border-[var(--line)] bg-[var(--night-mid)] px-3 py-2"
            value={value.band}
            onChange={(e) => set("band", e.target.value)}
          >
            {BANDS.map((b) => (
              <option key={b} value={b}>
                {b}
              </option>
            ))}
          </select>
        </Field>
        <Field label={tr("mode")}>
          <select
            className="w-full rounded-md border border-[var(--line)] bg-[var(--night-mid)] px-3 py-2"
            value={value.mode}
            onChange={(e) => set("mode", e.target.value)}
          >
            {MODES.map((m) => (
              <option key={m} value={m}>
                {m}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Freq TX">
          <Input
            type="number"
            step="0.001"
            value={value.freq || ""}
            onChange={(e) => set("freq", Number(e.target.value) || 0)}
          />
        </Field>
        <Field label={`${tr("rst")} S/R`}>
          <div className="flex gap-2">
            <Input
              value={value.rstsent}
              onChange={(e) => set("rstsent", e.target.value)}
            />
            <Input
              value={value.rstrcvd}
              onChange={(e) => set("rstrcvd", e.target.value)}
            />
          </div>
        </Field>
        <Field label={tr("name")}>
          <Input value={value.name} onChange={(e) => set("name", e.target.value)} />
        </Field>
        <Field label="QTH">
          <Input value={value.qth} onChange={(e) => set("qth", e.target.value)} />
        </Field>
        <Field label={tr("country")}>
          <Input
            value={value.country}
            onChange={(e) => set("country", e.target.value)}
          />
        </Field>
        <Field label={tr("grid")}>
          <Input
            value={value.gridsquare}
            onChange={(e) => set("gridsquare", e.target.value.toUpperCase())}
          />
        </Field>
        <Field label="DXCC">
          <Input
            type="number"
            value={value.dxcc || ""}
            onChange={(e) => set("dxcc", Number(e.target.value) || 0)}
          />
        </Field>
        <Field label="TX PWR">
          <Input
            type="number"
            value={value.txpwr ?? ""}
            onChange={(e) =>
              set("txpwr", e.target.value === "" ? null : Number(e.target.value))
            }
          />
        </Field>
      </div>

      <Field label={tr("comment")}>
        <Textarea
          value={value.comment}
          onChange={(e) => set("comment", e.target.value)}
        />
      </Field>

      <div>
        <h3 className="mb-2 text-sm font-bold text-[var(--sand)]">{tr("awards")}</h3>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          <Field label="SOTA">
            <Input
              value={value.sotaRef}
              onChange={(e) => set("sotaRef", e.target.value.toUpperCase())}
            />
          </Field>
          <Field label="POTA">
            <Input
              value={value.potaRef}
              onChange={(e) => set("potaRef", e.target.value.toUpperCase())}
            />
          </Field>
          <Field label="WWFF">
            <Input
              value={value.wwffRef}
              onChange={(e) => set("wwffRef", e.target.value.toUpperCase())}
            />
          </Field>
          <Field label="COTA">
            <Input
              value={value.cotaRef}
              onChange={(e) => set("cotaRef", e.target.value.toUpperCase())}
            />
          </Field>
          <Field label="IOTA">
            <Input
              value={value.iota}
              onChange={(e) => set("iota", e.target.value.toUpperCase())}
            />
          </Field>
        </div>
      </div>

      <div className="flex flex-wrap gap-2">
        <Button type="submit" disabled={saving}>
          {tr("save")}
        </Button>
        {onDelete && (
          <Button
            type="button"
            variant="danger"
            disabled={saving}
            onClick={async () => {
              if (confirm(tr("confirmDelete"))) await onDelete();
            }}
          >
            {tr("delete")}
          </Button>
        )}
      </div>
    </form>
  );
}
