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

The seed namespace keeps test data deterministic. Use a different namespace for isolated CI runs.

Failure artifacts are retained in ignored folders:

- `SeatFlow-fe/test-results/`
- `SeatFlow-fe/playwright-report/`
