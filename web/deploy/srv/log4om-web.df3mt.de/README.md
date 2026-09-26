# `/srv/log4om-web.df3mt.de`

Next.js web UI. Host port **8081**. Images from GHCR; no local build.

Needs the API stack (`/srv/log4om-api.df3mt.de`) running first (shared network `log4om`).

```bash
cd /srv/log4om-web.df3mt.de
./install.sh
```

| Service | Image / port |
|---------|----------------|
| web     | `ghcr.io/df3mt/log4om-web:latest` → `:8081` |

Reverse proxy tip: `log4om-web.df3mt.de` → `:8081`, `log4om-api.df3mt.de` → `:8080`.
