# Quellix

Quellix is a **multi-tenant incident command platform for regulated payment operators** such as banks, QR payment switches and wallet providers.

When a critical payment system fails, responders have to coordinate across teams, find the change that caused it, run remediation safely on production, and later prove to auditors who did what, who approved it and who could see it. Quellix brings that into one place:

- **Incident command isolated per institution.** Each tenant (one financial institution or business unit) is isolated at the database, Kafka and authorization layers.
- **Remediation runbooks with maker-checker approval,** executed by runner agents inside the institution's own network. The runners only make outbound connections.
- **Live streaming** of incident timelines and execution output, governed by fine-grained bitwise permissions that take effect mid-stream.
- **A tamper-evident audit trail,** hash-chained per tenant and anchored by signed checkpoints in WORM storage.
- **Federated sign-in** with Google and Microsoft only. There are no local passwords.

The design models the incident-handling requirements of Nepal Rastra Bank's IT and cyber-resilience guidelines. It **models** those requirements; it does not claim compliance.

The full architecture, decision log and roadmap are in [`../incident-command-center-system-design.md`](../incident-command-center-system-design.md).

> **Status:** foundations. The layered architecture, API versioning, response envelope and security baseline are in place, with a health check and a sample `user` feature. The incident, tenancy and authorization modules are not built yet.

---

## Tech stack

| Area | Technology |
|---|---|
| Language | Java 25 (Gradle toolchain) |
| Framework | Spring Boot 4.2, Spring WebFlux (reactive) |
| Database | PostgreSQL through R2DBC (reactive), Flyway migrations over JDBC |
| Security | Spring Security, OAuth2 Resource Server |
| Messaging and cache | Kafka, Redis (reactive). Both are included for upcoming features and are not used yet. |
| Validation | Jakarta Bean Validation |
| API docs | springdoc-openapi (Swagger UI) |
| Observability | Spring Boot Actuator, Micrometer, Brave tracing, Prometheus registry |
| Testing | JUnit 5, Testcontainers (Kafka, Redis) |
| Build | Gradle 9 (wrapper), Lombok |

---

## Getting started

### Prerequisites
- Java 25
- A running PostgreSQL instance
- Docker, for the Testcontainers-based tests

### 1. Configure the environment

```bash
cp .env.example .env
```

Then edit `.env`:

| Variable | Purpose | Example |
|---|---|---|
| `APP_NAME` | Application name | `Quellix` |
| `APP_ENV` | Active Spring profile | `dev` |
| `DB_HOST` / `DB_PORT` | PostgreSQL host and port | `localhost` / `5432` |
| `DB_NAME` | Database name | `quellix` |
| `DB_USERNAME` / `DB_PASSWORD` | Database credentials | — |
| `SERVER_PORT` | HTTP port | `8080` |

`.env` is git-ignored. Never commit real credentials.

### 2. Run

```bash
make run
```

`make run` loads `.env` and starts the app with `./gradlew bootRun`. Flyway applies the migrations in `src/main/resources/db/migration` on startup.

### Make targets

| Command | What it does |
|---|---|
| `make help` | List all targets |
| `make env` | Show the loaded environment configuration |
| `make run` | Start the application with `.env` loaded |
| `make build` | Build and run the tests |
| `make clean-build` | Build with refreshed dependencies, skipping tests |
| `make test` | Run the tests only |
| `make clean` | Delete build output |

---

## API

### Versioning
Pick the API version with the `X-API-Version` request header. When the header is missing, the version defaults to **`2.0`**. Version `1.0` is deprecated, and responses for it carry deprecation and sunset headers (RFC 9745, RFC 8594).

### Response envelope
Every successful response is wrapped in the same envelope:

```json
{
  "message": "User created",
  "data": { "...": "..." },
  "timestamp": "2026-11-26T10:15:30Z"
}
```

Clients detect errors from the HTTP status code; the body has no `success` flag.

### Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/system/health` | Public | Application health check |
| `POST` | `/users` | Required | Create a user (`201`, or `409` if the email is taken) |
| `GET` | `/users/{id}` | Required | Get a user by id (`404` if not found) |
| `GET` | `/actuator/health` | Public | Actuator health |
| `GET` | `/swagger-ui.html` | Public | Interactive API documentation |
| `GET` | `/v3/api-docs` | Public | OpenAPI specification |

### Security
The API is stateless: no sessions, no form login, no HTTP Basic. Health checks and API documentation are public. Every other endpoint requires authentication, and **no sign-in mechanism is configured yet**, so protected endpoints currently return `401`.

---

## Project structure

```
backend/
├── build.gradle                 Dependencies and build configuration
├── settings.gradle              Gradle project name
├── Makefile                     Shortcuts for running, building and testing
├── .env.example                 Template for local environment variables
├── gradle/wrapper/              Pinned Gradle version
└── src/
    ├── main/
    │   ├── java/io/kajisaab/quellix/
    │   │   ├── QuellixApplication.java    Application entry point
    │   │   ├── bootstrap/                 Startup tasks (startup summary log)
    │   │   ├── configuration/             Cross-cutting Spring configuration
    │   │   ├── core/                      Shared building blocks for all features
    │   │   └── features/                  Business features, one folder each
    │   └── resources/
    │       ├── application.yaml           Application configuration
    │       └── db/migration/              Flyway SQL migrations (V1__..., V2__...)
    └── test/java/io/kajisaab/quellix/     Tests and Testcontainers setup
```

