# Requirement Management System — Initial Plan

**Project:** Self-hosted requirement management system for regulated software
**Target context:** Class B Software as a Medical Device (SaMD), EU + US markets
**Backend:** Kotlin + Spring Boot (server-rendered, minimal external dependencies)
**Status:** Draft v0.2 — for review

> **Guiding design principle — guided and guarded, not open-ended.** An open-ended requirements tool is easy to use incorrectly, and in a regulated context that means audit findings. This system is deliberately opinionated: it walks the user through the right next step, makes the relationships between items obvious, and warns loudly when something is missing or inconsistent. See §12 for how this is enforced throughout the UI. *(All §11 open questions are now resolved — see §11.)*

---

## 1. Purpose and scope

The system manages the full requirements lifecycle for medical device software and keeps every artifact traceable, versioned, and audit-ready. It is built for a regulatory-heavy context where each requirement, test, risk, and design output must be linked, justified, and reproducible at any point in time.

In scope for the product:

- Authoring and management of User Needs, System Requirements, Software Requirements (SRS), and Design specifications.
- Design Verification and Software Verification, including a composite-test capability that connects concrete test cases to requirements.
- Risk management: Hazards, Risks, risk controls, and sFMEA (software failure mode and effects analysis).
- Real-time visualization of the traceability matrix and a traceability graph.
- Export of static, controlled reports (PDF/DOCX/CSV) for design history files, audits, and submissions.
- Item-level and baseline-level versioning with full history and audit trail.

Out of scope for the first release (candidates for later): full QMS/document control, CAPA management, supplier management, and clinical evaluation. The system is designed to interoperate with these rather than replace them.

---

## 2. Regulatory alignment

For Class B SaMD sold in both the EU and the US, the data model and workflows are designed to satisfy the following standards. The system does not "certify" anything — it provides the structure, traceability, and records auditors and notified bodies expect.

| Standard / regulation | Market | What the system provides |
|---|---|---|
| **IEC 62304** (software lifecycle) | EU + US | Software safety classification (A/B/C), SRS management, software unit/integration/system verification, traceability from requirement → design → test, problem/anomaly linkage. |
| **ISO 14971** (risk management) | EU + US | Hazard → hazardous situation → harm chains, risk estimation (severity × probability), risk controls, residual risk, and risk-control traceability to requirements and verification. |
| **ISO 13485** (QMS context) | EU + US | Design controls structure (design inputs, outputs, verification, validation), controlled records, change control hooks. |
| **FDA 21 CFR 820.30** (design controls) | US | Design input/output/verification/validation traceability and a Design History File (DHF) export. |
| **FDA 21 CFR Part 11** (electronic records & signatures) | US | Immutable audit trail, electronic signatures with meaning/intent, access controls, record integrity. |
| **EU MDR 2017/745** (incl. Annex II technical documentation) | EU | Technical documentation outputs, GSPR-style requirement linkage, and traceability for the technical file. |
| **IEC 62366-1** (usability engineering) | EU + US | Use-related requirements and use-error risk linkage into the risk model. |

A short traceability requirement underpins all of these: **every User Need traces down to verification, and every verification traces back up to a User Need.** Orphan detection (items with no upstream or downstream link) is a first-class feature, because gaps in this chain are the most common audit findings.

---

## 3. Core domain model

The domain is a set of versioned, linkable items. The central abstraction is a **Requirement-like Item** that participates in typed trace links. Concrete item types share common behavior (identity, version, status, history) but carry type-specific fields.

**Item types and the trace hierarchy (top to bottom):**

1. **User Need** — what the user/patient/clinician requires. Top of the trace chain.
2. **System Requirement** — system-level behavior derived from user needs.
3. **Software Requirement (SRS)** — software-specific requirements derived from system requirements; carries IEC 62304 software safety class.
4. **Design Specification / Software Design** — how a requirement is realized (architecture, units).
5. **Verification** — Design Verification and Software Verification activities and their results.

**Risk side (linked across the hierarchy):**

