# Design: README + Download page visuals (Log4OM-aligned)

**Date:** 2026-08-12  
**Status:** Approved & implemented  
**Scope:** `README.md` and `site/index.template.html` (GitHub Pages)  
**Out of scope:** App Compose theme, App Store / iOS, rewriting technical docs content

## Goal

Make first-time visitors feel welcome and oriented: clear brand, one primary action (download / get started), calm amateur-radio + Log4OM website familiarity — without copying Log4OM’s trademarked logo artwork.

## Brand references

- [log4om.com](https://www.log4om.com/) / [download](https://www.log4om.com/download/): warm sand header, dark photo hero, royal-blue primary CTA, white content on mid-grey, strong product name.
- Companion positioning: “Android companion for Log4OM2 MySQL log” (DF3MT).

## Visual system (shared)

| Token | Value | Use |
|-------|--------|-----|
| Sand | `#E8C9A0` | Top brand strip / README badge accents |
| Night | `#0E1520` → `#1A2838` | Hero / page background |
| Mist | `#9AA8BC` | Secondary text |
| Paper | `#F4F6F8` | Content panels |
| Signal blue | `#2F6FED` | Primary CTA / links |
| Marker red | `#C62828` | Thin accent line (echo of Log4OM “2”, not a fake logo) |
| Type (Pages) | Source Sans 3 + Source Code Pro | UI + monospace meta |
| Type (README) | GitHub default + emoji sparingly | Markdown constraints |

**Avoid:** purple/indigo AI clichés, Inter/Roboto stacks on the Pages site, card-stacked hero, floating badge stickers on hero media, inset hero “media cards”.

## Download page (`site/index.template.html`)

### Composition (first viewport = one job)

1. Full-bleed night atmosphere (CSS gradient + subtle SVG frequency/wave motif — not a stock photo dependency).
2. Sand strip at top with product name **Log4OM Android** (hero-level brand).
3. One headline (DE/EN: prefer English for Pages to match current template, short supporting line).
4. One primary CTA: **Download latest APK**.
5. Secondary links: versioned APK, GitHub Release, repository.
6. Version meta (`{{VERSION_*}}` placeholders) in a quiet paper panel *below* the CTA group (interaction/support info, not competing with brand).

### Motion (2–3 intentional)

- Soft wave path opacity / drift (~8–12s loop).
- Primary button hover lift / color shift.
- Optional fade-in of meta panel on load (subtle, `prefers-reduced-motion: reduce` disables).

### Content tone

Welcoming, non-developer-first. Keep install tip (“unknown apps”) in footer. Keep template placeholders unchanged.

## README (`README.md`)

### Structure (user-first)

1. **Hero block** — centered title, one-liner, link badges (Download Pages, Releases, Android API, License/contact).
2. Optional **banner image** (`docs/assets/readme-hero.svg` or PNG) committed to repo — sand/night/wave motif matching Pages; no trademarked Log4OM logo.
3. **Why this app** — 3–4 bullets max.
4. **Features** — keep table or compact list (scannable).
5. **Download & install** — Pages + Releases + uninstall-debug tip (signature clash).
6. **Quick start (phone)** — Settings steps only.
7. **Requirements / Security** — short, visible warnings.
8. **For developers** — `<details>` wrapping architecture, CI secrets, project layout, known limitations (collapse noise).

Preserve factual accuracy; tighten wording, do not delete security warnings.

### Badges

shields.io (or static) using sand/blue hex where possible; link to Pages and Releases.

## Approaches considered

- **A (chosen):** Log4OM-website-adjacent palette + night hero — recommended.
- B: Pure dark shack — rejected for weaker brand link.
- C: Light App Store — rejected as generic.

## Success criteria

- Pages first screen reads as one composition with brand dominant.
- README top feels inviting; power-user docs still reachable in `<details>`.
- No Log4OM logo bitmap/SVG copied from their site.
- Template variables still render in CI Pages job.

## Self-review checklist

- [x] No placeholder “TBD” sections for required decisions  
- [x] Placeholders `{{…}}` must remain in HTML template  
- [x] Scope excludes Compose theme (app stays green/amber until a separate task)  
- [x] Explicitly no trademarked logo asset  
