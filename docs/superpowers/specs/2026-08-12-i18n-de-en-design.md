# Multilingualität (DE/EN) — Design

**Date:** 2026-08-12  
**Status:** Approved (user: Systemsprache, DE fallback, Scope B)

## Decisions

- Language follows Android system locale (no in-app picker).
- German in `values/strings.xml` (default/fallback).
- English in `values-en/strings.xml`.
- Scope: all user-visible UI **and** ViewModel error/status messages.
- Date formats unchanged for now.
- Ham radio terms (QSO, RST, DXCC, QTH, Locator, Band, Mode values) stay as-is.

## Architecture

- Compose: `stringResource(R.string.*)`.
- ViewModels: emit `UiText` (resource id + optional format args, or raw exception detail) instead of hard-coded German.
- UI resolves `UiText` via `asString()` / `stringResource`.
- `MainActivity` nav labels use `@StringRes` ids.

## Out of scope

- Additional languages
- Locale-specific number/date formatting
- Translating CallsignCountry country names
