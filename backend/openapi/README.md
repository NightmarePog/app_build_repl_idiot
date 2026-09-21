# OpenAPI

The backend is the source of truth. This directory contains documentation customizers,
API metadata, documentation tests, and the generated `openapi.json` contract.
Controllers, DTOs, and runtime validation remain in `backend/src/`.

The backend build compiles these sources with its tests. Documentation tooling and
Springdoc are not included in the production jar. The exporter starts the real backend
with the documentation configuration enabled and uses its HTTP API to produce the spec.

From `frontend/`, run:

```sh
pnpm api:generate
pnpm api:check
pnpm test:api
```

Generation updates `backend/openapi/openapi.json` and the frontend client in
`frontend/src/lib/api/generated/`. Commit both; do not edit either by hand.
Check mode generates into temporary files and fails if the committed files differ.

The exporter requires the backend test PostgreSQL database, defaulting to
`localhost:5433/app_test`. It shares the backend test profile and its
`TEST_DATABASE_URL`, `TEST_POSTGRES_USER`, and `TEST_POSTGRES_PASSWORD` overrides.
Live response fixtures and the raw spec are written to `backend/build/openapi/`.

To run the documentation checks or only export the raw contract from the repository root:

```sh
backend/gradlew -p backend check
backend/gradlew -p backend exportOpenApi
```

The existing backend build owns compilation, formatting, tests, and export. CI runs
these checks, verifies the generated contract and client, and checks for breaking changes.