### Shared packages

| Package | Contents |
|---|---|
| `bootstrap/` | `ApplicationStartup`: logs the application, profile, port and database pool summary when the app starts. |
| `configuration/` | `SecurityConfiguration` (public vs protected paths), `ReactiveApiVersioningConfig` (header-based versioning), `JacksonConfiguration` (JSON settings), `R2dbcConfiguration` (R2DBC auditing for created/modified timestamps). |
| `core/usecase/` | `UseCase<I, O>`, the contract every use case implements, plus the `UseCaseRequest` and `UseCaseResponse` marker interfaces. |
| `core/response/` | `ApiResponse<T>`, the envelope for every successful response. |

### Feature structure

Each feature lives in `features/<name>/` and is split into four layers:

```
features/user/
├── api/                          HTTP layer
│   └── UserController            Maps HTTP requests to use cases and results to status codes
├── application/                  What the feature does
│   ├── request/                  Use case inputs (CreateUserUseCaseRequest, GetUserUseCaseRequest)
│   ├── response/                 Use case outputs (UserUseCaseResponse)
│   └── usecase/                  One class per action (CreateUserUseCase, GetUserUseCase)
├── domain/                       Business model and rules, free of Spring and database code
│   ├── model/                    Domain objects (User)
│   ├── repository/               Storage interfaces the use cases depend on (UserRepository)
│   └── exception/                Business errors (UserAlreadyExistsException)
└── infrastructure/               Technical details
    └── persistence/              Database access
        ├── UserEntity            Maps a row of the users table
        ├── UserR2dbcRepository   Spring Data repository (generated queries)
        └── UserRepositoryAdapter Implements UserRepository, converting UserEntity ↔ User
```

| Layer | Responsibility | May depend on |
|---|---|---|
| `api` | HTTP: routing, request validation, status codes | `application` |
| `application` | Use cases: orchestrate one action, apply business rules | `domain` |
| `domain` | Business model, rules and the interfaces it needs from outside | nothing |
| `infrastructure` | Databases, Kafka, Redis, external APIs; implements `domain` interfaces | `domain` |

Dependencies point **inward**. The domain never imports from `api`, `application` or `infrastructure`, so storage or transport can change without touching business code. Features that don't store anything need only the layers they use: `system` has just `api` and `application`.

### Infrastructure sub-packages

`infrastructure/` is split by the external system each part talks to. Only `persistence/` exists today; the others are added when a feature needs them:

```
user/infrastructure/
├── persistence/   PostgreSQL through R2DBC (built)
├── messaging/     Kafka producers/consumers, e.g. publish "UserCreated"
├── cache/         Redis
└── client/        Calls to other services' HTTP APIs
```

Each follows the same pattern as `persistence/`: the `domain` or `application` layer defines an interface for what it needs, and the infrastructure sub-package implements it.

### Request flow

```
POST /users
  → UserController            validates the body, calls the use case
  → CreateUserUseCase         builds a User, checks the email is unique, saves it
  → UserRepository            domain interface
  → UserRepositoryAdapter     converts User → UserEntity
  → UserR2dbcRepository       INSERT into users
  ← UserUseCaseResponse       wrapped in ApiResponse by the controller
```

---

## Conventions

### Naming

| Kind | Pattern | Example |
|---|---|---|
| Use case | `<Action><Feature>UseCase` | `CreateUserUseCase` |
| Use case input | `<Action><Feature>UseCaseRequest` | `CreateUserUseCaseRequest` |
| Use case output | `<Feature>UseCaseResponse` | `UserUseCaseResponse` |
| Controller | `<Feature>Controller` | `UserController` |
| Domain repository | `<Feature>Repository` | `UserRepository` |
| Database entity | `<Feature>Entity` | `UserEntity` |
| Spring Data repository | `<Feature>R2dbcRepository` | `UserR2dbcRepository` |
| Repository implementation | `<Feature>RepositoryAdapter` | `UserRepositoryAdapter` |
| Configuration | `<Topic>Configuration` | `SecurityConfiguration` |
| Migration | `V<n>__<description>.sql` | `V1__create_users_table.sql` |

### Coding rules
- Use constructor injection (`private final` fields with `@RequiredArgsConstructor`), never field `@Autowired`.
- Use cases return the result object (`Mono<O>`). Only controllers wrap results in `ApiResponse`.
- Use Java records for requests, responses and domain objects.
- Never edit a migration that has already run; add a new versioned file instead.

### Adding a new feature
1. Create `features/<name>/` with the layers it needs.
2. Add the domain model and the repository interface in `domain/`.
3. Add a Flyway migration for any new tables.
4. Implement persistence in `infrastructure/persistence/`.
5. Add the request and response records and one use case per action in `application/`.
6. Expose the use cases through a controller in `api/`.
7. Decide which paths are public in `SecurityConfiguration`.

---

## Testing

```bash
make test
```

The tests use Testcontainers, so Docker must be running. Kafka and Redis containers start automatically through `TestcontainersConfiguration`. A PostgreSQL container is not configured yet.
