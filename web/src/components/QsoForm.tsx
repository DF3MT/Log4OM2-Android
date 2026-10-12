"use client";

import { useEffect, useState, type FormEvent } from "react";
import { useLocale } from "@/components/LocaleProvider";
import { Button, Field, Input, Textarea } from "@/components/ui";
import type { Qso } from "@/lib/types";

const BANDS = [
  "160m", "80m", "60m", "40m", "30m", "20m", "17m", "15m", "12m", "10m",
  "6m", "4m", "2m", "70cm", "23cm",
];
const MODES = [
  "SSB", "CW", "FM", "AM", "FT8", "FT4", "JS8", "JT65", "JT9", "RTTY",
  "PSK31", "OLIVIA", "DIGITAL",
];

/** SSB calling area in MHz. Log4OM stores frequency in kHz. */
const BAND_MHZ: Record<string, number> = {
  "160m": 1.85,
  "80m": 3.7,
  "60m": 5.357,
  "40m": 7.1,
  "30m": 10.136,
  "20m": 14.225,
  "17m": 18.13,
  "15m": 21.2,
  "12m": 24.93,
  "10m": 28.4,
  "6m": 50.15,
  "4m": 70.2,
  "2m": 144.3,
  "70cm": 432.1,
  "23cm": 1296.1,
};

export function freqKhzForBand(band: string): number {
  const mhz = BAND_MHZ[band];
  return mhz ? Math.round(mhz * 1000) : 0;
}

function khzToMhzInput(khz: number): string {
  if (!khz) return "";
  return String(Math.round(khz) / 1000);
}

function withCurrent(options: string[], current: string): string[] {
  if (!current || options.includes(current)) return options;
  return [current, ...options];
}

function dateInputValue(iso: string): string {
  return iso ? iso.slice(0, 19) : "";
}

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
  const [freqMhz, setFreqMhz] = useState(() => khzToMhzInput(value.freq));

  useEffect(() => {
    setFreqMhz(khzToMhzInput(value.freq));
    // Sync when the record or band changes. Typing updates freq without resetting the text.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value.qsoid, value.band]);

  const set = <K extends keyof Qso>(key: K, v: Qso[K]) =>
    onChange({ ...value, [key]: v });

  const handle = async (e: FormEvent) => {
    e.preventDefault();
    await onSubmit();
  };

  const bands = withCurrent(BANDS, value.band);
  const modes = withCurrent(MODES, value.mode);

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
            step={1}
            value={dateInputValue(value.qsodate)}
            onChange={(e) => {
              const v = e.target.value;
              set("qsodate", v.length === 16 ? `${v}:00` : v);
            }}
          />
        </Field>
        <Field label={tr("band")}>
          <select
            className="w-full rounded-md border border-[var(--line)] bg-[var(--night-mid)] px-3 py-2"
            value={value.band}
            onChange={(e) => {
              const band = e.target.value;
              const freq = freqKhzForBand(band) || value.freq;
              onChange({ ...value, band, freq });
            }}
          >
            {bands.map((b) => (
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
            {modes.map((m) => (
              <option key={m} value={m}>
                {m}
              </option>
            ))}
          </select>
        </Field>
        <Field label={tr("freq")}>
          <Input
            type="number"
            inputMode="decimal"
            step="0.001"
            min="0"
            value={freqMhz}
            onChange={(e) => {
              const text = e.target.value;
              setFreqMhz(text);
              if (text === "" || text === "." || text === "-" || text.endsWith(".")) {
                if (text === "") set("freq", 0);
                return;
              }
              const mhz = Number(text);
              if (!Number.isNaN(mhz)) set("freq", Math.round(mhz * 1000));
            }}
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
