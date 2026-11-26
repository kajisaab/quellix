# Incident Command Center — System Design Document

> Working name. Optional product name: **Kavach** ("armour").
> Flagship backend portfolio project · Java 25 · Spring Boot 4.x · Spring WebFlux · PostgreSQL (R2DBC) · Kafka
> Target roles: platform / infrastructure engineering, fintech / regulated (e.g. F1Soft)
> Status: design agreed, implementation started (M0 foundations) · Last updated: 2026-11-26

---

## 0. Decision log

Every decision below was made during the architecture discussion. Each one should get an ADR (§14).

| # | Area | Decision |
|---|---|---|
| D1 | Product spine | **Incident-first.** Releases are recorded as *change records* linked to incidents. They are not executed. |
| D2 | Execution | **A separate runner agent service** executes typed runbook steps inside the tenant's network. It only makes outbound connections. |
| D3 | Permission depth | **Custom roles per org**, plus allow/deny overrides at **service and incident** scope. |
| D4 | Target / budget | Platform/infra and fintech/regulated roles · 3 h/day (about 21 h/week) |
| D5 | Tenancy | **Schema per tenant** (`platform` schema + `tenant_<slug>` schemas) |
| D6 | Override depth | Org → service → **incident** |
| D7 | Joining | Invitation-only for now. The domain auto-join tables and verification are designed but **not built**. |
| D8 | Audit integrity | Hash chain per tenant + **Ed25519-signed checkpoints** written to WORM object storage |
| D9 | Identity providers | **Google and Microsoft only.** Personal and work accounts. No passwords. |
| D10 | Memberships | Global, in `platform`. Roles and role assignments live in tenant schemas. |
| D11 | DB isolation | **One Postgres role per tenant**, with `SET LOCAL ROLE` and `SET LOCAL search_path` per transaction |
| D12 | Approvals | **Policy per severity** (N distinct approvers, optional required role, expiry) |
| D13 | Browser session | **Strict BFF.** The browser holds only a session cookie. JWTs exist only behind the BFF. |
| D14 | Runner → Kafka | **Runners produce directly to Kafka** (SASL/OAUTHBEARER, tenant-scoped ACLs) |
| D15 | Web stack | **Spring WebFlux everywhere:** the core API and the separate stream gateway are both reactive, with R2DBC for PostgreSQL. *(Revised 2026-11-26: the core API was originally planned on Spring MVC with virtual threads.)* |
| D16 | Kafka topology | **Per-tenant topics** (`tenant.<slug>.*`) with ACLs |

Decisions derived from the above (push back on any of them):

| # | Decision |
|---|---|
| D17 | The BFF is built on Spring Cloud Gateway (reactive), using `oauth2Login` and a TokenRelay filter. |
| D18 | The core API is the internal token issuer. The BFF obtains internal tokens through **RFC 8693 token exchange**, authenticating itself with `private_key_jwt`. |
| D19 | The core API makes all authorization decisions. The stream gateway enforces them, calling a bulk decision endpoint and caching the answers. The bitwise engine is a plain Java library shared by both. |
| D20 | Each claimed execution gets its **own signing key**, because Kafka ACLs authorize topics, not individual records. |
| D21 | The SSE `Last-Event-ID` is the Kafka `partition-offset`. History beyond Kafka retention comes from the core API. |
| D22 | Sessions are stored in Redis via Spring Session. |

---

## 1. Problem statement

Payment operators and banks run critical systems: QR payment switches, mobile banking APIs and wallet ledgers. When one fails, responders must:

1. coordinate across teams;
2. find the change that caused it;
3. run remediation safely on production infrastructure;
4. later prove to auditors and regulators what happened, who authorized each action, and who could see what.

This platform provides:
- **incident command isolated per institution**;
- **remediation runbooks with maker-checker approval**, executed by runner agents inside the institution's own network;
- **live streaming** of timeline events and execution output, governed by fine-grained bitwise permissions that take effect *mid-stream*;
- a **tamper-evident audit trail** anchored in WORM storage.

**Tenant model:** one tenant is one financial institution or business unit. The expected scale is tens to low hundreds of tenants, which is the range where schema-per-tenant is the right trade-off.

**Regulatory framing:** model the requirements of Nepal Rastra Bank's IT and cyber-resilience guidelines for incident handling and reporting. Read the current published versions and cite them in the README. Claim that the design *models* the requirements, never that it is *compliant*.

## 2. Goals and non-goals

