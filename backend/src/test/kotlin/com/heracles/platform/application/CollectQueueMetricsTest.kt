package com.heracles.platform.application

import com.heracles.platform.application.port.out.PipelineAges
import com.heracles.platform.application.port.out.QueueDepths
import com.heracles.platform.application.port.out.QueueMetrics
import com.heracles.platform.domain.QueueSnapshot
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Duration

private class FixedDepths(val work: Int, val dead: Int) : QueueDepths {
    override fun workQueue(): Int = work
    override fun deadLetterQueue(): Int = dead
}

private class FixedAges(val outbox: Duration, val task: Duration) : PipelineAges {
    override fun oldestUnpublishedOutboxMessage(): Duration = outbox
    override fun oldestUnfinishedTask(): Duration = task
}

private class Published : QueueMetrics {
    val snapshots = mutableListOf<QueueSnapshot>()
    override fun publish(snapshot: QueueSnapshot) { snapshots += snapshot }
}

class CollectQueueMetricsTest {
    @Test
    fun `what the queue, the dead letter queue and the database report is published together`() {
        val published = Published()
        CollectQueueMetrics(FixedDepths(work = 7, dead = 2), FixedAges(Duration.ofSeconds(90), Duration.ofMinutes(5)), published)()

        assertThat(published.snapshots).containsExactly(
            QueueSnapshot(workQueueDepth = 7, deadLetterDepth = 2, oldestOutboxMessage = Duration.ofSeconds(90), oldestUnfinishedTask = Duration.ofMinutes(5)),
        )
    }

    @Test
    fun `a dead letter queue that is not empty is what an alarm looks for`() {
        assertThat(QueueSnapshot(0, 0, Duration.ZERO, Duration.ZERO).hasDeadLetters).isFalse()
        assertThat(QueueSnapshot(0, 1, Duration.ZERO, Duration.ZERO).hasDeadLetters).isTrue()
    }

    @Test
    fun `if one source fails nothing half-true is published`() {
        val published = Published()
        val broken = object : QueueDepths {
            override fun workQueue(): Int = error("queue unreachable")
            override fun deadLetterQueue(): Int = 0
        }
        assertThatThrownBy { CollectQueueMetrics(broken, FixedAges(Duration.ZERO, Duration.ZERO), published)() }.hasMessage("queue unreachable")
        assertThat(published.snapshots).isEmpty()
    }
}
