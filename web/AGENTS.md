# AGENTS.md — Log4OM2-Web

Conventions for AI maintenance of this portal.

## Stack

- Next.js 15 App Router, TypeScript, Tailwind CSS 4
- No sticky sessions; JWT in `localStorage` via `src/lib/auth-storage.ts`
- API client: `src/lib/api.ts` (refresh-on-401, `cache: 'no-store'`)
- Brand tokens (sand/night/signal) live in `src/app/globals.css` — match Android marketing site

## Do

- Keep feature parity with Android logbook/settings (except GPS reference sync)
- Prefer small client components under `src/app/(app)/…`
- Extend `src/lib/types.ts` + `api.ts` when API DTOs change
- Add DE/EN strings in `src/lib/i18n.ts` together
- Keep health at `/api/health` for HAProxy

## Don't

- Add server-side HTTP sessions or sticky-session assumptions
- Cache authenticated fetches (`force-cache`, route cache for user data)
- Put secrets in `NEXT_PUBLIC_*` env vars
- Introduce a second UI kit without need (custom components in `src/components/ui.tsx`)

## Commands

```bash
npm run dev
npm run lint
npm run build
```

## Related repos

- `Log4OM2-API` — Spring Boot backend
- `Log4OM2-Android` — mobile client (will move to this API)
