# Backend

This directory contains the Go backend in an `entity-first` hexagonal layout.

## File Responsibilities

- `cmd/api/main.go` - backend entry point, dependency wiring, and HTTP server startup.
- `cmd/openapi/` - config and script for generating the transport layer from `api/openapi.yaml`.
- `internal/artwork/` - domain logic for artworks, calendar data, tags, and artwork of the day.
- `internal/favorite/` - favorite artwork domain logic.
- `internal/settings/` - user settings.
- `internal/user/` - user context.
- `internal/auth/` - token issuing and auth responses without hardcoded token strings.
- `internal/adapters/http/` - HTTP adapter, DTO mapping, and routing.
- `internal/adapters/postgres/` - Postgres repositories.
- `internal/platform/config/` - environment config loading.
- `internal/platform/postgres/` - database connection and SQL migration runner.
- `migrations/` - database schema and the local administrator seed.
- `internal/catalog/` - validation and database synchronization for the local artwork catalog.
- `../local-data/artworks/` - ignored local catalog, source document, and images; mounted read-only by Docker Compose.
- `docker-compose.yml` - local backend + Postgres runtime.
- `Dockerfile` - backend container image.

## Configuration

Main environment variables:

- `APP_PORT` - API port.
- `WEB_DIR` - path to the desktop frontend that the backend can serve as the root site.
- `IMAGES_DIR` - path to local images.
- `ARTWORK_CATALOG_PATH` - path to the local JSON artwork catalog.
- `MIGRATIONS_DIR` - path to SQL migrations.
- `PUBLIC_BASE_URL` - public base URL of the application.
- `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_SSLMODE` - Postgres settings.
- `AUTH_TOKEN_TYPE`, `AUTH_ACCESS_TOKEN_TTL_SECONDS`, `AUTH_REFRESH_TOKEN_TTL_SECONDS` - token settings.
- `AUTH_JWT_SECRET` - a long, random secret required to sign JWTs. Set a unique value in every production deployment; the compose default is for local development only.

Authentication is stateful where it matters: passwords are stored as bcrypt hashes,
access tokens are signed JWTs, and refresh tokens are persisted and rotated in
Postgres so logout and token reuse can be revoked.

Daily artwork assignment is persisted per user and date. A unique user/artwork
constraint prevents repetitions, user collection settings limit future picks,
and a deterministic user-specific order gives accounts different sequences.
When a collection is exhausted, no duplicate history row is created; the last
assignment remains visible until a new catalog item is synchronized.

## Main Commands

Generate the transport layer:

```bash
./cmd/openapi/generate.sh
```

Run locally without Docker:

```bash
GOCACHE=/tmp/dailycanvas-go-build GOSUMDB=off go run ./cmd/api
```

Run tests:

```bash
GOCACHE=/tmp/dailycanvas-go-build GOSUMDB=off go test ./...
```

Start the backend and Postgres in Docker:

```bash
docker compose up --build
```

The local seed contains one administrator account for development only:
`user@example.com` / `Admin123!`. Replace or remove it and set a unique
`AUTH_JWT_SECRET` before any public deployment. New accounts created through
the registration endpoint are stored in Postgres.