**Goals**
- Isolation per tenant at the database, Kafka and authorization layers, with tests that prove it.
- Federated sign-in with Google and Microsoft, with no local passwords.
- Bitwise permissions with custom roles and overrides at three scopes.
- Permission revocation that reaches live streams in under a second (p99, measured).
- Segregation of duties for every production action.
- An audit log that can be verified, anchored outside the database.
- Production readiness: Docker Compose, Testcontainers, OpenTelemetry, RFC 7807 errors.

**Non-goals (deferred)**
- Domain auto-join (the schema exists, the feature is not built).
- A full single-page frontend. The project ships an OpenAPI spec, a Bruno collection and one static demo page.
- Running in multiple regions.
- Placing a premium tenant in its own database (designed for, not built).
- Paging and alerting integrations.

## 3. Core user flows

1. An admin creates an org, invites members by email and assigns roles. Each role is a bitmask.
2. A user accepts an invitation by signing in with Google or Microsoft.
3. A team registers a runner agent inside its network.
4. A responder declares an incident on a service, links the suspected change record and invites other responders.
5. A responder requests a runbook execution. Approvers required by the severity policy approve it, and none of them may be the requester.
6. The runner claims the job and executes it. Output streams live to everyone allowed to see it.
7. An admin revokes someone's permission during the incident. Their live stream is filtered or closed within the latency target, and the change is audited.
8. The incident is resolved. The timeline, approvals and executions are exported as a report, and the audit chain is verified against the signed checkpoints.

## 4. Architecture

```
                 ┌──────────── Google / Microsoft Entra (OIDC) ────────────┐
                 │                                                          │
Browser ──cookie──► BFF (Spring Cloud Gateway, reactive)                    │
                     · OAuth2 Client (code + PKCE), Redis sessions, CSRF    │
                     · RFC 8693 token exchange ─────► Core API ◄────────────┘ (validates ID token)
                     · TokenRelay (internal JWT)            │
                          │ REST                            │
                          ▼                                 │
                     Core API (Spring WebFlux, R2DBC, Spring Modulith)
                     · identity, tenancy, authz decisions, incidents,
                       runbooks, approvals, audit, outbox relay, JWKS
                          │                    ▲
                          │ SSE (proxied)      │ bulk authz decisions
                          ▼                    │
                     Stream Gateway (WebFlux) ─┘   enforces authz on every event
                          ▲
                          │ consumes tenant.*.{execution-output, incident-events, authz-events}
                        Kafka (KRaft) ◄──SASL/OAUTHBEARER (TLS)── Runner Agent (in the bank's network)
                          ▲                                          │ HTTPS: claim jobs (outbound only)
                          └── outbox relay (Core)                    └──────────► Core API

            PostgreSQL (platform + tenant_<slug> schemas) · Redis · MinIO (Object Lock)
```

**Deployables**

| Service | Stack | Responsibility |
|---|---|---|
| `bff` | Spring Cloud Gateway (WebFlux) | OIDC client, server-side sessions, CSRF, token relay, SSE proxy |
| `core-api` | Spring WebFlux + R2DBC, Spring Modulith | Identity, tenancy, authorization decisions, incidents, runbooks, approvals, audit, outbox, JWKS, runner API |
| `stream-gateway` | Spring WebFlux | SSE fan-out, authorization at delivery time, backpressure, revocation |
| `runner-agent` | Java 25 / Spring Boot 4 | Registration, job claiming, typed step execution, producing output to Kafka |

**Infrastructure:** PostgreSQL 16+, Kafka (KRaft mode), Redis, MinIO (Object Lock), Prometheus, Grafana, OpenTelemetry Collector.

**Modules inside `core-api`** (Spring Modulith): `identity`, `tenancy`, `authz`, `incident`, `change`, `runbook`, `execution`, `runner`, `audit`, `outbox`, `shared`.

**Shared library:** `permission-engine`. Pure Java with no Spring dependency, used by both `core-api` and `stream-gateway`.

## 5. Tenancy and data model

### 5.1 Schemas

**`platform` schema (global; holds no tenant business data)**

| Table | Purpose / key columns |
|---|---|
| `users` | Local identity: display name, primary email, status |
| `user_identities` | provider, `issuer`, `subject`, `entra_tid`, `google_hd`, email at link time, `email_verified`. **Unique on (`issuer`, `subject`)** |
| `organizations` | Tenant registry: slug, `schema_name`, `db_role`, status, migration version |
| `org_domain_bindings` | (provider, value) such as (google, hd) or (microsoft, tid); verification status and method. Unique on (provider, value) where verified. *Designed, not built.* |
| `memberships` | user ↔ org, status, **`permissions_version`** |
| `invitations` | email, org, intended roles, token hash, expiry, accepted by (`iss`, `sub`) |
| `sessions` | `sid`, user, BFF session reference, created, revoked |
| `refresh_token_families` / `refresh_tokens` | Hashed tokens, rotation chain, revocation for reuse detection |
| `platform_audit_events` | Hash-chained. Covers provisioning, domain bindings and platform-admin actions |
| `platform_outbox_events` | Session revocation and tenant lifecycle events |

