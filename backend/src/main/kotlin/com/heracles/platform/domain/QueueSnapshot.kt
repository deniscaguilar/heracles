package com.heracles.platform.domain

import java.time.Duration

/** A reading of the asynchronous pipeline: how much waits, and for how long. Counts and durations only, no content. */
data class QueueSnapshot(
    val workQueueDepth: Int,
    val deadLetterDepth: Int,
    /** Age of the oldest outbox message not yet sent to the queue (zero when there is none). */
    val oldestOutboxMessage: Duration,
    /** Age of the oldest task still PENDING or PROCESSING (zero when there is none). */
    val oldestUnfinishedTask: Duration,
) {
    /** What the alarm of a non-empty dead letter queue looks for (CA16). */
    val hasDeadLetters: Boolean get() = deadLetterDepth > 0
}
