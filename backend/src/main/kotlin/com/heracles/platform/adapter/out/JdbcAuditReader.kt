package com.heracles.platform.adapter.out

import com.heracles.platform.application.port.out.AuditReader
import com.heracles.platform.domain.AccessScope
import com.heracles.platform.domain.AuditCursor
import com.heracles.platform.domain.AuditRecord
import com.heracles.platform.domain.AuditResult
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Component
import java.sql.Timestamp
import java.util.UUID

@Component
class JdbcAuditReader(private val jdbc: JdbcTemplate) : AuditReader {
    private val mapper = RowMapper { rs, _ ->
        AuditRecord(
            id = rs.getObject("id", UUID::class.java),
            occurredAt = rs.getTimestamp("occurred_at").toInstant(),
            actorUserId = rs.getObject("actor_user_id", UUID::class.java),
            actorScope = AccessScope.entries.first { it.databaseValue == rs.getString("actor_scope") },
            action = rs.getString("action"),
            entityType = rs.getString("entity_type"),
            entityId = rs.getString("entity_id"),
            result = AuditResult.valueOf(rs.getString("result")),
            fields = (rs.getArray("fields").array as Array<*>).map { it as String },
            traceId = rs.getString("trace_id"),
        )
    }

    override fun find(after: AuditCursor?, limit: Int): List<AuditRecord> {
        val columns = "id, occurred_at, actor_user_id, actor_scope, action, entity_type, entity_id, result, fields, trace_id"
        return if (after == null) {
            jdbc.query("select $columns from audit_event order by occurred_at desc, id desc limit ?", mapper, limit)
        } else {
            jdbc.query(
                "select $columns from audit_event where (occurred_at, id) < (?, ?) order by occurred_at desc, id desc limit ?",
                mapper, Timestamp.from(after.occurredAt), after.id, limit,
            )
        }
    }
}