**`tenant_<slug>` schema (one per institution, identical structure in each)**

| Area | Tables |
|---|---|
| Authorization | `roles` (permissions BIGINT, is_system, is_default), `member_roles`, `permission_overrides` (scope_type, scope_id, subject_type, subject_id, allow BIGINT, deny BIGINT) |
| Catalog | `services`, `change_records` |
| Incidents | `incidents`, `incident_timeline_entries` (append-only), `incident_responders` |
| Runbooks | `runbooks`, `runbook_versions` (immutable, content hash), `approval_policies`, `runbook_executions`, `execution_approvals`, `execution_output_chunks` |
| Runners | `runners` (public key, labels, status, last_heartbeat_at), `runner_registration_tokens` |
| Audit | `audit_events` (seq, prev_hash, hash), `audit_checkpoints` |
| Plumbing | `outbox_events`, `idempotency_keys` |

Tables in tenant schemas refer to `platform.users` by `user_id` **without a foreign key across schemas**. Each tenant schema is self-contained, so it can later move into a dedicated database.

### 5.2 Isolation enforcement
- **`search_path` is not a security boundary:** one fully qualified query can read another tenant's tables.
- **Per-tenant database roles:** each tenant has a Postgres role with privileges only on its own schema. At the start of every transaction the application runs `SET LOCAL ROLE tenant_<slug>` and `SET LOCAL search_path`.
- **No leakage through the pool:** `SET LOCAL` resets at commit, so a pooled connection cannot carry one tenant's context into the next request.
- **Where the tenant comes from:** only the `org` claim of the internal JWT. If a URL contains an org slug that doesn't match, the request gets 403 `tenant-mismatch`.
- **Proof by test:** a Testcontainers test asserts that a tenant A session receives **permission denied** (not just zero rows) on tenant B's tables.

### 5.3 Tenant provisioning saga (idempotent)
1. Create the schema and the tenant's database role.
2. Run Flyway migrations for that schema.
3. Create Kafka topics and ACLs: `tenant.<slug>.execution-output`, `tenant.<slug>.incident-events`, `tenant.<slug>.authz-events`.
4. Seed the system roles and the default role.
5. Mark the org active and write a platform audit entry.

Every step can safely be re-run. A failed provisioning resumes rather than duplicating anything.

### 5.4 Key indexes
- `user_identities (issuer, subject)`: unique
- `org_domain_bindings (provider, value)`: unique where the binding is verified
- `permission_overrides (scope_type, scope_id, subject_type, subject_id)`: unique
- `incidents (status, severity, declared_at desc)`
- `incident_timeline_entries (incident_id, occurred_at)`
- `runbook_executions`: partial index where status is `PENDING_APPROVAL` or `QUEUED`
- `execution_output_chunks (execution_id, seq)`: unique (makes ingest deduplication possible)

## 6. Identity and sessions

### 6.1 Sign-in and token exchange

```
Browser ──► BFF ──(code + PKCE + nonce + state)──► Google / Entra
Browser ◄── provider sign-in, callback with code ──► BFF
BFF ──(code exchange)──► provider ──► ID token
BFF ──(RFC 8693 token exchange: subject_token = ID token, client auth = private_key_jwt)──► Core API
Core API:
  1. validates the ID token again (signature, iss, aud == BFF client id, exp, nonce)
  2. looks up user_identities by (iss, sub)
       · found      → that user
       · not found  → pending invitation? link it : 403 ProblemDetail
  3. creates a session (sid) and a refresh-token family
  4. returns an internal access JWT and a refresh token to the BFF
BFF: stores both in its Redis session, discards the provider tokens,
     and sets a session cookie (HttpOnly, Secure, SameSite=Strict)
```

### 6.2 Rules
- **Link accounts on (`iss`, `sub`) only, never on email.** Emails are reassigned and recycled.
- **Never trust the Microsoft `email` claim** for linking or auto-join. Entra tenant admins can set it to anything (the 2023 "nOAuth" class of vulnerability). Future auto-join for Microsoft binds on `tid`.
- **Microsoft issuer validation:** the `common` endpoint yields one issuer per Entra tenant. The issuer must match `https://login.microsoftonline.com/{tid}/v2.0`, with `{tid}` equal to the token's `tid` claim. Disabling issuer validation is a vulnerability, not a fix.
- **Personal Microsoft accounts** always carry the consumer tenant ID `9188040d-6c67-4c5b-b112-36a304b66dad`. That value is permanently blocked as a domain-binding target.
- **Personal Gmail accounts** have no `hd` claim. They can join by invitation only.
- **Invitation acceptance:**
  - The link carries a single-use token, stored hashed and expiring after 72 hours.
  - The person must sign in, and the token binds to whichever identity accepts it.
  - For Google, the account's verified email must match the invited email.
  - For Microsoft, the check is possession of the link, plus an audit entry recording the accepting `iss`/`sub`.
