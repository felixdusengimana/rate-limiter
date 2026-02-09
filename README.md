# API Rate Limiter – Irembo Assignment

Distributed API rate limiter for a notification service (SMS/Email): **Java Spring Boot** backend and **Angular** frontend.

## Repository layout

- **`backend/`** – Backend (Spring Boot, PostgreSQL, Redis)
- **`frontend/`** – Angular UI for subscription plans, clients, global limits, and sending notifications

## Prerequisites

- **Backend:** JDK 17+, Maven 3.9+, Docker & Docker Compose
- **Frontend:** Node.js 20+ (e.g. via [nvm](https://github.com/nvm-sh/nvm))

## How to run

### 1. Start infrastructure (PostgreSQL + Redis)

From the repo root:

```bash
cd backend
docker compose -f docker/docker-compose.yml up -d
```

### 2. Run the backend

```bash
# still in backend/
./mvnw spring-boot:run
```

API base: **http://localhost:8080**

### 3. Run the frontend

In a new terminal:

```bash
cd frontend
npm install
npm start
```

Open **http://localhost:4200**. The app uses `http://localhost:8080` as the API URL (see `frontend/src/app/environments/environment.ts` to change it).

## How to test

### Backend tests

```bash
cd backend
./mvnw test
```

With coverage report:

```bash
./mvnw test jacoco:report
# open backend/target/site/jacoco/index.html
```

### Frontend tests

```bash
cd frontend
npm test
```

### Manual / API checks

- Create a client: `POST /api/clients` with `{"name":"Acme"}`
- Create a rate limit rule: `POST /api/limits` (see `backend/README.md` for body examples)
- Send a notification: `POST /api/notify/sms` or `POST /api/notify/email` with header `X-API-Key: <apiKey>`

When a limit is exceeded, the API returns **429 Too Many Requests** with a `Retry-After` header.

## Throttling behaviour

- **Clients (subscription limits):** **Hard** throttling – immediate 429 when over limit.
- **System (global limit):** **Soft** until 120% usage, then **hard**:
  - **80%** of global limit used: log warning + TODO notify system administrator (e.g. when email is added).
  - **100%**: log again, reject with 429 (soft: optional delay + Retry-After).
  - **120%** and above: switch to **hard** – no delay, immediate 429.

Configure in `backend/src/main/resources/application.properties`:  
`rate-limiter.throttling=soft` and `rate-limiter.soft-delay-ms=500` (for soft global throttling).

## More details

- Backend API, configuration, and design: **`backend/README.md`**
- Frontend setup and scripts: **`frontend/README.md`**
