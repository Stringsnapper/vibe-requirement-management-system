package com.rms.domain

import com.rms.audit.Audited
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Stable, type-agnostic identity of a requirement-like item. The human key never changes;
 * the evolving content lives in [ItemRevision]s. (DATA-MODEL §2.)
 */
@Entity
@Table(name = "item")
class Item(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "project_id", nullable = false)
    val projectId: UUID,
    @Column(name = "component_id")
    val componentId: UUID? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: ItemType,
    @Column(name = "human_key", nullable = false, unique = true)
    val humanKey: String,
    /** Points at the working / `is_current` revision. */
    @Column(name = "current_revision_id")
    var currentRevisionId: UUID? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
    @Column(name = "created_by")
    var createdBy: UUID? = null,
) : Audited {
    override val auditId: UUID get() = id

    override fun auditLabel() = "$humanKey [${type.name}]"
}
