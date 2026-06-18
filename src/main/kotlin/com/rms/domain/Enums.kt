package com.rms.domain

/** Status of a Project or Component. */
enum class LifecycleState { ACTIVE, ARCHIVED }

/** IEC 62304 software safety classification. */
enum class SafetyClass { A, B, C }

/**
 * The type of a versioned Item. Phase 0 carries the full vocabulary so the
 * human-key scheme and the `item.type` CHECK constraint are stable from the start,
 * even though later phases add the type-specific behaviour.
 */
enum class ItemType(
    val keyAbbrev: String,
    val componentScoped: Boolean,
) {
    USER_NEED("UN", false),
    SYSTEM_REQUIREMENT("SYS", false),
    SOFTWARE_REQUIREMENT("SRS", true),
    DESIGN_SPEC("DES", true),
    HAZARD("HAZ", false),
    RISK("RSK", false),
    RISK_CONTROL("RC", false),
    SFMEA_ENTRY("FME", true),
    TEST_CASE("TC", true),
    COMPOSITE_TEST("CT", true),
}

/** Governed lifecycle status of an ItemRevision (PLAN §5, status-lifecycle diagram). */
enum class LifecycleStatus { DRAFT, IN_REVIEW, APPROVED, DECLINED, DEPRECATED, RETIRED }

/** Where a User Need originates (DATA-MODEL §3a). */
enum class UserNeedSource { STAKEHOLDER, CLINICAL, REGULATORY, BUSINESS, USABILITY }

/** Requirement category for System/Software requirements (DATA-MODEL §3b). */
enum class RequirementCategory { FUNCTIONAL, PERFORMANCE, INTERFACE, SAFETY, SECURITY, USABILITY, REGULATORY }

/** Requirement priority (MoSCoW subset, DATA-MODEL §3b). */
enum class Priority { MUST, SHOULD, COULD }

/** Typed, directional trace-link relationship between two revisions (DATA-MODEL §4). */
enum class LinkType(
    val label: String,
    val inwardLabel: String,
) {
    DERIVES_FROM("derives from", "derived into"),
    VERIFIES("verifies", "verified by"),
    MITIGATES("mitigates", "mitigated by"),
    IMPLEMENTS("implements", "implemented by"),
    RELATES_TO("relates to", "related from"),
    BRANCHED_FROM("branched from", "branched into"),
}

/** Action recorded in the append-only audit trail (Part 11, DATA-MODEL §18). */
enum class AuditAction { CREATE, UPDATE, DELETE, STATUS_CHANGE, SIGN, RELEASE }

/** Built-in roles (DATA-MODEL §17). */
enum class RoleName { AUTHOR, REVIEWER, APPROVER, QA, ADMIN }
