package com.rms.audit

import com.rms.domain.AuditAction
import org.hibernate.Interceptor
import org.hibernate.type.Type
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

/**
 * Hibernate-level audit trail. Every insert/update/delete of an [Audited] entity is captured
 * here, so the trail cannot be bypassed by normal code paths (PLAN §8, Part 11).
 *
 * Records are buffered per flush and written in [postFlush] via [AuditWriter] (plain JDBC on
 * the same transaction), which avoids triggering another Hibernate flush. The buffer is
 * thread-local because a single interceptor instance serves all sessions.
 *
 * Dependencies are pulled lazily through [ObjectProvider] because Hibernate instantiates /
 * wires the interceptor early in bootstrap, before the full Spring context is available.
 */
@Component
class AuditInterceptor(
    private val writerProvider: ObjectProvider<AuditWriter>,
    private val actorProvider: ObjectProvider<CurrentActor>,
) : Interceptor {
    private val buffer = ThreadLocal.withInitial { mutableListOf<AuditRecord>() }

    override fun onSave(
        entity: Any,
        id: Any?,
        state: Array<Any?>,
        propertyNames: Array<String>,
        types: Array<Type>,
    ): Boolean {
        if (entity is Audited) {
            buffer.get().add(
                record(entity, AuditAction.CREATE, field = null, old = null, new = entity.auditLabel()),
            )
        }
        return false
    }

    override fun onFlushDirty(
        entity: Any,
        id: Any?,
        currentState: Array<Any?>,
        previousState: Array<Any?>?,
        propertyNames: Array<String>,
        types: Array<Type>,
    ): Boolean {
        if (entity is Audited) {
            for (i in propertyNames.indices) {
                val old = previousState?.get(i)
                val new = currentState[i]
                if (old != new) {
                    val isStatus = propertyNames[i] == "lifecycleStatus" || propertyNames[i] == "status"
                    buffer.get().add(
                        record(
                            entity,
                            if (isStatus) AuditAction.STATUS_CHANGE else AuditAction.UPDATE,
                            field = propertyNames[i],
                            old = old?.toString(),
                            new = new?.toString(),
                        ),
                    )
                }
            }
        }
        return false
    }

    override fun onDelete(
        entity: Any,
        id: Any?,
        state: Array<Any?>,
        propertyNames: Array<String>,
        types: Array<Type>,
    ) {
        if (entity is Audited) {
            buffer.get().add(
                record(entity, AuditAction.DELETE, field = null, old = entity.auditLabel(), new = null),
            )
        }
    }

    override fun postFlush(entities: MutableIterator<Any?>) {
        val pending = buffer.get()
        if (pending.isEmpty()) return
        val toWrite = pending.toList()
        pending.clear()
        writerProvider.getObject().write(toWrite)
    }

    private fun record(
        entity: Audited,
        action: AuditAction,
        field: String?,
        old: String?,
        new: String?,
    ) = AuditRecord(
        entityType = entity.javaClass.simpleName,
        entityId = entity.auditId,
        action = action,
        actorUserId = currentActorId(),
        occurredAt = Instant.now(),
        field = field,
        oldValue = old,
        newValue = new,
    )

    private fun currentActorId(): UUID? = actorProvider.ifAvailable?.userId()
}
