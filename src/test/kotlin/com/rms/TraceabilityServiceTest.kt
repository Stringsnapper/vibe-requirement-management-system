package com.rms

import com.rms.domain.ItemType
import com.rms.domain.LifecycleState
import com.rms.domain.LinkType
import com.rms.domain.Priority
import com.rms.domain.Project
import com.rms.domain.RequirementCategory
import com.rms.domain.SafetyClass
import com.rms.repo.AppUserRepository
import com.rms.repo.ComponentRepository
import com.rms.repo.ProjectRepository
import com.rms.service.ItemService
import com.rms.service.TraceLinkService
import com.rms.service.TraceabilityService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.util.UUID

/**
 * The Phase 2 traceability views (matrix, ego graph, impact analysis, gap detection) over a
 * small User Need → System Requirement → Software Requirement hierarchy, including one
 * deliberately orphaned software requirement.
 */
@SpringBootTest
@ActiveProfiles("test")
class TraceabilityServiceTest(
    @Autowired private val itemService: ItemService,
    @Autowired private val traceLinks: TraceLinkService,
    @Autowired private val traceability: TraceabilityService,
    @Autowired private val projects: ProjectRepository,
    @Autowired private val components: ComponentRepository,
    @Autowired private val users: AppUserRepository,
) {
    private fun adminId(): UUID = users.findByUsername("admin")!!.id

    private fun content(
        title: String,
        category: RequirementCategory? = null,
        priority: Priority? = null,
        safetyClass: SafetyClass? = null,
    ) = ItemService.RevisionContent(
        title = title,
        statement = "$title statement",
        acceptanceCriteria = "Given X, when Y, then Z",
        category = category,
        priority = priority,
        softwareSafetyClass = safetyClass,
    )

    @Test
    fun `matrix, graph, impact and gap views reflect the live link graph`() {
        val admin = adminId()
        val project =
            projects.save(Project(key = "TRC", name = "Traceability Project", status = LifecycleState.ACTIVE, createdBy = admin))
        val comp = components.save(com.rms.domain.Component(projectId = project.id, key = "CORE", name = "Core", createdBy = admin))

        val need = itemService.createItem(ItemService.NewItem(project.id, ItemType.USER_NEED, null, admin, content("Need")))
        val sysReq =
            itemService.createItem(
                ItemService.NewItem(
                    project.id,
                    ItemType.SYSTEM_REQUIREMENT,
                    null,
                    admin,
                    content("Sys req", RequirementCategory.FUNCTIONAL, Priority.MUST),
                ),
            )
        val swReq =
            itemService.createItem(
                ItemService.NewItem(
                    project.id,
                    ItemType.SOFTWARE_REQUIREMENT,
                    comp.id,
                    admin,
                    content("SW req", RequirementCategory.FUNCTIONAL, Priority.MUST, SafetyClass.B),
                ),
            )
        // Deliberately orphaned: a software requirement with no parent link.
        val orphanSwReq =
            itemService.createItem(
                ItemService.NewItem(
                    project.id,
                    ItemType.SOFTWARE_REQUIREMENT,
                    comp.id,
                    admin,
                    content("Orphan SW req", RequirementCategory.FUNCTIONAL, Priority.SHOULD, SafetyClass.B),
                ),
            )

        traceLinks.link(sysReq.id, need.id, LinkType.DERIVES_FROM, "derives", admin)
        traceLinks.link(swReq.id, sysReq.id, LinkType.DERIVES_FROM, null, admin)

        // --- Matrix: System Requirements (rows) x Software Requirements (cols) ---
        val matrix = traceability.matrix(project.id, ItemType.SYSTEM_REQUIREMENT, ItemType.SOFTWARE_REQUIREMENT)
        assertThat(matrix.rows.map { it.id }).containsExactly(sysReq.id)
        assertThat(matrix.cols.map { it.id }).containsExactlyInAnyOrder(swReq.id, orphanSwReq.id)
        assertThat(
            matrix.cells
                .getValue(sysReq.id)
                .getValue(swReq.id)
                .linkTypes,
        ).containsExactly(LinkType.DERIVES_FROM)
        assertThat(matrix.uncoveredColIds).containsExactly(orphanSwReq.id)
        assertThat(matrix.uncoveredRowIds).isEmpty()

        // --- Ego graph around the software requirement: need <- sysReq <- swReq ---
        val graph = traceability.graph(swReq.id)
        assertThat(graph.nodes.map { it.item.id to it.level }).containsExactlyInAnyOrder(
            swReq.id to 0,
            sysReq.id to -1,
            need.id to -2,
        )
        assertThat(graph.edges).hasSize(2)

        // --- Impact analysis: changing the user need ripples down to both requirements ---
        val impact = traceability.impact(need.id)
        assertThat(impact.downstream.map { it.id }).containsExactlyInAnyOrder(sysReq.id, swReq.id)
        assertThat(impact.upstream).isEmpty()

        // Changing the leaf software requirement has no downstream impact, but depends on both parents.
        val leafImpact = traceability.impact(swReq.id)
        assertThat(leafImpact.downstream).isEmpty()
        assertThat(leafImpact.upstream.map { it.id }).containsExactlyInAnyOrder(sysReq.id, need.id)

        // --- Gap detection: only the orphaned software requirement is flagged as an orphan ---
        val gaps = traceability.gaps(project.id)
        val orphanReasons = gaps.filter { it.item.id == orphanSwReq.id }.map { it.reason }
        assertThat(orphanReasons).anyMatch { it.contains("Orphan") }
        assertThat(gaps.none { it.item.id == swReq.id && it.reason.contains("Orphan") }).isTrue()
    }
}
