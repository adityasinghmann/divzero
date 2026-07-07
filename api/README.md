# DIV/ZERO API

Spring Boot 3 backend for DIV/ZERO.

## Requirements

- Java 17
- Maven 3.9+
- PostgreSQL
- Docker, used by the judge sandbox

## Configuration

Environment variables are read by `src/main/resources/application.yml`.

```env
PORT=3000
DATABASE_URL=jdbc:postgresql://localhost:5432/divzero
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=postgres
FRONTEND_URL=http://localhost:5173
JWT_SECRET=test
DOCKER_COMMAND=docker
JUDGE_DOCKER_IMAGE=frolvlad/alpine-gxx
JUDGE_MEMORY=256m
JUDGE_CPUS=0.5
JUDGE_EXECUTION_TIMEOUT_MS=10000
JUDGE_COMPILE_TIMEOUT_MS=30000
```

Auth tokens are still passed as the raw JWT in the `Authorization` header.

## Run Locally

```bash
mvn spring-boot:run
```

The API runs on `http://localhost:3000`.

## Docker Compose

From the repository root:

```bash
docker compose up --build
```

Compose starts PostgreSQL and the API. The API container mounts the host Docker socket so the judge can run `docker run` for C++ submissions.

## Endpoints

| Method | Route | Description |
| --- | --- | --- |
| GET | `/api/health` | Health check |
| POST | `/api/signup` | Register |
| POST | `/api/signin` | Login |
| GET | `/api/view-problems` | List problems |
| GET | `/api/view-tests?problemId=` | List tests for a problem |
| POST | `/api/submit-code` | Compile and judge C++ |
| POST | `/api/create-problem` | Admin creates a problem |
| POST | `/api/create-test` | Admin creates a test case |
| GET | `/api/view-users` | Admin lists users |
