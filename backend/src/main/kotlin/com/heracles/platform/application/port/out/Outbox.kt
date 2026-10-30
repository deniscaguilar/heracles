package com.heracles.platform.application.port.out

import com.heracles.platform.domain.QueueMessage

/**
 * Transactional outbox: the message is stored in the SAME transaction as the state change, so both stay or both go.
 * A publisher (E1-S6.4) later sends it to the queue. Works from any access scope.
 */
interface Outbox {
    /** [message] id must be a UUID. Joins the current transaction. */
    fun append(aggregateType: String, aggregateId: String, message: QueueMessage)
}
