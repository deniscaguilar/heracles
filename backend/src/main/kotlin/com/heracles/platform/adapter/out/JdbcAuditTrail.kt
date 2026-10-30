package com.heracles.platform.adapter.out

import com.heracles.platform.application.port.out.AuditTrail
import com.heracles.platform.domain.AuditEvent
import org.springframework.jdbc.core.ConnectionCallback
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.util.UUID

/** The only way in is the database function `platform.record_audit_event` (V4). */
@Component
class JdbcAuditTrail(private val jdbc: JdbcTemplate) : AuditTrail {
    override fun append(event: AuditEvent): UUID =
        jdbc.execute(ConnectionCallback<UUID> { connection ->
            connection.prepareStatement("select platform.record_audit_event(?, ?, ?, ?, ?, ?, ?, ?)").use { statement ->
                statement.setObject(1, event.actorUserId)
                statement.setString(2, event.actorScope.databaseValue)
                statement.setString(3, event.action)
                statement.setString(4, event.entityType)
                statement.setString(5, event.entityId)
                statement.setString(6, event.result.name)
                statement.setArray(7, connection.createArrayOf("text", event.fields.toTypedArray()))
                statement.setString(8, event.traceId)
                statement.executeQuery().use { rs ->
                    rs.next()
                    rs.getObject(1, UUID::class.java)
                }
            }
        })!!
}