6. **Hazard** — potential source of harm.
7. **Risk** — a hazard → hazardous situation → harm chain with severity and probability, pre- and post-mitigation.
8. **Risk Control** — a measure that reduces risk; implemented as (or linked to) a requirement and proven by verification.
9. **sFMEA Entry** — failure mode, cause, local/system effect, severity/occurrence/detection, RPN, and recommended actions.

**Testing side:**

10. **Test Case** — a single executable check (steps, expected result, preconditions).
11. **Composite Test** — an ordered/grouped set of test cases that, together, verify one or more requirements (see §6).
12. **Test Execution / Result** — a run of a test or composite test at a point in time, with pass/fail, evidence, executor, and signature.

**Supporting entities:** Project, Component (§3.1), Baseline (a named, frozen set of item versions — §5), TraceLink (typed relationship between two items), User/Role, AuditEntry, ElectronicSignature, Attachment/Evidence, and Comment/Review record.

### 3.1 Components (software items)

A product is decomposed into **Components** — software items that are managed separately, each with its own requirement set (maps to the IEC 62304 software-item concept). A Software Requirement belongs to **exactly one component**, and the component code is part of the item's human key (e.g. `CGM-SENSOR-SRS-014`) so ownership is visible at a glance. Design specs, test cases, and sFMEA entries are likewise component-scoped; user needs, system requirements, hazards and risks stay at the system level and decompose *into* component requirements via `derives_from`. Components can nest, and the traceability matrix and coverage dashboard can be filtered per component so each software item can be reviewed and verified on its own.

A visual entity/relationship diagram accompanies this plan (see `data-model.mermaid`).

### Trace links

Trace links are first-class, typed, and directional, e.g. `derives_from`, `verifies`, `mitigates`, `implements`, `relates_to`. Modeling links as their own entity (rather than foreign keys scattered across tables) is what makes the traceability matrix, graph, and impact analysis straightforward and uniform. Each link is itself versioned so the matrix can be reconstructed for any historical baseline.

---

## 4. Traceability: matrix and graph

Two real-time views over the same trace-link data:

**Traceability matrix** — a configurable grid (e.g. User Needs × Software Requirements, or Requirements × Test Cases). Cells show link presence/type and roll-up coverage status. The matrix surfaces gaps directly: requirements with no verification, risks with no control, controls with no proof. It can be scoped to a baseline so you can show the matrix "as of" any release.

**Traceability graph** — an interactive node-link diagram for exploring relationships around a single item (upstream and downstream), useful for impact analysis when a requirement changes. The server renders the graph data; the browser draws it with a small, dependency-light visualization layer (see §8 on the front-end approach).

**Impact analysis** — given a changed item, the system computes the transitive set of linked items (downstream tests to re-run, upstream needs affected) so reviewers see the blast radius before approving a change.

---

## 5. Versioning

Requirements are **living items** that evolve, so versioning is layered and — importantly — status is modeled as several independent axes rather than one linear lifecycle. See the accompanying `status-lifecycle.mermaid` diagram.

**Item-level versioning (revisions).** Every item has an append-only history of revisions. A revision is immutable once approved; editing an approved/released revision spawns a new Draft revision with author, timestamp, change reason, and a diff against the prior one. An `is_current` pointer marks the working revision. So an item may simultaneously hold, e.g., revision 3 (released in R1.0, frozen) and revision 4 (Draft, targeting R1.1).

**Status is four independent axes, not one line.** Approval, implementation, verification, and release progress are orthogonal — a requirement can be *approved but not implemented*, *approved and implemented but not released*, or *implemented but not yet verified*. Collapsing these into a single enum can't represent those real states, so the model uses one governed status plus three derived progress dimensions:

| Axis | Values | Set by |
|---|---|---|
| **Lifecycle status** | Draft → In Review → Approved → Deprecated → Retired (Declined loops back to Draft) | Manual, role-gated, e-signed — governs the *spec* only |
| **Implementation** | Not implemented → Implemented | Derived from a linked, approved design/software output (or a dev flag requiring that link) |
| **Verification** | Unverified → Verified / Failing / Stale | Derived from linked tests and their latest executions |
| **Release** | Unreleased → Released in R*x* → Deprecated in R*y* | Derived from baseline membership |

Keeping Implementation, Verification, and Release *derived* (not hand-set statuses) prevents the data from drifting out of sync with the actual links and baselines.

