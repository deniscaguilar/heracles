package com.heracles.platform.application.port.out

import com.heracles.platform.domain.QueueMessage
import com.heracles.platform.domain.ReceivedMessage

/** The queue behind asynchronous work (SQS in production, ElasticMQ locally). Delivery is at least once. */
interface TaskQueue {
    fun send(message: QueueMessage)

    /** Waits up to [waitSeconds] for messages (long polling). */
    fun receive(maxMessages: Int, waitSeconds: Int): List<ReceivedMessage>

    /** Removes the message. One that is not acknowledged is delivered again after the visibility timeout. */
    fun acknowledge(receipt: String)
}
