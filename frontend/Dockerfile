FROM docker.io/library/node:24-alpine AS builder

RUN corepack enable && corepack prepare pnpm@11 --activate

WORKDIR /app

ARG COMMIT_HASH
ARG VERSION
ARG VITE_SENTRY_DSN

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile

COPY . .
RUN --mount=type=secret,id=sentry_auth_token \
    SENTRY_AUTH_TOKEN=$(cat /run/secrets/sentry_auth_token 2>/dev/null || true) \
    pnpm run build

RUN find dist -type f \( -name '*.js' -o -name '*.css' -o -name '*.html' -o -name '*.svg' -o -name '*.json' -o -name '*.xml' -o -name '*.txt' -o -name '*.webmanifest' \) \
     -exec gzip -k -9 {} \; \
     -exec brotli -k -9 {} \;

RUN echo "{\"commit\":\"${COMMIT_HASH}\",\"version\":\"${VERSION}\"}" > /app/dist/commit.json

FROM docker.io/library/caddy:2-alpine

COPY Caddyfile /etc/caddy/Caddyfile
COPY --from=builder /app/dist /srv
