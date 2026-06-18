package com.rms

import com.rms.domain.AuditAction
import com.rms.domain.Component
import com.rms.domain.ItemType
import com.rms.domain.LifecycleState
import com.rms.domain.LifecycleStatus
import com.rms.domain.LinkType
import com.rms.domain.Priority
import com.rms.domain.Project
import com.rms.domain.RequirementCategory
import com.rms.domain.SafetyClass
import com.rms.repo.AppUserRepository
import com.rms.repo.AuditEntryRepository
import com.rms.repo.ComponentRepository
import com.rms.repo.ItemRevisionRepository
import com.rms.repo.ProjectRepository
import com.rms.service.ItemService
import com.rms.service.RevisionWorkflowService
import com.rms.service.TraceLinkService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
class RequirementWorkflowTest(
    @Autowired private val itemService: ItemService,
    @Autowired private val workflow: RevisionWorkflowService,
    @Autowired private val traceLinks: TraceLinkService,
    @Autowired private val projects: ProjectRepository,
    @Autowired private val components: ComponentRepository,
    @Autowired private val users: AppUserRepository,
    @Autowired private val revisions: ItemRevisionRepository,
    @Autowired private val audit: AuditEntryRepository,
) {
    private fun adminId(): UUID = users.findByUsername("admin")!!.id

    private fun project(key: String): Project =
        projects.save(Project(key = key, name = "$key Project", status = LifecycleState.ACTIVE, createdBy = adminId()))

    @Test
    fun `requirements link down the hierarchy and the wrong direction is rejected`() {
        val admin = adminId()
        val p = project("LNK")
        val comp = components.save(Component(projectId = p.id, key = "CORE", name = "Core", createdBy = admin))

        val need =
            itemService.createItem(
                ItemService.NewItem(p.id, ItemType.USER_NEED, null, admin, content("Need")),
            )
        val sysReq =
            itemService.createItem(
                ItemService.NewItem(
                    p.id,
                    ItemType.SYSTEM_REQUIREMENT,
                    null,
                    admin,
                    content("System req", RequirementCategory.FUNCTIONAL, Priority.MUST),
                ),
            )
        val swReq =
            itemService.createItem(
                ItemService.NewItem(
                    p.id,
                    ItemType.SOFTWARE_REQUIREMENT,
                    comp.id,
                    admin,
                    content("Software req", RequirementCategory.FUNCTIONAL, Priority.MUST, SafetyClass.B),
                ),
            )

        assertThat(swReq.humanKey).isEqualTo("LNK-CORE-SRS-001")

        traceLinks.link(sysReq.id, need.id, LinkType.DERIVES_FROM, "derives", admin)
        traceLinks.link(swReq.id, sysReq.id, LinkType.DERIVES_FROM, null, admin)

        // The software requirement traces up to the system requirement.
        val swView = traceLinks.traceView(swReq.id)
        assertThat(swView.outgoing)
            .singleElement()
            .satisfies({ assertThat(it.otherHumanKey).isEqualTo(sysReq.humanKey) })

        // And the system requirement sees the software requirement downstream and the need upstream.
        val sysView = traceLinks.traceView(sysReq.id)
        assertThat(sysView.outgoing.map { it.otherHumanKey }).containsExactly(need.humanKey)
        assertThat(sysView.incoming.map { it.otherHumanKey }).containsExactly(swReq.humanKey)

        // Wrong direction is guarded: a software requirement cannot derive from a user need.
        assertThatThrownBy { traceLinks.link(swReq.id, need.id, LinkType.DERIVES_FROM, null, admin) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `approving a revision freezes it and is audited as a status change`() {
        val admin = adminId()
        val p = project("APR")
        val item =
            itemService.createItem(
                ItemService.NewItem(p.id, ItemType.USER_NEED, null, admin, content("Need")),
            )
        val revId = item.currentRevisionId!!

        workflow.submitForReview(revId)
        workflow.approve(revId, admin)

        val rev = revisions.findById(revId).get()
        assertThat(rev.lifecycleStatus).isEqualTo(LifecycleStatus.APPROVED)
        assertThat(rev.frozen).isTrue()
        assertThat(rev.approvedBy).isEqualTo(admin)

        // A frozen revision cannot be edited in place.
        assertThatThrownBy { itemService.updateDraft(revId, content("Edited")) }
            .isInstanceOf(IllegalStateException::class.java)

        // Editing instead spawns a new draft revision.
        itemService.createRevision(item.id, content("Edited"), "post-approval edit", admin)
        val revs = revisions.findByItemIdOrderByRevisionNoAsc(item.id)
        assertThat(revs).hasSize(2)
        assertThat(revs.last().lifecycleStatus).isEqualTo(LifecycleStatus.DRAFT)

        // The approval transition is captured in the audit trail.
        val statusChanges =
            audit
                .findByEntityIdOrderByOccurredAtAsc(revId)
                .filter { it.action == AuditAction.STATUS_CHANGE && it.field == "lifecycleStatus" }
        assertThat(statusChanges.map { it.newValue }).contains("APPROVED")
    }

    @Test
    fun `illegal lifecycle transitions are rejected`() {
        val admin = adminId()
        val p = project("ILL")
        val item =
            itemService.createItem(
                ItemService.NewItem(p.id, ItemType.USER_NEED, null, admin, content("Need")),
            )
        // Cannot approve straight from Draft.
        assertThatThrownBy { workflow.approve(item.currentRevisionId!!, admin) }
            .isInstanceOf(IllegalStateException::class.java)
    }

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
}