- **Provider tokens are never stored.** This is data minimization.

### 6.3 Internal tokens
- **Access JWT:** ES256, 5-minute lifetime, `kid` header for key rotation. Public keys are served at `/.well-known/jwks.json`.
- **Claims:** `sub` (internal user ID), `org` (active org), `sid`, `pv` (permissions version), `perms` (org base mask), `aud`, `exp`, `jti`.
- **Org-scoped:** switching org performs another token exchange for the requested org.
- **Refresh tokens:**
  - opaque, stored hashed;
  - **rotated on every use**;
  - **reuse detection**: presenting an already-used token revokes the whole family and the session.
- **Stale-token check:** each request compares the token's `pv` with the current version in a local cache. A mismatch returns 401 `token-stale`, and the BFF refreshes transparently.

### 6.4 Browser protections
- **Session cookie:** `HttpOnly`, `Secure`, `SameSite=Strict`.
- **CSRF:** Spring Security CSRF tokens on all mutating requests.
- **Logout:** revokes the session and publishes `session-revoked`. Every stream belonging to that `sid` is closed (§8.4).

### 6.5 Future: domain auto-join (designed, not built)
- **Google:** bind on `hd` together with `email_verified = true`. The domain is verified with a DNS TXT record.
- **Microsoft:** bind on `tid`. The binding is only created when an admin of that Entra tenant signs in and the `tid` is captured from their token.
- **Uniqueness:** each provider value can be bound to one org only.

## 7. Authorization engine

### 7.1 Permission bit layout

Permissions are a 63-bit `BIGINT` mask. **Bit 63 (the sign bit) is never used.** Bits are **never reused**: a deprecated bit stays reserved for good.

The registry lives in `permission_definitions` (bit, key, description, applicable scopes, deprecated_at). At startup it is checked against the Java enum.

| Bits | Group | Examples | Overridable at |
|---|---|---|---|
| 0–7 | Org admin | `ADMINISTRATOR`, `MANAGE_ORG`, `MANAGE_DOMAIN_BINDINGS` | Org only |
| 8–15 | Members and roles | `INVITE_MEMBERS`, `MANAGE_ROLES`, `ASSIGN_ROLES` | Org only |
| 16–23 | Services | `VIEW_SERVICE`, `MANAGE_SERVICE`, `MANAGE_OVERRIDES` | Org, service |
| 24–35 | Incidents | `VIEW_INCIDENT`, `DECLARE_INCIDENT`, `COMMAND_INCIDENT`, `POST_TIMELINE`, `CHANGE_SEVERITY`, `RESOLVE_INCIDENT` | Org, service, incident |
| 36–43 | Runbooks | `VIEW_RUNBOOK`, `MANAGE_RUNBOOK`, `REQUEST_EXECUTION`, `APPROVE_EXECUTION`, `VIEW_EXECUTION_OUTPUT` | Org, service, incident |
| 44–51 | Runners | `VIEW_RUNNERS`, `REGISTER_RUNNER`, `REVOKE_RUNNER` | Org only |
| 52–57 | Compliance | `VIEW_AUDIT`, `EXPORT_AUDIT`, `VERIFY_AUDIT_CHAIN` | Org only |
| 58–62 | Reserved | Future additions | — |

An override that sets a bit outside that bit's allowed scopes is rejected with a 400 ProblemDetail.

### 7.2 Roles
- **System roles** (seeded templates): Owner, Incident Commander, Responder, Approver, Viewer, Auditor.
- **Custom roles** are defined per org.
- **The default role** is held implicitly by every member, like Discord's `@everyone`. It is what makes confidential incidents possible: deny `VIEW_INCIDENT` to the default role at incident scope, then allow it for named responders.
- **The Owner can never be locked out.**

### 7.3 Evaluation order (from broad to specific)
1. **Base mask:** OR of all the member's role masks. If `ADMINISTRATOR` is set, or the member is the Owner, grant everything and stop.
2. **Service layer:**
   - Role overrides: OR all their denies and clear those bits, then OR all their allows and set them.
   - Then the member's own override, applied the same way.
3. **Incident layer:** same as the service layer.

The more specific scope always wins.

