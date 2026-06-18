package com.rms

import com.rms.domain.AuditAction
import com.rms.domain.ItemType
import com.rms.domain.LifecycleState
import com.rms.domain.Project
import com.rms.domain.UserNeedSource
import com.rms.repo.AppUserRepository
import com.rms.repo.AuditEntryRepository
import com.rms.repo.ItemRevisionRepository
import com.rms.repo.ProjectRepository
import com.rms.service.ItemService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class ItemAuditTest(
    @Autowired private val itemService: ItemService,
    @Autowired private val projects: ProjectRepository,
    @Autowired private val users: AppUserRepository,
    @Autowired private val revisions: ItemRevisionRepository,
    @Autowired private val audit: AuditEntryRepository,
) {
    @Test
    fun `creating and revising an item produces a versioned, audited record`() {
        val admin = users.findByUsername("admin")!!
        val project =
            projects.save(
                Project(key = "TST", name = "Test Project", status = LifecycleState.ACTIVE, createdBy = admin.id),
            )

        // Create a versioned item.
        val item =
            itemService.createItem(
                ItemService.NewItem(
                    projectId = project.id,
                    type = ItemType.USER_NEED,
                    componentId = null,
                    authorId = admin.id,
                    content =
                        ItemService.RevisionContent(
                            title = "Clinician can review alarms",
                            statement = "The clinician shall be able to review all active alarms.",
                            rationale = "Clinical safety need.",
                            userNeedSource = UserNeedSource.CLINICAL,
                        ),
                ),
            )

        assertThat(item.humanKey).isEqualTo("TST-UN-001")
        assertThat(item.currentRevisionId).isNotNull()

        // Spawn a second revision.
        itemService.createRevision(
            itemId = item.id,
            content =
                ItemService.RevisionContent(
                    title = "Clinician can review and acknowledge alarms",
                    statement = "The clinician shall be able to review and acknowledge all active alarms.",
                ),
            changeReason = "Added acknowledge capability after review.",
            authorId = admin.id,
        )

        // Two revisions exist; exactly one is current.
        val revs = revisions.findByItemIdOrderByRevisionNoAsc(item.id)
        assertThat(revs).hasSize(2)
        assertThat(revs.count { it.isCurrent }).isEqualTo(1)
        assertThat(revs.last().isCurrent).isTrue()
        assertThat(revs.last().revisionNo).isEqualTo(2)

        // The item creation is audited.
        val itemAudit = audit.findByEntityIdOrderByOccurredAtAsc(item.id)
        assertThat(itemAudit).anyMatch { it.action == AuditAction.CREATE }
        assertThat(itemAudit).anyMatch {
            it.action == AuditAction.UPDATE && it.field == "currentRevisionId"
        }

        // The revisions are audited as creates.
        val rev1Audit = audit.findByEntityIdOrderByOccurredAtAsc(revs.first().id)
        assertThat(rev1Audit).anyMatch { it.action == AuditAction.CREATE }
    }
}
