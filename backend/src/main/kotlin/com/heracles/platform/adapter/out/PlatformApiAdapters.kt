package com.heracles.platform.adapter.out

import com.heracles.platform.adapter.`in`.web.TraceIds
import com.heracles.platform.api.AuditLog
import com.heracles.platform.api.ClockProvider
import com.heracles.platform.api.EventPublisher
import com.heracles.platform.api.Notifier
import com.heracles.platform.api.Outcome
import com.heracles.platform.api.Scope
import com.heracles.platform.api.TaskGateway
import com.heracles.platform.api.Transactions
import com.heracles.platform.application.RecordAudit
import com.heracles.platform.application.port.out.Clock
import com.heracles.platform.application.port.out.NotificationChannel
import com.heracles.platform.application.port.out.Outbox
import com.heracles.platform.application.port.out.TransactionContext
import com.heracles.platform.domain.AccessContext
import com.heracles.platform.domain.AccessScope
import com.heracles.platform.domain.AuditEvent
import com.heracles.platform.domain.AuditResult
import com.heracles.platform.domain.QueueMessage
import org.springframework.beans.factory.ObjectProvider
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** The platform's public face for other modules (`platform.api`), built on its own ports and use cases. */
@Component
class ApiTransactions(private val context: TransactionContext) : Transactions {
    override fun <T> asUser(userId: UUID, block: () -> T): T = context.run(AccessContext.forUser(userId), block)
    override fun <T> asAdmin(block: () -> T): T = context.run(AccessContext.admin(), block)
    override fun <T> asSystem(block: () -> T): T = context.run(AccessContext.system(), block)
}

@Component
class ApiAuditLog(private val recordAudit: RecordAudit) : AuditLog {
    override fun record(
        actorUserId: UUID?, actorScope: Scope, action: String, entityType: String, entityId: String?,
        outcome: Outcome, fields: List<String>,
    ) {
        recordAudit(
            AuditEvent(
                actorUserId = actorUserId, actorScope = AccessScope.valueOf(actorScope.name), action = action,
                entityType = entityType, entityId = entityId, result = AuditResult.valueOf(outcome.name),
                fields = fields, traceId = TraceIds.current(),
            ),
        )
    }
}

@Component
class ApiClockProvider(private val clock: Clock) : ClockProvider {
    override fun now(): Instant = clock.instant()
    override val zone: ZoneId get() = clock.zone
}

/**
 * The channel is a bean only when a real adapter exists or the local fakes are on (LocalAdaptersConfig); without one, sending
 * fails at the call, never silently, so a production run cannot pretend a person was told.
 */
@Component
class ApiNotifier(private val channel: ObjectProvider<NotificationChannel>) : Notifier {
    override fun send(userId: UUID, template: String, params: Map<String, String>) {
        val available = channel.ifAvailable ?: throw IllegalStateException("no notification channel is configured")
        available.send(userId, template, params)
    }
}

@Component
class ApiEventPublisher(private val outbox: Outbox) : EventPublisher {
    override fun publish(aggregateType: String, aggregateId: String, type: String, ids: Map<String, String>) =
        outbox.append(aggregateType, aggregateId, QueueMessage(UUID.randomUUID().toString(), type, ids))
}

/** The task row and its outbox message go in the caller's transaction, so both stay or both go. */
@Component
class ApiTaskGateway(private val jdbc: JdbcTemplate, private val outbox: Outbox) : TaskGateway {
    override fun enqueue(userId: UUID?, type: String, ids: Map<String, String>): UUID {
        val taskId = UUID.randomUUID()
        jdbc.queryForRowSet("select platform.create_task(?, ?, ?)", taskId, userId, type)
        val carried = ids + mapOf("taskId" to taskId.toString()) + (userId?.let { mapOf("userId" to it.toString()) } ?: emptyMap())
        outbox.append("task", taskId.toString(), QueueMessage(taskId.toString(), type, carried))
        return taskId
    }
}
