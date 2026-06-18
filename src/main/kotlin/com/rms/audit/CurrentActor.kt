package com.rms.audit

import com.rms.security.AppUserPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import java.util.UUID

/** Resolves the authenticated user driving the current change, for the audit trail. */
@Component
class CurrentActor {
    fun userId(): UUID? {
        val auth = SecurityContextHolder.getContext().authentication ?: return null
        val principal = auth.principal
        return if (principal is AppUserPrincipal) principal.userId else null
    }
}
