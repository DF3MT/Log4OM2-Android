# Logbook Filter, Multi-Select & ADIF Export — Design

**Date:** 2026-08-12  
**Status:** Approved

## Goals

1. Filter logbook by callsign, band, mode, date range, country and/or DXCC.
2. Wildcard `*` in callsign and country text filters (mapped to SQL `%`).
3. Multi-select QSOs (long-press + toolbar); select-all = all rows matching current filters.
4. Export selection as ADIF via Android Share sheet and “Save as…” (`CreateDocument`).

## Non-goals

- ADIF export of entire DB without selection/filters UI path (select-all-on-filter covers this).
- In-app language picker; date format localization.

## Data

`LogFilter`: callsignPattern, band, mode, dateFrom, dateTo, countryPattern, dxcc (nullable Int).

SQL: AND of active clauses; callsign/country: if pattern contains `*` replace with `%`, else wrap as `%value%` for contains; escape literal `%`/`_` in user input before substituting `*`.

## UI

- Filter icon → modal bottom sheet; active filter chips under top bar.
- Selection mode contextual top bar: count, select all, share, save, cancel.
- File name: `log4om_export_yyyyMMdd_HHmm.adi`.

## Export fields

CALL, QSO_DATE, TIME_ON, BAND, MODE, FREQ, FREQ_RX, RST_SENT, RST_RCVD, NAME, QTH, COUNTRY, DXCC, CQZ, ITUZ, GRIDSQUARE, CONT, COMMENT, NOTES, STATION_CALLSIGN, MY_GRIDSQUARE, MY_NAME, MY_RIG, OPERATOR, TX_PWR, PROP_MODE, CONTEST_ID, SAT_NAME, SAT_MODE, ADDRESS — omit blank fields.
