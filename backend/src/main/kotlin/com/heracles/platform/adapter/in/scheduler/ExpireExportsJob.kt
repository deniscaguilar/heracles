package com.heracles.platform.adapter.`in`.scheduler

import com.heracles.platform.application.ExpireExports
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

/** Removes export files after their 24 hours. Wired only in the worker (WorkerConfig). */
class ExpireExportsJob(private val expire: ExpireExports) {
    private val log = LoggerFactory.getLogger(ExpireExportsJob::class.java)

    @Scheduled(fixedDelayString = "\${heracles.exports.expire.interval:10m}")
    fun run() {
        try {
            expire()
        } catch (e: Exception) {
            log.error("clearing expired exports failed: {}", e.javaClass.simpleName)
        }
    }
}
