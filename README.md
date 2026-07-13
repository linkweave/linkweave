# LinkWeave

A self-hosted bookmark manager for organizing web resources with folders, tags, and shared collections.

Learn more at [about.linkweave.dev](https://about.linkweave.dev).

## Features

Nested folder hierarchies, color-coded tags, shared collections with role-based access, a search operator language with saved smart collections, typed bookmark properties, local AI tag suggestions, full offline mode, screenshot previews, browser import with review, a Chrome/Firefox extension, and a REST API with personal API keys.

See the full feature overview at [about.linkweave.dev](https://about.linkweave.dev).

## Getting Started

### Local HTTPS Certificates

Dev mode serves HTTPS only, so generate locally-trusted certificates before starting anything. Install [mkcert](https://github.com/FiloSottile/mkcert), then run:

```shell
mkcert -install
./scripts/certs/generate-keypair.sh
```

Certs are output to `developer-local-settings/config/certs/`.

### Backend (Quarkus)

```shell
cd api && ./mvnw quarkus:dev
```

The API runs at `https://localhost:8443`. Dev UI is available at `https://localhost:8443/q/dev/`.

### Frontend (Vue.js + Vite)

```shell
cd frontend && pnpm install && pnpm run dev
```

The app is served at `https://local-linkweave.localhost:5173`.

## Deployment

Release images are published to [GHCR](https://github.com/orgs/linkweave/packages) on every `v*` tag. The whole stack is two containers and one SQLite file:

```yaml
# compose.yaml
services:
  api:
    image: ghcr.io/linkweave/linkweave-api:latest
    environment:
      LINKWEAVE_DB_PATH: /data/linkweave.db
    volumes:
      - linkweave-data:/data

  frontend:
    image: ghcr.io/linkweave/linkweave-frontend:latest
    ports:
      - "8080:80"

volumes:
  linkweave-data:
```

```shell
docker compose up -d
```

Then open <http://localhost:8080> and register your first account. Back up your entire library by copying the `linkweave.db` file.

Optional: add `ghcr.io/linkweave/linkweave-screenshot-service` for bookmark screenshot previews (point the API at it via `LINKWEAVE_SCREENSHOT_SERVICE_URL`) and an [Ollama](https://ollama.com) container for local AI tag suggestions.

## Tech Stack

| Layer       | Technology                              |
|-------------|------------------------------------------|
| Backend     | Quarkus (Java 25), Hibernate ORM, JAX-RS |
| Frontend    | Vue.js, Pinia, Tailwind CSS, shadcn/vue  |
| Database    | SQLite (WAL mode)                        |
| Migrations  | Flyway                                   |
| Screenshots | Playwright sidecar (Node.js)             |
| Build       | Maven, pnpm                              |

## License

This project is licensed under the [Business Source License 1.1](LICENSE) (BSL), converting to AGPL-3.0 on 2030-01-01.
