package com.heracles.platform.application.port.out

import com.heracles.platform.domain.QueueMessage
import com.heracles.platform.domain.ReceivedMessage

/** Where a message goes when it has failed every attempt. It waits there for a person (reprocess: E1-S6.8). */
interface DeadLetters {
    fun send(message: QueueMessage)

    /** Looks for the message with this id; the receipt in the result is for [remove]. Other messages stay where they are. */
    fun find(messageId: String): ReceivedMessage?

    fun remove(receipt: String)
}
