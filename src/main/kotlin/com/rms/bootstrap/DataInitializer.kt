package com.rms.bootstrap

import com.rms.domain.AppUser
import com.rms.domain.Component
import com.rms.domain.LifecycleState
import com.rms.domain.Project
import com.rms.domain.Role
import com.rms.domain.RoleAssignment
import com.rms.domain.RoleName
import com.rms.domain.SafetyClass
import com.rms.repo.AppUserRepository
import com.rms.repo.ComponentRepository
import com.rms.repo.ProjectRepository
import com.rms.repo.RoleAssignmentRepository
import com.rms.repo.RoleRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Transactional
import org.springframework.stereotype.Component as SpringComponent

/**
 * Seeds the built-in roles, a default admin user, and a sample project + component on first
 * boot so the application is usable immediately. Idempotent: does nothing if users already exist.
 */
@SpringComponent
class DataInitializer(
    private val users: AppUserRepository,
    private val roles: RoleRepository,
    private val roleAssignments: RoleAssignmentRepository,
    private val projects: ProjectRepository,
    private val components: ComponentRepository,
    private val passwordEncoder: PasswordEncoder,
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun run(args: ApplicationArguments) {
        // Reference roles (idempotent).
        RoleName.entries.forEach { name ->
            if (roles.findByName(name) == null) roles.save(Role(name = name))
        }

        if (users.count() > 0L) return

        val admin =
            users.save(
                AppUser(
                    username = "admin",
                    email = "admin@example.com",
                    fullName = "Default Administrator",
                    password = passwordEncoder.encode("admin"),
                ),
            )
        val adminRole = roles.findByName(RoleName.ADMIN)!!
        roleAssignments.save(RoleAssignment(userId = admin.id, roleId = adminRole.id, projectId = null))

        val project =
            projects.save(
                Project(
                    key = "DEMO",
                    name = "Demo Product",
                    description = "Sample project created on first boot.",
                    status = LifecycleState.ACTIVE,
                    defaultSafetyClass = SafetyClass.B,
                    createdBy = admin.id,
                ),
            )
        components.save(
            Component(
                projectId = project.id,
                key = "CORE",
                name = "Core Component",
                safetyClass = SafetyClass.B,
                createdBy = admin.id,
            ),
        )

        log.info("Seeded default admin user (admin/admin) and project DEMO. Change the admin password.")
    }
}
