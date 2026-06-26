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

/** Drives the Phase 2 traceability pages (dashboard, matrix, graph, impact) through MockMvc. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TraceabilityWebTest(
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
    fun `traceability dashboard, matrix, graph and impact pages render`() {
        val project = projects.findByKey("DEMO")!!
        val component = components.findByProjectIdOrderByKeyAsc(project.id).first()
        val pid = project.id.toString()

        val needId = createItem(pid, "USER_NEED", mapOf("userNeedSource" to "CLINICAL", "userNeedActor" to "Clinician"))
        val sysId = createItem(pid, "SYSTEM_REQUIREMENT", mapOf("category" to "FUNCTIONAL", "priority" to "MUST"))

        mockMvc
            .post("/items/$sysId/links") {
                with(user(admin()))
                with(csrf())
                param("targetItemId", needId)
                param("linkType", "DERIVES_FROM")
            }.andExpect { status { is3xxRedirection() } }

        mockMvc.get("/projects/$pid/traceability") { with(user(admin())) }.andExpect { status { isOk() } }
        mockMvc
            .get("/projects/$pid/matrix") {
                with(user(admin()))
                param("rowType", "USER_NEED")
                param("colType", "SYSTEM_REQUIREMENT")
            }.andExpect { status { isOk() } }
        mockMvc.get("/items/$sysId/graph") { with(user(admin())) }.andExpect { status { isOk() } }
        mockMvc.get("/items/$sysId/impact") { with(user(admin())) }.andExpect { status { isOk() } }
        mockMvc.get("/items/$needId/impact") { with(user(admin())) }.andExpect { status { isOk() } }
    }
}
