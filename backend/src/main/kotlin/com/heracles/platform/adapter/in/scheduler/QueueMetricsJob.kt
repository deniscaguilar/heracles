package com.heracles.platform.adapter.`in`.scheduler

import com.heracles.platform.application.CollectQueueMetrics
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

/** Refreshes the queue metrics on a schedule. Wired only in the worker (WorkerConfig). */
class QueueMetricsJob(private val collect: CollectQueueMetrics) {
    private val log = LoggerFactory.getLogger(QueueMetricsJob::class.java)

    @Scheduled(fixedDelayString = "\${heracles.queue.metrics.interval:15s}")
    fun run() {
        try {
            collect()
        } catch (e: Exception) {
            log.error("collecting the queue metrics failed: {}", e.javaClass.simpleName)
        }
    }
}
