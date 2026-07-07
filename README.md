# DIV/ZERO

A minimal competitive programming platform. Submit C++ code, get a verdict. No bloat.

---

## Stack

- **Backend** - Java 17, Spring Boot, Spring Security, Spring Data JPA, Flyway, PostgreSQL, Kafka, Redis
- **Frontend** - React + Vite, Tailwind CSS, Monaco Editor, React Router

---

## Structure

```text
DivZero/
+-- api/        # Spring Boot HTTP server
+-- client/     # React frontend
```

---

## Running It Locally

**1. Database**

Set up a PostgreSQL database and add a `.env` file inside `api/`:

```env
DATABASE_URL=jdbc:postgresql://localhost:5432/divzero
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=postgres
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
REDIS_HOST=localhost
REDIS_PORT=6379
JWT_SECRET=something-secret
FRONTEND_URL=http://localhost:5173
```

Flyway runs migrations automatically when the API starts.

**2. API**

```bash
cd api
mvn spring-boot:run
```

Runs on `http://localhost:3000`.

**3. Client**

```bash
cd client
npm install
npm run dev
```

Runs on `http://localhost:5173`.

---

## Docker Compose

From the repository root:

```bash
docker compose up --build
```

This starts PostgreSQL, Kafka, Redis, and the Spring Boot API. The API uses Kafka for submission events, Redis for cached reads, and Docker to sandbox C++ submissions.

---

## API Endpoints

| Method | Route | Auth | Description |
| --- | --- | --- | --- |
| POST | `/api/signup` | - | Register |
| POST | `/api/signin` | - | Login, returns JWT |
| GET | `/api/view-problems` | User | List all problems |
| GET | `/api/view-tests?problemId=` | User | Get tests for a problem |
| POST | `/api/submit-code` | User | Compile and judge C++ code |
| POST | `/api/create-problem` | Admin | Create a problem |
| POST | `/api/create-test` | Admin | Add a test case |
| GET | `/api/view-users` | Admin | List users |

Auth is a JWT passed as the `Authorization` header, with no `Bearer` prefix required.

---

## How Judging Works

Submitted code is persisted as `QUEUED`, published as a Kafka `SubmissionCreated` event, judged by the worker, then completed through a Kafka `SubmissionCompleted` event. The REST endpoint still waits for that completion so the React frontend receives the same verdict response shape.

The worker writes code to a temporary workspace, compiles in Docker with `g++`, then executes in Docker against every test case with network disabled, memory and CPU limits, and a timeout.

Verdicts are **Accepted**, **Wrong Answer**, **Compilation Error**, **Runtime Error**, and **Time Limit Exceeded**.

You need Docker installed and accessible to the API process for submissions to work.

---

## Roles

Accounts are `USER` by default. To make an account an admin, update the `role` column in the database directly:

```sql
UPDATE "User" SET role = 'ADMIN' WHERE username = 'yourname';
```

Admins get access to the `/admin` dashboard to create problems and test cases.
