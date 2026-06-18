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
 * The versioned, audited content of an [Item]. Append-only: editing an Approved/frozen
 * revision spawns a new Draft revision rather than mutating this one. (DATA-MODEL §3.)
 */
@Entity
@Table(name = "item_revision")
class ItemRevision(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "item_id", nullable = false)
    val itemId: UUID,

    @Column(name = "revision_no", nullable = false)
    val revisionNo: Int,

    @Column(name = "parent_revision_id")
    val parentRevisionId: UUID? = null,

    @Column(name = "is_current", nullable = false)
    var isCurrent: Boolean = true,

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false)
    var lifecycleStatus: LifecycleStatus = LifecycleStatus.DRAFT,

    @Column(nullable = false)
    var title: String,

    @Column(nullable = false, columnDefinition = "text")
    var statement: String,

    @Column(columnDefinition = "text")
    var rationale: String? = null,

    @Column(name = "author_id", nullable = false)
    val authorId: UUID,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "change_reason", columnDefinition = "text")
    var changeReason: String? = null,

    @Column(nullable = false)
    var frozen: Boolean = false,

    @Column(name = "frozen_at")
    var frozenAt: Instant? = null,

    @Column(name = "content_hash", nullable = false)
    var contentHash: String,
) : Audited {
    override val auditId: UUID get() = id
    override fun auditLabel() = "rev $revisionNo: $title"
}
