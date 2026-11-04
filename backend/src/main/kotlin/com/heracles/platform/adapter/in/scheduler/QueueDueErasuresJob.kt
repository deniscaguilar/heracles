package com.heracles.platform.adapter.`in`.scheduler

import com.heracles.platform.application.QueueDueErasures
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

/** Turns the erasure requests whose 7 days passed into tasks. Wired only in the worker (WorkerConfig). */
class QueueDueErasuresJob(private val queueDue: QueueDueErasures) {
    private val log = LoggerFactory.getLogger(QueueDueErasuresJob::class.java)

    @Scheduled(fixedDelayString = "\${heracles.erasure.scheduler.interval:1m}")
    fun run() {
        try {
            queueDue()
        } catch (e: Exception) {
            log.error("queueing due erasures failed: {}", e.javaClass.simpleName)
        }
    }
}