**Freeze and assignment.**

- A revision **freezes** (becomes immutable) when it reaches **Approved** — this is what lets it be safely reviewed and assigned before any release exists.
- A revision can be **assigned to a release without freezing** — i.e. targeted at a *draft* baseline for planning, while still editable.
- **Releasing a baseline freezes every revision in it** and stamps the release id on each.
- Need to change a released requirement? It stays frozen and released; a new Draft revision is created as the working copy for the next release.

**Carry-forward.** The next release's working set automatically inherits all current revisions; untouched requirements ride along unchanged until a new revision is created or they are deprecated. A revision also records its effective **release range** (*introduced in R1.2 → superseded/deprecated in R2.0*), which makes "show me the requirements as of R1.5" a simple query.

**Baseline (set) versioning.** A Baseline is a named snapshot that pins a specific revision of every included item *and the trace links between them*. This is what gets reviewed, signed, exported as a DHF, and submitted. Baselines are comparable (diff between Baseline 1.0 and 1.1: which requirements were added/changed/removed, which tests re-run). This satisfies the project goal of versioning "each requirement … as well as that specific set of requirements."

**Release guardrail.** A baseline cannot be released while any member requirement is not Approved + Verified (and normally Implemented). The system lists each offending requirement and what's missing; releasing anyway requires a documented, signed deviation. This is where the multi-axis model pays off — "approved + implemented but not verified" is exactly the state caught here.

Implementation approach: an append-only revision table per item with an `is_current` pointer, derived progress flags computed from links/executions/baselines, and a Baseline → BaselineItem join that records the exact revision id of each member. No destructive updates anywhere in the audited tables.

---

## 6. Testing and composite tests

Standard test management plus the requested composite capability:

- **Test Case**: identity, preconditions, steps, expected results, type (unit/integration/system/manual/automated), and links to the requirement(s) it verifies.
- **Composite Test**: a named container that sequences or groups multiple test cases into a higher-level verification. A composite test can be connected to one requirement (full verification of a complex requirement by several steps) or aggregate several test cases that together cover a requirement set. Coverage rolls up: a requirement is "verified" only when its linked tests (including all members of a linked composite) have passing executions in the current baseline.
- **Test Execution**: a recorded run with environment, date, executor, evidence attachments, pass/fail per step, and an electronic signature on completion. Results are tied to the item versions that were current at execution time, so a result is never silently invalidated by a later edit — instead it is flagged stale for re-verification.

This makes "what proves this requirement, and is that proof current?" answerable at a glance, which is the core of design verification evidence.

---

## 7. Risk management and sFMEA

Risk is modeled per ISO 14971 and linked into the requirement/verification chain:

- **Hazard → Hazardous situation → Harm** chains with severity and probability, producing an initial risk level via a configurable risk matrix.
- **Risk Controls** reduce risk and are implemented as requirements (or linked to existing ones), then proven by verification — so every control is traceable to both a requirement and its evidence. Residual risk and risk/benefit are recorded post-mitigation.
- **sFMEA** captures software failure modes with cause, effect, severity/occurrence/detection ratings and RPN, plus recommended actions that can spawn requirements or controls.
- Risk items appear in the same traceability matrix/graph, so "every identified risk has a control, and every control is verified" is checkable, and use-error risks (IEC 62366-1) flow into the same model.

A **Risk Management File** view aggregates all of this for export.

### 7.1 Where the risk scales come from

ISO 14971 deliberately does **not** prescribe severity/probability levels or an acceptability threshold — the *manufacturer* defines them in its risk management plan/policy (ISO 14971 §4.4 and Annex C). So in this system the scales are configuration, not hard-coded: an admin-editable, per-project **Risk Policy** defines the severity levels, probability levels, and the acceptability matrix (which severity×probability cells are acceptable / ALARP / unacceptable). The Risk Policy is itself versioned and recorded, so every risk evaluation is tied to the policy in force at the time. A sensible default ships out of the box (e.g. a 5×5 severity×probability matrix with three acceptability bands) that the quality team can tailor.

---

## 8. Architecture

