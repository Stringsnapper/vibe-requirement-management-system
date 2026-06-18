# Entity Field Reference

Companion to `PLAN.md` and the `data-model.mermaid` / `status-lifecycle.mermaid` diagrams. Field-level definition of every entity. Types are conceptual (the Spring Data JPA / PostgreSQL mapping is straightforward from these).

## Conventions

- Every table has `id` (UUID, PK).
- **Audited** = all changes are captured in `audit_entry` (who/what/when/old→new/reason); these tables are append-only or never hard-deleted.
- `enum` values are fixed, configurable lists — never free text (see PLAN §12, constrained vocabulary).
- "Item types" (UserNeed, SystemRequirement, SoftwareRequirement, DesignSpec, Hazard, Risk, RiskControl, sFMEAEntry, TestCase, CompositeTest) all share the **Item + ItemRevision** base and add type-specific fields to the revision. This gives uniform versioning, status, trace links, and audit for free.

---

## 1. Project (= product)

| Field | Type | Req | Notes |
|---|---|---|---|
| id | UUID | ✓ | |
| key | string(10) | ✓ | Short code, e.g. `CGM`; used as prefix in human keys |
| name | string | ✓ | |
| description | text | | |
| status | enum | ✓ | Active, Archived |
| default_safety_class | enum | | A / B / C default for new software requirements |
| risk_policy_id | UUID FK | ✓ | Current Risk Policy (§11) |
| created_at / created_by | datetime / FK | ✓ | |

---

## 1b. Component (= software item / subsystem)

A project contains multiple components that are managed separately, each with its own requirement set. Maps to the IEC 62304 "software item" concept (a software system is composed of software items, which may contain further items).

| Field | Type | Req | Notes |
|---|---|---|---|
| id | UUID | ✓ | |
| project_id | UUID FK | ✓ | |
| key | string(12) | ✓ | Short code, e.g. `SENSOR`; appears in item keys to show ownership |
| name | string | ✓ | |
| description | text | | |
| parent_component_id | UUID FK | | Optional nesting (item contains items) |
| safety_class | enum | | A / B / C default for the component's software requirements |
| status | enum | ✓ | Active, Archived |
| created_at / created_by | datetime / FK | ✓ | |

Component-scoped item types (SoftwareRequirement, DesignSpec, TestCase, CompositeTest, sFMEAEntry) carry a `component_id`. Project/system-level types (UserNeed, SystemRequirement, Hazard, Risk, RiskControl) are not component-scoped — system requirements decompose *into* component software requirements via `derives_from` links.

---

## 2. Item (stable identity, type-agnostic)

Holds the identity that persists across all revisions. The human key never changes.

| Field | Type | Req | Notes |
|---|---|---|---|
| id | UUID | ✓ | |
| project_id | UUID FK | ✓ | |
| component_id | UUID FK | ✓ for component-scoped types | The software item this belongs to (§1b) |
| type | enum | ✓ | UserNeed, SystemRequirement, SoftwareRequirement, DesignSpec, Hazard, Risk, RiskControl, sFMEAEntry, TestCase, CompositeTest |
| human_key | string | ✓ | `PROJECT-COMPONENT-TYPE-NNN`, e.g. `CGM-SENSOR-SRS-014`; component-scoped types include the component code so ownership is visible in the key. Non-scoped types omit it (`CGM-UN-007`). Number unique per (project, component, type); immutable. |
| **forked_from_item_id** | UUID FK | | Provenance: the origin item this was inherited from (cross-project) |
| **forked_from_revision_id** | UUID FK | | The exact origin revision at fork time (the fork point) |
| current_revision_id | UUID FK | ✓ | Points at the working/`is_current` revision |
| created_at / created_by | datetime / FK | ✓ | |

The two `forked_from_*` fields record where an inherited requirement came from and the revision it diverged at, enabling the divergence view and controlled cherry-pick (§21). After forking, the item lives and versions fully independently in its new product.

---

## 3. ItemRevision (base — shared by all item types)

The versioned, audited content of an item. Editing an Approved/Released revision creates a new Draft revision.

