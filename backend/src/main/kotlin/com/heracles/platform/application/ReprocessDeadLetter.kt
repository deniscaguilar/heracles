package com.heracles.platform.application

import com.heracles.platform.application.port.out.DeadLetters
import com.heracles.platform.application.port.out.TaskQueue
import com.heracles.platform.application.port.out.TaskStatuses
import com.heracles.platform.domain.AccessScope
import com.heracles.platform.domain.AppUser
import com.heracles.platform.domain.AuditEvent
import com.heracles.platform.domain.AuditResult
import com.heracles.platform.domain.DeadLetterNotFoundException
import java.util.UUID

/**
 * Sends a dead message back to the work queue (same message id). It is sent first and removed from the dead letter
 * queue after, so a crash in between duplicates the message instead of losing it; the consumer is idempotent by id,
 * and the marker of the failed attempts was rolled back, so the effect happens exactly once. The action is audited.
 */
class ReprocessDeadLetter(
    private val deadLetters: DeadLetters,
    private val queue: TaskQueue,
    private val statuses: TaskStatuses,
    private val recordAudit: RecordAudit,
) {
    operator fun invoke(admin: AppUser, messageId: String) {
        val dead = deadLetters.find(messageId) ?: throw DeadLetterNotFoundException()
        queue.send(dead.message)
        deadLetters.remove(dead.receipt)
        dead.message.ids["taskId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }?.let(statuses::markRequeued)
        recordAudit(
            AuditEvent(
                actorUserId = admin.id, actorScope = AccessScope.ADMIN, action = "dead_letter.reprocess",
                entityType = "dead_letter", entityId = messageId, result = AuditResult.SUCCESS, fields = emptyList(), traceId = null,
            ),
        )
    }
}
