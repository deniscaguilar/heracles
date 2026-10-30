package com.heracles.platform.adapter.out

import com.heracles.platform.application.RecordAudit
import com.heracles.platform.application.port.out.TransactionContext
import com.heracles.platform.domain.AccessContext
import com.heracles.platform.domain.AccessScope
import com.heracles.platform.domain.AuditEvent
import com.heracles.platform.domain.AuditResult
import com.heracles.platform.domain.InvalidAuditEventException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID

/** E1-S4.2: RecordAudit writes through the database function; a value in place of a field name never reaches it. */
@SpringBootTest
class AuditTrailIT(
    @Autowired private val recordAudit: RecordAudit,
    @Autowired private val transactionContext: TransactionContext,
    @Autowired private val jdbc: JdbcTemplate,
) {
    private fun event(action: String, fields: List<String>) = AuditEvent(
        actorUserId = UUID.randomUUID(), actorScope = AccessScope.USER, action = action, entityType = "exam",
        entityId = "e-1", result = AuditResult.DENIED, fields = fields, traceId = "trace-9",
    )

    @Test
    fun `the event is stored with names and identifiers only`() {
        val action = "probe.${UUID.randomUUID().toString().replace('-', '_')}"
        transactionContext.run(AccessContext.system()) { recordAudit(event(action, listOf("name", "unit"))) }

        val row = transactionContext.run(AccessContext.admin()) {
            jdbc.queryForMap("select result, entity_type, entity_id, actor_scope, trace_id, fields::text as fields from audit_event where action = ?", action)
        }
        assertThat(row).containsEntry("result", "DENIED").containsEntry("entity_type", "exam")
            .containsEntry("actor_scope", "user").containsEntry("trace_id", "trace-9").containsEntry("fields", "{name,unit}")
    }

    @Test
    fun `a value in place of a field name is refused before reaching the database`() {
        val action = "probe.value.${UUID.randomUUID().toString().replace('-', '_')}"
        assertThatThrownBy { transactionContext.run(AccessContext.system()) { recordAudit(event(action, listOf("joao@example.com"))) } }
            .isInstanceOf(InvalidAuditEventException::class.java)
        assertThat(transactionContext.run(AccessContext.admin()) {
            jdbc.queryForList("select id from audit_event where action = ?", UUID::class.java, action)
        }).isEmpty()
    }
}