| Field | Type | Req | Notes |
|---|---|---|---|
| id | UUID | ✓ | |
| item_id | UUID FK | ✓ | |
| revision_no | int | ✓ | 1, 2, 3 … |
| parent_revision_id | UUID FK | | Prior revision (diff lineage) |
| is_current | bool | ✓ | One current revision per item |
| **lifecycle_status** | enum | ✓ | Draft, InReview, Approved, Declined, Deprecated, Retired (governance axis) |
| title | string | ✓ | |
| body / statement | text | ✓ | The requirement/spec text (type-specific extensions below) |
| rationale | text | | Why this exists |
| author | UUID FK | ✓ | |
| created_at | datetime | ✓ | |
| change_reason | text | ✓ when not rev 1 | Required for every revision after the first |
| reviewed_by / reviewed_at | FK / datetime | | Set on review |
| approved_by / approved_at | FK / datetime | | Set on approval |
| frozen | bool | ✓ | True once Approved or released; blocks edits |
| frozen_at | datetime | | |
| content_hash | string | ✓ | Integrity check for the frozen content |
| cherry_picked_from_revision_id | UUID FK | | If this revision was created by pulling an origin change into a fork (§21) |

### Derived progress (computed + cached on the revision — not hand-set)

| Field | Type | Notes |
|---|---|---|
| implementation_state | enum | NotImplemented, Implemented — derived from a linked, approved DesignSpec output (or dev flag requiring that link) |
| verification_state | enum | Unverified, Verified, Failing, Stale — derived from linked tests + latest executions |
| release_state | enum | Unreleased, Released, Deprecated — derived from baseline membership |
| introduced_in_baseline_id | UUID FK | First released baseline containing this revision |
| deprecated_in_baseline_id | UUID FK | Baseline where it was deprecated/superseded |

### 3a–3h. Type-specific revision fields

**UserNeed** — `source` (enum: Stakeholder/Clinical/Regulatory/Business/Usability), `actor` (who has the need), `acceptance_criteria` text.

**SystemRequirement** — `acceptance_criteria` text, `category` (enum: Functional/Performance/Interface/Safety/Security/Usability/Regulatory), `priority` (enum: Must/Should/Could).

**SoftwareRequirement (SRS)** — `acceptance_criteria` text, **`software_safety_class`** (enum A/B/C, IEC 62304), `category`, `priority`. Belongs to **exactly one Component** via the item's `component_id` (§1b); the component code is part of the human key. Missing safety class or missing component is a flagged gap (PLAN §12).

**DesignSpec / SoftwareDesign** — `design_description` text, `architecture_ref`, `software_unit`, `implementation_ref` (commit/PR/design-doc URL). An approved DesignSpec linked via `implements` is what flips a requirement's `implementation_state` to Implemented.

**Hazard** — `hazard_description`, `cause_category`, `foreseeable_sequence` (sequence of events leading to a hazardous situation).

**Risk** — see §8.

**RiskControl** — see §9.

**sFMEAEntry** — see §10.

**TestCase / CompositeTest** — see §12–13.

---

## 4. TraceLink

First-class, typed, directional relationship between two **revisions** (so any historical baseline reconstructs exactly). Audited.

| Field | Type | Req | Notes |
|---|---|---|---|
| id | UUID | ✓ | |
| project_id | UUID FK | ✓ | |
| source_revision_id | UUID FK | ✓ | |
| target_revision_id | UUID FK | ✓ | |
| link_type | enum | ✓ | derives_from, verifies, mitigates, implements, relates_to, **branched_from** (cross-project provenance, §21) |
| rationale | text | | |
| suspect | bool | ✓ | Auto-set when an endpoint gets a new revision → flags the link for review |
| created_at / created_by | datetime / FK | ✓ | |

---

## 5. Baseline (the "set" version)

| Field | Type | Req | Notes |
|---|---|---|---|
| id | UUID | ✓ | |
| project_id | UUID FK | ✓ | |
| name | string | ✓ | |
| version_label | string | ✓ | e.g. `R1.1` |
| status | enum | ✓ | Draft, UnderReview, Released, Superseded |
| description | text | | |
| created_at / created_by | datetime / FK | ✓ | |
| released_at / released_by | datetime / FK | | Set on release; triggers freeze of all members |
| signature_id | UUID FK | | E-signature applied at release |

