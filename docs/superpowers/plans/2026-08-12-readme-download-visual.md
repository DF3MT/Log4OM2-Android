# README + Download Visual Polish Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Ship Log4OM-website-adjacent visuals for GitHub Pages download + welcoming README per `docs/superpowers/specs/2026-08-12-readme-download-visual-design.md`.

**Architecture:** Static HTML/CSS in `site/index.template.html` (CI sed placeholders unchanged). Shared motif via `docs/assets/readme-hero.svg` linked from README. No Log4OM trademark logo.

**Tech Stack:** HTML5, CSS (custom properties, reduced-motion), SVG, GitHub-flavored Markdown.

---

### Task 1: Hero SVG asset

**Files:**
- Create: `docs/assets/readme-hero.svg`

**Step 1:** Draw 1200×420 SVG — sand top strip, night gradient, frequency wave, title text “Log4OM Android”, thin red accent line. No external fonts required (system or outlined).

**Step 2:** Commit with README later.

---

### Task 2: Download page template

**Files:**
- Modify: `site/index.template.html`

**Step 1:** Replace layout with brand strip + night hero + primary/secondary CTAs + paper meta panel; keep all `{{…}}` tokens.

**Step 2:** Add wave animation + `prefers-reduced-motion`.

**Step 3:** Visually spot-check by opening a local copy with sample values (optional).

---

### Task 3: README rewrite

**Files:**
- Modify: `README.md`

**Step 1:** Hero image + badges + user-first sections; wrap developer/CI/architecture in `<details>`.

**Step 2:** Keep security warnings visible.

---

### Task 4: Verify + commit

**Step 1:** Grep template for required placeholders: `VERSION_NAME`, `VERSION_CODE`, `TAG`, `APK_NAME`, `SHA_SHORT`, `LATEST_ASSET`, `VERSIONED_ASSET`, `RELEASE_URL`, `REPO_URL`.

**Step 2:** Commit when user asks (or if already authorized in-session).
