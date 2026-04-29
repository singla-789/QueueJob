# QueueJob Platform

> A distributed job queue platform built with Spring Boot microservices, Apache Kafka, and PostgreSQL — featuring priority-based routing, exponential backoff retries, dead-letter handling, and email notifications.

---

## Architecture

```
                                    ┌─────────────────────────────────────────────────────┐
                                    │                   INFRASTRUCTURE                    │
                                    │                                                     │
 ┌──────────┐   ┌──────────────┐    │   ┌─────────────┐        ┌───────────────────────┐  │
 │          │   │              │    │   │             │        │       Kafka Topics      │  │
 │  Client  ├──►│  API Gateway ├────┼──►│ job-service ├───────►│  jobs.high-priority    │  │
 │          │   │   :8080      │    │   │   :8081     │        │  jobs.medium-priority  │  │
 └──────────┘   │              │    │   │             │        │  jobs.low-priority     │  │
                │ • Rate Limit │    │   │ • REST API  │        │  jobs.completed        │  │
                │ • API Key    │    │   │ • Flyway    │        │  jobs.dead-letter      │  │
                │ • Logging    │    │   │ • Audit Log │        └───────────┬─────────────┘  │
                │ • CORS       │    │   └──────┬──────┘                    │                │
                └──────────────┘    │          │                           │                │
                                    │          │ status                    │ consume        │
                                    │          │ updates                   ▼                │
                                    │          │               ┌──────────────────┐         │
                                    │          │               │                  │         │
                                    │          └───────────────│  worker-service  │         │
                                    │                          │     :8082        │         │
                                    │   ┌──────────────┐       │                  │         │
                                    │   │              │       │ • Job Processing │         │
                                    │   │  PostgreSQL  │◄──────│ • Retry Logic    │         │
                                    │   │    :5432     │       │ • Exp. Backoff   │         │
                                    │   │              │       │ • Dead-Letter    │         │
                                    │   └──────────────┘       └────────┬─────────┘         │
                                    │                                   │                   │
                                    │   ┌──────────────┐                │ jobs.completed    │
                                    │   │              │                ▼                   │
                                    │   │    Redis     │   ┌────────────────────────┐       │
                                    │   │    :6379     │   │                        │       │
                                    │   │              │   │ notification-service   │       │
                                    │   └──────────────┘   │       :8083           │       │
                                    │                      │                        │       │
                                    │   ┌──────────────┐   │ • Kafka Consumer       │       │
                                    │   │  Zookeeper   │   │ • Thymeleaf Email      │       │
                                    │   │    :2181     │   │ • Spring Mail (SMTP)   │       │
                                    │   └──────────────┘   └────────────┬───────────┘       │
                                    │                                   │                   │
                                    └───────────────────────────────────┼───────────────────┘
                                                                        │
                                                                        ▼
                                                                  ┌───────────┐
                                                                  │   SMTP    │
                                                                  │  Server   │
                                                                  └───────────┘
```

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| **Language** | Java 21 |
| **Framework** | Spring Boot 3.4.5, Spring Cloud 2024.0.1 |
| **API Gateway** | Spring Cloud Gateway (reactive / Netty) |
| **Messaging** | Apache Kafka 7.7.1 (Zookeeper mode) |
| **Database** | PostgreSQL 17 |
| **Cache / Rate Limit** | Redis 7 |
| **Migrations** | Flyway |
| **Email** | Spring Mail + Thymeleaf templates |
| **Build** | Maven (multi-module) |
| **Containers** | Docker, Docker Compose |
| **Runtime** | Eclipse Temurin JDK/JRE 21 Alpine |

---

## Features

