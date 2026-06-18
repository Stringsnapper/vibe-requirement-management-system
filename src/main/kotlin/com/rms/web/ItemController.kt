package com.rms.web

import com.rms.domain.ItemType
import com.rms.repo.AuditEntryRepository
import com.rms.repo.ComponentRepository
import com.rms.repo.ItemRepository
import com.rms.repo.ItemRevisionRepository
import com.rms.repo.ProjectRepository
import com.rms.security.AppUserPrincipal
import com.rms.service.ItemService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.util.UUID

@Controller
class ItemController(
    private val itemService: ItemService,
    private val items: ItemRepository,
    private val revisions: ItemRevisionRepository,
    private val projects: ProjectRepository,
    private val components: ComponentRepository,
    private val audit: AuditEntryRepository,
) {

    @GetMapping("/items/new")
    fun newItemForm(model: Model): String {
        model.addAttribute("projects", projects.findAllByOrderByKeyAsc())
        model.addAttribute("components", components.findAll())
        model.addAttribute("itemTypes", ItemType.entries)
        return "item-new"
    }

    @PostMapping("/items")
    fun createItem(
        @RequestParam projectId: UUID,
        @RequestParam type: ItemType,
        @RequestParam(required = false) componentId: UUID?,
        @RequestParam title: String,
        @RequestParam statement: String,
        @RequestParam(required = false) rationale: String?,
        @AuthenticationPrincipal principal: AppUserPrincipal,
        redirect: RedirectAttributes,
    ): String {
        val item = itemService.createItem(
            ItemService.NewItem(
                projectId = projectId,
                type = type,
                componentId = componentId?.takeIf { type.componentScoped },
                title = title,
                statement = statement,
                rationale = rationale?.ifBlank { null },
                authorId = principal.userId,
            ),
        )
        redirect.addFlashAttribute("message", "Created ${item.humanKey}")
        return "redirect:/items/${item.id}"
    }

    @GetMapping("/items/{id}")
    fun itemDetail(@PathVariable id: UUID, model: Model): String {
        val item = items.findById(id).orElseThrow { IllegalArgumentException("Unknown item $id") }
        model.addAttribute("item", item)
        model.addAttribute("revisions", revisions.findByItemIdOrderByRevisionNoAsc(id))
        model.addAttribute("auditEntries", audit.findByEntityIdOrderByOccurredAtAsc(id))
        return "item-detail"
    }

    @PostMapping("/items/{id}/revisions")
    fun newRevision(
        @PathVariable id: UUID,
        @RequestParam title: String,
        @RequestParam statement: String,
        @RequestParam(required = false) rationale: String?,
        @RequestParam changeReason: String,
        @AuthenticationPrincipal principal: AppUserPrincipal,
        redirect: RedirectAttributes,
    ): String {
        itemService.createRevision(
            itemId = id,
            title = title,
            statement = statement,
            rationale = rationale?.ifBlank { null },
            changeReason = changeReason,
            authorId = principal.userId,
        )
        redirect.addFlashAttribute("message", "Created new revision")
        return "redirect:/items/$id"
    }
}
