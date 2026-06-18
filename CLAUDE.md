# CLAUDE.md — Working guidelines for this repository

Project context lives in [PLAN.md](PLAN.md) and [DATA-MODEL.md](DATA-MODEL.md). Read those first.
This is a self-hosted **Requirement Management System** for Class B SaMD (EU + US),
built as a deliberately boring, low-dependency, server-rendered Spring Boot application.

## Commit conventions — IMPORTANT

**Always use [Conventional Commits](https://www.conventionalcommits.org/).** Every commit message
must follow `type(scope): subject`:

- **Allowed types:** `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `revert`.
- **Scope** is optional but encouraged — use the area touched, e.g. `feat(domain):`, `build(gradle):`, `feat(audit):`.
- Subject is imperative, lower-case, no trailing period, ≤ ~72 chars.
- Breaking changes: add `!` after the type/scope (`feat(api)!:`) and/or a `BREAKING CHANGE:` footer.
- Keep commits focused; one logical change per commit.

This applies to **every** commit in this repository without exception.

## Tech stack (see PLAN §8)

- Kotlin on the JVM (Java 21 LTS), Spring Boot (MVC, Data JPA, Security).
- Thymeleaf + HTMX for server-rendered UI (no SPA, no npm build step).
- PostgreSQL with Flyway versioned SQL migrations (never edit an applied migration — add a new one).
- Gradle (Kotlin DSL). Single executable JAR or Docker + Postgres via docker-compose.

## Conventions

- Package root: `com.rms`.
- Audited tables are append-only / never hard-deleted; all changes flow through the audit trail.
- Enums are fixed, constrained vocabularies — never free text.
- Standard layering: controller → service → repository; keep the domain free of web concerns.
- Run `./gradlew test` before committing; keep the build green.

## Delivery phases

Tracked in PLAN §10. Currently building **Phase 0 — Foundations**.
