# DailyArt

OpenAPI specification: `api/openapi.yaml`

The project has three application areas:

- `backend/` - API, Postgres, migrations, and catalog synchronization.
- `frontend/` - desktop browser frontend.
- `android/` - native Android client built with Kotlin and Jetpack Compose.

Developer documentation is split by area:

- [backend/README.md](backend/README.md)
- [frontend/README.md](frontend/README.md)
- [android/README.md](android/README.md)

## Running The Project

1. Start the backend and Postgres:

```bash
cd backend
docker compose up --build
```

2. After startup, these endpoints are available:

- desktop frontend: `http://localhost:37117/`
- API: `http://localhost:37117/api/v1`
- Postgres: `localhost:37432`

3. For Android:

- open `android/` in Android Studio
- create `android/local.properties` from `android/local.properties.example`
- verify `dailycanvas.api.baseUrl`
- run the `app` configuration

## Working With The Project

- The API contract is changed in `api/openapi.yaml`.
- After changing OpenAPI, regenerate the backend transport layer:

```bash
./backend/cmd/openapi/generate.sh
```

- The only local source for artwork metadata is `local-data/artworks/catalog.json`.
- Artwork images live in `local-data/artworks/images/`; the source document is
  kept in `local-data/artworks/source/`. The whole `local-data/` directory is
  ignored by Git because it will be replaced with S3-backed storage.
- The API validates and synchronizes the catalog on every startup. Removing an
  item deactivates it without breaking existing user history.
- Daily history is personal: each user receives a stable assignment for the day,
  filtered by their Russian/world selection, and never receives the same artwork
  twice. Newly added catalog items automatically join the unseen pool.
- The desktop frontend reads runtime config from `frontend/config.js`.
- Android reads the backend URL from `local.properties` or the Gradle property `dailycanvas.api.baseUrl`.

## Local Commands

Run the backend without Docker:

```bash
cd backend
GOCACHE=/tmp/dailycanvas-go-build GOSUMDB=off go run ./cmd/api
```

Run backend tests:

```bash
cd backend
GOCACHE=/tmp/dailycanvas-go-build GOSUMDB=off go test ./...
```
