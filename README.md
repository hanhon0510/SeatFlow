# SeatFlow

SeatFlow is a full-stack seat reservation and booking platform scaffolded with the same tech stack and project conventions as CentralAuth.

## Contents

- [Repository Layout](#repository-layout)
- [Local Setup](#local-setup)
- [Configuration](#configuration)

## Repository Layout

```text
.
|-- SeatFlow-be/       Spring Boot 3.5 backend, Java 21, MyBatis, Flyway
|-- SeatFlow-fe/       React 19, TypeScript, Vite, Ant Design
|-- docker-compose.yml PostgreSQL, Redis, Kafka
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
- `KAFKA_PORT` — local Kafka port
- `KAFKA_BOOTSTRAP_SERVERS` — Kafka bootstrap servers for the backend
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
- `SEATFLOW_OUTBOX_PUBLISHER_ENABLED` — enables scheduled outbox publishing, default `false` except local profile
- `SEATFLOW_OUTBOX_PUBLISHER_BATCH_SIZE` — number of pending outbox records locked per publish loop
- `SEATFLOW_OUTBOX_PUBLISHER_RETRY_DELAY` — delay before retrying a failed outbox publish
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

Health endpoints:

- `GET /api/v1/health` — application health
- `GET /api/v1/health/database` — PostgreSQL health through MyBatis

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
