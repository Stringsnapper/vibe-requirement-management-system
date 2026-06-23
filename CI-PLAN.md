# CI Pipeline Plan — GitHub Actions

**Project:** Requirement Management System (self-hosted RMS for Class B SaMD, EU + US)
**Stack:** Kotlin (Java 21) · Spring Boot 3.3 · Gradle (Kotlin DSL) · PostgreSQL + Flyway · Thymeleaf/HTMX
**Status:** Draft v0.1 — plan only, no workflow files committed yet
**Companion docs:** [PLAN.md](PLAN.md) · [DATA-MODEL.md](DATA-MODEL.md) · [CLAUDE.md](CLAUDE.md)

---

## 1. Purpose and principles

Continuous integration for this repository is not just about catching broken builds. Because the product is a tool used to develop medical device software, the CI pipeline is itself part of the development environment that an auditor may scrutinise (tool validation, IEC 62304 §5.1.4 / §8). The pipeline is therefore designed around four principles:

- **Reproducible.** Pinned JDK, pinned action versions, the committed Gradle wrapper, and a single source of build truth. The same commit produces the same result on any runner.
- **Traceable.** Every artifact (test report, coverage report, SBOM, JAR, image) is tied to the exact commit SHA that produced it, and retained long enough to serve as evidence.
- **Gated, not advisory.** Checks that matter (tests, coverage floor, vulnerability scan) are *required* status checks on protected branches — they block merge rather than merely warning.
- **Low-dependency, like the product.** The pipeline leans on first-party GitHub features (Actions, Dependabot, CodeQL, secret scanning, GHCR) and a small number of well-maintained marketplace actions, mirroring the project's deliberate "boring and low-dependency" philosophy (PLAN §8).

The plan is staged (see §11) so CI can grow with the delivery phases rather than landing all at once.

---

## 2. Branch model and triggers

The repository currently has a single `development` branch. The pipeline assumes a trunk-plus-PR model:

- **`main`** — release/integration branch. Protected. Only updated via reviewed pull request.
- **`development`** — active integration branch. Protected. Day-to-day work merges here via PR.
- **feature branches** — short-lived, merged into `development` via PR.

Trigger matrix:

| Workflow | `pull_request` → `development`/`main` | `push` to `development`/`main` | tag `v*` | schedule |
|---|---|---|---|---|
| Build & test | ✓ | ✓ | — | — |
| Quality gates (coverage, lint, commit lint) | ✓ | ✓ | — | — |
| Security & supply chain (CodeQL, deps, SBOM, secrets) | ✓ (fast subset) | ✓ | — | weekly (full scan) |
| Release-please (version + changelog PR) | — | ✓ (on `main`) | — | — |
| Build & publish artifacts | — | ✓ (snapshot on `main`) | ✓ (release tag) | — |

`concurrency` groups cancel superseded runs on the same branch/PR to save minutes. The full, slower security scan runs on a weekly `schedule` and on `main` pushes; PRs run the fast subset to keep feedback under a few minutes.

---

## 3. Pipeline overview

Three workflow files, separated by concern and lifecycle so each can evolve independently:

```
.github/
  workflows/
    ci.yml             # build, test, quality gates — runs on every PR and push
    security.yml       # CodeQL, dependency review, OWASP/Trivy, SBOM, secret scan
    release-please.yml # maintains the version + changelog release PR (on main)
    release.yml        # JAR + Docker image build and publish (on release tag)
  dependabot.yml       # automated dependency update PRs (Gradle + Actions)
  release-please-config.json + .release-please-manifest.json  # versioning config
```

Rationale for splitting rather than one mega-workflow: security scans and release builds have different triggers, permissions, and runtimes than the fast inner-loop `ci.yml`. Keeping them separate means the everyday PR check stays quick, and the privileged release job (which needs write access to the registry) is isolated from untrusted PR code.

All workflows set least-privilege permissions at the top (`permissions: contents: read`) and elevate per-job only where needed.

---

## 4. Core build & test (`ci.yml`)

