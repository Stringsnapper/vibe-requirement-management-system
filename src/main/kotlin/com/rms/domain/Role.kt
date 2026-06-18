package com.rms.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "role")
class Role(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    val name: RoleName,
)

@Entity
@Table(name = "role_assignment")
class RoleAssignment(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "role_id", nullable = false)
    val roleId: UUID,

    /** Null = global assignment (applies to every project). */
    @Column(name = "project_id")
    val projectId: UUID? = null,
)
