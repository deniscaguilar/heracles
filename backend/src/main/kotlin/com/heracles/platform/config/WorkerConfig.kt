package com.heracles.platform.config

import com.heracles.platform.adapter.`in`.scheduler.OutboxPublisherJob
import com.heracles.platform.adapter.`in`.scheduler.QueueConsumerJob
import com.heracles.platform.application.ProcessDelivery
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
}