- **Priority-based job queuing** — jobs are routed to `HIGH`, `MEDIUM`, or `LOW` priority Kafka topics with dedicated consumer thread pools
- **Exponential backoff retries** — failed jobs retry with `2^retryCount` second delays (capped at 60s)
- **Dead-letter queue** — jobs exceeding max retries are routed to `jobs.dead-letter` and persisted with full error + stack trace
- **Email notifications** — HTML emails sent via SMTP on job completion using Thymeleaf templates
- **API Gateway** — centralized entry point with route-based proxying
- **Redis rate limiting** — 10 req/s (burst 20) on job submission endpoints
- **API key authentication** — validates `X-API-Key` against Redis, returns 401 if invalid
- **Request logging** — method, URI, client IP, response status, and duration logged per request
- **Redis caching** — job responses cached for fast reads
- **Rate limiting per job type** — max 100 jobs/min per type
- **Job audit trail** — every status change logged to `job_audit_logs` table
- **Job scheduling** — submit jobs with a future `scheduledAt` timestamp
- **CORS support** — configurable cross-origin access for frontend clients
- **Health checks** — all services expose `/actuator/health` for Docker orchestration

---

## Quick Start

### Prerequisites

- **Docker** & **Docker Compose** installed

### Run

```bash
docker-compose up --build
```

That's it. Docker builds all 4 services from source and starts the full stack:

| Service | URL |
|---------|-----|
| API Gateway | http://localhost:8080 |
| Job Service | http://localhost:8081 |
| Worker Service | http://localhost:8082 |
| Notification Service | http://localhost:8083 |

### Default API Keys

The gateway seeds these keys on startup for development:

```
queuejob-dev-key-001
queuejob-dev-key-002
queuejob-test-key-001
```

Include in every request: `X-API-Key: queuejob-dev-key-001`

---

## API Endpoints

All requests go through the API Gateway on port `8080`.

### Submit a Job

```
POST /api/jobs
```

**Request:**
```json
{
  "type": "EMAIL_CAMPAIGN",
  "payload": "{\"to\": \"user@example.com\", \"subject\": \"Welcome!\"}",
  "priority": "HIGH",
  "maxRetries": 3
}
```

**Response** `201 Created`:
```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "type": "EMAIL_CAMPAIGN",
  "payload": "{\"to\": \"user@example.com\", \"subject\": \"Welcome!\"}",
  "status": "QUEUED",
  "priority": "HIGH",
  "retryCount": 0,
  "maxRetries": 3,
  "createdAt": "2026-04-29T10:00:00",
  "scheduledAt": null,
  "completedAt": null,
  "errorMessage": null
}
```

**cURL:**
```bash
curl -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -H "X-API-Key: queuejob-dev-key-001" \
  -d '{
    "type": "EMAIL_CAMPAIGN",
    "payload": "{\"to\": \"user@example.com\"}",
    "priority": "HIGH",
    "maxRetries": 3
  }'
```

---

### Get Job by ID

```
GET /api/jobs/{id}
```

**Response** `200 OK`:
```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "type": "EMAIL_CAMPAIGN",
  "payload": "{\"to\": \"user@example.com\"}",
  "status": "COMPLETED",
  "priority": "HIGH",
  "retryCount": 0,
  "maxRetries": 3,
  "createdAt": "2026-04-29T10:00:00",
  "scheduledAt": null,
  "completedAt": "2026-04-29T10:00:05",
  "errorMessage": null
}
```

**cURL:**
```bash
curl http://localhost:8080/api/jobs/a1b2c3d4-e5f6-7890-abcd-ef1234567890 \
  -H "X-API-Key: queuejob-dev-key-001"
```

---

### List Jobs (with optional filters)

```
GET /api/jobs?status={status}&type={type}
```

| Parameter | Required | Values |
|-----------|:--------:|--------|
| `status` | No | `PENDING`, `SCHEDULED`, `QUEUED`, `PROCESSING`, `COMPLETED`, `FAILED`, `CANCELLED`, `RETRYING` |
| `type` | No | Any job type string |

**Response** `200 OK`:
```json
[
  {
    "id": "a1b2c3d4-...",
    "type": "EMAIL_CAMPAIGN",
    "status": "COMPLETED",
    "priority": "HIGH",
    "retryCount": 0,
    "maxRetries": 3,
    "createdAt": "2026-04-29T10:00:00",
    "completedAt": "2026-04-29T10:00:05"
  }
]
```

