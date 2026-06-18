package com.rms.service

import com.rms.domain.ItemType
import com.rms.domain.LinkType
import com.rms.domain.TraceLink
import com.rms.repo.ItemRepository
import com.rms.repo.ItemRevisionRepository
import com.rms.repo.TraceLinkRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Creates and reads typed trace links between items' current revisions. Enforces the basic
 * "guided and guarded" rules (PLAN §12): a link must make sense for the two item types and the
 * direction — e.g. a Software Requirement *derives from* a System Requirement, not vice versa.
 */
@Service
class TraceLinkService(
    private val links: TraceLinkRepository,
    private val items: ItemRepository,
    private val revisions: ItemRevisionRepository,
) {
    /** One side of an item's local trace view. */
    data class LinkedItem(
        val linkId: UUID,
        val linkType: LinkType,
        val otherItemId: UUID,
        val otherHumanKey: String,
        val otherType: ItemType,
        val suspect: Boolean,
    )

    /** Upstream (this item is the link source) and downstream (this item is the target) views. */
    data class TraceView(
        val outgoing: List<LinkedItem>,
        val incoming: List<LinkedItem>,
    )

    /** Link the current revisions of two items. */
    @Transactional
    fun link(
        sourceItemId: UUID,
        targetItemId: UUID,
        linkType: LinkType,
        rationale: String?,
        actorId: UUID,
    ): TraceLink {
        require(sourceItemId != targetItemId) { "An item cannot be linked to itself" }
        val source =
            items.findById(sourceItemId).orElseThrow {
                IllegalArgumentException("Unknown item $sourceItemId")
            }
        val target =
            items.findById(targetItemId).orElseThrow {
                IllegalArgumentException("Unknown item $targetItemId")
            }
        validateDirection(source.type, target.type, linkType)

        val sourceRev =
            revisions.findByItemIdAndIsCurrentTrue(sourceItemId)
                ?: error("Item $sourceItemId has no current revision")
        val targetRev =
            revisions.findByItemIdAndIsCurrentTrue(targetItemId)
                ?: error("Item $targetItemId has no current revision")

        require(
            !links.existsBySourceRevisionIdAndTargetRevisionIdAndLinkType(sourceRev.id, targetRev.id, linkType),
        ) { "That link already exists" }

        return links.save(
            TraceLink(
                projectId = source.projectId,
                sourceRevisionId = sourceRev.id,
                targetRevisionId = targetRev.id,
                linkType = linkType,
                rationale = rationale?.ifBlank { null },
                createdBy = actorId,
            ),
        )
    }

    /** Build the local trace view (parents/children) for an item's current revision. */
    @Transactional(readOnly = true)
    fun traceView(itemId: UUID): TraceView {
        val current = revisions.findByItemIdAndIsCurrentTrue(itemId) ?: return TraceView(emptyList(), emptyList())
        val outgoing = links.findBySourceRevisionIdIn(listOf(current.id)).map { toLinkedItem(it, it.targetRevisionId) }
        val incoming = links.findByTargetRevisionIdIn(listOf(current.id)).map { toLinkedItem(it, it.sourceRevisionId) }
        return TraceView(outgoing, incoming)
    }

    private fun toLinkedItem(
        link: TraceLink,
        otherRevisionId: UUID,
    ): LinkedItem {
        val otherRev =
            revisions.findById(otherRevisionId).orElseThrow {
                IllegalStateException("Dangling trace link ${link.id}")
            }
        val otherItem =
            items.findById(otherRev.itemId).orElseThrow {
                IllegalStateException("Dangling item for revision $otherRevisionId")
            }
        return LinkedItem(
            linkId = link.id,
            linkType = link.linkType,
            otherItemId = otherItem.id,
            otherHumanKey = otherItem.humanKey,
            otherType = otherItem.type,
            suspect = link.suspect,
        )
    }

    /**
     * Guardrail for Phase 1's requirement hierarchy. `derives_from` must flow strictly downward
     * (UserNeed ← SystemRequirement ← SoftwareRequirement); `relates_to` is allowed between any
     * two items. Other link types belong to later phases (tests, risk) and are not offered yet.
     */
    private fun validateDirection(
        source: ItemType,
        target: ItemType,
        linkType: LinkType,
    ) {
        when (linkType) {
            LinkType.RELATES_TO -> return
            LinkType.DERIVES_FROM -> {
                val allowed =
                    (source == ItemType.SYSTEM_REQUIREMENT && target == ItemType.USER_NEED) ||
                        (source == ItemType.SOFTWARE_REQUIREMENT && target == ItemType.SYSTEM_REQUIREMENT)
                require(allowed) {
                    "$source cannot derive from $target; derives-from flows " +
                        "SoftwareRequirement → SystemRequirement → UserNeed"
                }
            }
            else -> throw IllegalArgumentException("$linkType links are not available in this phase")
        }
    }
}
