package com.heracles.platform.adapter.out

import com.heracles.platform.application.port.out.OutboxStore
import com.heracles.platform.domain.OutboxEntry
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Component
class JdbcOutboxStore(private val jdbc: JdbcTemplate) : OutboxStore {
    private val json = ObjectMapper()

    private val mapper = RowMapper { rs, _ ->
        val payload = json.readTree(rs.getString("payload"))
        OutboxEntry(
            id = rs.getObject("id", UUID::class.java),
            type = rs.getString("type"),
            payload = payload.propertyNames().associateWith { payload.get(it).asString() },
            attempts = rs.getInt("attempts"),
        )
    }

    override fun claimDue(limit: Int, now: Instant): List<OutboxEntry> = jdbc.query(
        """
        select id, type, payload::text as payload, attempts from outbox_message
         where published_at is null and next_attempt_at <= ?
         order by next_attempt_at, created_at
         limit ? for update skip locked
        """.trimIndent(),
        mapper, Timestamp.from(now), limit,
    )

    override fun markPublished(id: UUID, at: Instant) {
        jdbc.update("update outbox_message set published_at = ? where id = ?", Timestamp.from(at), id)
    }

    override fun markFailed(id: UUID, attempts: Int, nextAttemptAt: Instant) {
        jdbc.update("update outbox_message set attempts = ?, next_attempt_at = ? where id = ?", attempts, Timestamp.from(nextAttemptAt), id)
    }
}