A conventional, server-rendered Spring Boot application — deliberately boring and low-dependency so it is easy to self-host, validate, and maintain over a long regulated lifecycle.

**Stack**

- **Language/Runtime:** Kotlin on the JVM (LTS, e.g. Java 21).
- **Framework:** Spring Boot — Spring MVC, Spring Data JPA, Spring Security.
- **Templating:** Thymeleaf for server-rendered HTML (no SPA, no React).
- **Interactivity:** HTMX (single small JS file, no build step) for partial updates — live matrix/graph refresh, inline editing, filtering — without a JavaScript framework. Hyperscript/Alpine optional and minimal if needed.
- **Visualization:** a single lightweight, dependency-free or near-zero-dependency library for the graph (e.g. Cytoscape.js or vis-network loaded as a static asset); the matrix is plain server-rendered HTML/CSS. No npm/bundler toolchain required.
- **Persistence:** PostgreSQL. Append-only/audited tables for versioned items.
- **Schema migrations:** Flyway (versioned SQL migrations — important for a validated system).
- **Reporting/export:** server-side generation — see §9.
- **Build:** Gradle (Kotlin DSL).
- **Packaging/deploy:** single executable JAR or a Docker image + Postgres via docker-compose; runs on-prem with no external SaaS dependencies.
- **Auth:** Spring Security with role-based access (e.g. Author, Reviewer, Approver, QA, Admin), form login now, pluggable to SSO/LDAP/OIDC later.
- **Multi-product, single team:** one installation, one user directory, but every item, baseline, and report is scoped to a **Project (= product)**. A persistent project switcher in the top navigation lets the team move between products in one click; the chosen project is remembered per user. Roles can be assigned per project. This is multi-*project*, not multi-tenant — far simpler to build and validate, and a good fit since one team owns all products. Cross-project reuse of items can be added later if needed.

**Layering:** standard controller → service → repository, with the domain (items, versions, links, baselines) isolated from web concerns so reporting and a future REST/API surface reuse it.

**Cross-cutting for compliance:**

- **Audit trail (Part 11):** every create/update/delete/sign captured in an append-only `audit_entry` table (who, what, when, old→new, reason), implemented via JPA listeners/interceptors so it cannot be bypassed by normal code paths.
- **Electronic signatures (Part 11):** signing requires re-authentication, records signer identity, meaning/intent, and timestamp, and binds to the exact item/baseline version.
- **Access control:** least-privilege roles; lock approved/released versions against edits.

Dependency philosophy: lean on the Spring ecosystem (which is mature and well-supported for regulated/enterprise use) and avoid a sprawling front-end dependency tree entirely. The only client-side assets are HTMX and one graph library, both served as static files.

---

## 9. Reporting and export

Reports are generated server-side from a chosen baseline so they are deterministic and reproducible:

- **Traceability matrix report** (PDF / CSV / XLSX).
- **Design History File (DHF) / Technical Documentation** bundle — requirements, design, verification, risk, and signatures for a baseline (PDF/DOCX).
- **Risk Management File** export.
- **Test report** — executions and results for a baseline.

**Output formats:** the two primary formats are **PDF** (controlled, signed, submission/audit-ready) and a **Confluence-friendly format** so reports fit your existing Confluence workflow. For Confluence, two options, in order of preference:

1. **Direct publish** to a Confluence page via the Confluence REST API — the system pushes a generated page (and updates it on new baselines), keeping the wiki in sync with the source of truth.
2. **Confluence Storage Format / clean HTML export** — an XHTML file the team can paste or import into a Confluence page without reformatting.

Recommended approach: render an HTML report from Thymeleaf once, then (a) convert to PDF with a JVM HTML-to-PDF engine such as OpenHTMLtoPDF/Flying Saucer, and (b) emit Confluence storage-format markup or publish via REST. XLSX/DOCX via Apache POI remain available for matrices and Office deliverables. One templating language drives screen, PDF, and Confluence output.

---

## 10. Suggested delivery phases

**Phase 0 — Foundations.** Project skeleton (Spring Boot + Kotlin + Gradle), Postgres + Flyway, base item/version model, auth + roles, audit-trail interceptor. *Outcome: you can create a versioned item and see it audited.*

