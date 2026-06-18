package com.rms.audit

/**
 * Marker for entities whose every create/update/delete must be captured in the
 * append-only audit trail (Part 11, PLAN §8). The [AuditInterceptor] picks up any
 * entity implementing this interface; the audit cannot be bypassed by normal code paths.
 *
 * Implementors expose a human-readable label used in the audit row's `new_value`/`old_value`
 * summary so the trail is meaningful without joining back to the source table.
 */
interface Audited {
    /** Stable identity of the row being audited. */
    val auditId: java.util.UUID

    /** Short human description of this entity for the audit summary (e.g. the human key). */
    fun auditLabel(): String
}
