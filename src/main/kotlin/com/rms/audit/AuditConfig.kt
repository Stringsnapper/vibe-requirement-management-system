package com.rms.audit

import org.hibernate.cfg.AvailableSettings
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer
import org.springframework.context.annotation.Configuration

/**
 * Registers the [AuditInterceptor] as Hibernate's session-factory interceptor so it is
 * invoked for every persistence operation across the application.
 */
@Configuration
class AuditConfig(private val auditInterceptor: AuditInterceptor) : HibernatePropertiesCustomizer {

    override fun customize(hibernateProperties: MutableMap<String, Any>) {
        hibernateProperties[AvailableSettings.INTERCEPTOR] = auditInterceptor
    }
}
