package com.heracles.platform.application.port.out

import com.heracles.platform.domain.QueueMessage

/** Where a message goes when it has failed every attempt. It waits there for a person (reprocess: E1-S6.8). */
interface DeadLetters {
    fun send(message: QueueMessage)
}