## 6. BaselineItem (pins exact revisions)

| Field | Type | Req | Notes |
|---|---|---|---|
| baseline_id | UUID FK | ✓ | PK part |
| item_id | UUID FK | ✓ | PK part |
| item_revision_id | UUID FK | ✓ | The exact revision frozen into this baseline |
| frozen | bool | ✓ | True for Released baselines |

---

## 7. Hazard

Item type; carries the base revision fields plus: `hazard_description`, `cause_category`, `foreseeable_sequence`. Linked to one or more Risks.

## 8. Risk (ISO 14971)

Revision-extra fields (on a Risk item revision):

| Field | Type | Req | Notes |
|---|---|---|---|
| hazard_item_id | UUID FK | ✓ | Source hazard |
| hazardous_situation | text | ✓ | |
| harm_description | text | ✓ | |
| risk_policy_id | UUID FK | ✓ | Policy in force at evaluation (§11) |
| initial_severity | int | ✓ | Per policy scale |
| initial_probability | int | ✓ | Per policy scale |
| initial_risk_level | enum | derived | Acceptable / ALARP / Unacceptable, from policy matrix |
| residual_severity | int | | After controls |
| residual_probability | int | | After controls |
| residual_risk_level | enum | derived | |
| risk_acceptable | bool | derived | From policy matrix |
| benefit_risk_rationale | text | | Required when residual risk is not fully Acceptable |

## 9. RiskControl

Revision-extra fields:

| Field | Type | Req | Notes |
|---|---|---|---|
| control_description | text | ✓ | |
| control_type | enum | ✓ | InherentSafeDesign / ProtectiveMeasure / InformationForSafety (ISO 14971 priority order) |
| implementing_requirement_item_id | UUID FK | | The requirement that realizes the control |
| effectiveness_check | text | | How effectiveness is confirmed |

A control's "verified" state is derived from its implementing requirement's verification. Risk → mitigated_by → RiskControl → implemented_as → SoftwareRequirement → verified_by → Test gives the full chain.

## 10. sFMEA Entry

Revision-extra fields:

| Field | Type | Req | Notes |
|---|---|---|---|
| function_or_component | string | ✓ | |
| failure_mode | text | ✓ | |
| cause | text | ✓ | |
| local_effect | text | | |
| system_effect | text | ✓ | |
| severity (S) | int | ✓ | Per policy/FMEA scale |
| occurrence (O) | int | ✓ | |
| detection (D) | int | ✓ | |
| rpn | int | derived | S × O × D |
| recommended_action | text | | |
| action_owner | UUID FK | | |
| action_status | enum | | Open / InProgress / Done |
| post_S / post_O / post_D / post_RPN | int | | After action |

## 11. RiskPolicy (configurable, versioned)

The ISO 14971 scales the *manufacturer* defines (PLAN §7.1).

| Field | Type | Req | Notes |
|---|---|---|---|
| id | UUID | ✓ | |
| project_id | UUID FK | ✓ | |
| version_no | int | ✓ | Versioned; risks record which policy they used |
| severity_levels | json | ✓ | Ordinal + label + description (e.g. 1=Negligible … 5=Catastrophic) |
| probability_levels | json | ✓ | Ordinal + label + frequency band |
| acceptability_matrix | json | ✓ | Each S×P cell → Acceptable / ALARP / Unacceptable |
| effective_from | datetime | ✓ | |
| status | enum | ✓ | Draft / Active / Superseded |
| created_by | UUID FK | ✓ | |

---

## 12. TestCase

Item type; revision-extra fields:

| Field | Type | Req | Notes |
|---|---|---|---|
| objective | text | ✓ | |
| preconditions | text | | |
| test_steps | ordered list | ✓ | Each: step_no, action, expected_result |
| overall_expected_result | text | ✓ | |
| test_type | enum | ✓ | Unit / Integration / System / Manual / Automated |
| automation_ref | string | | Link to automated test / CI job |

