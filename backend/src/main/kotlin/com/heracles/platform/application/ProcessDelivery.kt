package com.heracles.platform.application

import com.heracles.platform.application.port.out.DeadLetters
import com.heracles.platform.application.port.out.TaskQueue
import com.heracles.platform.application.port.out.TaskStatuses
import com.heracles.platform.domain.Backoff
import com.heracles.platform.domain.ReceivedMessage
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.UUID

enum class DeliveryOutcome { PROCESSED, DUPLICATE, RETRY_SCHEDULED, DEAD_LETTERED }

/**
 * What to do with one delivery: consume it; if that fails, wait longer each time and try again, and at the last
 * attempt send it to the dead letter queue and mark its task FAILED. The task is the `taskId` in the message, when there is one.
 * Only the kind of failure (the exception class) is recorded and logged: the message of an exception may hold data.
 */
class ProcessDelivery(
    private val consume: ConsumeTask,
    private val queue: TaskQueue,
    private val deadLetters: DeadLetters,
    private val statuses: TaskStatuses,
    private val maxAttempts: Int,
    private val delay: (attempt: Int) -> Duration = Backoff::delay,
) {
    private val log = LoggerFactory.getLogger(ProcessDelivery::class.java)

    operator fun invoke(delivery: ReceivedMessage): DeliveryOutcome {
        val taskId = delivery.message.ids["taskId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        return try {
            val outcome = consume(delivery)
            taskId?.let(statuses::markDone)
            if (outcome == ConsumeOutcome.PROCESSED) DeliveryOutcome.PROCESSED else DeliveryOutcome.DUPLICATE
        } catch (e: Exception) {
            val errorCode = e.javaClass.simpleName
            val attempts = delivery.receiveCount
            if (attempts >= maxAttempts) {
                deadLetters.send(delivery.message)
                taskId?.let { statuses.markFailed(it, attempts, errorCode) }
                queue.acknowledge(delivery.receipt)
                log.warn("message {} ({}) dead-lettered after {} attempts: {}", delivery.message.id, delivery.message.type, attempts, errorCode)
                DeliveryOutcome.DEAD_LETTERED
            } else {
                taskId?.let { statuses.markRetrying(it, attempts, errorCode) }
                queue.postpone(delivery.receipt, delay(attempts))
                log.warn("message {} ({}) failed on attempt {}: {}", delivery.message.id, delivery.message.type, attempts, errorCode)
                DeliveryOutcome.RETRY_SCHEDULED
            }
        }
    }
}
