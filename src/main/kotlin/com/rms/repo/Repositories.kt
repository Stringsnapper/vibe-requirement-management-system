package com.rms.repo

import com.rms.domain.AppUser
import com.rms.domain.AuditEntry
import com.rms.domain.Component
import com.rms.domain.Item
import com.rms.domain.ItemRevision
import com.rms.domain.ItemType
import com.rms.domain.Project
import com.rms.domain.Role
import com.rms.domain.RoleAssignment
import com.rms.domain.RoleName
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AppUserRepository : JpaRepository<AppUser, UUID> {
    fun findByUsername(username: String): AppUser?
}

interface RoleRepository : JpaRepository<Role, UUID> {
    fun findByName(name: RoleName): Role?
}

interface RoleAssignmentRepository : JpaRepository<RoleAssignment, UUID> {
    fun findByUserId(userId: UUID): List<RoleAssignment>
}

interface ProjectRepository : JpaRepository<Project, UUID> {
    fun findByKey(key: String): Project?
    fun findAllByOrderByKeyAsc(): List<Project>
}

interface ComponentRepository : JpaRepository<Component, UUID> {
    fun findByProjectIdOrderByKeyAsc(projectId: UUID): List<Component>
}

interface ItemRepository : JpaRepository<Item, UUID> {
    fun findByProjectIdOrderByHumanKeyAsc(projectId: UUID): List<Item>
    fun countByProjectIdAndComponentIdAndType(projectId: UUID, componentId: UUID?, type: ItemType): Long
    fun existsByHumanKey(humanKey: String): Boolean
}

interface ItemRevisionRepository : JpaRepository<ItemRevision, UUID> {
    fun findByItemIdOrderByRevisionNoAsc(itemId: UUID): List<ItemRevision>
}

interface AuditEntryRepository : JpaRepository<AuditEntry, UUID> {
    fun findByEntityIdOrderByOccurredAtAsc(entityId: UUID): List<AuditEntry>
    fun findTop100ByOrderByOccurredAtDesc(): List<AuditEntry>
}
