# Design: Mobile feature pack (lookup, map, DXCC needed, award refs)

**Date:** 2026-08-12  
**Status:** Approved by user (“alles ausser 1”)  
**Out of scope:** QSL/LoTW confirmation UI (#1)

## 2 — Multi-source lookup
- Shared `CallsignLookupData` (+ keep QRZ type as alias/mapper).
- Order: QRZ (if creds) → HamQTH (if creds) → Club Log DXCC endpoint (if API key) for missing dxcc/cq only.
- Settings: HamQTH user/pass, Club Log API key (optional).
- New QSO shows which source filled the data.

## 3 — Map / distance
- `GridLocator`: maidenhead ↔ lat/lon, haversine km, bearing °.
- On lookup/save: fill contact lat/lon/distance when grid or QRZ coords exist.
- UI: show distance + bearing; button opens `geo:lat,lon` / maps intent.
- Persist `lat`, `lon`, `distance` in INSERT/UPDATE when columns exist.

## 4 — Needed DXCC
- DAO: worked DXCC set; worked (dxcc, band) pairs (cached in VM).
- Chip on New QSO: New DXCC / New on band / Worked (same band).

## 5 — SOTA/POTA/IOTA/WWFF
- Model + DAO + ADIF + New QSO fields + LogFilter (+ chips).
- Column names: `sota_ref`, `pota_ref`, `wwff_ref`, `iota` (ADIF-aligned). Reading uses safe column lookup so older DBs don’t crash.
