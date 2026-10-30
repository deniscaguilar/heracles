package com.heracles.platform.adapter.out

import com.heracles.platform.application.port.out.Outbox
import com.heracles.platform.domain.QueueMessage
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.util.UUID

/** Calls `platform.append_outbox_message`, the only way into the outbox from the user and admin scopes. */
@Component
class JdbcOutbox(private val jdbc: JdbcTemplate) : Outbox {
    private val json = ObjectMapper()

    override fun append(aggregateType: String, aggregateId: String, message: QueueMessage) {
        val id = try {
            UUID.fromString(message.id)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("outbox message id must be a UUID")
        }
        jdbc.queryForList(
            "select platform.append_outbox_message(?, ?, ?, ?, ?::jsonb)::text",
            id, message.type, aggregateType, aggregateId, json.writeValueAsString(message.ids),
        )
    }
}