The foundation everything else builds on.

**Environment**

- Runner: `ubuntu-latest`.
- JDK: Temurin **21** via `actions/setup-java@v4` (matches the Gradle toolchain `JavaLanguageVersion.of(21)` in `build.gradle.kts`).
- Build tool: the committed `./gradlew` wrapper — never a separately installed Gradle, so the version is pinned by the repo.
- Gradle caching: `gradle/actions/setup-gradle@v4`, which handles dependency and build caching with the correct cache keys (preferred over hand-rolled `actions/cache`).

**Steps**

1. `actions/checkout@v4` (with `fetch-depth: 0` only where commit-lint or versioning needs full history).
2. `setup-java` (Temurin 21).
3. `setup-gradle`.
4. `./gradlew build` — compiles, runs unit/integration tests, and assembles. The existing tests use an in-memory **H2** database (`spring-security-test` + `h2` are already test dependencies and `src/test/resources/application-test.yml` exists), so the core test job needs **no external Postgres service** today.
5. Publish results:
   - Upload `build/reports/tests/` and `build/test-results/` as workflow artifacts.
   - Surface JUnit results as a PR check (e.g. `mikepenz/action-junit-report` or `dorny/test-reporter`) so failures are visible inline.

**Postgres-backed integration tests (forward-looking).** As later phases add JPA/Flyway integration tests that need real Postgres behaviour, two options:
- **Testcontainers** (preferred) — spins up `postgres:16` from inside the test JVM; no workflow changes, keeps the build self-contained and matches `docker-compose.yml`.
- **GitHub Actions `services:`** — a `postgres:16` service container with a health check mirroring `docker-compose.yml`, exposed to the job.

The plan recommends Testcontainers so local and CI runs stay identical and Flyway migrations are exercised against the real engine.

---

## 5. Quality gates (`ci.yml`)

Run alongside build/test; each is a required check on protected branches.

**Code coverage — JaCoCo.** Add the `jacoco` Gradle plugin and a `jacocoTestReport` task (XML + HTML). CI uploads the HTML report as an artifact and posts a coverage summary to the PR. Enforce a floor with `jacocoTestCoverageVerification` (start modest, e.g. 60% line coverage, ratchet upward per phase rather than demanding a high bar on day one). For a validated tool, the trend and the floor matter more than a vanity number.

**Kotlin lint / static analysis.** Two complementary tools:
- **ktlint** (via the `ktlint` Gradle plugin or `./gradlew ktlintCheck`) — formatting and style, fast, opinionated, near-zero config.
- **detekt** — deeper static analysis (complexity, potential bugs, code smells); can additionally emit SARIF that GitHub renders in the Security tab.

Recommendation: adopt ktlint first (cheap, deterministic) and add detekt in a later phase. Run them as a separate, parallel job so a style failure doesn't mask a real test failure.

