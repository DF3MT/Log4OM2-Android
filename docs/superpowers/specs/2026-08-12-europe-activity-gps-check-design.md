# Design: Europe activity-reference GPS check (COTA/POTA/SOTA/IOTA/WWFF)

**Date:** 2026-08-12  
**Status:** Approved (scope Europe, approach Hybrid #1)  
**Out of scope:** True worldwide PAD-US polygons; scraping ToS-hostile map UIs; QSL (#1 earlier)

## Goal

Detect whether the phone’s current GPS fix lies **inside** or **within the activation footprint** of one or more European award references, show multi-matches, and offer to fill QSO fields (`sota_ref`, `pota_ref`, `iota`, `wwff_ref`, **new** `cota_ref`).

## Geographic scope

**Europe** (EU associations / country prefixes as available): DE/DM/DL, OE, HB/HB0, ON, PA, F, G/GM/GW/GI, I, EA, SM, LA, OZ, OH, OK, OM, SP, HA, S5, 9A, LZ, YO, SV, CT, EI, LX, etc.

## Matching semantics (honest)

| Program | Geometry available | Match rule (default) | UI label |
|---------|--------------------|----------------------|----------|
| POTA | Point from directory; polygons when we can ingest country GeoJSON | Point-in-polygon if present, else radius **800 m** | `polygon` / `radius` |
| SOTA | Summit lat/lon (official CSV/API) | Horizontal radius **200 m** (official rule is ~25 m *vertical* — shown as note) | `radius` |
| WWFF | Directory point | Radius **500 m** | `radius` |
| COTA | Castle point (regional lists / map feeds) | Radius **1000 m** (COTA-PA-style activation circle) | `radius` |
| IOTA | Group bbox / island center from official JSON | Point-in-bbox, else radius **5 km** around center | `bbox` / `radius` |

Radii configurable in Settings. Always show **method + distance** per hit.

## Data pipeline

1. **ReferenceCatalog** (Room/SQLite or files in `cacheDir/refs/`):
   - Tables/files: `ref_id`, `program`, `reference`, `name`, `lat`, `lon`, `radius_m?`, `geom_geojson?`, `country`, `updated_at`
2. **Sync** (manual + optional on Wi‑Fi):
   - SOTA: `https://storage.sota.org.uk/summitslist.csv` → filter EU associations
   - IOTA: official `groups.json` / `islands.json`
   - POTA: public park lists / location APIs where accessible; optional later: polygon packs derived from community maps (pota-map.info style) for selected countries
   - WWFF: directory export / OK1SIM-compatible feed if licensed for app use; else user-import CSV
   - COTA: COTA-DL / WCA list with coordinates when available; document source in Settings
3. **Spatial index**: grid bucket or R-tree lite (lat/lon cells ~0.2°) for candidate filter before precise test.

## App changes

- Model/DAO/ADIF/filter/UI: add **`cota_ref`**
- `ActivityProximityService`: GPS → candidates → match → `List<ActivityHit>`
- New QSO: button **„Referenzen hier“** → dialog with multi-select → fill fields
- Optional: draw hit footprints on OsmMiniMap (circle/bbox)
- i18n DE/EN; Settings: radii + last sync status

## Legal / ToS

- Prefer **official** downloads (SOTA CSV, IOTA JSON, POTA API with proper User-Agent).
- Do **not** reverse-engineer private map backends without permission.
- Credit OpenStreetMap + programme trademarks in UI/about.

## Success criteria

- On a known summit/park test point in DE/AT/CH, correct reference appears with transparent method.
- Multiple overlapping hits (e.g. POTA+WWFF) listed.
- Works offline after sync.
- No crash if a catalogue is missing — empty with “Sync needed”.

## Self-review

- [x] No fake “true boundary” claims where only radius exists  
- [x] Europe scope explicit  
- [x] COTA included as first-class field  
- [x] Defaults for radii documented  
