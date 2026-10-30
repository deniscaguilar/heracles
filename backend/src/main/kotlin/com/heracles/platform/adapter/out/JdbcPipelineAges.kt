package com.heracles.platform.adapter.out

import com.heracles.platform.application.port.out.PipelineAges
import com.heracles.platform.application.port.out.TransactionContext
import com.heracles.platform.domain.AccessContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.time.Duration

/** System scope (the outbox is system-only); ages come from the database clock, so they do not depend on this host's clock. */
@Component
class JdbcPipelineAges(private val transactions: TransactionContext, private val jdbc: JdbcTemplate) : PipelineAges {
    override fun oldestUnpublishedOutboxMessage(): Duration =
        age("select extract(epoch from now() - min(created_at)) from outbox_message where published_at is null")

    override fun oldestUnfinishedTask(): Duration =
        age("select extract(epoch from now() - min(created_at)) from task where status in ('PENDING', 'PROCESSING')")

    private fun age(sql: String): Duration {
        val seconds = transactions.run(AccessContext.system()) { jdbc.queryForObject(sql, Double::class.javaObjectType) }
        return if (seconds == null || seconds < 0) Duration.ZERO else Duration.ofMillis((seconds * 1000).toLong())
    }
}
