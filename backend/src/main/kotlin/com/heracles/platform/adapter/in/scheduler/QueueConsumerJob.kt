package com.heracles.platform.adapter.`in`.scheduler

import com.heracles.platform.application.ProcessDelivery
import com.heracles.platform.application.port.out.TaskQueue
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

/** Reads the task queue (long polling) and hands each delivery to [ProcessDelivery]. Wired only in the worker (WorkerConfig). */
class QueueConsumerJob(private val queue: TaskQueue, private val processDelivery: ProcessDelivery) {
    private val log = LoggerFactory.getLogger(QueueConsumerJob::class.java)

    @Scheduled(fixedDelayString = "\${heracles.queue.consumer.interval:1s}")
    fun run() {
        try {
            queue.receive(maxMessages = 10, waitSeconds = 5).forEach { delivery ->
                try {
                    processDelivery(delivery)
                } catch (e: Exception) {
                    // The message stays in the queue and comes back; log the kind only.
                    log.error("delivery of message {} could not be handled: {}", delivery.message.id, e.javaClass.simpleName)
                }
            }
        } catch (e: Exception) {
            log.error("reading the task queue failed: {}", e.javaClass.simpleName)
        }
    }
}
