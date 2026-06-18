package com.rms.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "app_user")
class AppUser(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(nullable = false, unique = true)
    var username: String,
    @Column(nullable = false)
    var email: String,
    @Column(name = "full_name", nullable = false)
    var fullName: String,
    @Column(nullable = false)
    var password: String,
    @Column(nullable = false)
    var active: Boolean = true,
    @Column(name = "sso_subject")
    var ssoSubject: String? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
)