**Phase 1 — Requirements core.** User Needs, System Requirements, SRS, status lifecycle, item versioning, typed trace links, basic CRUD UI with HTMX. *Outcome: author and link requirements.*

**Phase 2 — Traceability views.** Real-time traceability matrix + graph, orphan/gap detection, impact analysis. *Outcome: live traceability.*

**Phase 3 — Testing.** Test cases, composite tests, executions/results, coverage roll-up, e-signatures on results. *Outcome: verification evidence tied to requirements.*

**Phase 4 — Risk.** Hazards, risks, controls, residual risk, sFMEA, risk items in the matrix/graph. *Outcome: ISO 14971 chain complete.*

**Phase 5 — Baselines & reporting.** Baseline snapshots + diffing, DHF/RMF/matrix/test report exports (PDF/DOCX/XLSX). *Outcome: audit- and submission-ready outputs.*

**Phase 6 — Hardening.** Validation documentation for the tool itself, performance on large datasets, SSO, backup/restore, deployment packaging.

---

## 11. Resolved decisions

- **Tool validation:** the system itself will be validated as a custom (GAMP Category 5) computerized system used in the quality system, using a risk-based GAMP 5 v2 / FDA CSA approach. Detailed in §14.
- **Graph library:** proceed with a lightweight library (Cytoscape.js or vis-network) via a quick Phase 2 spike — confirmed.
- **Multi-product:** single team, multiple products → multi-*project* with a persistent project switcher (see §8). Not multi-tenant.
- **Migration:** importing from **Requirement Yogi** (Confluence). Path detailed in §13 — ReqIF preferred, Excel fallback.
- **Risk matrix scales:** not prescribed by ISO 14971 — defined by the manufacturer. Implemented as an admin-configurable, per-project **Risk Policy** with a sensible default (see §7.1).
- **Report formats:** PDF + Confluence-friendly (see §9).

---

## 12. Guided and guarded UX (core requirement)

The system must hold the user's hand and make misuse hard. Concretely:

**Guided authoring.** Items are never created in a vacuum. Creating a Software Requirement, for example, starts from its parent System Requirement so the `derives_from` link exists by construction. Wizards walk the user through the required fields and the expected next link, and the UI always answers "what should I do next?" (e.g. "This requirement has no verification — add a test").

**Make relationships obvious.** Every item screen shows its place in the chain — parents above, children below, risks and tests to the side — as a small local trace view, not a buried tab. Link types are labeled in plain language ("verified by", "mitigates", "derived from") rather than abstract relationship codes, so the meaning is never ambiguous.

**Loud, specific warnings.** Real-time validation flags gaps the moment they appear, with clear, plain-language messages and a direct link to fix them. Standard checks:

- Orphan items (no upstream and/or no downstream link).
- Required fields missing (e.g. a requirement with no acceptance criteria, a risk with no control).
- Requirements with no verification, or whose verification is failing/stale.
- Risks with no risk control; controls not yet implemented or not yet verified.
- Software requirements with no assigned IEC 62304 safety class.

**Completeness dashboard.** Each project shows a live readiness scorecard — coverage percentages and an explicit list of every open gap — so the team always knows how far they are from a clean baseline.

**Guardrails on critical actions.** A baseline cannot be frozen/approved while blocking gaps exist; the system lists exactly what must be resolved first. Approved/Released versions are locked against edits. Destructive actions require confirmation and a reason (which feeds the audit trail).

**Constrained vocabulary.** Statuses, link types, item types, and risk scales are fixed, configurable lists — not free text — so the data stays consistent and reportable. Inline contextual help explains each field and standard reference where the user is working.

---

## 13. Migration from Requirement Yogi

Requirement Yogi (your current Confluence app) can export to Excel and supports **ReqIF**, the XML standard for requirement interchange. Plan:

