# TraceFlow — API & Distributed Log Analyzer

TraceFlow imports, analyzes and visualizes logs from monolithic and distributed Java applications. It turns raw log
lines into structured events, reconstructs requests across services by `correlationId` / `traceId`, and lets you
investigate them as a service graph, a waterfall and an event timeline, with an Error Explorer and analytics on top.

Everything runs locally: logs are never sent to external services, and no AI or paid API is involved.

---

## Table of contents

- [The problem](#the-problem)
- [Features](#features)
- [Technology](#technology)
- [Architecture](#architecture)
- [Repository structure](#repository-structure)
- [Prerequisites](#prerequisites)
- [Run with Docker](#run-with-docker)
- [Run locally without Docker](#run-locally-without-docker)
- [Configuration](#configuration)
- [REST API](#rest-api)
- [Importing logs](#importing-logs)
- [Demo scenarios](#demo-scenarios)
- [Sample files](#sample-files)
- [How traces are reconstructed](#how-traces-are-reconstructed)
- [Security and privacy](#security-and-privacy)
- [Tests](#tests)
- [Known limitations](#known-limitations)
- [Roadmap](#roadmap)

---

## The problem

When a request fails in a distributed system, its story is scattered across the log files of several services, in
different formats (Spring Boot console output, JSON encoders, plain text), interleaved with thousands of unrelated
lines and multi-line stack traces. Answering "what happened to request X, where did it fail and how long did each
step take?" usually means grepping several files by hand.

TraceFlow automates that investigation, and it is strict about evidence: it never invents relations between services,
durations or dependencies. Every relation, duration and suspected error origin shown in the UI is labeled with the
evidence it comes from.

## Features

| Area | What you get |
|------|--------------|
| **Dashboard** | Traces, events, errors, warnings, services and average request duration; events per minute by severity, errors by service, severity distribution, most frequent exceptions, request duration histogram. All values come from the backend. |
| **Trace Explorer** | Search and filter traces (correlationId/traceId, status, sort), then open a trace: an interactive service graph (zoom, pan, fit, node selection, sync vs async edges, confirmed vs inferred relations, error highlighting, probable error origin), a span waterfall, an event timeline with event details (raw line, attributes, stack trace) and the technical evidence used for the reconstruction. Export as JSON. |
| **Log Analyzer** | Raw log (syntax highlighted) side by side with the parsed events. Selecting an event highlights its source lines; parse problems are listed and flagged in the raw view. Works on stored analyses, or on pasted content that is parsed without being stored. CSV export. |
| **Demo Scenarios** | Five deterministic scenarios (success, timeout, database exception, circuit breaker with retries, async messaging) available right after start, each with run status, correlationId, logs, trace, graph, timeline, technical details and run history. |
| **Import Logs** | Drag and drop of multiple `.log` / `.txt` / `.json` / `.jsonl` files, client and server validation, upload progress followed by processing progress, import history, reprocessing and deletion. |
| **Error Explorer** | Error events filtered by service, exception class, period, correlationId and text; grouping of similar errors; occurrence counts; stack trace viewer with the probable origin frame; link to the related trace; CSV/JSON export. |
| **Analytics** | Event volume and error rate over time, event and trace error rates, services with most failures, recurring exceptions, request durations and operation durations (avg, max, P50/P95/P99 only when there are enough samples). |
| **Settings** | Theme (dark by default, light available), default data scope, polling interval, server limits and retention policy. |

The header contains a global correlationId/traceId search, the **Demo / Imported / All** data scope selector, a
processing indicator and the theme toggle. The interface is responsive.

## Technology

**Frontend**: Angular 21 (standalone components, signals, `@if`/`@for`/`@switch`, `inject`, Reactive Forms,
`rxResource`), TypeScript, SCSS, PrimeNG 21 + PrimeIcons, Apache ECharts 6 (ngx-echarts), Cytoscape.js with the dagre
layout for the service graph, Vitest.

**Backend**: Java 25, Spring Boot 4.1 (Web MVC, Data JPA, Validation, Actuator), PostgreSQL 17, Flyway, Jackson 3,
springdoc-openapi 3 (Swagger UI), Resilience4j 2.4, Lombok, Maven (wrapper included), JUnit 5, Mockito, AssertJ.

**Infrastructure**: Docker multi-stage builds, Docker Compose, Nginx (static files + reverse proxy for `/api`).

> **Why Angular 21 / PrimeNG 21 and not 22?** Starting with version 22, PrimeNG is distributed under the commercial
> "PrimeUI License": it needs a license key and shows an "Invalid PrimeUI License" banner without one. PrimeNG
> 21.1.10 is the last release under the MIT license, and it requires Angular 21. TraceFlow uses it so the project has
> no paid or registration-gated dependency. Angular 21 satisfies the "Angular 20 or higher" requirement.

## Architecture

```
 Browser ──► Nginx (frontend container, :4200)
               ├── /            Angular SPA (static files, SPA fallback)
               ├── /api/**      ──► Spring Boot backend (:8080, internal network only)
               └── /swagger-ui, /v3/api-docs, /actuator/health ──► backend
                                         │
                                         ├── PostgreSQL 17 (schema managed by Flyway)
                                         └── /data/uploads volume (masked copies of uploaded files)
```

The backend is organized by feature. Each feature separates domain (entities, repositories), application
(services, DTOs), infrastructure (storage, JDBC batch writer) and HTTP interface (controllers):

| Package | Responsibility |
|---------|----------------|
| `parsing` | Extensible parser chain (`LogLineParser`): JSON, Spring Boot, classic text; streaming engine (`LogStreamParser`) for line based files and JSON arrays; multi-line stack traces; context extraction; fingerprints; stack trace analysis. |
| `analysis` | Imports: upload validation, masked storage, asynchronous ingestion pipeline with progress, reprocessing, deletion, retention, live (non-persistent) analyzer. |
| `traces` | Correlation engine (`CorrelationSession`, union-find over correlationId/traceId), trace summaries, graph/waterfall/timeline reconstruction (`TraceGraphBuilder`). |
| `events`, `errors`, `analytics` | Query APIs with filters, pagination, grouping, CSV export and metrics. |
| `scenarios` | Demo engine (virtual clock, spans, structured JSON logs), simulated services, the five scenarios. |
| `common`, `config`, `system` | Global error handling, paging, sensitive data masking, configuration, system info. |

**Ingestion pipeline.** Files are read line by line (JSON arrays token by token), so memory use does not depend on
file size. Events are written to PostgreSQL in JDBC batches of 500. Trace summaries are accumulated incrementally and
written at the end. Progress is persisted after every batch and polled by the UI. Demo scenarios write their logs to a
file that goes through exactly the same pipeline as uploaded files.

## Repository structure

```
traceflow/
├── backend/                 Spring Boot application (Maven wrapper, Dockerfile)
│   └── src/main/resources/db/migration   Flyway migrations
├── frontend/                Angular application (Dockerfile, proxy.conf.json for ng serve)
├── docker/
│   ├── nginx/default.conf   Nginx site: SPA + /api reverse proxy + security headers
│   └── certs/               Optional extra CA certificates for builds behind TLS inspection
├── samples/                 Sample log files for imports
├── scripts/                 run-local (sh/ps1), test-all.sh, smoke-test.sh
├── docker-compose.yml
├── .env.example
└── README.md
```

## Prerequisites

- **Docker version**: Docker with Docker Compose v2. Nothing else (no Java, Node.js or PostgreSQL).
- **Local version**: Java 25 and Node.js 22+ (Node 24 recommended). Maven is downloaded by the wrapper; no database
  installation is needed (the `local` profile uses an embedded H2 database).

## Run with Docker

```bash
docker compose up --build -d
```

Then open **http://localhost:4200**. On the first start the database schema is created by Flyway and every demo
scenario is executed once, so the dashboard has data immediately.

| URL | |
|-----|-|
| http://localhost:4200 | Web UI |
| http://localhost:4200/swagger-ui.html | OpenAPI / Swagger UI |
| http://localhost:4200/actuator/health | Health |

The backend is not published on the host; the UI reaches it through the Nginx proxy. Containers have health checks,
restart policies and start in order (PostgreSQL → backend → frontend). Data survives restarts in the `pgdata` and
`uploads` volumes. Use `docker compose down -v` to delete everything, and `docker compose logs -f backend` to follow
the backend logs.

**Behind a corporate proxy or antivirus HTTPS scanning?** If the build fails with certificate errors while
downloading Maven or npm dependencies, put the intercepting root certificate (PEM, `.crt`) in `docker/certs/` and
rebuild. See [docker/certs/README.md](docker/certs/README.md).

## Run locally without Docker

```bash
./scripts/run-local.sh          # Linux / macOS / Git Bash
.\scripts\run-local.ps1         # Windows PowerShell
```

The script starts the backend with the `local` profile (embedded H2 database stored in `backend/data/`) on port 8080,
then the Angular dev server on http://localhost:4200, which proxies `/api` to the backend. Manually:

```bash
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
cd frontend && npm ci && npm start
```

To run the backend against a local PostgreSQL instead, omit the profile and set `DB_URL`, `DB_USER` and `DB_PASSWORD`.

## Configuration

All settings are environment variables (see [.env.example](.env.example)):

| Variable | Default | Description |
|----------|---------|-------------|
| `FRONTEND_PORT` | `4200` | Host port of the UI |
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | `traceflow` | Database credentials |
| `TRACEFLOW_MAX_FILE_SIZE` | `50MB` | Maximum size per file |
| `TRACEFLOW_MAX_FILES` | `10` | Maximum files per import |
| `TRACEFLOW_PUBLIC_MODE` | `false` | `true` disables persistent imports (see [security](#security-and-privacy)) |
| `TRACEFLOW_RETENTION_ENABLED` / `TRACEFLOW_RETENTION_DAYS` | `true` / `30` | Nightly deletion of old analyses |
| `TRACEFLOW_DEMO_SEED` | `true` | Run each scenario once on first start |
| `TRACEFLOW_DEFAULT_ZONE` | `UTC` | Zone for timestamps without offset |
| `TRACEFLOW_CORS_ORIGINS` | `http://localhost:4200` | Allowed origins when the UI is not served by the bundled Nginx |

## REST API

Interactive documentation of every endpoint is available in Swagger UI (`/swagger-ui.html`, OpenAPI at
`/v3/api-docs`). Read endpoints accept `scope=ALL|DEMO|IMPORT`; lists are paginated (`page`, `size`).

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/imports` | Upload files (multipart `files`, optional `name`); returns the analysis (202) |
| GET | `/api/analyses` | Import history and demo analyses (`kind=ALL|IMPORT|DEMO`) |
| GET | `/api/analyses/{id}` | Analysis with its files |
| GET | `/api/analyses/{id}/job` | Processing status and progress |
| POST | `/api/analyses/{id}/reprocess` | Reprocess stored files |
| DELETE | `/api/analyses/{id}` | Delete an analysis, its events, traces and files |
| GET | `/api/analyses/{id}/files/{fileId}/lines` | Window of raw (masked) lines (`from`, `limit`) |
| GET | `/api/analyses/{id}/parse-errors` | Parse problems |
| GET | `/api/traces` | Traces (`q`, `service`, `status`, `from`, `to`, `sort`) |
| GET | `/api/traces/{id}` | Trace detail: nodes, edges, spans, windows, timeline, evidence, error origin |
| GET | `/api/traces/by-correlation/{correlationId}` | Traces by correlationId |
| GET | `/api/traces/by-trace-id/{traceId}` | Traces by traceId |
| GET | `/api/search?q=` | Global search by correlationId / traceId prefix |
| GET | `/api/events` | Events (`analysisId`, `fileId`, `traceRef`, `correlationId`, `traceId`, `service`, `level`, `q`, `from`, `to`, `errorsOnly`) |
| GET | `/api/events/{id}` | Event with raw content and stack trace |
| GET | `/api/events/export.csv` | Streamed CSV export of filtered events (max 100,000 rows) |
| GET | `/api/errors` | Error events (`service`, `exceptionClass`, `correlationId`, `fingerprint`, `q`, `from`, `to`) |
| GET | `/api/errors/groups` | Similar errors grouped by fingerprint |
| GET | `/api/errors/facets` | Services and exception classes for filters |
| GET | `/api/errors/{eventId}` | Error detail: stack analysis, probable origin, related trace |
| GET | `/api/scenarios` | Scenario catalog with last run |
| POST | `/api/scenarios/{key}/execute` | Execute a scenario |
| GET | `/api/scenarios/runs`, `/api/scenarios/runs/{id}` | Run history |
| GET | `/api/dashboard` | Dashboard metrics |
| GET | `/api/analytics` | Analytics (`from`, `to`) |
| POST | `/api/analyzer/parse` | Parse pasted content without storing it |
| GET | `/api/system/info` | Limits, retention, public mode |

Errors use a uniform JSON body (`status`, `error`, `message`, `path`, `violations`) without stack traces or internal
details.

## Importing logs

1. Open **Import Logs**, drop one or more files (or use *Choose files*) and optionally name the analysis.
2. Files uploaded **together** form one analysis: events are correlated across all of them, which is how logs of
   different services are joined into one trace.
3. The upload progress bar is followed by the server processing progress. Parse problems never stop the analysis;
   they are counted and listed in the Log Analyzer.
4. From the history you can open the analysis in the Log Analyzer, reprocess it or delete it.

Supported formats, mixable in the same file:

- **Spring Boot** default layout (Boot 2 with Sleuth `[app,traceId,spanId]`, Boot 3/4 with application name and the
  Micrometer `[traceId-spanId]` section).
- **JSON / JSON Lines** (Logstash/Logback encoders, ECS-style nested fields, `mdc`, epoch timestamps, numeric levels,
  `stack_trace` or nested `exception` objects) and **JSON arrays** (streamed).
- **Classic text**: `timestamp LEVEL [service] [correlationId=…] message`, Logback default
  (`timestamp [thread] LEVEL logger - message`) and similar bracketed layouts.
- **Multi-line Java stack traces** (`at …`, `Caused by:`, `... n more`) attached to the preceding event.

Recognized context: `correlationId` (also `X-Correlation-Id`, `requestId` as fallback), `traceId`, `spanId`,
`parentSpanId`, `spanKind`, `operation`, `peerService`, `messageId`, explicit durations (`durationMs`,
`duration=1.5s`, `took 120 ms`) and HTTP status. When an event has no service, a kebab-case file name such as
`payment-service.log` is used as the service name (marked as inferred from the file name).

Invalid timestamps keep the event without a timestamp (and add a warning). Events without any correlation identifier
are kept and searchable but are not assigned to a trace.

New formats can be added by implementing `LogLineParser` and registering it in `LogParsers.defaults()`.

## Demo scenarios

Open **Demo Scenarios** and press **Execute Scenario** (or **Run again**) on any card. Selecting a card shows the
latest run: status, HTTP status, correlationId/traceId (copyable), technical details, the full trace view (graph,
waterfall, timeline) and the run history.

| # | Scenario | Flow | Result |
|---|----------|------|--------|
| 1 | Successful Request | API Gateway → Order Service → Payment Service → Database | HTTP 200, every span successful |
| 2 | Service Timeout | API Gateway → Order Service → Payment Service | HTTP 504 after the 5 s client timeout; payment-service flagged as the probable error origin; its span overruns the caller on the waterfall |
| 3 | Database Exception | API Gateway → Order Service → Database | HTTP 500, `DataIntegrityViolationException` caused by `SQLException` (SQLState 23505), real Java stack trace pointing to `OrderRepository.insert` |
| 4 | Circuit Breaker & Retries | Order Service → External Payment API | Three failed attempts (Resilience4j Retry), circuit breaker `CLOSED → OPEN`, two follow-up calls rejected with `CallNotPermittedException` |
| 5 | Async Messaging | Order Service → Message Broker → Notification Service | HTTP 202, message published to an in-memory broker and consumed on its own thread; async edges with measured delivery latency |

The scenarios run real Java code (simulated services, a real Resilience4j circuit breaker and retry, a real consumer
thread). Behavior is deterministic; every run gets new identifiers. Latencies are simulated with a virtual clock, so a
"5 second timeout" produces correct timestamps and durations without making you wait. Every run is persisted
(analysis, events, trace, run record).

## Sample files

The [samples/](samples/) folder contains files for testing imports independently of the scenarios: a distributed
system split into three files of different formats, a Spring Boot log with tracing, a monolith log with parse problems
and secrets to mask, and a JSON array. See [samples/README.md](samples/README.md).

## How traces are reconstructed

- **Grouping**: by correlationId, otherwise traceId. An event carrying both links them, so traceId-only events join the
  correlated trace (union-find). Nothing is grouped by timing or proximity.
- **Relations** between services are labeled:
  - **CONFIRMED**: a span whose `parentSpanId` points to a span of another service, or a client span that explicitly
    declares its `peerService` (databases and third-party APIs appear as dashed "declared peer" nodes).
  - **INFERRED**: a log message explicitly mentions calling, forwarding or publishing to another service of the same
    trace. Drawn dotted, with the message as evidence.
  - **UNKNOWN**: services without evidence are listed as unlinked and no edge is drawn. When there are no relations at
    all, the trace is presented as an event timeline only.
  - **Async** edges come from `PRODUCER`/`CONSUMER` span kinds or shared message ids.
- **Durations**: `REPORTED` when logged by the application (e.g. `durationMs` on the root span), `OBSERVED` when
  computed from first/last timestamps, `UNKNOWN` otherwise. Request (trace) durations and operation durations are
  never mixed. Without span ids, the waterfall shows observed per-service windows and says so.
- **Probable error origin**: the deepest failing span of the call tree. Without spans, the first error event, with
  that weaker justification stated. In the Error Explorer, the origin is the first application (non JDK/framework)
  frame of the root cause, and is only shown when such a frame exists.

## Security and privacy

- Uploads are validated: extension allowlist, size and count limits, non-empty content, binary detection and
  JSON/JSONL structure checks. Nginx and Spring also enforce request size limits.
- **Path traversal**: user file names are sanitized and only displayed; stored files get generated names, and every
  resolved path is checked to stay inside the storage directory.
- **Masking** of JWTs, passwords, secrets, API keys, tokens, cookies, `Authorization` headers and credentials in URLs
  happens line by line **before** anything is written to disk or to the database. Raw views, events, exports and
  reprocessing therefore only ever see masked content.
- Displayed content is rendered as text by Angular (no `innerHTML`); CSV exports neutralize spreadsheet formulas.
- API errors never expose stack traces, SQL or credentials (`server.error.include-*` disabled, global handler).
- Nginx sends `Content-Security-Policy`, `X-Frame-Options`, `X-Content-Type-Options` and `Referrer-Policy` headers.
- Configurable CORS and retention policy; analyses can be deleted at any time.
- **Public deployments**: there is no authentication yet, so set `TRACEFLOW_PUBLIC_MODE=true`. Persistent imports are
  then disabled, while demo scenarios and the non-persistent paste analyzer stay available.
- No data leaves the server: no telemetry, no external services.

## Tests

```bash
./scripts/test-all.sh              # backend + frontend tests + production build
./scripts/smoke-test.sh            # against a running stack (default http://localhost:4200)
```

Or separately: `cd backend && ./mvnw test`, and `cd frontend && npx ng test --watch=false`.

- **Backend: 67 tests** (JUnit 5, Mockito, AssertJ). Parsers (Spring Boot, JSON, classic text, multi-line stack traces,
  malformed and mixed input, JSON arrays), correlationId/traceId extraction, masking, fingerprints, stack analysis,
  correlation and trace summaries, graph reconstruction (confirmed/inferred/unknown, async, error origin), duration
  percentiles, upload validation (Mockito). It also runs **API integration tests** against the real Spring context and
  Flyway schema (H2 in PostgreSQL mode): all five scenarios end to end, distributed import with cross-file
  correlation, masking, filters, CSV export, reprocess/delete, invalid uploads, dashboard/analytics/error grouping,
  the live analyzer and error responses.
- **Frontend: 38 tests** (Vitest + Angular TestBed). HTTP service (parameters, multipart upload, URL encoding, error
  messages), filters (Error Explorer query mapping, upload validation), chart option builders, the log syntax
  highlighter, dashboard (data, loading, empty and error states, scope changes), trace list (rendering, debounced
  search, navigation, empty state), demo scenarios (catalog, execution with running state and result), waterfall,
  timeline and badges.

The Docker stack has also been verified end to end on PostgreSQL 17 with the smoke test (five scenarios, distributed
import, read APIs) and by rendering every page in a headless browser in dark, light and phone-width layouts.

## Known limitations

- No authentication or multi-tenancy yet. Use public mode for shared deployments.
- Processing runs in the backend instance (two parallel import jobs); there is no distributed job queue.
- The trace detail view loads up to 5,000 events per trace (configurable); larger traces are flagged as truncated.
- Analytics percentiles and duration histograms are computed in the application from up to 50,000 samples.
- Message-based relation inference is English keyword based (`calling`, `forwarding to`, `publish`, …).
- Timestamps without a zone use one configured default zone for all files.
- Plain-text multi-line messages are only joined when continuation lines are indented; other unknown lines are
  reported as parse problems.
- Live progress uses polling, not SSE.
- The demo message broker is in-memory (no Kafka/RabbitMQ), as allowed for the MVP.

## Roadmap

- Authentication, per-user workspaces and role-based access.
- OpenTelemetry (OTLP) and Zipkin JSON ingestion; live tailing via SSE.
- Configurable custom parsers (regex/grok) from the UI.
- Saved searches, comparison between traces and alerting on error-rate thresholds.
- Object storage for uploads and an external job queue for horizontal scaling.
