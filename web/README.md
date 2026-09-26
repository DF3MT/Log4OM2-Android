# Log4OM2 Web Portal

Next.js 15 portal for the Log4OM2 multi-tenant API.

- Auth: JWT access + refresh (stored in `localStorage`, refreshed on 401)
- Features: logbook filter, QSO CRUD, ADIF import/export, station/DB/lookup settings
- i18n: DE / EN
- HAProxy-ready: `GET /api/health`, authenticated views are client-dynamic, API calls use `cache: 'no-store'`

## Quick start

```bash
cp .env.example .env.local
# set NEXT_PUBLIC_API_URL to your API (default http://localhost:8080)
npm install
npm run dev
```

App: http://localhost:3000  
Health: http://localhost:3000/api/health

API must be running (see sibling repo `Log4OM2-API`) with CORS allowing this origin.

## Docker

```bash
docker compose -f docker-compose.dev.yml up --build
```

Image: `ghcr.io/<owner>/log4om-web:latest`

## Pages

| Path | Purpose |
|------|---------|
| `/login`, `/register` | Auth |
| `/log` | Logbook + ADIF |
| `/qso/new`, `/qso/{id}/edit` | QSO form |
| `/settings/station` | Station + defaults |
| `/settings/database` | BYODB MySQL config |
| `/settings/lookup` | QRZ / HamQTH / Club Log |

## Environment

- `NEXT_PUBLIC_API_URL` — browser-visible API base URL (baked into client bundle)