### 7.4 Enforcement
- **Single-resource checks:** `@PreAuthorize` calls a permission bean, for example "the user needs `VIEW_INCIDENT` on incident `#id`".
- **List queries:** evaluated **inside SQL** with `&`, `|` and `~` on `BIGINT` across LEFT JOINs to the service and incident override rows, so pagination stays correct. Filtering in Java after the query is forbidden.
- **Deny by default:** an ArchUnit rule fails the build if any controller method lacks `@PreAuthorize`.
- **Caching:** effective masks are cached per (user, scope), keyed by `permissions_version` plus an override version per scope. Every change bumps a version, which invalidates the cache and also serves as the revocation signal (§8.4).

## 8. Runbooks, runners and streaming

### 8.1 Runbooks and maker-checker
- **Runbook versions are immutable** and identified by a content hash. An execution always points at the exact version that ran.
- **Step types are typed only:**
  - an HTTP check;
  - a script pinned by hash;
  - a container pinned by image digest.

  No free-form shell strings are typed into the UI.
- **Approval policies** (`approval_policies`, one row per severity):
  - how many distinct approvers are required;
  - an optional required role (for example SEV1 needs 2 approvers, one of them an Incident Commander);
  - how long an approval stays valid.
- **Segregation of duties:** a database CHECK ensures an approver is never the requester. Approvers must hold `APPROVE_EXECUTION` **at that incident's scope**.
- **Approvals bind to one version:** an approval of version N does not authorize version N+1.
- **Checked again at claim time:** when a runner claims the job, approvals are re-evaluated. An approver who has lost `APPROVE_EXECUTION` no longer counts.

### 8.2 Runner identity
1. **Bootstrap:** an admin with `REGISTER_RUNNER` creates a registration token. It is shown once, stored hashed, single-use and expires after 1 hour.
2. **Register:** the runner generates its own key pair and sends the token plus its **public key**. The server stores the public key and returns a `runner_id`. **No shared secret exists anywhere on the server.**
3. **Authenticate:** the runner signs a short-lived client assertion (`private_key_jwt`, RFC 7523) and exchanges it at `/runner/token` for a 5-minute runner JWT (`aud=runner-api`, `org`, `runner_id`).
4. **Claim jobs:** the runner long-polls `/runner/jobs/claim` over HTTPS. It is **outbound only**, so no inbound firewall port is ever opened into the bank's network.
5. **Signed jobs:** each job envelope carries the runbook version hash, approval IDs, parameters and the execution's own signing key. It is signed with the platform key, and the runner verifies it against the JWKS before executing anything.
6. **Kafka access:** the runner presents its runner JWT to Kafka through SASL/OAUTHBEARER on a TLS listener. Its ACL allows WRITE on `tenant.<slug>.execution-output` only.
7. **Revocation:** `REVOKE_RUNNER` does the following:
   - the token endpoint refuses the runner;
   - its Kafka ACLs are removed, which takes effect on its next produce request;
   - `connections.max.reauth.ms` bounds how long an existing connection survives;
   - any running execution moves to `RUNNER_REVOKED`;
   - the change is recorded in the audit log and on the incident timeline.

### 8.3 Streaming pipeline

```
Runner ──signed batches (execution_id, seq)──► Kafka: tenant.<slug>.execution-output (key = execution_id)
Core outbox relay ──────────────────────────► Kafka: tenant.<slug>.incident-events  (key = incident_id)
                                               Kafka: tenant.<slug>.authz-events     (key = org_id)

execution-output ─► core persistence consumer ─► verifies the batch signature, deduplicates on (execution_id, seq)
                                                ─► tenant.execution_output_chunks

tenant.*.* topics ─► stream-gateway (one broadcast consumer per node, pattern subscription)
                 ─► subscription index ─► SSE ─► BFF proxy ─► browser
```

- **Kafka ACLs authorize the topic, not the record.** The per-execution signing key stops one runner from injecting output into another runner's execution within the same tenant.
- **Ordering:** using `execution_id` as the key keeps one execution's output in order on a single partition.
- **Pattern subscription:** new tenant topics are picked up on the next metadata refresh (`metadata.max.age.ms`).
- **Library caveat:** Reactor Kafka is deprecated (check its current status). Bridge a plain `KafkaConsumer`, running on its own thread, into a Reactor `Sinks.Many`.
- **Outbox relay:** claims rows from each tenant's outbox with `FOR UPDATE SKIP LOCKED`, so several relay instances run safely. Debezium change data capture is the upgrade path.

### 8.4 SSE delivery and mid-stream revocation

