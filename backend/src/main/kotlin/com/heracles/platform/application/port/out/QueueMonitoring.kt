package com.heracles.platform.application.port.out

import com.heracles.platform.domain.QueueSnapshot
import java.time.Duration

/** Messages in the queues, waiting or being handled. Approximate, as in SQS. */
interface QueueDepths {
    fun workQueue(): Int
    fun deadLetterQueue(): Int
}

/** How long the oldest waiting work has been waiting, by the database clock. Zero when nothing waits. */
interface PipelineAges {
    fun oldestUnpublishedOutboxMessage(): Duration
    fun oldestUnfinishedTask(): Duration
}

/** Where a reading goes out as metrics. */
interface QueueMetrics {
    fun publish(snapshot: QueueSnapshot)
}
