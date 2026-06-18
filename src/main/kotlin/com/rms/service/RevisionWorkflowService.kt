package com.rms.service

import com.rms.domain.ItemRevision
import com.rms.domain.LifecycleStatus
import com.rms.repo.ItemRevisionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Governs the lifecycle-status axis of an [ItemRevision] (PLAN §5, status-lifecycle diagram).
 * Only legal transitions are allowed; reaching **Approved** freezes the revision so it can be
 * safely reviewed, assigned and released. Authorization (who may call these) is enforced at the
 * web layer — the domain just guards the transition itself.
 */
@Service
class RevisionWorkflowService(
    private val revisions: ItemRevisionRepository,
) {
    /** Draft → In Review. */
    @Transactional
    fun submitForReview(revisionId: UUID): ItemRevision =
        transition(revisionId, from = LifecycleStatus.DRAFT, to = LifecycleStatus.IN_REVIEW)

    /** In Review → Approved, and freeze the revision. */
    @Transactional
    fun approve(
        revisionId: UUID,
        approverId: UUID,
    ): ItemRevision {
        val rev = transition(revisionId, from = LifecycleStatus.IN_REVIEW, to = LifecycleStatus.APPROVED)
        val now = Instant.now()
        rev.approvedBy = approverId
        rev.approvedAt = now
        rev.frozen = true
        rev.frozenAt = now
        return rev
    }

    /** In Review → Declined (loops back to a new Draft via the editor). */
    @Transactional
    fun decline(
        revisionId: UUID,
        reviewerId: UUID,
    ): ItemRevision {
        val rev = transition(revisionId, from = LifecycleStatus.IN_REVIEW, to = LifecycleStatus.DECLINED)
        rev.reviewedBy = reviewerId
        rev.reviewedAt = Instant.now()
        return rev
    }

    /** Approved → Deprecated (superseded / no longer applies). */
    @Transactional
    fun deprecate(revisionId: UUID): ItemRevision = transition(revisionId, from = LifecycleStatus.APPROVED, to = LifecycleStatus.DEPRECATED)

    /** Deprecated → Retired (removed from the current product). */
    @Transactional
    fun retire(revisionId: UUID): ItemRevision = transition(revisionId, from = LifecycleStatus.DEPRECATED, to = LifecycleStatus.RETIRED)

    private fun transition(
        revisionId: UUID,
        from: LifecycleStatus,
        to: LifecycleStatus,
    ): ItemRevision {
        val rev =
            revisions.findById(revisionId).orElseThrow {
                IllegalArgumentException("Unknown revision $revisionId")
            }
        check(rev.lifecycleStatus == from) {
            "Cannot move revision from ${rev.lifecycleStatus} to $to; expected it to be $from"
        }
        rev.lifecycleStatus = to
        return rev
    }
}
