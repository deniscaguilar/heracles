package com.heracles.platform.application

import com.heracles.platform.application.port.out.OutboxStore
import com.heracles.platform.application.port.out.TaskQueue
import com.heracles.platform.application.port.out.TransactionContext
import com.heracles.platform.domain.AccessContext
import com.heracles.platform.domain.Backoff
import com.heracles.platform.domain.QueueMessage
import org.slf4j.LoggerFactory
import java.time.Clock

data class PublishResult(val published: Int, val failed: Int)

/**
 * Sends the due outbox messages to the queue, one batch per call, inside one transaction that keeps the rows locked
 * (so other publishers skip them). Delivery is at least once: if the process dies after the send and before the commit,
 * the message goes out again, which is why consumers are idempotent. A failed send is retried later, waiting longer each time.
 */
class PublishOutbox(
    private val transactions: TransactionContext,
    private val store: OutboxStore,
    private val queue: TaskQueue,
    private val clock: Clock,
    private val batchSize: Int = 50,
) {
    private val log = LoggerFactory.getLogger(PublishOutbox::class.java)

    operator fun invoke(): PublishResult = transactions.run(AccessContext.system()) {
        val now = clock.instant()
        var published = 0
        var failed = 0
        store.claimDue(batchSize, now).forEach { entry ->
            try {
                queue.send(QueueMessage(entry.id.toString(), entry.type, entry.payload))
                store.markPublished(entry.id, now)
                published++
            } catch (e: Exception) {
                val attempts = entry.attempts + 1
                store.markFailed(entry.id, attempts, now.plus(Backoff.delay(attempts)))
                failed++
                // Only the kind of failure: the message of an exception may hold data.
                log.warn("outbox message {} not sent (attempt {}): {}", entry.id, attempts, e.javaClass.simpleName)
            }
        }
        PublishResult(published, failed)
    }
}