**Delivery**
- **Transport:** SSE goes from the browser to the BFF (authenticated by the session cookie) and is proxied to the stream gateway with the relayed internal JWT. No stream tickets are needed.
- **Why SSE rather than WebSockets:** traffic toward the browser is one-way, and commands go through REST with `Idempotency-Key`.
- **Resume:** `Last-Event-ID` is the Kafka `partition-offset`. On reconnect the gateway seeks straight back to it. Older history comes from the core API.
- **Backpressure:** each subscriber gets a bounded buffer. On overflow the gateway sends `resync` and closes, and the client reconnects with `Last-Event-ID`.

**Principle: authorization is evaluated when an event is delivered, not when it was produced.**

**Revocation path**
1. **The change and its signal are written together.** Any authorization change bumps a version and writes an `AuthzChanged` outbox event **in the same transaction**. The event includes the scope, who is affected (one user, a role or the default role) and the new versions. Changes covered:
   - editing a role's mask
   - assigning or removing a role
   - adding or changing an override
   - suspending a membership
   - revoking a session
2. **Fan-out:** the relay publishes to `tenant.<slug>.authz-events`. Session revocations go to `platform.session-events`.
3. **Targeted re-check:** each stream gateway node indexes open subscriptions by user, role, scope and `sid`. It re-evaluates only the affected subscriptions, using cached layers and the core API's bulk decision endpoint when the cache misses.
4. **Partial revocation:** each event type requires its own permission bit, for example `VIEW_EXECUTION_OUTPUT` for output and `VIEW_INCIDENT` for timeline events.
   - Losing one bit filters out those events and keeps the stream open.
   - Losing `VIEW_INCIDENT` sends a final `event: revoked` with a ProblemDetail body (`…/permission-revoked`) and closes the stream.
   - The browser's automatic reconnect then gets 403, and a non-200 response stops `EventSource` from retrying.
5. **Safety nets:**
   - a version comparison on every event (one integer comparison);
   - a database re-check of every subscription every 60 seconds;
   - a maximum stream lifetime of 1 hour, forcing re-authorization;
   - closing every stream of a revoked session.
6. **Measured, not claimed:** a histogram metric `authz_revocation_latency` records the time from the authorization change's commit to the stream being closed or filtered.

## 9. Audit and compliance
- **One hash chain per tenant.**
  - Each entry stores `seq`, `prev_hash` and `hash`.
  - The hash covers the **canonical JSON** (RFC 8785) of the event plus the previous hash.
  - Appends are serialized per tenant with an advisory lock. This deliberately caps audit write throughput per tenant, which is acceptable at this scale.
- **Database protection:** the application's database role has INSERT only on `audit_events`, and a trigger rejects UPDATE and DELETE.
- **Signed checkpoints:** a scheduled job signs (schema, seq, hash, timestamp) with an Ed25519 key. It writes the checkpoint to **MinIO with Object Lock in compliance mode** and records it in `audit_checkpoints`.
- **Verification endpoint:** recomputes the chain and checks it against the signed checkpoints. Even a database superuser cannot rewrite history without detection.
- **What is audited:**
  - role, assignment and override changes
  - invitations and their acceptance
  - runner registration and revocation
  - approval requests and decisions
  - execution lifecycle
  - incident severity and status changes
  - data exports
- **Incident report export:** timeline, linked changes, approvals and executions, with chain verification status, for regulatory review.

## 10. API conventions
- **Errors:** every error is an RFC 7807 `ProblemDetail` with a stable `type` URI catalog: `token-stale`, `tenant-mismatch`, `permission-denied`, `permission-revoked`, `segregation-of-duties`, `approval-expired`, `idempotency-conflict`, `invalid-override-scope`, `runner-revoked`.
- **Idempotency:** `Idempotency-Key` is required on unsafe operations (request execution, approve, declare incident). Keys are stored per tenant, together with a hash of the response.
- **Documentation:** the OpenAPI 3.1 spec is generated from code, alongside a Bruno collection and one static demo page for the live stream.

## 11. Observability and operations
- **Tracing:** OpenTelemetry traces run from the browser request through the BFF, core API and Kafka to the runner and stream gateway. Trace context travels in Kafka headers.
- **Prometheus metrics:**
  - `authz_revocation_latency` (histogram)
  - open streams per tenant
  - consumer lag per tenant topic
  - approval wait time
  - outbox relay lag
  - audit checkpoint age
- **Dashboard:** one Grafana dashboard covering the above.
- **Local stack:** Docker Compose with Postgres, Kafka (KRaft, SASL/OAUTHBEARER external listener), Redis, MinIO, Prometheus, Grafana, OTel Collector and all four services.
- **CI:** GitHub Actions runs the build, unit tests, Testcontainers integration tests and ArchUnit/Modulith checks.

