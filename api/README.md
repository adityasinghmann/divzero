# DIV/ZERO API

Spring Boot 3.3 backend for DIV/ZERO.

## Requirements

- Java 17
- Maven 3.9+
- PostgreSQL, Kafka, Redis
- Docker, used by the judge sandbox **and** by the Testcontainers-based tests

## Run Locally

`JWT_SECRET` is mandatory - see [Environment variables](#environment-variables).

```bash
JWT_SECRET=replace-with-a-random-32-plus-char-value mvn spring-boot:run
```

The API runs on `http://localhost:3000`. Flyway applies migrations at startup;
`spring.jpa.hibernate.ddl-auto` is `validate`, so every entity field must have a matching
column or the application will not start.

## Docker Compose

From the repository root:

```bash
docker compose up --build
```

Compose starts PostgreSQL, Kafka, Redis, the public API, a separate judge worker and the client.
Only the judge container mounts the host Docker socket (to run `docker run` for C++ submissions);
the public API runs with `JUDGE_WORKER_ENABLED=false` and has no socket.

## Endpoints

**14 routes.** Auth is a JWT in the `Authorization` header; a `Bearer ` prefix is accepted but
not required. "Public" means the route is in the `permitAll` list in `SecurityConfig`; "User"
means any authenticated caller; "Admin" is enforced by `@PreAuthorize("hasRole('ADMIN')")`.

| Method | Route | Auth | Description |
| --- | --- | --- | --- |
| POST | `/api/signup` | Public | Register. 201 on success, 409 if the username is taken |
| POST | `/api/signin` | Public | Log in. 200 with a JWT, 401 on bad credentials |
| GET | `/api/health` | Public | Health check |
| GET | `/api/leaderboard` | Public | Global ranking |
| GET | `/api/view-leaderboard` | User | Same handler as `/api/leaderboard`, but this path is not in the public list |
| GET | `/api/view-problems` | User | List all problems |
| GET | `/api/view-problem?problemId=` | User | One problem plus its test cases (samples only unless admin) |
| GET | `/api/view-tests?problemId=` | User | Test cases for a problem (samples only unless admin) |
| POST | `/api/submit-code` | User | Compile and judge C++. 200 with a verdict, 202 if still judging, 429 if rate-limited, 500 on an internal failure |
| GET | `/api/view-submissions?problemId=` | User | The caller's own submissions |
| POST | `/api/create-problem` | Admin | Create a problem |
| POST | `/api/create-test` | Admin | Add a test case |
| GET | `/api/view-users` | Admin | List users |
| GET | `/api/me` | User | The caller's own profile and role |

`POST /api/submit-code` accepts only `code` and `problemId`; the owner is the authenticated
principal. `GET /api/view-submissions` ignores any `userId` parameter and always scopes results
to the caller.

### Status codes

| Situation | Status |
| --- | --- |
| Unauthenticated request to a protected route | 401, JSON body |
| Authenticated but lacking `ROLE_ADMIN` | 403, JSON body |
| Bean-validation failure | 400 with per-field messages under `errors` |
| Signup with a taken username | 409 |
| Signup with a username outside 3-32 of `[A-Za-z0-9_.-]`, or a password outside 8-72 characters | 400 with per-field messages |
| Wrong HTTP method / non-JSON body | 405 / 415, JSON body (never a 500) |
| Submission to an unknown problem / a problem with no test cases | 404 / 409 |
| Test case for an unknown problem | 404 |
| Signin with an unknown username or a wrong password | 401, identical body for both (no username enumeration) |
| Submission rate limit exceeded | 429 with a `Retry-After` header |
| Judge pipeline failure | 500, with the submission persisted as `INTERNAL_ERROR` |

No response body ever contains a raw exception string.

## Environment variables

All 43 variables read by `src/main/resources/application.yml`, with their defaults.

### Server, datastores

| Variable | Default |
| --- | --- |
| `PORT` | `3000` |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/divzero` |
| `DATABASE_USERNAME` | `postgres` |
| `DATABASE_PASSWORD` | `postgres` |
| `REDIS_HOST` | `localhost` |
| `REDIS_PORT` | `6379` |
| `REDIS_PASSWORD` | *(empty)* |
| `REDIS_TIMEOUT_MS` | `2000` |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |

### Auth

| Variable | Default |
| --- | --- |
| `JWT_SECRET` | **none - required.** Startup fails if unset or shorter than 32 characters |
| `JWT_EXPIRATION_MS` | `604800000` (7 days) |
| `FRONTEND_URL` | `http://localhost:5173` - comma-separated list of allowed CORS origins |

### Judge sandbox

| Variable | Default |
| --- | --- |
| `DOCKER_COMMAND` | `docker` |
| `JUDGE_WORKER_ENABLED` | `true` - set `false` on API instances that must not run submissions or touch Docker |
| `JUDGE_DOCKER_IMAGE` | `frolvlad/alpine-gxx` |
| `JUDGE_WORKSPACE_ROOT` | *(empty - uses the system temp directory)* |
| `JUDGE_MEMORY` | `256m` - compile-time budget, and the fallback execution limit |
| `JUDGE_CPUS` | `0.5` |
| `JUDGE_EXECUTION_TIMEOUT_MS` | `10000` - fallback only; the problem's `timeLimitMs` wins |
| `JUDGE_COMPILE_TIMEOUT_MS` | `120000` |
| `JUDGE_OUTPUT_LIMIT_BYTES` | `1048576` |
| `JUDGE_VOLUME_LABEL` | *(empty)* - appended to the `/code` bind mount, e.g. `z` for SELinux |
| `JUDGE_PIDS_LIMIT` | `64` |
| `JUDGE_READ_ONLY_ROOTFS` | `true` |
| `JUDGE_RUN_AS_USER` | `65534:65534` |
| `JUDGE_TMPFS_SIZE_BYTES` | `67108864` (64 MB) |

### Kafka

| Variable | Default |
| --- | --- |
| `KAFKA_JUDGE_GROUP_ID` | `divzero-judge-workers` |
| `KAFKA_COMPLETION_GROUP_ID` | `divzero-submission-completion` - a random UUID is appended per instance |
| `KAFKA_DLT_GROUP_ID` | `divzero-dead-letter-handler` |
| `KAFKA_RETRY_MAX_ATTEMPTS` | `3` |
| `KAFKA_RETRY_BACKOFF_MS` | `2000` |
| `KAFKA_PUBLISH_TIMEOUT_MS` | `10000` |
| `SUBMISSION_RESULT_WAIT_TIMEOUT_MS` | `5000` - past this the caller gets 202 and polls |
| `KAFKA_TOPIC_PARTITIONS` | `3` |
| `KAFKA_TOPIC_REPLICAS` | `1` |
| `KAFKA_TOPIC_SUBMISSION_CREATED` | `divzero.submission.created` |
| `KAFKA_TOPIC_SUBMISSION_COMPLETED` | `divzero.submission.completed` |
| `KAFKA_TOPIC_SUBMISSION_CREATED_DLT` | `divzero.submission.created.DLT` |
| `KAFKA_TOPIC_SUBMISSION_COMPLETED_DLT` | `divzero.submission.completed.DLT` |

### Rate limiting and caching

| Variable | Default |
| --- | --- |
| `RATE_LIMIT_SUBMISSIONS_PER_WINDOW` | `10` |
| `RATE_LIMIT_WINDOW_SECONDS` | `60` |
| `CACHE_PROBLEM_LIST_TTL_SECONDS` | `300` |
| `CACHE_PROBLEM_DETAILS_TTL_SECONDS` | `300` |
| `CACHE_LEADERBOARD_TTL_SECONDS` | `120` |

## Migrations

Flyway, in `src/main/resources/db/migration`. Applied migrations are immutable - add a new
versioned file rather than editing one.

| Version | Contents |
| --- | --- |
| `V1__init_schema.sql` | `User`, `Problem`, `Submission`, `Test` and the `Roles` enum |
| `V2__submission_lifecycle_metadata.sql` | Submission status, timings, language, judge logs |
| `V3__submission_indexes.sql` | Indexes for submission history and the leaderboard aggregate |
| `V4__problem_limits.sql` | Per-problem `timeLimitMs`, `memoryLimitMb`, `difficulty` |

Column names are quoted camelCase (`"userId"`, `"problemId"`, `"executionTimeMs"`) and must be
matched exactly in `@Column(name = "\`...\`")`, or `ddl-auto: validate` fails at startup.

## Tests

```bash
mvn test
```

| Suite | Type | Covers |
| --- | --- | --- |
| `VerdictServiceTest` | Plain JUnit | Compilation error, wrong answer on the second of three tests, TLE, runtime error, all-passing, and output normalisation |
| `JwtServiceTest` | Plain JUnit | Token round-trip, foreign secret, expiry, tampering, every `extractToken` header shape |
| `SubmissionRepositoryTest` | `@DataJpaTest` + Postgres Testcontainer | The distinct-problem scoring rule and the best-run-per-problem tiebreaker |
| `SubmissionPipelineIntegrationTest` | `@SpringBootTest` + Postgres and Kafka Testcontainers | A full Kafka round trip with `JudgeService` mocked, plus the duplicate-completion idempotency guarantee |
| `SecurityRulesTest` | `@SpringBootTest` + MockMvc | 401 unauthenticated, 403 for a USER token on admin routes, public routes |

Postgres uses `@ServiceConnection`. Kafka does not: Spring Boot 3.3 only ships connection-details
factories for the older Confluent-based `KafkaContainer`, and support for
`org.testcontainers.kafka.KafkaContainer` arrives in Boot 3.4, so the pipeline test registers
`spring.kafka.bootstrap-servers` through `@DynamicPropertySource` in order to keep using
`apache/kafka:3.7.0` - the same broker image compose runs.

**Docker API version.** The surefire configuration pins `api.version` to `1.41`. docker-java,
which Testcontainers uses internally, otherwise talks to the daemon at API v1.32, and Docker
Engine 29 rejects anything below v1.40 - `/v1.32/info` answers 400 and Testcontainers reports
"Could not find a valid Docker environment" even though Docker is running. Override with
`-Ddocker.api.version=` if a daemon needs something else.