## 13. CompositeTest + members

CompositeTest revision-extra: `objective`, `description`, `ordering` (enum Sequential/Grouped).

**CompositeTestMember:**

| Field | Type | Req | Notes |
|---|---|---|---|
| composite_revision_id | UUID FK | ✓ | |
| test_case_item_id | UUID FK | ✓ | Member test |
| sequence_no | int | ✓ | Order within the composite |
| optional | bool | ✓ | If false, must pass for the composite to pass |

A requirement linked (via `verifies`) to a composite is Verified only when all non-optional members have passing, non-stale executions.

## 14. TestExecution

| Field | Type | Req | Notes |
|---|---|---|---|
| id | UUID | ✓ | |
| project_id | UUID FK | ✓ | |
| target_revision_id | UUID FK | ✓ | TestCase or CompositeTest revision executed |
| baseline_id | UUID FK | | Context the run was performed against |
| environment | string | ✓ | OS/build/config |
| executed_by | UUID FK | ✓ | |
| executed_at | datetime | ✓ | |
| overall_result | enum | ✓ | Pass / Fail / Blocked |
| notes | text | | |
| stale | bool | ✓ | Auto-set when a linked item gets a newer revision |
| signature_id | UUID FK | | E-signature on completion |

**TestStepResult:** `execution_id` FK, `step_no`, `actual_result` text, `result` (Pass/Fail), `evidence_attachment_id` FK.

---

## 15. Attachment / Evidence

| Field | Type | Req | Notes |
|---|---|---|---|
| id | UUID | ✓ | |
| project_id | UUID FK | ✓ | |
| filename / content_type / size | string / string / int | ✓ | |
| storage_ref | string | ✓ | Path/object key |
| content_hash | string | ✓ | Integrity |
| uploaded_by / uploaded_at | FK / datetime | ✓ | |
| linked_entity_type / linked_entity_id | string / UUID | ✓ | What it evidences |

## 16. User

| Field | Type | Req | Notes |
|---|---|---|---|
| id | UUID | ✓ | |
| username | string | ✓ | |
| email | string | ✓ | |
| full_name | string | ✓ | Shown on signatures/audit |
| active | bool | ✓ | |
| sso_subject | string | | For OIDC/SAML later |
| created_at | datetime | ✓ | |

Credentials/MFA are handled by Spring Security, not stored as plain fields here.

## 17. Role & RoleAssignment

- **Role:** `id`, `name` (Author / Reviewer / Approver / QA / Admin), `permissions` (set).
- **RoleAssignment:** `user_id` FK, `project_id` FK (null = global), `role_id` FK. Enables per-product roles for the single team.

## 18. AuditEntry (Part 11, append-only)

| Field | Type | Req | Notes |
|---|---|---|---|
| id | UUID | ✓ | |
| entity_type / entity_id | string / UUID | ✓ | |
| action | enum | ✓ | Create, Update, Delete, StatusChange, Sign, Release |
| actor_user_id | UUID FK | ✓ | |
| timestamp | datetime | ✓ | Server time, UTC |
| field / old_value / new_value | string / text / text | | Or a JSON delta |
| reason | text | | Required for status changes and edits to frozen items |
| session_ref | string | | Session/IP for traceability |

## 19. ElectronicSignature (Part 11)

| Field | Type | Req | Notes |
|---|---|---|---|
| id | UUID | ✓ | |
| signer_user_id | UUID FK | ✓ | |
| signed_entity_type / signed_entity_id | string / UUID | ✓ | Revision, Baseline, or TestExecution |
| meaning | enum | ✓ | Authored / Reviewed / Approved / Released / Executed |
| signed_at | datetime | ✓ | |
| auth_method | enum | ✓ | Required re-authentication method |
| comment | text | | |

Signatures are immutable and bound to the exact entity revision.

## 20. Comment / Review record

`id`, `entity_type`, `entity_id`, `author` FK, `created_at`, `body` text, `review_decision` (enum: Approve/RequestChanges/null), `resolved` bool. Drives the In-Review workflow and review history.

---

## How the derived axes are computed

