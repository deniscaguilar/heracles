package com.heracles.platform.adapter.out

import com.heracles.platform.application.port.out.AccountErasure
import com.heracles.platform.application.port.out.AccountErasures
import com.heracles.platform.application.port.out.AccountRecords
import com.heracles.platform.application.port.out.ErasureStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Component
class JdbcAccountErasures(private val jdbc: JdbcTemplate) : AccountErasures {
    private val columns = "id, user_id, requested_at, due_at, status"

    override fun open(userId: UUID): AccountErasure? =
        jdbc.query("select $columns from account_erasure where user_id = ? and status in ('PENDING', 'RUNNING')", ::rowOf, userId).firstOrNull()

    override fun create(userId: UUID, requestedAt: Instant, dueAt: Instant): AccountErasure {
        val id = UUID.randomUUID()
        jdbc.update(
            "insert into account_erasure (id, user_id, requested_at, due_at) values (?, ?, ?, ?)",
            id, userId, Timestamp.from(requestedAt), Timestamp.from(dueAt),
        )
        return AccountErasure(id, userId, requestedAt, dueAt, ErasureStatus.PENDING)
    }

    override fun cancel(userId: UUID): Boolean =
        jdbc.update("update account_erasure set status = 'CANCELED' where user_id = ? and status = 'PENDING'", userId) > 0

    override fun due(now: Instant): List<AccountErasure> =
        jdbc.query("select $columns from account_erasure where status = 'PENDING' and due_at <= ? order by due_at", ::rowOf, Timestamp.from(now))

    override fun markRunning(id: UUID) {
        jdbc.update("update account_erasure set status = 'RUNNING' where id = ?", id)
    }

    override fun markDone(userId: UUID) {
        jdbc.update("update account_erasure set status = 'DONE' where user_id = ? and status in ('PENDING', 'RUNNING')", userId)
    }

    private fun rowOf(rs: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int) = AccountErasure(
        rs.getObject("id", UUID::class.java), rs.getObject("user_id", UUID::class.java),
        rs.getTimestamp("requested_at").toInstant(), rs.getTimestamp("due_at").toInstant(), ErasureStatus.valueOf(rs.getString("status")),
    )
}

@Component
class JdbcAccountRecords(private val jdbc: JdbcTemplate) : AccountRecords {
    override fun subjectOf(userId: UUID): String? =
        jdbc.query("select sub from app_user where id = ?", { rs, _ -> rs.getString("sub") }, userId).firstOrNull()

    override fun empty(userId: UUID) {
        jdbc.update("update app_user set sub = null, deleted_at = coalesce(deleted_at, now()), role = 'USER' where id = ?", userId)
    }

    override fun unlinkAuditTrail(userId: UUID) {
        jdbc.queryForObject("select platform.pseudonymize_user(?, ?)", Int::class.javaObjectType, userId, UUID.randomUUID())
    }
}
