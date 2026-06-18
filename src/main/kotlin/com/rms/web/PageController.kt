package com.rms.web

import com.rms.repo.AuditEntryRepository
import com.rms.repo.ItemRepository
import com.rms.repo.ItemRevisionRepository
import com.rms.repo.ProjectRepository
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class PageController(
    private val projects: ProjectRepository,
    private val items: ItemRepository,
    private val audit: AuditEntryRepository,
    private val revisions: ItemRevisionRepository,
) {

    @GetMapping("/login")
    fun login() = "login"

    @GetMapping("/")
    fun home(model: Model): String {
        val projectList = projects.findAllByOrderByKeyAsc()
        model.addAttribute("projects", projectList)
        model.addAttribute(
            "items",
            projectList.flatMap { items.findByProjectIdOrderByHumanKeyAsc(it.id) },
        )
        model.addAttribute("recentAudit", audit.findTop100ByOrderByOccurredAtDesc().take(20))
        return "home"
    }
}