- **implementation_state** → Implemented when the requirement has at least one `implements`-linked DesignSpec whose revision is Approved (and not suspect). Otherwise NotImplemented.
- **verification_state** → Verified when every `verifies`-linked TestCase/CompositeTest has a passing, non-stale TestExecution; Failing if any latest execution failed; Stale if a linked item changed after the last run; Unverified if never run.
- **release_state** → Released once the revision appears in a Released baseline (`introduced_in_baseline_id` set); Deprecated when superseded/deprecated (`deprecated_in_baseline_id` set); else Unreleased.

These are recomputed on the relevant events (link change, execution recorded, baseline released) and cached on the revision for fast matrix/dashboard rendering.

---

## 21. Cross-product inheritance & branching (controlled cherry-pick)

A new product can inherit one or more requirements from an older one; after that, the copies branch and live in parallel.

**Forking = copy with provenance.** Inheriting an item creates a new Item in the target project with its own key, its own revision history, and `forked_from_item_id` / `forked_from_revision_id` pointing at the origin and the exact fork-point revision. A `branched_from` TraceLink records the same relationship for the graph. From that moment the two items version completely independently — a change in one product never silently affects another.

**Granularity.** You can fork a single item, a whole Component, or a whole Baseline. Bulk forks recreate the internal trace links *among the forked items* inside the new product (the structure carries over), while `branched_from` links capture the cross-product origin of each.

**Divergence view.** Because each fork stores its fork point, the system can show, for any inherited requirement, three-way differences: origin-at-fork vs origin-now vs branch-now — so you can see what changed upstream and what you changed locally.

**Controlled cherry-pick (chosen model).** There is no automatic sync. To bring a later origin change into a branch, a user deliberately pulls it: this creates a new Draft revision in the branch with `cherry_picked_from_revision_id` set to the origin revision, then goes through the branch's normal review/approval. Every cross-product change is therefore explicit, reviewed, and audited — appropriate for separate regulated products.

**Upstream-change notification.** Because the branch stores its fork point, the system can detect when the origin has *approved* revisions newer than what the branch has adopted, and proactively alert the branch owners so they can decide whether to act:

- Derived flag on the forked item: **`upstream_status`** (enum: UpToDate, UpstreamChanged, UpstreamDeprecated) — UpstreamChanged when the origin has an approved revision newer than `forked_from_revision_id` and newer than any already cherry-picked/acknowledged revision.
- `last_acknowledged_origin_revision_id` (UUID FK) on the forked item — lets a user record "reviewed up to here, chose not to adopt," which clears the alert until the *next* upstream change. The decision (adopt / dismiss + reason) is audited.
- A **Notification** is raised to the branch's owners when the flag flips to UpstreamChanged or UpstreamDeprecated; the divergence view is one click away so they can compare and then cherry-pick or dismiss.

**Notification** entity:

| Field | Type | Req | Notes |
|---|---|---|---|
| id | UUID | ✓ | |
| recipient_user_id | UUID FK | ✓ | Or role-scoped fan-out |
| type | enum | ✓ | UpstreamChange, UpstreamDeprecated, SuspectLink, VerificationStale, ReviewRequested … |
| entity_type / entity_id | string / UUID | ✓ | The branch item (or other subject) |
| origin_revision_id | UUID FK | | The upstream revision that triggered it |
| created_at | datetime | ✓ | |
| read | bool | ✓ | |
| resolved | bool | ✓ | Set when the user acts or dismisses |
| decision / decision_reason | enum / text | | Adopted / Dismissed (+ reason; audited) |

**Supporting entity — ForkRecord** (optional but useful for bulk operations and reporting):

| Field | Type | Req | Notes |
|---|---|---|---|
| id | UUID | ✓ | |
| source_project_id / target_project_id | UUID FK | ✓ | |
| scope | enum | ✓ | Item / Component / Baseline |
| source_ref_id | UUID | ✓ | The forked item/component/baseline |
| created_at / created_by | datetime / FK | ✓ | |

Item-level `forked_from_*` fields are the source of truth; ForkRecord just groups a bulk inheritance event for traceability and reporting.
