# SF-048 Load Testing

This folder contains the local k6 harness for Sprint SF-048. It is intended for measured portfolio results only; do not publish throughput or latency numbers unless they came from a saved run in `load-tests/results/`.

## Scenarios

- Event browsing: `GET /api/v1/events`
- Seat-map loading: `GET /api/v1/events/{eventId}/seats`
- Hot-seat competition: many users contend for one event seat
- Popular-event seat selection: users hold and release random seats
- Payment retries: users buy seats and repeat the same payment request with the same idempotency key

## Metrics

k6 writes these in `load-tests/results/sf048-summary.md` and `load-tests/results/sf048-summary.json`:

- Throughput
- p50, p95, and p99 latency
- HTTP error rate
- Unexpected flow error rate
- Hold conflicts
- Successful purchases
- In-flow duplicate booking count
- Payment duplicate submissions deduped

Run `load-tests/sql/verify_sf048.sql` after k6 and save the output in `load-tests/results/` for DB-side duplicate-sales and duplicate-successful-payment checks.

## Local Run

Start infrastructure and backend with load-test-friendly settings:

```powershell
docker compose up -d postgres redis kafka
$env:SEATFLOW_RATE_LIMIT_ENABLED = "false"
$env:SEATFLOW_OUTBOX_PUBLISHER_ENABLED = "false"
$env:SEATFLOW_KAFKA_ENABLED = "false"
cd SeatFlow-be
.\mvnw.cmd spring-boot:run
```

In a second shell from the repo root, reset Redis holds and seed deterministic data:

```powershell
docker compose exec redis redis-cli FLUSHDB
Get-Content .\load-tests\sql\seed_sf048.sql | docker compose exec -T postgres psql -U seatflow -d seatflow
```

Run k6 with the native binary:

```powershell
k6 run .\load-tests\sf048.k6.js
```

Or run k6 through Docker:

```powershell
docker run --rm -i `
  -v ${PWD}:/work `
  -w /work `
  grafana/k6 run `
  -e BASE_URL=http://host.docker.internal:8080 `
  load-tests/sf048.k6.js
```

For the portfolio-sized local profile:

```powershell
k6 run -e PROFILE=portfolio -e USER_COUNT=80 .\load-tests\sf048.k6.js
```

Save DB verification output after each run:

```powershell
Get-Content .\load-tests\sql\verify_sf048.sql |
  docker compose exec -T postgres psql -U seatflow -d seatflow |
  Tee-Object .\load-tests\results\sf048-db-checks.txt
```

Acceptance requires `duplicate_sales = 0` and `payment_duplicate_count = 0` in the DB verification output.

## Before And After Optimization

The backend migration `V16__add_public_event_load_test_indexes.sql` adds:

- `events_published_start_name_id_idx` for published event browsing sorted by start/name/id
- `event_seats_available_event_price_idx` for available-seat minimum-price aggregation

To measure before/after locally:

1. Apply `load-tests/sql/optimization/drop_sf048_indexes.sql`.
2. Run the seed, k6 test, and verification commands. Save the generated files with a `baseline` suffix.
3. Apply `load-tests/sql/optimization/apply_sf048_indexes.sql`.
4. Re-run the same k6 profile and verification. Save the generated files with an `optimized` suffix.
5. Document the bottleneck and measured delta in `load-tests/results/sf048-run-notes.md`.

Do not use unmeasured estimates in a portfolio, resume, or CV. If the local machine, Docker, or k6 setup changes, re-run and store new results.

Apply the optimization scripts with:

```powershell
Get-Content .\load-tests\sql\optimization\drop_sf048_indexes.sql |
  docker compose exec -T postgres psql -U seatflow -d seatflow

Get-Content .\load-tests\sql\optimization\apply_sf048_indexes.sql |
  docker compose exec -T postgres psql -U seatflow -d seatflow
```
