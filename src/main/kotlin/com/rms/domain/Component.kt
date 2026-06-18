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
@Table(name = "component")
class Component(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "project_id", nullable = false)
    val projectId: UUID,

    @Column(name = "`key`", nullable = false)
    var key: String,

    @Column(nullable = false)
    var name: String,

    @Column(columnDefinition = "text")
    var description: String? = null,

    @Column(name = "parent_component_id")
    var parentComponentId: UUID? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "safety_class")
    var safetyClass: SafetyClass? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: LifecycleState = LifecycleState.ACTIVE,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "created_by")
    var createdBy: UUID? = null,
) : Audited {
    override val auditId: UUID get() = id
    override fun auditLabel() = "Component $key ($name)"
}
