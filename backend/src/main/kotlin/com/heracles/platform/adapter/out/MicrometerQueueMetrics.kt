package com.heracles.platform.adapter.out

import com.heracles.platform.application.port.out.QueueMetrics
import com.heracles.platform.domain.QueueSnapshot
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Gauges that follow the last snapshot: `heracles.queue.depth` (tag queue = work | dlq) and the two ages, in seconds. */
@Component
class MicrometerQueueMetrics(registry: MeterRegistry) : QueueMetrics {
    private val work = AtomicInteger()
    private val dead = AtomicInteger()
    private val outboxAgeMillis = AtomicLong()
    private val taskAgeMillis = AtomicLong()

    init {
        Gauge.builder("heracles.queue.depth", work) { it.get().toDouble() }.tag("queue", "work").description("Messages in the work queue").register(registry)
        Gauge.builder("heracles.queue.depth", dead) { it.get().toDouble() }.tag("queue", "dlq").description("Messages in the dead letter queue").register(registry)
        Gauge.builder("heracles.outbox.oldest.age", outboxAgeMillis) { it.get() / 1000.0 }.baseUnit("seconds")
            .description("Age of the oldest outbox message not yet sent").register(registry)
        Gauge.builder("heracles.task.oldest.age", taskAgeMillis) { it.get() / 1000.0 }.baseUnit("seconds")
            .description("Age of the oldest task still pending or processing").register(registry)
    }

    override fun publish(snapshot: QueueSnapshot) {
        work.set(snapshot.workQueueDepth)
        dead.set(snapshot.deadLetterDepth)
        outboxAgeMillis.set(snapshot.oldestOutboxMessage.toMillis())
        taskAgeMillis.set(snapshot.oldestUnfinishedTask.toMillis())
    }
}
