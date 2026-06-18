package com.rms.security

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import java.util.UUID

/**
 * Authenticated principal carrying the user's stable UUID so the audit trail can record
 * exactly who made each change without a second lookup.
 */
class AppUserPrincipal(
    val userId: UUID,
    private val username: String,
    private val password: String,
    private val active: Boolean,
    private val authorities: Collection<GrantedAuthority>,
) : UserDetails {
    override fun getAuthorities() = authorities

    override fun getPassword() = password

    override fun getUsername() = username

    override fun isAccountNonExpired() = true

    override fun isAccountNonLocked() = true

    override fun isCredentialsNonExpired() = true

    override fun isEnabled() = active
}
