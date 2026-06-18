# Requirement Management System

Self-hosted requirement management for Class B Software as a Medical Device (SaMD), EU + US.
See [PLAN.md](PLAN.md) and [DATA-MODEL.md](DATA-MODEL.md) for the full design.

## Status

**Phase 0 — Foundations** (current). Spring Boot + Kotlin skeleton, PostgreSQL + Flyway,
base item/version model, auth + roles, and an append-only audit trail.
*Outcome: you can create a versioned item and see it audited.*

## Stack

Kotlin (Java 21) · Spring Boot (MVC, Data JPA, Security) · Thymeleaf · PostgreSQL · Flyway · Gradle.

## Running locally

```bash
# 1. Start PostgreSQL
docker compose up -d db

# 2. Run the application
./gradlew bootRun
```

Then open <http://localhost:8080> and sign in with the seeded credentials **admin / admin**
(created on first boot — change the password). A sample project `DEMO` with a `CORE` component
is created automatically.

### Configuration

Database connection is overridable via environment variables (defaults in `application.yml`):

| Variable | Default |
|---|---|
| `RMS_DB_URL` | `jdbc:postgresql://localhost:5432/rms` |
| `RMS_DB_USER` | `rms` |
| `RMS_DB_PASSWORD` | `rms` |

## Tests

```bash
./gradlew test
```

Tests run against in-memory H2 (PostgreSQL compatibility mode); no database is required.

## Audit trail

Every change to an audited entity (`Project`, `Component`, `Item`, `ItemRevision`) is captured
in the append-only `audit_entry` table by a Hibernate interceptor
([`AuditInterceptor`](src/main/kotlin/com/rms/audit/AuditInterceptor.kt)), so it cannot be
bypassed by ordinary code paths. The dashboard and each item page show the trail.

## Conventions

Commits follow [Conventional Commits](https://www.conventionalcommits.org/). See [CLAUDE.md](CLAUDE.md).
