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
enum class ItemType(val keyAbbrev: String, val componentScoped: Boolean) {
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

/** Action recorded in the append-only audit trail (Part 11, DATA-MODEL §18). */
enum class AuditAction { CREATE, UPDATE, DELETE, STATUS_CHANGE, SIGN, RELEASE }

/** Built-in roles (DATA-MODEL §17). */
enum class RoleName { AUTHOR, REVIEWER, APPROVER, QA, ADMIN }
