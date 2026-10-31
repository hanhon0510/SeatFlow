# SeatFlow

[![CI](https://github.com/hanhon0510/SeatFlow/actions/workflows/ci.yml/badge.svg)](https://github.com/hanhon0510/SeatFlow/actions/workflows/ci.yml)

SeatFlow is a full-stack seat reservation and booking platform scaffolded with the same tech stack and project conventions as CentralAuth.

## Contents

- [Repository Layout](#repository-layout)
- [Local Setup](#local-setup)
- [Configuration](#configuration)
- [Continuous Integration](#continuous-integration)

## Repository Layout

```text
.
|-- SeatFlow-be/       Spring Boot 3.5 backend, Java 21, MyBatis, Flyway
|-- SeatFlow-fe/       React 19, TypeScript, Vite, Ant Design
|-- docker-compose.yml PostgreSQL, Redis, Kafka, Mailpit (optional)
|-- docs/              Project notes and generated documentation
`-- README.md
```

## Local Setup

### Prerequisites

- Java 21
- Node.js and npm
- Docker Desktop or Docker Engine

### Start Infrastructure

```sh
docker compose up -d postgres redis kafka
```

Local service ports:

| Service    | URL                     |
| ---------- | ----------------------- |
| Backend    | `http://localhost:8080` |
| Frontend   | `http://localhost:5173` |
| PostgreSQL | `localhost:5432`        |
| Redis      | `localhost:6379`        |
| Kafka      | `localhost:9092`        |

### Configure Environment

```sh
cp .env.example .env
```

Set all values in `.env` before starting PostgreSQL or the backend. The Spring application YAML does not define fallback values.

### Run Backend

Windows:

```sh
cd SeatFlow-be
.\mvnw.cmd -Dspring-boot.run.profiles=local spring-boot:run
```

macOS/Linux:

```sh
cd SeatFlow-be
./mvnw -Dspring-boot.run.profiles=local spring-boot:run
```

### Run Frontend

```sh
cd SeatFlow-fe
npm install
npm run dev
```

Vite proxies `/api` to `http://localhost:8080`.

## Configuration

Backend loads optional root `.env` via `spring.config.import`.

Key variables (see `.env.example`):

- `POSTGRES_DB` — PostgreSQL database name
- `POSTGRES_USER` — PostgreSQL user
- `POSTGRES_PASSWORD` — PostgreSQL password, stored only in local `.env`
- `POSTGRES_PORT` — local PostgreSQL port
- `POSTGRES_CONNECTION_TIMEOUT_MS` — PostgreSQL connection timeout
- `POSTGRES_VALIDATION_TIMEOUT_MS` — PostgreSQL validation timeout
- `KAFKA_PORT` — local Kafka port
- `KAFKA_BOOTSTRAP_SERVERS` — Kafka bootstrap servers for the backend
- `KAFKA_ADMIN_FAIL_FAST` — whether Kafka admin startup fails the app when brokers are unavailable
- `KAFKA_ADMIN_REQUEST_TIMEOUT_MS` — Kafka admin request timeout
- `KAFKA_ADMIN_DEFAULT_API_TIMEOUT_MS` — Kafka admin API timeout
- `KAFKA_CONSUMER_REQUEST_TIMEOUT_MS` — Kafka consumer request timeout
- `KAFKA_CONSUMER_DEFAULT_API_TIMEOUT_MS` — Kafka consumer API timeout
- `PORT` — backend HTTP port
- `SEATFLOW_JWT_SECRET` — JWT signing secret, at least 32 bytes
- `SEATFLOW_JWT_ISSUER` — JWT issuer
- `SEATFLOW_JWT_EXPIRES_IN_SECONDS` — access token lifetime
- `SEATFLOW_REFRESH_TOKEN_COOKIE_NAME` — refresh-token cookie name
- `SEATFLOW_REFRESH_TOKEN_EXPIRES_IN_SECONDS` — refresh token lifetime
- `SEATFLOW_REFRESH_TOKEN_COOKIE_SECURE` — whether refresh cookies require HTTPS
- `SEATFLOW_REFRESH_TOKEN_SAME_SITE` — refresh cookie SameSite policy
- `SEATFLOW_KAFKA_ENABLED` — enables backend Kafka infrastructure, default `false` except local profile
- `SEATFLOW_KAFKA_TOPIC_ORDER_EVENTS` — order event topic name
- `SEATFLOW_KAFKA_TOPIC_NOTIFICATION_EVENTS` — notification event topic name
- `SEATFLOW_KAFKA_TOPIC_DEAD_LETTER` — dead-letter topic name
- `SEATFLOW_KAFKA_GROUP_ORDER_EVENTS` — order event consumer group
- `SEATFLOW_KAFKA_GROUP_NOTIFICATION_EVENTS` — notification event consumer group
- `SEATFLOW_KAFKA_RETRY_MAX_ATTEMPTS` — max consumer delivery attempts before dead-lettering
- `SEATFLOW_KAFKA_RETRY_BACKOFF` — delay between consumer retry attempts
- `SEATFLOW_KAFKA_HEALTH_TIMEOUT` — timeout for Kafka readiness checks
- `SEATFLOW_OUTBOX_PUBLISHER_ENABLED` — enables scheduled outbox publishing, default `false` except local profile
- `SEATFLOW_OUTBOX_PUBLISHER_BATCH_SIZE` — number of pending outbox records locked per publish loop
- `SEATFLOW_OUTBOX_PUBLISHER_RETRY_DELAY` — delay before retrying a failed outbox publish
- `SEATFLOW_OUTBOX_PUBLISHER_RETRY_MAX_DELAY` — maximum backoff between failed outbox publish attempts
- `SEATFLOW_OUTBOX_PUBLISHER_TIMEOUT` — timeout while waiting for Kafka send acknowledgement
- `SEATFLOW_OUTBOX_PUBLISHER_FIXED_DELAY_MS` — scheduler delay between outbox publish loops
- `SEATFLOW_LOCAL_ADMIN_ENABLED` — opt-in local profile admin seeding, default `false`
- `SEATFLOW_LOCAL_ADMIN_EMAIL` — local admin seed email
- `SEATFLOW_LOCAL_ADMIN_PASSWORD` — local admin seed password, at least 12 characters

Local admin seeding runs only with the Spring `local` profile and only when
`SEATFLOW_LOCAL_ADMIN_ENABLED=true`. It creates the admin if the email does not
exist, leaves an existing admin unchanged, and refuses to promote an existing
non-admin account automatically.

### Kafka And Outbox

Paid purchases insert an `OrderPaid` row into `outbox_events` in the same
database transaction as the order, payment, ticket, and seat updates. The
scheduled publisher locks pending rows with `FOR UPDATE SKIP LOCKED`, publishes
to Kafka, marks successes as published, and keeps failed sends pending with a
future retry time.

Kafka consumers retry transient failures with the configured backoff. Permanent
validation errors and exhausted transient failures are published to the
dead-letter topic with safe metadata headers for original event id, topic,
partition, correlation id, and error category; stack traces are kept in logs.

### Email Notifications

Buyers get an email when an order is paid (their seats and total) and when it is
refunded, including the refunds that follow an event being cancelled. A consumer
in its own Kafka consumer group reads the order events topic, so a slow mail
server never delays the analytics consumer. A failed send is retried and then
dead-lettered; an event that was already emailed is skipped.

Sending is off by default and the app only logs which email it would have sent,
so no mail server is needed. To try it locally:

```sh
docker compose up -d mailpit
```

then set `SEATFLOW_MAIL_ENABLED=true` and `SEATFLOW_KAFKA_ENABLED=true`
(and `SEATFLOW_OUTBOX_PUBLISHER_ENABLED=true`) in `.env`. Mailpit catches every
message and shows it at `http://localhost:8025`. For a real provider, point
`SEATFLOW_MAIL_HOST`, `SEATFLOW_MAIL_PORT`, `SEATFLOW_MAIL_USERNAME`,
`SEATFLOW_MAIL_PASSWORD` (plus `SEATFLOW_MAIL_SMTP_AUTH` and
`SEATFLOW_MAIL_STARTTLS`) at any SMTP service and set `SEATFLOW_MAIL_FROM` and
`SEATFLOW_FRONTEND_BASE_URL` (used for links in the emails).

#### Password reset

"Forgot your password?" on the login page emails a single-use link that expires
after `SEATFLOW_PASSWORD_RESET_TTL` (default `1h`). Only a hash of the link's
token is stored. The request always answers the same way, so it cannot be used
to find out who has an account, and it is rate limited
(`SEATFLOW_RATE_LIMIT_PASSWORD_RESET_*`). Choosing a new password signs the
account out on every device and sends a "password changed" notice.

#### Event reminders

Buyers get one reminder email per order before the show. Admins choose how many
hours before the start (1 to 336) or turn reminders off per event, in the
"Reminder email" card on the event page; events nobody configured use
`SEATFLOW_REMINDERS_DEFAULT_HOURS_BEFORE` (default `24`). A background job sends
them every minute. It is off by default so orders are never marked as reminded
without a real email going out: set `SEATFLOW_REMINDERS_ENABLED=true` together
with `SEATFLOW_MAIL_ENABLED=true`. Orders placed after the reminder time are
not reminded again, since they were told everything when they bought.

#### Waitlist

When an event has no free seats (or fewer than a buyer wants), signed-in buyers
can join its waitlist from the event page and say how many seats they need. Seats
come back on their own, when a hold lapses or an order is refunded, so a
background job looks for them every 15 seconds. When enough are free for the
first buyer in line it sets those seats aside for them, shows them as held to
everyone else, and emails the buyer. They have `SEATFLOW_WAITLIST_OFFER_WINDOW`
(default `10m`) to hold the seats like any others; after that the seats go to the
next buyer. A buyer who needs more seats than are free keeps their place and is
passed over until enough come back. The job is off by default so offers are
never made without telling anyone: set `SEATFLOW_WAITLIST_ENABLED=true` together
with `SEATFLOW_MAIL_ENABLED=true`. Admins see the queue on the event page.

#### Sales Analytics

Admins open **Analytics** for sales across all events, or one, over a range of
UTC days (the last 30 days by default, at most 366). It shows gross, refunded and
net sales and the refund rate per currency, net sales per day, the top events,
how many started checkouts ended in a payment, and how many waitlist offers were
taken up. An order counts on the day it was placed, and a later refund shows as
refunded rather than moving the sale, so past days do not change. **Export CSV**
downloads one line per day, event and currency. Behind it:
`GET /api/v1/admin/analytics` and `GET /api/v1/admin/analytics/export`, both with
`from`, `to` (`YYYY-MM-DD`) and `eventId`, all optional.

Health endpoints:

- `GET /api/v1/health` — application health
- `GET /api/v1/health/live` — liveness, independent of dependency state
- `GET /api/v1/health/ready` — readiness for PostgreSQL, Redis, and enabled Kafka
- `GET /api/v1/health/database` — PostgreSQL health through MyBatis
- `GET /api/v1/health/redis` — Redis health through PING
- `GET /api/v1/health/kafka` — Kafka health when Kafka is enabled, otherwise `DISABLED`

## Continuous Integration

`.github/workflows/ci.yml` runs on every push to `main` and on every pull
request. Two jobs run in parallel:

- **Backend** — `./mvnw verify` in `SeatFlow-be`. The integration tests start
  PostgreSQL with Testcontainers and apply every Flyway migration to an empty
  database, so a migration that does not apply from scratch fails the build.
  The surefire reports are kept as an artifact when it fails.
- **Frontend** — `npm ci`, `npm run lint`, `npm run build` (type-checks first),
  then `npm test` in `SeatFlow-fe`.

No `.env` file or secrets are needed; the tests supply their own configuration.
To run the same checks locally:

```sh
cd SeatFlow-be && ./mvnw verify                      # needs Docker running
cd SeatFlow-fe && npm ci && npm run lint && npm run build && npm test
```

## Backend Conventions

- Java 21, Spring Boot 3.5
- Constructor injection, record DTOs, `@RestControllerAdvice`
- MyBatis mappers under `resources/mappers`
- Flyway migrations under `resources/db/migration`
- Stateless Spring Security with BCrypt

## Frontend Conventions

- Feature folders under `src/features`
- App shell under `src/app` (`providers`, `router`)
- Shared utilities under `src/shared`
- Vitest for unit tests, Playwright for e2e
