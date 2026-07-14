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
|-- docker-compose.yml PostgreSQL, Redis, Kafka, Kafka UI
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
docker compose up -d postgres redis
```

Start Kafka only when testing event pipelines:

```sh
docker compose up -d kafka kafka-ui
```

Local service ports:

| Service    | URL                     |
| ---------- | ----------------------- |
| Backend    | `http://localhost:8080` |
| Frontend   | `http://localhost:5173` |
| PostgreSQL | `localhost:5432`        |
| Redis      | `localhost:6379`        |
| Kafka      | `localhost:9092`        |
| Kafka UI   | `http://localhost:8081` |

### Configure Environment

```sh
cp .env.example .env
```

### Run Backend

Windows:

```sh
cd SeatFlow-be
.\mvnw.cmd spring-boot:run
```

macOS/Linux:

```sh
cd SeatFlow-be
./mvnw spring-boot:run
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

- `DATASOURCE_*` — PostgreSQL connection
- `SEATFLOW_REDIS_*` — Redis connection
- `SEATFLOW_JWT_*` — JWT signing settings
- `SEATFLOW_KAFKA_ENABLED` — optional Kafka (off by default)

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
