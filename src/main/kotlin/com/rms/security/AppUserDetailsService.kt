package com.rms.security

import com.rms.repo.AppUserRepository
import com.rms.repo.RoleAssignmentRepository
import com.rms.repo.RoleRepository
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AppUserDetailsService(
    private val users: AppUserRepository,
    private val roleAssignments: RoleAssignmentRepository,
    private val roles: RoleRepository,
) : UserDetailsService {

    @Transactional(readOnly = true)
    override fun loadUserByUsername(username: String): UserDetails {
        val user = users.findByUsername(username)
            ?: throw UsernameNotFoundException("Unknown user: $username")

        val authorities = roleAssignments.findByUserId(user.id)
            .mapNotNull { roles.findById(it.roleId).orElse(null) }
            .map { SimpleGrantedAuthority("ROLE_${it.name.name}") }
            .distinct()

        return AppUserPrincipal(
            userId = user.id,
            username = user.username,
            password = user.password,
            active = user.active,
            authorities = authorities,
        )
    }
}
