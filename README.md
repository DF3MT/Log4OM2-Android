# Log4OM Android

Android companion client for [Log4OM2](https://www.log4om.com/) that connects directly to your Log4OM **MySQL** log database. Log QSOs from your phone, look up callsigns via QRZ.com, import ADIF files, and keep your station defaults in sync with the desktop log.

> **Repository:** [github.com/DF3MT/Log4OM2-Android](https://github.com/DF3MT/Log4OM2-Android)

---

## Features

| Area | What you get |
|------|----------------|
| **Logbook** | Paginated QSO list, search by callsign, edit & delete |
| **New QSO** | Full QSO form (band, mode, RST, DXCC, CQ/ITU, gridsquare, propagation, contest, notes, …) |
| **QRZ lookup** | Auto lookup while typing (XML API; subscription required) |
| **Past QSOs** | History with the same callsign shown while logging |
| **ADIF / ADI import** | Bulk import into MySQL; duplicates skipped (`INSERT IGNORE`) |
| **Station defaults** | Callsign, locator, name, rig, DXCC, default band/mode/RST/power |
| **GPS** | Optional latitude/longitude for *my* station when saving (if location permission granted) |
| **Languages** | German (default) and English via Android system language |

---

## Requirements

### On your phone / emulator

- Android **5.0+** (API 21); target SDK **35**
- Network access to the host running the Log4OM MySQL server
- Optional: location permission for station lat/lon
- Optional: [QRZ.com](https://www.qrz.com/) XML API subscription for callsign lookup

### On your Log4OM / network side

- Log4OM2 with MySQL backend enabled
- MySQL reachable from the phone (LAN, VPN, or port forward — **not** recommended over the open internet without TLS/VPN)
- A database user that can `SELECT` / `INSERT` / `UPDATE` / `DELETE` on the `log` table

---

## Getting started

### 1. Clone and open

```bash
git clone git@github.com:DF3MT/Log4OM2-Android.git
cd Log4OM2-Android
```

Open the folder in **Android Studio** (Ladybug / recent AGP-compatible version). Let Gradle sync.

### 2. Build & run

- Connect a device or start an emulator
- Run the `app` configuration, or from a terminal (once the Gradle wrapper scripts are present):

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

### 3. Configure the app

Open **Settings** (bottom navigation) and fill in:

1. **My station** — your callsign, gridsquare, name, rig, DXCC entity number  
2. **MySQL** — host/IP, port (default `3306`), database name, user, password → **Test connection**  
3. **QRZ.com** (optional) — username/callsign and password for XML API  
4. **Defaults** — default RST, band, mode, TX power for new QSOs  
5. Tap **Save**

---

## How it works

The phone is a **thin client**: there is no local Room/SQLite log copy. Every list/search/save/delete talks to the remote MySQL `log` table over JDBC.

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

### Architecture (packages)

```
com.log4om.android
├── MainActivity / Log4OMApp          # App shell, DI-ish wiring
├── ui/
│   ├── screens/                     # Log, New QSO, Settings
│   ├── viewmodel/                   # StateFlows + use cases
│   ├── components/                  # Shared Compose widgets
│   ├── theme/
│   └── util/UiText                  # Localized messages from VMs
├── data/
│   ├── db/                          # DatabaseHelper + QsoDao (JDBC)
│   ├── repository/LogRepository
│   ├── network/QrzApiService        # OkHttp
│   ├── prefs/AppPrefs               # DataStore
│   ├── adif/                        # Parser, mapper, callsign→country
│   └── model/
└── util/                            # AmateurRadio constants, LocationHelper
```

### Tech stack

- Kotlin, Jetpack Compose, Material 3, Navigation Compose  
- Coroutines + StateFlow  
- DataStore Preferences  
- OkHttp (QRZ)  
- MySQL Connector/J **5.1.49** (Android-compatible JDBC)  
- Core library desugaring (`java.time` on older APIs)

---

## Usage guide

### Logbook

- Scroll through recent QSOs (pages of 50)
- Search by callsign
- Tap a row to **edit**; overflow menu for edit/delete
- Pull refresh via the toolbar refresh icon

### New QSO

- Enter callsign — after a short debounce, past QSOs and QRZ data may fill in  
- Band change updates a default frequency; mode change adjusts default RST  
- Save writes an `INSERT` or `UPDATE` into MySQL  
- Station fields (`stationcallsign`, `mygridsquare`, `mycountry`, …) come from Settings (+ GPS if available)

### ADIF import

In Settings → **ADIF / ADI import**:

1. Pick an `.adi` / ADIF file  
2. Records without CALL / BAND / MODE / QSO_DATE are skipped  
3. Duplicates matching the DB unique key (typically mode + date + band + callsign) are skipped via `INSERT IGNORE`

---

## Localization

| Locale | Resource folder | Role |
|--------|-----------------|------|
| German | `res/values/` | Default / fallback |
| English | `res/values-en/` | Used when the system language is English |

There is **no in-app language switch** — change the device language. UI labels, dialogs, snackbars, and ViewModel status/error messages are localized. Ham terms (QSO, RST, DXCC, QTH, …) stay as-is.

---

## Logbook filters & ADIF export

- **Filters:** callsign, band, mode, date from/to, country, DXCC (toolbar filter icon → bottom sheet).
- **Wildcards:** use `*` in callsign/country (e.g. `DL*`, `*HB9*`). Without `*`, text matches as contains.
- **Multi-select:** long-press a row or tap the checklist toolbar icon; **All** selects every QSO matching the current filters (not only the loaded page).
- **Export:** Share (system share sheet) or Save as… → `.adi` file (`log4om_export_yyyyMMdd_HHmm.adi`).

---

## Security notes

Please read before exposing MySQL beyond your LAN:

- JDBC is configured with **`useSSL=false`** — credentials and QSOs travel in cleartext unless you wrap the path in a VPN / tunnel.
- DB and QRZ passwords are stored in **DataStore** (not encrypted at rest).
- `android:allowBackup="true"` may include preferences in Android backups.
- Prefer a dedicated MySQL user with the **minimum** rights on the log database.
- Do **not** expose MySQL port `3306` to the public internet.

---

## Project layout (repo)

```
Log4OM2-Android/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/log4om/android/…
│       └── res/
│           ├── values/strings.xml       # German
│           ├── values-en/strings.xml    # English
│           └── xml/network_security_config.xml
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
├── docs/superpowers/specs/              # Design notes
├── build.gradle.kts
├── settings.gradle.kts
└── .gitignore
```

---

## Development

### Conventions

- Conventional commits (`feat`, `fix`, `chore`, …) preferred  
- UI strings belong in `strings.xml` / `values-en`; ViewModels emit `UiText`, not hard-coded German  
- Keep JDBC on a background dispatcher (`Dispatchers.IO`)

### Known limitations / TODOs

- Frequency display/edit: DB stores kHz; UI labels MHz — be careful when editing existing QSOs  
- Location permission is declared but not requested with a runtime dialog yet  
- Large ADIF files are read fully into memory on import  
- Select-all on huge logs loads all matching IDs into memory  

---

## Contributing

Issues and pull requests are welcome on GitHub. For larger changes, open an issue first so scope stays clear.

---

## Credits

- Desktop logging: [Log4OM](https://www.log4om.com/)  
- Callsign data: [QRZ.com](https://www.qrz.com/) XML API  
- Callsign → country prefix table bundled in-app for ADIF / station country hints  

---

## License

License not specified in the repository yet. Contact the maintainer ([DF3MT](https://github.com/DF3MT)) before redistributing.