## 12. Testing strategy

| Layer | Tooling | What it proves |
|---|---|---|
| Permission engine | JUnit 5 + jqwik property tests | Deny always beats a broader allow; the Owner can't be locked out; incident scope always wins; deprecated bits are never granted |
| Tenant isolation | Testcontainers (Postgres) | A tenant A session gets **permission denied** on tenant B's tables |
| Identity | `mock-oauth2-server` (Google and Microsoft profiles) | Issuer and `tid` validation, linking on `iss`/`sub`, invitation binding, refresh-token reuse detection |
| Kafka security | Testcontainers Kafka with OAUTHBEARER | A runner can't write to another tenant's topic; a revoked runner is cut off; forged batches are rejected |
| Module boundaries | Spring Modulith + ArchUnit | No module reaches into another's internals; no endpoint lacks authorization |
| Streaming | WebTestClient + Gatling | Revocation p50/p99 under N concurrent streams; slow consumers get `resync`; `Last-Event-ID` resume works |
| Audit | Integration tests | Tampering with a row is caught by the verification endpoint |
| Maker-checker | Integration tests | Requester can't approve; policy for SEV1 enforced; an approval invalidated by revocation is rejected at claim time |

## 13. Risks and mitigations

| Risk | Mitigation |
|---|---|
| Reactive complexity (every service is reactive, including the core API) | Keep business logic in use cases and plain domain objects, away from Reactor plumbing. Never block an event-loop thread: JDBC is used only by Flyway at startup, and all request-time database access goes through R2DBC. |
| Configuring Kafka OAUTHBEARER in Testcontainers | Spike it during M6 week 1. Fall back to SASL/SCRAM per runner if it blocks progress for more than 2 days. |
| Migrations across many schemas | Idempotent provisioning saga, migration version stored per tenant, startup check |
| Hot partitions or catalog bloat at many tenants | The design target is ≤ 200 tenants. The premium-silo path is documented. |
| Scope creep | Hard MVP cut line. Do not start the next milestone until "done when" is true. |

## 14. ADRs to write (in `docs/adr/`)

1. Schema-per-tenant for tens to hundreds of institutional tenants
2. Per-tenant Postgres roles, because `search_path` alone isn't a security boundary
3. Linking accounts on (`iss`, `sub`), and never trusting the Entra email claim
4. Strict BFF with RFC 8693 token exchange
5. Bitwise permissions with overrides at three scopes
6. Authorization evaluated at delivery time for streams
7. Per-tenant Kafka topics and ACLs
8. Direct Kafka for runners over OAUTHBEARER, with per-execution signing
9. WebFlux and R2DBC for every service, including the core API (revised from MVC with virtual threads)
10. Hash chain plus signed WORM checkpoints
11. Transactional outbox for the authorization signal

---

## 15. Milestone roadmap (3 hours a day, about 21 hours a week)

Every milestone ends with a demo you can show and tests that pass in CI. **Don't start the next milestone until "done when" is true.**

| # | Milestone | Hours | Done when |
|---|---|---|---|
| M0 | **Foundations:** Gradle multi-module build, Compose stack, CI, Testcontainers base, ProblemDetail baseline, ADRs 1–3 | 20 | `docker compose up` boots everything, and CI runs on every push |
| M1 | **Tenancy core:** platform schema, provisioning saga (schema, role, Flyway), tenant context with `SET LOCAL ROLE` | 35 | The cross-tenant permission-denied test passes, and provisioning can safely be re-run |
| M2 | **Identity and BFF:** Google and Microsoft OIDC, token exchange, internal JWT and JWKS with rotation, refresh rotation with reuse detection, Redis sessions, CSRF, invitations, logout | 50 | You can sign in with both providers, accept an invitation, switch org, and a replayed refresh token kills the session |
| M3 | **Permission engine:** bit registry, custom and default roles, overrides at service and incident scope, SQL list filtering, `@PreAuthorize` bean, ArchUnit, stale-`pv` check, caches | 45 | Property tests are green, and a confidential incident is visible only to named responders |
| M4 | **Incident domain, audit chain, outbox:** incidents, timeline, change records, hash-chained audit, outbox relay, per-tenant topic provisioning | 35 | The incident flow works end to end through the API, and a tampered audit row is detected |
| M5 | **Stream gateway:** WebFlux SSE through the BFF, timeline streaming, delivery-time authorization, full revocation path, `Last-Event-ID` resume, backpressure | 50 | Revoking `VIEW_INCIDENT` closes a live stream, and the metric records it |
| | **── MVP cut line (about 235 hours, about 11 weeks) ──** | | *The core story is now demonstrable: tenancy, identity, bitwise authorization, live revocation. Ship it publicly.* |
| M6 | **Runners and maker-checker:** registration, `private_key_jwt`, signed jobs, OAUTHBEARER into Kafka with ACLs, per-execution signing, policies per severity, re-check at claim time | 55 | A SEV1 runbook needs 2 approvers, output streams live, and a revoked runner is cut off |
| M7 | **Compliance:** Ed25519 checkpoints to MinIO with Object Lock, verification endpoint, incident report export | 20 | Chain verification passes against WORM checkpoints, and the report exports |
| M8 | **Hardening:** Gatling load tests, OpenTelemetry traces and Grafana dashboard, failure drills (kill a gateway node, pause Kafka), README with diagrams, a 3-minute demo video | 35 | Benchmark numbers are recorded with their method, and a stranger can run the demo in 10 minutes |

