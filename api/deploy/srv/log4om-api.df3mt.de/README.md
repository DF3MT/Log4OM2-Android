# `/srv/log4om-api.df3mt.de`

API + Postgres. Host port **8080**. Images from GHCR (GitHub Actions); no local build.

Creates Docker network `log4om` for the web stack (`/srv/log4om-web.df3mt.de`).

```bash
cd /srv/log4om-api.df3mt.de
./install.sh
```

| Service  | Image / port |
|----------|----------------|
| api      | `ghcr.io/df3mt/log4om-api:latest` → `:8080` |
| postgres | `postgres:16-alpine` (internal) |
