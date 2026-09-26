# syntax=docker/dockerfile:1

FROM node:22-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

FROM node:22-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
ENV NEXT_TELEMETRY_DISABLED=1
# Browser always uses same-origin /backend; no public API URL required.
ARG NEXT_PUBLIC_API_URL=
ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL
RUN npm run build

FROM node:22-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
ENV PORT=3000
ENV HOSTNAME=0.0.0.0
# Overridden at runtime by compose; default matches service name "api"
ENV API_URL=http://api:8080
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app appuser
COPY --from=build /app/public ./public
COPY --from=build --chown=appuser:app /app/.next/standalone ./
COPY --from=build --chown=appuser:app /app/.next/static ./.next/static
USER appuser
EXPOSE 3000
HEALTHCHECK --interval=30s --timeout=3s --start-period=20s \
  CMD wget -qO- http://127.0.0.1:3000/api/health | grep -q UP || exit 1
CMD ["node", "server.js"]
