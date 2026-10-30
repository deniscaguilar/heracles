package com.heracles.platform.adapter.out

import com.heracles.platform.application.port.out.DeadLetters
import com.heracles.platform.application.port.out.TaskQueue
import com.heracles.platform.domain.QueueMessage
import com.heracles.platform.domain.ReceivedMessage
import java.time.Duration

/** The dead letter queue is another SQS queue; the message goes there as it came. */
class SqsDeadLetters(private val queue: TaskQueue) : DeadLetters {
    override fun send(message: QueueMessage) = queue.send(message)

    /**
     * SQS cannot look a message up by id, so the queue is read in batches until the id shows up. The messages read
     * and not wanted are made visible again at once. Meant for the rare administrative action, not for a hot path.
     */
    override fun find(messageId: String): ReceivedMessage? {
        repeat(MAX_BATCHES) {
            val batch = queue.receive(maxMessages = 10, waitSeconds = 1)
            if (batch.isEmpty()) return null
            val match = batch.firstOrNull { it.message.id == messageId }
            batch.filter { it !== match }.forEach { queue.postpone(it.receipt, Duration.ZERO) }
            if (match != null) return match
        }
        return null
    }

    override fun remove(receipt: String) = queue.acknowledge(receipt)

    private companion object {
        const val MAX_BATCHES = 50
    }
}
