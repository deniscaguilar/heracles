package com.heracles.platform.application.port.out

import com.heracles.platform.domain.QueueMessage

/** The work for one type of message. Runs inside the consumer's transaction, in the scope of the user named in the message. */
fun interface TaskHandler {
    fun handle(message: QueueMessage)
}
