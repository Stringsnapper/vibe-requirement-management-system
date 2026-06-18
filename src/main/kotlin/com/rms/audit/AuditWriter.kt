package com.rms.audit

import com.rms.domain.AuditAction
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/** A single pending audit record captured by the interceptor before it is written. */
data class AuditRecord(
    val entityType: String,
    val entityId: UUID,
    val action: AuditAction,
    val actorUserId: UUID?,
    val occurredAt: Instant,
    val field: String?,
    val oldValue: String?,
    val newValue: String?,
)

/**
 * Writes buffered audit records directly via JDBC on the transaction-bound connection.
 * Going through [JdbcTemplate] (not the Hibernate session) means writing the audit trail
 * does not trigger another flush — avoiding re-entrancy with the [AuditInterceptor].
 */
@Component
class AuditWriter(private val jdbc: JdbcTemplate) {

    fun write(records: List<AuditRecord>) {
        if (records.isEmpty()) return
        jdbc.batchUpdate(
            """
            INSERT INTO audit_entry
                (id, entity_type, entity_id, action, actor_user_id, occurred_at, field, old_value, new_value)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            records.map { r ->
                arrayOf(
                    UUID.randomUUID(),
                    r.entityType,
                    r.entityId,
                    r.action.name,
                    r.actorUserId,
                    Timestamp.from(r.occurredAt),
                    r.field,
                    r.oldValue,
                    r.newValue,
                )
            },
        )
    }
}