**cURL:**
```bash
# All failed jobs
curl "http://localhost:8080/api/jobs?status=FAILED" \
  -H "X-API-Key: queuejob-dev-key-001"

# All jobs of a specific type
curl "http://localhost:8080/api/jobs?type=EMAIL_CAMPAIGN" \
  -H "X-API-Key: queuejob-dev-key-001"
```

---

### Cancel a Job

```
DELETE /api/jobs/{id}
```

> Only `PENDING` or `SCHEDULED` jobs can be cancelled.

**Response** `200 OK`:
```json
{
  "id": "a1b2c3d4-...",
  "status": "CANCELLED",
  "completedAt": "2026-04-29T10:01:00"
}
```

**cURL:**
```bash
curl -X DELETE http://localhost:8080/api/jobs/a1b2c3d4-e5f6-7890-abcd-ef1234567890 \
  -H "X-API-Key: queuejob-dev-key-001"
```

---

### Update Job Status (internal — used by worker-service)

```
PATCH /api/jobs/{id}/status
```

**Request:**
```json
{
  "status": "COMPLETED",
  "errorMessage": null
}
```

**Response** `200 OK`:
```json
{
  "id": "a1b2c3d4-...",
  "status": "COMPLETED",
  "completedAt": "2026-04-29T10:00:05"
}
```

---

### Health Check

```
GET /actuator/health
```

**Response** `200 OK`:
```json
{
  "status": "UP"
}
```

---

## Job Lifecycle

```
PENDING → QUEUED → PROCESSING → COMPLETED
                              ↘ RETRYING → PROCESSING → ... (up to maxRetries)
                                                       ↘ FAILED → Dead-Letter Queue

PENDING / SCHEDULED → CANCELLED (via DELETE)
```

**Retry backoff schedule** (default `maxRetries=3`):

| Attempt | Backoff Delay |
|:-------:|:-------------:|
| 1 | 2 seconds |
| 2 | 4 seconds |
| 3 | 8 seconds |
| After 3 | → Dead-letter topic + `dead_letter_jobs` table |

---

## Project Structure

```
queueJob/
├── api-gateway/             # Spring Cloud Gateway — routing, rate limiting, auth
├── job-service/             # REST API, Kafka producer, PostgreSQL, Redis cache
├── worker-service/          # Kafka consumer, job processing, retry logic, DLQ
├── notification-service/    # Kafka consumer, email notifications (Thymeleaf + SMTP)
├── docker-compose.yml       # Full stack: Postgres, Redis, Zookeeper, Kafka + 4 services
└── pom.xml                  # Parent POM (multi-module Maven)
```

---

## Kafka Topics

| Topic | Producer | Consumer | Purpose |
|-------|----------|----------|---------|
| `jobs.high-priority` | job-service | worker-service | HIGH/CRITICAL priority jobs |
| `jobs.medium-priority` | job-service | worker-service | MEDIUM priority jobs |
| `jobs.low-priority` | job-service | worker-service | LOW priority jobs |
| `jobs.completed` | worker-service | notification-service | Triggers email notification |
| `jobs.dead-letter` | worker-service | worker-service | Failed jobs after max retries |

---

## Database Schema

| Table | Description |
|-------|-------------|
| `jobs` | All jobs with status, priority, retry count, timestamps |
| `job_audit_logs` | Status change history for every job |
| `dead_letter_jobs` | Failed jobs with error message and stack trace |

---

## Environment Variables

### Mail Configuration (notification-service)

| Variable | Default | Description |
|----------|---------|-------------|
| `MAIL_HOST` | `smtp.gmail.com` | SMTP server host |
| `MAIL_PORT` | `587` | SMTP server port |
| `MAIL_USERNAME` | — | SMTP username |
| `MAIL_PASSWORD` | — | SMTP app password |
| `MAIL_FROM` | `noreply@queuejob.com` | Sender address |
| `MAIL_TO_DEFAULT` | `admin@queuejob.com` | Default recipient |

---

## License

MIT