**Total: about 345 hours, or about 16–17 weeks.** With a 15–20% buffer, plan for around 4.5 months.

**If you need to cut, use these levers in this order:**
1. Simplify M7 to the hash chain plus a verification endpoint.
2. Drop `Last-Event-ID` resume and always replay from the core API.
3. Fold the stream gateway into the core API, which is already WebFlux. This saves about 25 hours but gives up independent scaling of stream fan-out.

---

## 16. Resume bullets

> **Fill every bracketed number from your own M8 benchmarks.** Never put a target on a resume as if it were a measurement. Document the method in the README: hardware, Gatling scenario, number of connections, revocation pattern.

- **Architected a multi-tenant incident command platform for regulated payment operators** (Java 25, Spring Boot 4, Spring WebFlux, PostgreSQL, Kafka). Each institution is isolated through schema-per-tenant storage, per-tenant database roles and tenant-scoped Kafka ACLs. **[N] automated isolation tests** verify that cross-tenant reads are impossible.
- **Built a bitwise authorization engine** with 63-bit permission masks, custom roles and allow/deny overrides at service and incident scope. It's enforced through custom `@PreAuthorize` expressions and evaluated in SQL so paginated queries stay correct, with **p99 evaluation under [X] µs**, validated by property-based tests across **[N] generated policy graphs**.
- **Engineered real-time SSE streaming** (Spring WebFlux, Kafka) with authorization checked at delivery time. Permission revocations **close live streams in p99 [≤500] ms across [5,000] concurrent connections** (Gatling), with backpressure handling and offset-based resume.
- **Implemented federated identity and compliance controls:** a BFF for Google and Microsoft OIDC that uses RFC 8693 token exchange to issue short-lived ES256 JWTs, with refresh-token rotation and reuse detection; maker-checker approvals per severity for remediation run by outbound-only runner agents (`private_key_jwt`, Kafka OAUTHBEARER); and a hash-chained audit log anchored by Ed25519-signed checkpoints in WORM object storage.

**Ordering by target:**
- For fintech (e.g. F1Soft), lead with bullets 1 and 4: isolation and compliance.
- For platform/infra roles, lead with 3, then bring forward the runner, Kafka and observability details.

---

## 17. Interview talking points (the questions this design is ready for)

| Likely question | Your answer |
|---|---|
| "Why not RBAC tables instead of bitmasks?" | Bitmasks make per-event checks on hot streaming paths cheap, carry a compact claim in the JWT, and allow bitwise filtering in SQL. The trade-offs: a 63-bit ceiling, the need to decode masks for readable audit diffs, and bits that can never be reused. |
| "Why schema-per-tenant?" | Tenants are institutions, tens to hundreds of them, not millions. It gives strong isolation, per-tenant retention and offboarding, and a path to a dedicated database later. The costs: migrations across N schemas and catalog growth. |
| "Isn't `search_path` enough?" | No. One fully qualified query crosses tenants. Hence per-tenant roles, `SET LOCAL`, and a test that proves permission is denied. |
| "Why link on `sub` and not email?" | Emails get reassigned. The Entra email claim is mutable by tenant admins (nOAuth). |
| "Why a BFF?" | No tokens in the browser at all, which follows current OWASP guidance for browser apps. JWTs still do real work across the service hops behind it. |
| "What happens if a revocation event is lost?" | It's written in the same transaction as the change (outbox), every event is version-checked, every subscription is re-checked against the database every 60 seconds, and streams have a maximum lifetime. |
| "Can a runner in tenant A affect tenant B?" | No. Topic ACLs are tenant-scoped, and within a tenant the per-execution signing key blocks forged output. |
| "What stops an admin from rewriting the audit log?" | INSERT-only grants, a trigger that rejects updates and deletes, and signed checkpoints in WORM storage outside the database. |
