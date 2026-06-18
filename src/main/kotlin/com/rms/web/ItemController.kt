package com.rms.web

import com.rms.domain.ItemType
import com.rms.domain.LinkType
import com.rms.domain.Priority
import com.rms.domain.RequirementCategory
import com.rms.domain.SafetyClass
import com.rms.domain.UserNeedSource
import com.rms.repo.AuditEntryRepository
import com.rms.repo.ComponentRepository
import com.rms.repo.ItemRepository
import com.rms.repo.ItemRevisionRepository
import com.rms.repo.ProjectRepository
import com.rms.security.AppUserPrincipal
import com.rms.service.ItemService
import com.rms.service.RevisionWorkflowService
import com.rms.service.TraceLinkService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.util.UUID

/** Parse an optional enum request param, tolerating null/blank values from empty `<select>`s. */
private inline fun <reified E : Enum<E>> enumOrNull(value: String?): E? = value?.takeIf { it.isNotBlank() }?.let { enumValueOf<E>(it) }

@Controller
class ItemController(
    private val itemService: ItemService,
    private val workflow: RevisionWorkflowService,
    private val traceLinks: TraceLinkService,
    private val items: ItemRepository,
    private val revisions: ItemRevisionRepository,
    private val projects: ProjectRepository,
    private val components: ComponentRepository,
    private val audit: AuditEntryRepository,
) {
    /** Requirement item types authored in Phase 1. */
    private val requirementTypes =
        listOf(
            ItemType.USER_NEED,
            ItemType.SYSTEM_REQUIREMENT,
            ItemType.SOFTWARE_REQUIREMENT,
        )

    @GetMapping("/items/new")
    @PreAuthorize("hasAnyRole('AUTHOR','ADMIN')")
    fun newItemForm(model: Model): String {
        addFormReferenceData(model)
        return "item-new"
    }

    @PostMapping("/items")
    @PreAuthorize("hasAnyRole('AUTHOR','ADMIN')")
    fun createItem(
        @RequestParam projectId: UUID,
        @RequestParam type: ItemType,
        @RequestParam(required = false) componentId: UUID?,
        @RequestParam title: String,
        @RequestParam statement: String,
        @RequestParam(required = false) rationale: String?,
        @RequestParam(required = false) acceptanceCriteria: String?,
        @RequestParam(required = false) userNeedSource: String?,
        @RequestParam(required = false) userNeedActor: String?,
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false) priority: String?,
        @RequestParam(required = false) softwareSafetyClass: String?,
        @AuthenticationPrincipal principal: AppUserPrincipal,
        redirect: RedirectAttributes,
    ): String {
        val item =
            itemService.createItem(
                ItemService.NewItem(
                    projectId = projectId,
                    type = type,
                    componentId = componentId?.takeIf { type.componentScoped },
                    authorId = principal.userId,
                    content =
                        ItemService.RevisionContent(
                            title = title,
                            statement = statement,
                            rationale = rationale?.ifBlank { null },
                            acceptanceCriteria = acceptanceCriteria?.ifBlank { null },
                            userNeedSource = enumOrNull<UserNeedSource>(userNeedSource).takeIf { type == ItemType.USER_NEED },
                            userNeedActor = userNeedActor?.ifBlank { null }.takeIf { type == ItemType.USER_NEED },
                            category = enumOrNull<RequirementCategory>(category).takeIf { type != ItemType.USER_NEED },
                            priority = enumOrNull<Priority>(priority).takeIf { type != ItemType.USER_NEED },
                            softwareSafetyClass =
                                enumOrNull<SafetyClass>(softwareSafetyClass)
                                    .takeIf { type == ItemType.SOFTWARE_REQUIREMENT },
                        ),
                ),
            )
        redirect.addFlashAttribute("message", "Created ${item.humanKey}")
        return "redirect:/items/${item.id}"
    }

    @GetMapping("/items/{id}")
    fun itemDetail(
        @PathVariable id: UUID,
        model: Model,
    ): String {
        val item = items.findById(id).orElseThrow { IllegalArgumentException("Unknown item $id") }
        val revs = revisions.findByItemIdOrderByRevisionNoAsc(id)
        model.addAttribute("item", item)
        model.addAttribute("revisions", revs)
        model.addAttribute("current", revs.firstOrNull { it.isCurrent })
        model.addAttribute("trace", traceLinks.traceView(id))
        model.addAttribute("auditEntries", audit.findByEntityIdOrderByOccurredAtAsc(id))
        // Candidate targets for a derives-from link: items of the parent type in the same project.
        model.addAttribute("linkCandidates", deriveFromCandidates(item))
        model.addAttribute("gaps", gapsFor(item, revs.firstOrNull { it.isCurrent }))
        return "item-detail"
    }

    @PostMapping("/items/{id}/revisions")
    @PreAuthorize("hasAnyRole('AUTHOR','ADMIN')")
    fun newRevision(
        @PathVariable id: UUID,
        @RequestParam title: String,
        @RequestParam statement: String,
        @RequestParam(required = false) rationale: String?,
        @RequestParam(required = false) acceptanceCriteria: String?,
        @RequestParam changeReason: String,
        @AuthenticationPrincipal principal: AppUserPrincipal,
        redirect: RedirectAttributes,
    ): String {
        itemService.createRevision(
            itemId = id,
            content =
                ItemService.RevisionContent(
                    title = title,
                    statement = statement,
                    rationale = rationale?.ifBlank { null },
                    acceptanceCriteria = acceptanceCriteria?.ifBlank { null },
                ),
            changeReason = changeReason,
            authorId = principal.userId,
        )
        redirect.addFlashAttribute("message", "Created a new draft revision")
        return "redirect:/items/$id"
    }

    // --- Lifecycle transitions (role-gated) ---

    @PostMapping("/revisions/{revId}/submit")
    @PreAuthorize("hasAnyRole('AUTHOR','ADMIN')")
    fun submit(
        @PathVariable revId: UUID,
        redirect: RedirectAttributes,
    ): String = transitionThen(revId, redirect, "Submitted for review") { workflow.submitForReview(revId) }

    @PostMapping("/revisions/{revId}/approve")
    @PreAuthorize("hasAnyRole('APPROVER','ADMIN')")
    fun approve(
        @PathVariable revId: UUID,
        @AuthenticationPrincipal principal: AppUserPrincipal,
        redirect: RedirectAttributes,
    ): String = transitionThen(revId, redirect, "Approved and frozen") { workflow.approve(revId, principal.userId) }

    @PostMapping("/revisions/{revId}/decline")
    @PreAuthorize("hasAnyRole('REVIEWER','APPROVER','ADMIN')")
    fun decline(
        @PathVariable revId: UUID,
        @AuthenticationPrincipal principal: AppUserPrincipal,
        redirect: RedirectAttributes,
    ): String = transitionThen(revId, redirect, "Declined") { workflow.decline(revId, principal.userId) }

    @PostMapping("/revisions/{revId}/deprecate")
    @PreAuthorize("hasAnyRole('APPROVER','ADMIN')")
    fun deprecate(
        @PathVariable revId: UUID,
        redirect: RedirectAttributes,
    ): String = transitionThen(revId, redirect, "Deprecated") { workflow.deprecate(revId) }

    @PostMapping("/revisions/{revId}/retire")
    @PreAuthorize("hasAnyRole('APPROVER','ADMIN')")
    fun retire(
        @PathVariable revId: UUID,
        redirect: RedirectAttributes,
    ): String = transitionThen(revId, redirect, "Retired") { workflow.retire(revId) }

    // --- Trace links ---

    @PostMapping("/items/{id}/links")
    @PreAuthorize("hasAnyRole('AUTHOR','ADMIN')")
    fun addLink(
        @PathVariable id: UUID,
        @RequestParam targetItemId: UUID,
        @RequestParam linkType: LinkType,
        @RequestParam(required = false) rationale: String?,
        @AuthenticationPrincipal principal: AppUserPrincipal,
        redirect: RedirectAttributes,
    ): String {
        try {
            traceLinks.link(id, targetItemId, linkType, rationale, principal.userId)
            redirect.addFlashAttribute("message", "Link added")
        } catch (e: IllegalArgumentException) {
            redirect.addFlashAttribute("error", e.message)
        }
        return "redirect:/items/$id"
    }

    private fun transitionThen(
        revId: UUID,
        redirect: RedirectAttributes,
        message: String,
        action: () -> com.rms.domain.ItemRevision,
    ): String {
        val rev =
            try {
                val r = action()
                redirect.addFlashAttribute("message", message)
                r
            } catch (e: RuntimeException) {
                redirect.addFlashAttribute("error", e.message)
                revisions.findById(revId).orElseThrow { IllegalArgumentException("Unknown revision $revId") }
            }
        return "redirect:/items/${rev.itemId}"
    }

    private fun addFormReferenceData(model: Model) {
        model.addAttribute("projects", projects.findAllByOrderByKeyAsc())
        model.addAttribute("components", components.findAll())
        model.addAttribute("itemTypes", requirementTypes)
        model.addAttribute("userNeedSources", UserNeedSource.entries)
        model.addAttribute("categories", RequirementCategory.entries)
        model.addAttribute("priorities", Priority.entries)
        model.addAttribute("safetyClasses", SafetyClass.entries)
    }

    private fun deriveFromCandidates(item: com.rms.domain.Item): List<com.rms.domain.Item> {
        val parentType =
            when (item.type) {
                ItemType.SYSTEM_REQUIREMENT -> ItemType.USER_NEED
                ItemType.SOFTWARE_REQUIREMENT -> ItemType.SYSTEM_REQUIREMENT
                else -> return emptyList()
            }
        return items.findByProjectIdOrderByHumanKeyAsc(item.projectId).filter { it.type == parentType }
    }

    /** Loud, specific gap warnings for the current revision (PLAN §12). */
    private fun gapsFor(
        item: com.rms.domain.Item,
        current: com.rms.domain.ItemRevision?,
    ): List<String> {
        if (current == null) return emptyList()
        val gaps = mutableListOf<String>()
        if (current.acceptanceCriteria.isNullOrBlank()) {
            gaps += "No acceptance criteria — add measurable criteria so this requirement is testable."
        }
        if (item.type == ItemType.SOFTWARE_REQUIREMENT && current.softwareSafetyClass == null) {
            gaps += "No IEC 62304 software safety class assigned."
        }
        if (item.type != ItemType.USER_NEED &&
            traceLinks.traceView(item.id).outgoing.none {
                it.linkType == LinkType.DERIVES_FROM
            }
        ) {
            gaps += "Orphan — this requirement does not derive from a parent requirement."
        }
        return gaps
    }
}
