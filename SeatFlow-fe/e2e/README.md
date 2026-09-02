# SeatFlow Playwright E2E

These tests exercise the SF-046 browser flows against the real frontend and backend.

## Prerequisites

Start infrastructure and the backend with the local profile:

```sh
docker compose up -d postgres redis kafka
cd SeatFlow-be
.\mvnw.cmd -Dspring-boot.run.profiles=local spring-boot:run
```

Enable the local admin seed in the root `.env` used by the backend:

```properties
SEATFLOW_LOCAL_ADMIN_ENABLED=true
SEATFLOW_LOCAL_ADMIN_EMAIL=admin@example.com
SEATFLOW_LOCAL_ADMIN_PASSWORD=ChangeMeStrong123!
```

The Playwright helpers read these admin values from process environment first, then from the root `.env`.

## Run

```sh
cd SeatFlow-fe
npm run test:e2e
```

Playwright starts Vite unless `E2E_START_FRONTEND=false` is set. Override defaults as needed:

```properties
E2E_BASE_URL=http://localhost:5173
E2E_API_BASE_URL=http://localhost:8080/api/v1
E2E_SEED_NAMESPACE=sf046
E2E_ADMIN_EMAIL=admin@example.com
E2E_ADMIN_PASSWORD=ChangeMeStrong123!
```

The seed namespace keeps test data deterministic. Use a different namespace for isolated CI runs. Generated test users use deterministic per-test passwords by default; set `E2E_USER_PASSWORD` only when a fixed override is required.

## Seed Cleanup

Every seeded venue, event and account is named after the seed namespace, so a run can delete
exactly its own rows. `globalSetup` purges leftovers from an interrupted earlier run and
`globalTeardown` purges what this run created, both regardless of whether the tests passed.

The API has no delete endpoint for events or users, so the purge runs SQL directly: first
`psql` on `PATH` using the root `.env` connection values, then `docker exec` into the compose
container. A purge that cannot reach either prints a warning with both failures and leaves the
run's result untouched - the tests already ran, and the rows are still there to inspect.

```properties
E2E_DB_HOST=localhost
E2E_DB_CONTAINER=seatflow-postgres
E2E_SKIP_CLEANUP=false
```

Set `E2E_SKIP_CLEANUP=true` to keep the seeded rows for debugging.

Failure artifacts are retained in ignored folders:

- `SeatFlow-fe/test-results/`
- `SeatFlow-fe/playwright-report/`
