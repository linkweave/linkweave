# LinkWeave

A self-hosted bookmark manager for organizing web resources with folders, tags, and shared collections.

Learn more at [about.linkweave.dev](https://about.linkweave.dev).

## Features

- **Bookmarks** — Save, edit, and delete bookmarks with URLs, titles, and descriptions
- **Folders** — Organize bookmarks into a nested folder hierarchy
- **Tags** — Label bookmarks with custom tags and filter by them
- **Collections** — Group bookmarks into separate collections; share collections with other users


## Getting Started

### Backend (Quarkus)

```shell
cd api && ./mvnw quarkus:dev
```

The API runs at `http://localhost:8080`. Dev UI is available at `http://localhost:8080/q/dev/`.

### Frontend (Vue.js + Vite)

```shell
cd frontend && npm install && npm run dev
```

### Local HTTPS Certificates

The project uses `mkcert` for locally-trusted TLS certificates. Install it from [mkcert](https://github.com/FiloSottile/mkcert), then run:

```shell
mkcert -install
./scripts/certs/generate-keypair.sh
```

Certs are output to `developer-local-settings/config/certs/`.

## Tech Stack

| Layer     | Technology                         |
|-----------|-------------------------------------|
| Backend   | Quarkus, Hibernate ORM, JAX-RS     |
| Frontend  | Vue.js, Pinia, Tailwind CSS, shadcn/vue |
| Database  | SQLite                             |
| Migrations | Flyway                            |

## License

This project is licensed under the [Business Source License 1.1](LICENSE) (BSL), converting to AGPL-3.0 on 2030-01-01.
