package com.heracles.platform.application

import com.heracles.platform.application.port.out.ProcessedMessages
import com.heracles.platform.application.port.out.TaskHandler
import com.heracles.platform.application.port.out.TaskQueue
import com.heracles.platform.application.port.out.TransactionContext
import com.heracles.platform.domain.AccessContext
import com.heracles.platform.domain.ReceivedMessage
import java.util.UUID

enum class ConsumeOutcome { PROCESSED, DUPLICATE }

class UnknownTaskTypeException(type: String) : RuntimeException("no handler for message type '$type'")

/**
 * Handles one delivery idempotently. In one transaction: record the message id for this consumer and, only if it is new,
 * run the handler. A repeat is acknowledged without effect. A failure rolls back the marker and the effect together and
 * leaves the message unacknowledged, so the queue delivers it again (retry, DLQ: E1-S6.6).
 * The transaction runs in the scope of the `userId` in the message (row-level security applies to the effect), or in
 * the system scope when the message names no user.
 */
class ConsumeTask(
    private val transactions: TransactionContext,
    private val processed: ProcessedMessages,
    private val queue: TaskQueue,
    private val handlers: Map<String, TaskHandler>,
    private val consumer: String = "task-consumer",
) {
    operator fun invoke(delivery: ReceivedMessage): ConsumeOutcome {
        val message = delivery.message
        val handler = handlers[message.type] ?: throw UnknownTaskTypeException(message.type)
        val context = message.ids["userId"]?.let { AccessContext.forUser(UUID.fromString(it)) } ?: AccessContext.system()

        val outcome = transactions.run(context) {
            if (processed.markProcessed(consumer, message.id)) {
                handler.handle(message)
                ConsumeOutcome.PROCESSED
            } else {
                ConsumeOutcome.DUPLICATE
            }
        }
        queue.acknowledge(delivery.receipt)
        return outcome
    }
}
