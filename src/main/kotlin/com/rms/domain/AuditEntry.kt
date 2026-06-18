package com.rms.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * One append-only audit record (Part 11, DATA-MODEL §18). Written by the [com.rms.audit.AuditInterceptor]
 * for every change to an [com.rms.audit.Audited] entity. This entity is intentionally NOT itself audited.
 */
@Entity
@Table(name = "audit_entry")
class AuditEntry(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "entity_type", nullable = false)
    val entityType: String,
    @Column(name = "entity_id", nullable = false)
    val entityId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val action: AuditAction,
    @Column(name = "actor_user_id")
    val actorUserId: UUID? = null,
    @Column(name = "occurred_at", nullable = false)
    val occurredAt: Instant = Instant.now(),
    @Column
    val field: String? = null,
    @Column(name = "old_value", columnDefinition = "text")
    val oldValue: String? = null,
    @Column(name = "new_value", columnDefinition = "text")
    val newValue: String? = null,
    @Column(columnDefinition = "text")
    val reason: String? = null,
    @Column(name = "session_ref")
    val sessionRef: String? = null,
)