**Conventional Commits enforcement.** CLAUDE.md mandates Conventional Commits on *every* commit, and — with the decision to **preserve commits** (no squash, see §10) — every individual commit also feeds automatic version bumps and the changelog (§7a). The history must therefore be clean at the commit level, so the authoritative gate is:
- **Commit message lint** — `commitlint` (`wagoid/commitlint-github-action`) over the PR's full commit range, using `@commitlint/config-conventional` plus the project's allowed-types list (`feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `revert`). Every commit in the PR must validate.
- **PR title check** (`amannn/action-semantic-pull-request`) — kept as a lightweight secondary check so the PR is also well-described; not authoritative, since merge preserves the underlying commits rather than the title.

A local `commit-msg` git hook (e.g. via a committed `commitlint` config + `pre-commit` or a small shell hook) is recommended so authors catch violations before pushing, not in CI.

---

## 6. Security & supply chain (`security.yml` + `dependabot.yml`)

Directly relevant to SaMD audit evidence: known-vulnerability management and a software bill of materials are increasingly expected (FDA premarket cybersecurity guidance, EU MDR Annex I cybersecurity GSPRs). The product manages medical device records, so its own supply chain must be defensible.

**Dependency updates — Dependabot (`dependabot.yml`).** Enable two ecosystems:
- `gradle` — weekly PRs for dependency upgrades (Spring Boot, Kotlin, Flyway, etc.).
- `github-actions` — keeps pinned action versions current.
Group minor/patch updates to reduce PR noise; keep majors separate for deliberate review.

**Dependency review on PRs.** `actions/dependency-review-action` blocks a PR that introduces a dependency with a known vulnerability or a disallowed licence — cheap, runs only on PRs, gives immediate feedback.

**Vulnerability scanning of the dependency tree.** One of:
- **OWASP Dependency-Check** (Gradle plugin) — thorough, NVD-backed, produces an HTML/JSON report suitable as an audit record. Heavier (NVD data download) — cache it; run on schedule + `main`, not every PR.
- **Trivy** (filesystem and, later, image scan) — fast, broad, SARIF output into the Security tab.
Recommendation: Trivy for fast PR feedback, OWASP Dependency-Check on the weekly schedule for the formal, retainable report.

**SAST — CodeQL.** `github/codeql-action` with the `java-kotlin` language pack. Runs on PR (subset) and weekly. Results land in the Security tab and can be a required check.

**SBOM generation.** Produce a CycloneDX SBOM (`cyclonedx-gradle-plugin`) on release builds, attach it to the GitHub Release and the container image. This is the artifact auditors and downstream customers increasingly ask for.

**Secret scanning.** Enable GitHub's native secret scanning + push protection on the repo (a settings toggle, not a workflow). Optionally add `gitleaks` as a workflow step to scan PR diffs for credentials before merge.

---

## 7a. Automatic semantic versioning & changelog (`release-please.yml`)

Versioning is **derived from the Conventional Commits**, not bumped by hand. The tool of choice is **release-please** (Google, language-agnostic), chosen over `semantic-release` because it introduces a human review gate that suits a regulated release — important when each release may correspond to a validated build.

**How it works**

1. On every push to `main`, `release-please` scans the Conventional Commits since the last release and computes the next [SemVer](https://semver.org/):
   - `fix:` → patch (`0.0.x`), `feat:` → minor (`0.x.0`), `feat!:` / `BREAKING CHANGE:` → major (`x.0.0`).
   - `docs/chore/ci/test/...` don't trigger a release on their own but are recorded.
2. It opens (and keeps updating) a **"release PR"** that bumps the version in `build.gradle.kts` and writes a grouped, categorised `CHANGELOG.md` entry built from the commit messages.
3. **Merging that release PR** is the release action: release-please creates the git tag (`v<version>`) and a GitHub Release with the changelog body.
4. The tag fires `release.yml` (§7), which builds and publishes the artifacts for that exact version.

This gives a clean separation: commits accumulate continuously, but a release is a deliberate, reviewable, auditable event (merge the PR) — and the changelog and version number are generated, never typed.

**Config.** `release-please-config.json` sets `release-type: simple` with an `extra-files` entry to update the `version = "..."` line in `build.gradle.kts`; `.release-please-manifest.json` tracks the current version. Because commits are preserved (not squashed), release-please sees the full, fine-grained commit stream — which is exactly why the §10 "preserve commits" decision matters here.

**Pre-1.0 note.** The project is at `0.0.1-SNAPSHOT`. Under SemVer 0.x, `feat:` still bumps the *minor* and breaking changes the *minor* (not major) until you cut `1.0.0`; release-please follows this `bootstrap-sha`/initial-version behaviour. Decide when to declare `1.0.0` (suggest: first audit-ready release, ~Phase 5).

---

## 7. Build & publish artifacts (`release.yml`)

Triggered on the `v*` tag that release-please (§7a) creates, and optionally on push to `main` for a `-SNAPSHOT` image.

**Executable JAR.** `./gradlew bootJar` produces the single executable JAR (PLAN §8 packaging). Upload as a workflow artifact for every `main` build; attach to the GitHub Release for tags.

**Docker image — build method (explicit `Dockerfile`, recommended).** There are two ways to produce the image:
- **Explicit `Dockerfile`** — you write a small multi-stage file: pin a base such as `eclipse-temurin:21-jre` (ideally by digest), copy the layered `bootJar`, set the entrypoint. *You* control and can see every layer and the exact base image.
- **Spring Boot buildpacks** (`./gradlew bootBuildImage`) — no Dockerfile; Paketo Cloud Native Buildpacks auto-detect the app and assemble an optimised image. Less to write, but the base image and contents are chosen by the buildpack — more of a black box.

For a SaMD tool the practical difference is **auditability and reproducibility**: with a Dockerfile you can state and pin exactly what's in the image (base OS, JRE, CVE surface), which is far easier to document and defend in a technical file than "whatever Paketo selected this week." Recommendation: **explicit Dockerfile**, base pinned by digest, built with `docker/build-push-action` + `docker/setup-buildx-action`.

**Registry — GHCR for now.** Push to **GitHub Container Registry (GHCR)**: it's currently free for private images, needs no credentials beyond the scoped `GITHUB_TOKEN`, and the `release.yml` job simply elevates to `packages: write`. Caveat to keep on file: GHCR is free *for now*, with GitHub committing to one month's notice before any billing — not a concern at this stage.

**Migration note.** A likely future move is taking the whole project to GitLab, at which point the natural registry is the **GitLab Container Registry** (free, tightly integrated with GitLab CI). Keep the registry swappable so this is painless: the only registry-specific parts of the workflow are the login step and the image-name prefix (`ghcr.io/<owner>/...` → `registry.gitlab.com/<group>/...`), so switching later is a few-line edit. (A self-hosted Harbor remains an option if you ever want the registry on your own infrastructure, matching the product's self-hosted ethos.)

**Versioning & traceability.** Tag images with both the git SHA and the release-please semantic version (`docker/metadata-action`). Stamp the build with the commit SHA and build timestamp (Gradle `springBoot { buildInfo() }`, exposed at `/actuator/info`) so a deployed instance is traceable back to its exact source — important for the "reproducible at any point in time" requirement (PLAN §1).

**Permissions.** The publish job elevates to `packages: write` (and `contents: write` for release assets); no other job gets registry write. PR-triggered runs never publish.

---

## 8. Branch protection & required checks

Configured in repo settings (not in YAML), but part of the plan because the gates are only meaningful when enforced:

- Protect `main` and `development`: require PR, require review, require status checks to pass, require branches up to date before merge, and disallow force-push.
- Required checks (recommended initial set): **build & test**, **coverage verification**, **ktlint**, **commitlint (Conventional Commits)**, **dependency review**, **CodeQL**.
- Use **"Rebase and merge"** or **"Create a merge commit"** and **disable "Squash and merge"** in repo settings, so individual commits are preserved (decision §10) and reach release-please intact.
- Disallow merge while any required check is failing or pending.
- (Phase 6 / regulated maturity) require signed commits and a linear history.

---

## 9. Performance, caching & cost

- `gradle/actions/setup-gradle@v4` caches the Gradle user home and configuration cache, keyed on wrapper + lockfiles.
- `concurrency` cancels superseded runs per branch/PR.
- Split fast (PR) vs. thorough (scheduled/`main`) security scans so PR feedback stays quick.
- Run independent jobs (test, lint, CodeQL) in parallel.
- Pin all third-party actions to a tag or, for the regulated posture, a commit SHA — protects against a compromised upstream action and makes runs reproducible.

---

## 10. Resolved decisions

| # | Decision | Choice | Notes |
|---|---|---|---|
| 1 | Merge strategy | **Preserve commits** (no squash) | Fine-grained history; every commit is conventional and feeds release-please. `commitlint` over the full commit range is the authoritative gate (§5). Trade-off below. |
| 2 | Coverage floors | **Tiered, ratcheting** | See §10.1. |
| 3 | Vulnerability tooling | **Trivy + OWASP Dependency-Check** | Trivy fast on PRs; OWASP weekly for the retainable report (§6). |
| 4 | Container build | **Explicit Dockerfile**, base pinned by digest | More auditable than buildpacks (§7). |
| 5 | Integration-test DB | **Testcontainers** (`postgres:16`) | Local and CI identical; exercises Flyway on the real engine (§4). |
| 6 | Image registry | **GHCR for now** | Currently free for private images; first-party `GITHUB_TOKEN` auth, zero extra setup. Likely migrate to GitLab Registry if/when the whole project moves to GitLab (§7). |
| 7 | Versioning & releases | **release-please**, Conventional-Commit-driven | Auto version + changelog + reviewable release PR (§7a). |

### 10.1 Is "preserve commits" a good idea?

Yes, with one condition. Preserving commits gives you the fine-grained history you want, and it's what lets release-please build a complete, accurate changelog and version bump from the real work (squashing collapses several `feat:`/`fix:` into one and loses that signal). The condition is **discipline**: every commit — not just the PR — must be a clean Conventional Commit, because they all become public history and changelog entries. That's enforced by `commitlint` in CI plus a local `commit-msg` hook (§5). If commit hygiene ever becomes a burden, the fallback is "squash with a conventional PR title," but you'd lose granularity in the changelog. Given the regulated, traceability-first goals of this project, preserve-commits is the right default.

### 10.2 Coverage thresholds (JaCoCo)

A young codebase shouldn't be gated at 90% on day one — that just encourages gaming. Suggested starting point, ratcheting per delivery phase:

| Scope | Initial floor | Target (by Phase 5) |
|---|---|---|
| Overall line coverage | 60% | 80% |
| Overall branch coverage | 45% | 70% |
| Core packages — `com.rms.domain`, `com.rms.service`, `com.rms.audit` | 75% | 90% |

Rationale: the **domain, service, and audit** code is the compliance-critical core (versioning, status axes, the Part 11 audit trail) and deserves a higher bar than the whole repo. **Exclude** boilerplate from the denominator so the number is meaningful: `RmsApplication.kt`, `com.rms.bootstrap` (data initializer), config classes, and Thymeleaf templates. Set the floors with `jacocoTestCoverageVerification` and raise them one phase at a time rather than all at once — a ratchet that only goes up.

---

## 11. Suggested rollout (mapped to delivery phases)

Aligns with PLAN §10 so CI rigor grows with the codebase rather than front-loading everything.

| Step | Lands with | Adds |
|---|---|---|
| **CI-0** | now (Phase 0 complete) | `ci.yml`: checkout → JDK 21 → Gradle build & test, JUnit report, branch protection with build/test required. |
| **CI-1** | Phase 1 | JaCoCo coverage + floors; ktlint; `commitlint` (full commit range); Dependabot (gradle + actions). Optionally enable `release-please.yml` now so the changelog accrues from the first feature commit (publishing waits for CI-3). |
| **CI-2** | Phase 2–3 | `security.yml`: CodeQL, dependency review, Trivy (fast) + OWASP (weekly); secret-scanning/push-protection enabled. Testcontainers Postgres integration tests in CI. |
| **CI-3** | Phase 5 | `release-please.yml` (auto version + changelog + release PR); `release.yml`: bootJar + Docker image (explicit Dockerfile) to GHCR, SBOM (CycloneDX), build-info stamping, SHA+semver tagging. |
| **CI-4** | Phase 6 (hardening) | detekt, SHA-pinned actions, signed commits, formal retained audit reports, optional image signing (cosign); declare `1.0.0`. Migrate registry to GitLab if the project moves there. |

---

## 12. Next step

All §10 decisions are resolved, so the workflows can be written. Suggested first commit, `CI-0`: a minimal, green `ci.yml` plus `dependabot.yml`, as a single `ci:`-scoped Conventional Commit, so the pipeline is live the moment the GitHub remote is pushed. `commitlint`, JaCoCo, ktlint, and `release-please.yml` follow in `CI-1`. Say the word and I'll write them.
