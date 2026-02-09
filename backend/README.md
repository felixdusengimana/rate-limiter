# API Rate Limiter

A distributed API rate limiter for a notification service (SMS/Email), built with **Java Spring Boot** and **Redis**. It supports per-client time-window limits, per-client monthly limits, and global system limits, with configurable hard/soft throttling.

## Features

- **Per-client time window**: e.g. 100 requests per minute
- **Per-client monthly**: e.g. 10,000 requests per month
- **Global limits**: cap total requests across the system (window or monthly)
- **Distributed**: uses Redis so limits are consistent across multiple API servers
- **Throttling**: hard (immediate 429) or soft (429 with `Retry-After` header, optional delay)
- **REST API** for managing clients and rate limit rules; protected notification endpoints

## Tech Stack

- **Backend**: Java 17, Spring Boot 3.4, Spring Data JPA, Spring Data Redis
- **Data**: PostgreSQL (clients & rules), Redis (rate limit counters)
- **Frontend** (optional): Angular — see `../frontend/` and instructions below

## Prerequisites

- JDK 17+
- Maven 3.9+
- Docker and Docker Compose (for PostgreSQL and Redis)

## Quick Start

### 1. Start infrastructure

```bash
docker compose -f docker/docker-compose.yml up -d
```

This starts:

- **PostgreSQL** on `localhost:5432` (database `ratelimiter`, user `rl_user`, password `rl_pass`)
- **Redis** on `localhost:6379`

### 2. Run the application

```bash
./mvnw spring-boot:run
```

The API is available at `http://localhost:8080`.

### 3. Create a client and limits

```bash
# Create a client (returns API key)
curl -s -X POST http://localhost:8080/api/clients \
  -H "Content-Type: application/json" \
  -d '{"name":"Acme Corp"}' | jq

# Example response: { "id": "...", "name": "Acme Corp", "apiKey": "rk_...", "active": true }
# Save the apiKey and client id for next steps.

# Add per-client window limit: 5 requests per 60 seconds
curl -s -X POST http://localhost:8080/api/limits \
  -H "Content-Type: application/json" \
  -d '{
    "limitType": "WINDOW",
    "limitValue": 5,
    "windowSeconds": 60,
    "clientId": "<CLIENT_ID>"
  }' | jq

# Add per-client monthly limit: 1000 per month
curl -s -X POST http://localhost:8080/api/limits \
  -H "Content-Type: application/json" \
  -d '{
    "limitType": "MONTHLY",
    "limitValue": 1000,
    "clientId": "<CLIENT_ID>"
  }' | jq

# (Optional) Global limit: 10000 requests per minute across all clients
curl -s -X POST http://localhost:8080/api/limits \
  -H "Content-Type: application/json" \
  -d '{
    "limitType": "GLOBAL",
    "limitValue": 10000,
    "globalWindowSeconds": 60
  }' | jq
```

### 4. Call the protected notification API

Use the client's `apiKey` in the `X-API-Key` header:

```bash
export API_KEY="rk_..."   # from step 3

# Send SMS (rate limited)
curl -s -X POST http://localhost:8080/api/notify/sms \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $API_KEY" \
  -d '{"recipient":"+250788123456","message":"Hello"}' | jq

# Send Email (rate limited)
curl -s -X POST http://localhost:8080/api/notify/email \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $API_KEY" \
  -d '{"recipient":"user@example.com","message":"Hello"}' | jq
```

When the limit is exceeded you get **429 Too Many Requests** with a `Retry-After` header and a JSON body.

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `rate-limiter.throttling` | `hard` | `hard` = immediate 429; `soft` = 429 with Retry-After (optional delay) |
| `rate-limiter.soft-delay-ms` | `0` | For soft throttling: delay in ms before returning 429 (0 = no delay) |

Database and Redis settings are in `src/main/resources/application.properties`.

## API Summary

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/clients` | Create client (body: `{"name":"..."}`) |
| GET | `/api/clients` | List clients |
| GET | `/api/clients/{id}` | Get client by ID |
| POST | `/api/limits` | Create rate limit rule (see DTO below) |
| GET | `/api/limits` | List all rules |
| GET | `/api/limits/client/{clientId}` | Rules for a client |
| POST | `/api/notify/sms` | Send SMS (requires `X-API-Key`) |
| POST | `/api/notify/email` | Send Email (requires `X-API-Key`) |

## Running tests

```bash
# Unit and integration tests (uses Docker Compose for Postgres + Redis when needed)
./mvnw test

# With coverage (JaCoCo)
./mvnw test jacoco:report
# Report: target/site/jacoco/index.html
```

Integration tests expect Docker to be running so that `spring-boot-docker-compose` can start Postgres and Redis.

## Design notes

- **Distributed rate limiting**: Counters are stored in Redis with keys like `rl:c:{clientId}:w:{bucket}` (window) and `rl:c:{clientId}:m:{yyyyMM}` (monthly). All API nodes share the same Redis, so limits are enforced across the cluster.
- **Atomicity**: Check-and-increment is done in a Lua script so each request is counted once and consistently.
- **Rollback**: If multiple rules apply (e.g. window + monthly), we only count the request when all pass; on the first failure we roll back any increments already made for that request.
- **Throttling**: Hard = reject immediately with 429. Soft = same 429 and `Retry-After`, with an optional server-side delay to reduce burst retries.

## Frontend (Angular)

The Angular app lives in the parent folder: `../frontend/`. To run it:

```bash
cd ../frontend
npm install
npm start
```

Open `http://localhost:4200`. Set the API base URL in `frontend/src/app/environments/environment.ts` if needed (default: `http://localhost:8080`).

## License

Demo / assignment project.