- **Preferred: ReqIF import.** Build a ReqIF importer that maps RY requirement keys → item `human_key`, RY properties/attributes → item fields, and RY links → typed trace links. ReqIF preserves attributes and relationships, so the traceability structure survives the move rather than being rebuilt by hand.
- **Fallback: Excel import.** A mapped spreadsheet importer (column → field mapping) for content that doesn't round-trip cleanly through ReqIF, or for bulk loading legacy items.
- **Approach:** run a trial import into a throwaway project, review with the team, refine the field mapping, then do the real import as the initial content of each product's project. Keep the RY export files as migration records.
- **Caveat:** RY models requirements inside Confluence pages; this system models them as first-class versioned items. Expect a one-time mapping exercise to decide how RY page structure becomes projects/items here. Worth a short spike before committing to a full import.

---

## 14. Tool validation (suggested approach)

Because this system holds SaMD design and risk evidence, it is software used as part of the quality system and must itself be validated for its intended use (ISO 13485 / 21 CFR 820, and the FDA QMSR aligning to ISO 13485). It is a **custom application = GAMP 5 Category 5**, the most rigorous category. Recommended path, **risk-based** per GAMP 5 v2 (2022) and FDA's *Computer Software Assurance (CSA)* guidance — effort scales with each feature's risk rather than validating everything to the same depth:

1. **Validation Plan** — scope, intended use, roles, the risk-based strategy, deliverables, and acceptance criteria.
2. **Intended Use & User Requirements Specification (URS)** — what the tool must do and the regulated functions it supports.
3. **Functional / configuration specification** — how those requirements are met (this PLAN and its successors are the seed).
4. **Tool risk assessment** — for each function, what could go wrong and the impact on product quality/patient safety. Concentrate validation rigor on the **high-risk functions**: audit trail integrity, electronic signatures, access control, versioning/baseline correctness, and the traceability/coverage calculations. Lower-risk, cosmetic features get lighter, unscripted/exploratory testing (the core CSA idea).
5. **Qualification testing** — IQ (installed/configured correctly, correct DB schema version), OQ (each function behaves to spec, especially the high-risk ones, with scripted tests), PQ (works in the team's real workflow on representative data).
6. **Traceability of tool requirements → tests** — the same discipline the tool enforces on its users, applied to itself; an automated regression suite (JUnit/Spring Boot tests) is evidence and makes re-validation cheap.
7. **Validation Summary Report** — results, deviations, and a release decision.
8. **Maintain the validated state** — change control, versioned migrations (Flyway gives you this), periodic review, and targeted re-validation on significant changes.

Design choices that make validation cheaper, several already in this plan: a built-in immutable audit trail, deterministic baseline-scoped reports, versioned DB migrations, a strong automated test suite, and clear separation of high-risk logic so it can be tested in isolation.

---

## 15. Cross-product inheritance and branching

Products are not islands — a new product often reuses requirements from an older one. The system supports inheriting one or many requirements (or a whole component or baseline) from another product, after which the copies **branch and live in parallel**.

The model is **fork-with-provenance, controlled cherry-pick** (no automatic sync, which would be dangerous across separately-regulated products):

- **Forking** copies the chosen items into the target product as new items with their own keys and history, each recording where it came from and the exact origin revision it diverged at. From then on the branches version completely independently.
- **Bulk forks** (component or baseline) recreate the internal trace links among the forked items inside the new product, so the structure carries over while each item still records its cross-product origin.
- A **divergence view** shows, for any inherited requirement, origin-at-fork vs origin-now vs branch-now — so you can see what changed upstream and locally.
- To adopt a later upstream change, a user **deliberately cherry-picks** it: this creates a new Draft revision in the branch (recording the origin revision it came from) that goes through the branch's normal review/approval. Every cross-product change is explicit, reviewed, and audited.
- **Upstream-change notifications.** When an origin requirement gets a new *approved* revision after the fork point, every branch derived from it is flagged "upstream changed" and its owners are notified, with the divergence view one click away. The owner decides whether to adopt (cherry-pick) or dismiss — and dismissing records an acknowledgement (with reason, audited) so the alert clears until the *next* upstream change rather than nagging. Origin deprecation raises the same kind of alert.

Field-level detail is in `DATA-MODEL.md` §21.

---

*Next step: I can expand any section — the URL/page map, the version/baseline/component/branch DB schema, the ReqIF field mapping, or a draft Validation Plan / URS — and scaffold the Phase 0 Spring Boot project.*
