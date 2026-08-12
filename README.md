<p align="center">
  <img src="docs/assets/readme-hero.svg" alt="Log4OM Android — companion for Log4OM2" width="100%" />
</p>

<p align="center">
  <strong>Android companion</strong> for <a href="https://www.log4om.com/">Log4OM2</a> —
  connect to your MySQL log, work QSOs from the phone, and stay in sync with the desktop station.
</p>

<p align="center">
  <a href="https://df3mt.github.io/Log4OM2-Android/"><img src="https://img.shields.io/badge/Download-APK-2F6FED?style=for-the-badge&labelColor=0E1520" alt="Download APK" /></a>
  <a href="https://github.com/DF3MT/Log4OM2-Android/releases"><img src="https://img.shields.io/github/v/release/DF3MT/Log4OM2-Android?style=for-the-badge&color=E8C9A0&labelColor=0E1520&label=Release" alt="Latest release" /></a>
  <a href="https://github.com/DF3MT/Log4OM2-Android/actions/workflows/release.yml"><img src="https://img.shields.io/github/actions/workflow/status/DF3MT/Log4OM2-Android/release.yml?branch=main&style=for-the-badge&label=CI&labelColor=0E1520" alt="CI status" /></a>
</p>

<p align="center">
  <a href="https://df3mt.github.io/Log4OM2-Android/">Download page</a>
  ·
  <a href="https://github.com/DF3MT/Log4OM2-Android/releases">Releases</a>
  ·
  <a href="https://www.log4om.com/download/">Log4OM desktop</a>
</p>

---

## Why this app?

- **Same log as the desktop** — thin client over your Log4OM **MySQL** `log` table (no separate phone database)
- **Log on the go** — new / edit / delete QSOs, QRZ lookup, past QSOs for the callsign
- **Filters & ADIF** — filter the book, multi-select, share or save `.adi`
- **Feels at home** — German / English follow the phone language; in-app update check from GitHub Releases

---

## Features

| Area | What you get |
|------|----------------|
| **Logbook** | Paginated list, search, edit & delete, filters (call / band / mode / date / country / DXCC) |
| **New QSO** | Full form — band, mode, RST, DXCC, CQ/ITU, grid, propagation, contest, notes… |
| **QRZ** | Auto lookup while typing (XML API; subscription required) |
| **ADIF** | Import into MySQL; export selected / filtered QSOs |
| **Station** | Callsign, locator, rig, defaults, optional GPS for *my* lat/lon |
| **Updates** | Checks GitHub Releases on startup and from Settings |

---

## Download & install

1. Grab the signed APK from the **[download page](https://df3mt.github.io/Log4OM2-Android/)** or **[Releases](https://github.com/DF3MT/Log4OM2-Android/releases)**
2. On the phone, allow install from that browser / file manager (“unknown apps”)
3. If you previously installed a **debug / Android Studio** build, **uninstall it first** — different signing keys show up as “App not installed”

Requires **Android 5.0+** (API 21).

---

## Quick start (on the phone)

Open **Settings** and set:

1. **My station** — callsign, grid, name, rig, DXCC  
2. **MySQL** — host, port (`3306`), database, user, password → **Test connection**  
3. **QRZ.com** (optional) — XML API credentials  
4. **Defaults** — RST, band, mode, power → **Save**

Your phone needs a network path to the MySQL host (LAN or VPN). Do **not** expose port `3306` to the open internet without protection.

---

## Requirements

**Phone**

- Network to the Log4OM MySQL server  
- Optional: location permission · QRZ XML subscription  

**Station / network**

- Log4OM2 with MySQL backend  
- DB user with `SELECT` / `INSERT` / `UPDATE` / `DELETE` on `log`  

---

## Security notes

Please read before exposing MySQL beyond your LAN:

- JDBC uses **`useSSL=false`** — prefer VPN / tunnel for anything outside a trusted LAN  
- DB and QRZ passwords live in **DataStore** (not encrypted at rest)  
- Prefer a dedicated MySQL user with **minimum** rights on the log database  
- Do **not** publish MySQL `3306` to the public internet  

---

## How it works

```
┌─────────────────┐     JDBC (MySQL)      ┌──────────────────┐
│  Log4OM Android │ ───────────────────► │  Log4OM2 MySQL   │
│  (Compose UI)   │                      │  `log` table     │
└────────┬────────┘                      └──────────────────┘
         │ HTTPS
         ▼
┌─────────────────┐
│  QRZ.com XML API│
└─────────────────┘
```

---

<details>
<summary><strong>For developers</strong> — build, architecture, CI, layout</summary>

### Clone & run

```bash
git clone git@github.com:DF3MT/Log4OM2-Android.git
cd Log4OM2-Android
```

Open in **Android Studio**, or:

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

Debug builds use `applicationId` `com.log4om.android.debug` so they do not clash with the signed release APK.

### Tech stack

Kotlin · Jetpack Compose · Material 3 · Coroutines / StateFlow · DataStore · OkHttp · MySQL Connector/J **5.1.49** · core library desugaring

### Package layout

```
com.log4om.android
├── ui/          # screens, viewmodels, theme
├── data/        # JDBC, repository, QRZ, ADIF, prefs
└── util/        # bands/modes, location, APK install
```

### CI / CD

Pushes to `main` (and manual **workflow_dispatch**) run [`.github/workflows/release.yml`](.github/workflows/release.yml): signed `assembleRelease` → GitHub Release (`build-<n>`) → Pages site from `site/index.template.html`.

**Secrets:** `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`  
**Pages:** Settings → Pages → Source = **GitHub Actions**

```bash
base64 -w0 release.keystore > keystore.b64
```

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore")) | Set-Clipboard
```

### Known limitations

- Frequency: DB stores kHz; UI labels MHz — careful when editing  
- Location permission declared; runtime dialog may still be incomplete  
- Large ADIF imports / select-all load matching data into memory  

### Conventions

Conventional commits · UI strings in `values` / `values-en` · ViewModels emit `UiText` · JDBC on `Dispatchers.IO`

</details>

---

## Credits

- Desktop logging: [Log4OM](https://www.log4om.com/) ([download](https://www.log4om.com/download/))  
- Callsign data: [QRZ.com](https://www.qrz.com/) XML API  
- Companion app: [DF3MT](https://github.com/DF3MT)

## License

License not specified in the repository yet. Contact the maintainer ([DF3MT](https://github.com/DF3MT)) before redistributing.
