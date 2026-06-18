package com.rms.domain

import com.rms.audit.Audited
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "project")
class Project(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "`key`", nullable = false, unique = true)
    var key: String,

    @Column(nullable = false)
    var name: String,

    @Column(columnDefinition = "text")
    var description: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: LifecycleState = LifecycleState.ACTIVE,

    @Enumerated(EnumType.STRING)
    @Column(name = "default_safety_class")
    var defaultSafetyClass: SafetyClass? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "created_by")
    var createdBy: UUID? = null,
) : Audited {
    override val auditId: UUID get() = id
    override fun auditLabel() = "Project $key ($name)"
}
