package com.heracles.platform.config

import com.heracles.platform.adapter.`in`.scheduler.ExpireExportsJob
import com.heracles.platform.adapter.`in`.scheduler.OutboxPublisherJob
import com.heracles.platform.adapter.`in`.scheduler.QueueConsumerJob
import com.heracles.platform.adapter.`in`.scheduler.QueueDueErasuresJob
import com.heracles.platform.adapter.`in`.scheduler.QueueMetricsJob
import com.heracles.platform.application.CollectQueueMetrics
import com.heracles.platform.application.ExpireExports
import com.heracles.platform.application.ProcessDelivery
import com.heracles.platform.application.QueueDueErasures
import com.heracles.platform.application.PublishOutbox
import com.heracles.platform.application.port.out.TaskQueue
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** What runs only where background processing runs (profiles `worker` and `all`). */
@Configuration
@WorkerOnly
class WorkerConfig {
    /** Switched off by `heracles.outbox.publisher.enabled=false` (the tests call the use case themselves). */
    @Bean
    @ConditionalOnProperty("heracles.outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
    fun outboxPublisherJob(publishOutbox: PublishOutbox) = OutboxPublisherJob(publishOutbox)

    /** Switched off by `heracles.queue.consumer.enabled=false` (the tests call the use cases themselves). */
    @Bean
    @ConditionalOnProperty("heracles.queue.consumer.enabled", havingValue = "true", matchIfMissing = true)
    fun queueConsumerJob(queue: TaskQueue, processDelivery: ProcessDelivery) = QueueConsumerJob(queue, processDelivery)

    /** Switched off by `heracles.exports.expire.enabled=false`. */
    @Bean
    @ConditionalOnProperty("heracles.exports.expire.enabled", havingValue = "true", matchIfMissing = true)
    fun expireExportsJob(expire: ExpireExports) = ExpireExportsJob(expire)

    /** Switched off by `heracles.erasure.scheduler.enabled=false` (the tests call the use case themselves). */
    @Bean
    @ConditionalOnProperty("heracles.erasure.scheduler.enabled", havingValue = "true", matchIfMissing = true)
    fun queueDueErasuresJob(queueDue: QueueDueErasures) = QueueDueErasuresJob(queueDue)

    /** Switched off by `heracles.queue.metrics.enabled=false`. */
    @Bean
    @ConditionalOnProperty("heracles.queue.metrics.enabled", havingValue = "true", matchIfMissing = true)
    fun queueMetricsJob(collect: CollectQueueMetrics) = QueueMetricsJob(collect)
}
