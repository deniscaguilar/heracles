package com.heracles.platform.adapter.`in`.scheduler

import com.heracles.platform.application.PublishOutbox
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

/** Runs the outbox publisher on a schedule. Wired only in the worker (WorkerConfig); tests switch it off and call the use case. */
class OutboxPublisherJob(private val publishOutbox: PublishOutbox) {
    private val log = LoggerFactory.getLogger(OutboxPublisherJob::class.java)

    @Scheduled(fixedDelayString = "\${heracles.outbox.publisher.interval:1s}")
    fun run() {
        try {
            val result = publishOutbox()
            if (result.published + result.failed > 0) log.info("outbox: {} sent, {} failed", result.published, result.failed)
        } catch (e: Exception) {
            // The next run tries again; log the kind only.
            log.error("outbox publishing failed: {}", e.javaClass.simpleName)
        }
    }
}
