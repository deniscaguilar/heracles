package com.heracles.platform.adapter.out

import com.heracles.platform.application.port.out.DeadLetters
import com.heracles.platform.application.port.out.TaskQueue
import com.heracles.platform.domain.QueueMessage

/** The dead letter queue is another SQS queue; the message goes there as it came. */
class SqsDeadLetters(private val queue: TaskQueue) : DeadLetters {
    override fun send(message: QueueMessage) = queue.send(message)
}
