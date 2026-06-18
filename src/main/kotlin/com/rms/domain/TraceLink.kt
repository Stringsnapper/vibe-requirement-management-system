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
 * A first-class, typed, directional relationship between two [ItemRevision]s (DATA-MODEL §4).
 * Modeling links as their own audited entity is what makes the traceability matrix, graph and
 * impact analysis uniform — and lets the matrix be reconstructed for any historical baseline.
 */
@Entity
@Table(name = "trace_link")
class TraceLink(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "project_id", nullable = false)
    val projectId: UUID,
    @Column(name = "source_revision_id", nullable = false)
    val sourceRevisionId: UUID,
    @Column(name = "target_revision_id", nullable = false)
    val targetRevisionId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(name = "link_type", nullable = false)
    val linkType: LinkType,
    @Column(columnDefinition = "text")
    var rationale: String? = null,
    /** Auto-set when an endpoint gets a new revision → flags the link for review. */
    @Column(nullable = false)
    var suspect: Boolean = false,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
    @Column(name = "created_by")
    var createdBy: UUID? = null,
) : Audited {
    override val auditId: UUID get() = id

    override fun auditLabel() = "${linkType.name} link"
}
