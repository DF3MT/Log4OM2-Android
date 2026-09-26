# Deploy on host (`/srv/log4om`)

**Images are built by GitHub Actions** (push to `main` in Log4OM2-API / Log4OM2-Web → GHCR).  
This folder’s `docker-compose.yml` **only pulls** prebuilt images — no `build:` keys.

| Image | Registry |
|-------|----------|
| API | `ghcr.io/df3mt/log4om-api:latest` |
| Web | `ghcr.io/df3mt/log4om-web:latest` |

HTTP-only public site: **http://log4om.df3.mt.de**

## Layout

| Path | Role |
|------|------|
| `/srv/log4om/docker-compose.yml` | postgres + api + web + nginx (**pull only**) |
| `/srv/log4om/.env` | secrets (not in git) |
| `/srv/log4om/nginx/log4om.conf` | reverse proxy :80 |

## Install / update (on the server as root)

```bash
cd /srv/log4om
docker compose pull
docker compose up -d
```

Or from a checkout of this folder:

```bash
chmod +x install.sh
./install.sh   # copies files, generates .env once, then pull + up
```

## DNS

`log4om.df3.mt.de` A/AAAA → this host.

## HTTPS later

Terminate TLS at nginx/Caddy on 443; keep the same upstreams. Set `X-Forwarded-Proto https`.
