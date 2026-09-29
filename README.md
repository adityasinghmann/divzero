# DIV/ZERO

A minimal competitive programming platform. Submit C++ code, get a verdict. No bloat.

---

## Stack

- **Backend** - Java 17, Spring Boot 3.3, Spring Security, Spring Data JPA, Flyway, PostgreSQL, Kafka, Redis
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

**1. Infrastructure**

PostgreSQL, Kafka and Redis are all required. The quickest way to get them is:

```bash
docker compose up -d postgres kafka redis
```

**2. API**

`JWT_SECRET` is mandatory and has no default - the API refuses to start without one of at
least 32 characters. Every other variable has a default (see
[Environment variables](#environment-variables)).

```bash
cd api
JWT_SECRET=replace-with-a-random-32-plus-char-value mvn spring-boot:run
```

Runs on `http://localhost:3000`. Flyway applies migrations at startup.

**3. Client**

```bash
cd client
cp .env.example .env     # sets VITE_API_BASE
npm ci
npm run dev
```

Runs on `http://localhost:5173`.

---

## Docker Compose

From the repository root:

```bash
docker compose up --build
```

This starts PostgreSQL, Kafka, Redis, the public Spring Boot API (port 3000), a separate judge
worker built from the same image, and the React client served by nginx (port 5173). Only the
judge mounts the Docker socket; the API runs with `JUDGE_WORKER_ENABLED=false` and never
executes submissions.

The `JWT_SECRET` in `docker-compose.yml` is an obvious placeholder for local development only
and must be overridden in any real deployment.

`VITE_API_BASE` is passed to the client image as a **build argument**, not a runtime
environment variable. Vite inlines `import.meta.env.*` into the bundle when it builds, so
setting the variable on the running container has no effect - the value is already compiled
into the JavaScript. It must match the origin allowed by the API's `FRONTEND_URL`, or the
browser's requests will fail CORS.

---

## API Endpoints

**14 routes**, all under `/api`. Auth is a JWT in the `Authorization` header; a `Bearer `
prefix is accepted but not required.

| Method | Route | Auth | Description |
| --- | --- | --- | --- |
| POST | `/api/signup` | Public | Register; 409 if the username is taken |
| POST | `/api/signin` | Public | Log in, returns a JWT; 401 on bad credentials |
| GET | `/api/health` | Public | Health check |
| GET | `/api/leaderboard` | Public | Global ranking |
| GET | `/api/view-leaderboard` | User | Alias of the above, but **not** in the public list |
| GET | `/api/view-problems` | User | List all problems |
| GET | `/api/view-problem?problemId=` | User | One problem plus its test cases (samples only unless admin) |
| GET | `/api/view-tests?problemId=` | User | Test cases for a problem (samples only unless admin) |
| POST | `/api/submit-code` | User | Compile and judge C++ |
| GET | `/api/view-submissions?problemId=` | User | The caller's own submissions |
| POST | `/api/create-problem` | Admin | Create a problem |
| POST | `/api/create-test` | Admin | Add a test case |
| GET | `/api/view-users` | Admin | List users |
| GET | `/api/me` | User | The caller's own profile and role |

Authorization is enforced by the Spring Security filter chain (`anyRequest().authenticated()`)
and by `@PreAuthorize("hasRole('ADMIN')")` on the three admin routes. Unauthenticated requests
get a JSON 401, forbidden ones a JSON 403 - never an HTML error page.

Two identity rules worth stating explicitly:

- `POST /api/submit-code` takes **no** `userId`. The submission is always attributed to the
  authenticated principal.
- `GET /api/view-submissions` ignores any `userId` query parameter and always returns the
  caller's own submissions.

---

## Architecture

### Kafka pipeline

| | |
| --- | --- |
| **Topics** | `divzero.submission.created`, `divzero.submission.completed` |
| **Dead-letter topics** | `divzero.submission.created.DLT`, `divzero.submission.completed.DLT` |
| **Partitions / replicas** | 3 / 1 (created at startup by `KafkaConfig`) |
| **Retry before DLT** | 3 attempts, 2000 ms fixed backoff |

Consumer groups:

| Listener | Group ID | Delivery |
| --- | --- | --- |
| `JudgeWorker` | `divzero-judge-workers` (shared) | Each submission is judged by exactly one instance |
| `SubmissionCompletedListener` | `divzero-submission-completion-<random UUID>` (per instance) | Every instance sees every completion event |
| `SubmissionDeadLetterListener` | `divzero-dead-letter-handler` (shared) | A DLT message is handled once |

The completion group ID is made unique per process at startup by
`CompletionGroupIdEnvironmentPostProcessor`. This is deliberate. A synchronous submit parks the
HTTP thread on an in-process `CompletableFuture`; with a shared group, a completion event
delivered to instance B would never release a future held by instance A, and the caller would
block until timeout. Broadcasting the topic to every instance fixes that.

**The idempotency guard.** Because every instance now consumes every completion event,
`SubmissionServiceImpl.handleSubmissionCompleted` runs N times for N instances, and Kafka
redelivery can repeat it again. The method is intentionally idempotent: a terminal-status check
is the very first thing it does, ahead of any database write or cache eviction, and returns
early on a duplicate - while still completing the waiting future, since the instance seeing the
duplicate may be the one holding it.

One consequence to be aware of: a fresh consumer group combined with
`auto-offset-reset: earliest` means each instance replays the whole completion topic when it
starts. Every replayed event hits the terminal-status guard and is discarded, so nothing is
corrupted, but startup work grows with total submission history.

### Redis cache layer

| Cache | Key | TTL | Invalidated by |
| --- | --- | --- | --- |
| `problemList` | `'all'` | 300 s | Creating a problem |
| `problemDetails` | problem ID | 300 s | Adding a test case to that problem |
| `leaderboard` | `'global'` | 120 s | A user's **first** accepted submission for a problem |

Leaderboard eviction is deliberately narrow. A non-accepted verdict cannot change the
standings, and neither can re-solving an already-solved problem, so neither evicts - otherwise
the 120 s TTL would never survive any real submission load. The "already solved?" probe runs
*before* the new terminal status is written, because Hibernate flushes pending changes ahead of
a query against the same table and the row being written would otherwise match the probe
itself.

Cache errors are logged and swallowed (`LoggingCacheErrorHandler`), so Redis being down
degrades reads rather than failing them.

Redis also backs the submission rate limiter: a fixed-window counter at
`ratelimit:submit:<userId>:<epochSecond / windowSeconds>`, defaulting to 10 submissions per
60 s, answering 429 with a `Retry-After` header. It **fails open** - if Redis is unavailable
the request is allowed and a warning is logged, on the grounds that judging availability
matters more than perfect throttling.

---

## How Judging Works

A submission is persisted as `QUEUED` and published as a Kafka `SubmissionCreated` event. The
worker marks it `RUNNING`, compiles and runs it, then publishes `SubmissionCompleted`, which the
completion listener persists.

The HTTP request waits up to `SUBMISSION_RESULT_WAIT_TIMEOUT_MS` (default **5000 ms**) for that
completion. If the verdict arrives in time the response is `200` with the verdict; otherwise the
response is **`202 Accepted`** carrying a `submissionId`, and the client polls
`GET /api/view-submissions` every 1500 ms for up to 60 s until a terminal verdict appears.

The worker writes the source to a per-submission workspace, compiles with `g++` in Docker, then
runs the binary once per test case. Each container is started with:

`--rm --init --network none --memory <limit> --cpus <limit> --pids-limit 64
--security-opt no-new-privileges --user 65534:65534 --read-only
--tmpfs /tmp:rw,noexec,nosuid,size=<bytes>` and the workspace bind-mounted at `/code`.

`--init` is load-bearing, not decoration: without an init process the shell execs `timeout` into
PID 1, where the SIGTERM it sends to the submitted program has no effect and the in-container
time limit silently never fires.

Time and memory limits are **per problem** (`timeLimitMs`, `memoryLimitMb` on the `Problem`
table), read by the worker at judge time. The `app.judge.*` values are used only as fallbacks
when a problem row has no value. Compilation always uses the global `JUDGE_MEMORY` budget rather
than the problem's execution limit, because `g++` needs far more headroom than a submitted
program and a low per-problem limit would otherwise turn every submission into a compile failure.

Peak memory is measured by reading the container's cgroup `memory.peak` from inside the sandbox
immediately before it exits, and is `null` when it cannot be read - never a sentinel value.

Output comparison normalises line endings, strips trailing whitespace from each line and drops
trailing blank lines. Leading whitespace is **not** stripped, since indentation can be part of a
correct answer.

Verdicts are **Accepted**, **Wrong Answer**, **Compilation Error**, **Runtime Error**,
**Time Limit Exceeded**, **Memory Limit Exceeded** and **Internal Error**. Memory Limit Exceeded is an
OOM kill (exit 137) by the container's memory limit; swap is pinned to the same value
(`--memory-swap`), so the limit is the problem's declared `memoryLimitMb`, not twice it.

A submission to a problem with no test cases is rejected with `409` rather than judged, and
the worker treats an empty suite as an internal error, so no code can be Accepted by vacuity.

Each test is either a **sample** or **hidden** (`isSample` on the `Test` table; admins choose when
creating it). Non-admins only ever receive sample tests, and a Wrong Answer or Runtime Error on a
hidden test reports only the verdict and the test id: no input, expected output, program output or
stderr, since a submission could echo the input back through any of them.

Docker must be installed and reachable by the API process for submissions to work.

---

## Leaderboard Scoring

Ranking is by **distinct problems solved**, so solving the same problem five times scores 1, not
5. Ties break on total execution time ascending, then total submissions ascending, then username.

The execution-time tiebreaker sums the **best** (minimum) accepted run per problem, so repeated
accepted attempts neither inflate nor deflate it.

---

## Roles

Accounts are `USER` by default. To promote one, update the database directly:

```sql
UPDATE "User" SET "role" = 'ADMIN' WHERE "username" = 'yourname';
```

Admins get the `/admin` dashboard for creating problems and test cases.

---

## Tests

```bash
cd api && mvn test
```

Requires Docker: the repository, pipeline and security tests use Testcontainers. See
[api/README.md](api/README.md#tests) for what each suite covers.

---

## Known Limitations

Stated plainly, because each one matters if you deploy this:

- **The judge is not a hard security boundary.** The judge worker mounts the host Docker socket
  and shells out to `docker run`. Anything able to reach that socket has effective root on the
  host. Compose keeps the socket out of the internet-facing API (which runs with
  `JUDGE_WORKER_ENABLED=false`) and gives it only to a judge service that publishes no ports and
  only consumes Kafka, so a compromised API no longer means a compromised host. The container
  flags above (no network, read-only rootfs, unprivileged user, PID and memory caps) raise the
  cost of escaping a *submission*, but a submission that does escape, or a compromise of the judge
  process itself, still reaches the socket. For real isolation run the judge on a dedicated host
  or VM, or under a sandboxed runtime such as gVisor. Do not run this on a host you care about
  without further isolation.
- **Only C++ is supported.** There is one image, one compiler and one hard-coded `cpp` language
  tag. There is no language selector, and nothing in the schema models one.
- **The synchronous submit path depends on the requesting instance staying alive.** The waiting
  future lives in an in-process map. If the instance handling the request restarts mid-judge, the
  future dies with it and that request never receives its verdict inline. Client polling is the
  durable fallback and takes over automatically once the wait times out and returns 202.
- **Rate limiting fails open.** With Redis unavailable, submissions are not throttled at all.
- **Admin promotion is a manual SQL statement.** There is no endpoint or UI for changing a role.
