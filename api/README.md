# Log4OM2 API

Multi-tenant REST API between Log4OM2 clients (Android / Web) and each user's Log4OM MySQL database.

- **Platform DB:** PostgreSQL (users, tenants, encrypted DB credentials, JWT refresh tokens)
- **Tenant DB:** existing Log4OM MySQL `log` table (BYODB)
- **Auth:** JWT access + refresh (stateless — HAProxy-friendly, no sticky sessions)
- **Cache:** `Cache-Control: no-store` on all authenticated/data routes

## Quick start (dev)

```bash
cp .env.example .env
docker compose -f docker-compose.dev.yml up --build
```

API: http://localhost:8080  
Swagger: http://localhost:8080/swagger-ui.html  
Health: http://localhost:8080/actuator/health

## Main endpoints

| Method | Path | Auth |
|--------|------|------|
| POST | `/auth/register` | no |
| POST | `/auth/login` | no |
| POST | `/auth/refresh` | no |
| POST | `/auth/logout` | yes |
| GET/PUT | `/me/station` | yes |
| GET/PUT | `/me/db-config` | yes |
| POST | `/me/db-config/test` | yes |
| GET/PUT | `/me/lookup-credentials` | yes |
| GET | `/qsos` | yes |
| POST/PUT/DELETE | `/qsos`, `/qsos/{id}` | yes |
| POST | `/adif/import`, `/adif/export` | yes |
| GET | `/stats/worked-dxcc` | yes |

## Environment

See `.env.example`. Required in production:

- `DATABASE_URL` / `DATABASE_USER` / `DATABASE_PASSWORD`
- `JWT_SECRET` (≥32 chars)
- `ENCRYPTION_KEY` (AES key material)
- `CORS_ORIGIN`

## HAProxy

Example config: [`deploy/haproxy.cfg`](deploy/haproxy.cfg) — round-robin, health checks, **no HTTP cache**.

## Image

`ghcr.io/<owner>/log4om-api:latest` (published by GitHub Actions on `main`).
