package com.rms

import com.rms.repo.ComponentRepository
import com.rms.repo.ProjectRepository
import com.rms.security.AppUserDetailsService
import com.rms.security.AppUserPrincipal
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

/**
 * Drives the Phase 1 web layer end-to-end through MockMvc — authoring each requirement type,
 * viewing the guided detail page, linking to a parent, and walking the lifecycle — which also
 * exercises the role-gated controller branches that the service tests don't reach.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebFlowTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val projects: ProjectRepository,
    @Autowired private val components: ComponentRepository,
    @Autowired private val userDetails: AppUserDetailsService,
) {
    private fun admin(): AppUserPrincipal = userDetails.loadUserByUsername("admin") as AppUserPrincipal

    private fun createItem(
        projectId: String,
        type: String,
        params: Map<String, String> = emptyMap(),
    ): String {
        val result =
            mockMvc
                .post("/items") {
                    with(user(admin()))
                    with(csrf())
                    param("projectId", projectId)
                    param("type", type)
                    param("title", "$type title")
                    param("statement", "$type statement")
                    param("acceptanceCriteria", "Given X, when Y, then Z")
                    params.forEach { (k, v) -> param(k, v) }
                }.andReturn()
        return result.response.redirectedUrl!!.removePrefix("/items/")
    }

    @Test
    fun `author, view, link and approve through the web layer`() {
        val project = projects.findByKey("DEMO")!!
        val component = components.findByProjectIdOrderByKeyAsc(project.id).first()
        val pid = project.id.toString()

        val needId = createItem(pid, "USER_NEED", mapOf("userNeedSource" to "CLINICAL", "userNeedActor" to "Clinician"))
        val sysId = createItem(pid, "SYSTEM_REQUIREMENT", mapOf("category" to "FUNCTIONAL", "priority" to "MUST"))
        val swId =
            createItem(
                pid,
                "SOFTWARE_REQUIREMENT",
                mapOf(
                    "componentId" to component.id.toString(),
                    "category" to "FUNCTIONAL",
                    "priority" to "MUST",
                    "softwareSafetyClass" to "B",
                ),
            )

        // The new-item form and detail pages render for an authenticated author.
        mockMvc.get("/items/new") { with(user(admin())) }.andExpect { status { isOk() } }
        mockMvc.get("/items/$swId") { with(user(admin())) }.andExpect { status { isOk() } }

        // Guided link: the system requirement derives from the user need.
        mockMvc
            .post("/items/$sysId/links") {
                with(user(admin()))
                with(csrf())
                param("targetItemId", needId)
                param("linkType", "DERIVES_FROM")
            }.andExpect { status { is3xxRedirection() } }

        // Walk the lifecycle of the system requirement's current revision.
        val sysRevId = revisionIdOf(sysId)
        post("/revisions/$sysRevId/submit")
        post("/revisions/$sysRevId/approve")

        mockMvc
            .get("/items/$sysId") { with(user(admin())) }
            .andExpect { status { isOk() } }
    }

    private fun post(url: String) {
        mockMvc
            .post(url) {
                with(user(admin()))
                with(csrf())
            }.andExpect { status { is3xxRedirection() } }
    }

    /** Read the current revision id off the rendered detail page's submit/transition form. */
    private fun revisionIdOf(itemId: String): String {
        val html =
            mockMvc
                .get("/items/$itemId") { with(user(admin())) }
                .andReturn()
                .response.contentAsString
        val match = Regex("/revisions/([0-9a-f-]{36})/").find(html)
        return match!!.groupValues[1]
    }
}
