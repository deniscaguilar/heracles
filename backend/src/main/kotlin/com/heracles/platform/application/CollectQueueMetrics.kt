package com.heracles.platform.application

import com.heracles.platform.application.port.out.PipelineAges
import com.heracles.platform.application.port.out.QueueDepths
import com.heracles.platform.application.port.out.QueueMetrics
import com.heracles.platform.domain.QueueSnapshot

/** Reads the queues and the database and publishes one snapshot. If any source fails nothing is published (no half-true reading). */
class CollectQueueMetrics(
    private val depths: QueueDepths,
    private val ages: PipelineAges,
    private val metrics: QueueMetrics,
) {
    operator fun invoke() {
        metrics.publish(
            QueueSnapshot(
                workQueueDepth = depths.workQueue(),
                deadLetterDepth = depths.deadLetterQueue(),
                oldestOutboxMessage = ages.oldestUnpublishedOutboxMessage(),
                oldestUnfinishedTask = ages.oldestUnfinishedTask(),
            ),
        )
    }
}
