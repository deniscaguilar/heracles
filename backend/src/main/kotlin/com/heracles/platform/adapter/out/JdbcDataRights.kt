package com.heracles.platform.adapter.out

import com.heracles.platform.application.port.out.DataExport
import com.heracles.platform.application.port.out.DataExports
import com.heracles.platform.application.port.out.PersonalData
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class JdbcDataExports(private val jdbc: JdbcTemplate) : DataExports {
    override fun save(export: DataExport) {
        jdbc.update(
            "insert into data_export (task_id, user_id, storage_key, created_at, expires_at) values (?, ?, ?, ?, ?) on conflict (task_id) do nothing",
            export.taskId, export.userId, export.storageKey, java.sql.Timestamp.from(export.createdAt), java.sql.Timestamp.from(export.expiresAt),
        )
    }

    override fun find(taskId: UUID): DataExport? =
        jdbc.query("select task_id, user_id, storage_key, created_at, expires_at from data_export where task_id = ?", ::rowOf, taskId).firstOrNull()

    override fun expired(now: Instant): List<DataExport> =
        jdbc.query("select task_id, user_id, storage_key, created_at, expires_at from data_export where expires_at <= ?", ::rowOf, java.sql.Timestamp.from(now))

    override fun ofUser(userId: UUID): List<DataExport> =
        jdbc.query("select task_id, user_id, storage_key, created_at, expires_at from data_export where user_id = ?", ::rowOf, userId)

    override fun delete(taskId: UUID) {
        jdbc.update("delete from data_export where task_id = ?", taskId)
    }

    private fun rowOf(rs: java.sql.ResultSet, @Suppress("UNUSED_PARAMETER") row: Int) = DataExport(
        rs.getObject("task_id", UUID::class.java), rs.getObject("user_id", UUID::class.java), rs.getString("storage_key"),
        rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("expires_at").toInstant(),
    )
}

@Component
class JdbcPersonalData(private val jdbc: JdbcTemplate) : PersonalData {
    override fun account(userId: UUID): Map<String, Any?> =
        jdbc.query("select id, role, status, created_at from app_user where id = ?", { rs, _ ->
            mapOf<String, Any?>(
                "id" to rs.getString("id"), "role" to rs.getString("role"), "status" to rs.getString("status"),
                "createdAt" to rs.getTimestamp("created_at").toInstant().toString(),
            )
        }, userId).firstOrNull() ?: emptyMap()

    override fun auditTrail(): List<Map<String, Any?>> =
        jdbc.query("select occurred_at, action, entity_type, result, fields from platform.my_audit_events()") { rs, _ ->
            mapOf<String, Any?>(
                "occurredAt" to rs.getTimestamp("occurred_at").toInstant().toString(), "action" to rs.getString("action"),
                "entityType" to rs.getString("entity_type"), "result" to rs.getString("result"),
                "fields" to (rs.getArray("fields").array as Array<*>).map { it.toString() },
            )
        }
}
