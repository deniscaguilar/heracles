package com.heracles.platform.adapter.out

import com.heracles.platform.application.port.out.ProcessedMessages
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/** Calls `platform.mark_processed` (V7), the way in for every scope. */
@Component
class JdbcProcessedMessages(private val jdbc: JdbcTemplate) : ProcessedMessages {
    override fun markProcessed(consumer: String, messageId: String): Boolean =
        jdbc.queryForObject("select platform.mark_processed(?, ?)", Boolean::class.java, consumer, messageId) == true
}
