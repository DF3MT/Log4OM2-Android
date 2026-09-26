# Agent notes (Log4OM2-API)

## Stack
- Kotlin + Spring Boot 3.4, Java 21
- JPA + Flyway for platform Postgres
- JDBC/Hikari per-tenant for Log4OM MySQL
- JJWT, BCrypt, AES-GCM (`SecretBox`)

## Conventions
- Package root: `com.log4om.api`
- Never store tenant DB passwords in plaintext — always `SecretBox`
- Never add sticky-session requirements
- Keep `NoStoreCacheFilter` on `/auth /me /qsos /adif /stats /lookup`
- Award refs live in MySQL column `contactreferences` JSON (`ContactReferencesJson`)

## Commands
```bash
./gradlew test
./gradlew bootRun
docker compose -f docker-compose.dev.yml up --build
```

## Do not
- Enable HAProxy/browser caching for authenticated responses
- Put QRZ/Club Log secrets in client apps (store via `/me/lookup-credentials`)
