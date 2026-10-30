package com.heracles.platform.adapter.out

import com.heracles.platform.application.port.out.TransactionContext
import com.heracles.platform.domain.AccessContext
import com.heracles.support.PostgresInitializer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.sql.SQLException
import java.util.UUID

/** E1-S4.1 (CA8): the audit trail is append-only, written through a database function, readable only by admin. */
@SpringBootTest
class AuditEventIT(
    @Autowired private val transactionContext: TransactionContext,
    @Autowired private val jdbc: JdbcTemplate,
) {
    private fun jdbcAs(user: String, password: String) =
        JdbcTemplate(DriverManagerDataSource(PostgresInitializer.postgres.jdbcUrl, user, password))

    private val superuser = jdbcAs(PostgresInitializer.postgres.username, PostgresInitializer.postgres.password)
    private val owner = jdbcAs(PostgresInitializer.OWNER_ROLE, PostgresInitializer.OWNER_PASSWORD)

    private fun record(
        action: String = "exam.view",
        result: String = "SUCCESS",
        fields: String = "{name,unit}",
        actor: UUID? = UUID.randomUUID(),
    ): UUID = jdbc.queryForObject(
        "select platform.record_audit_event(?, 'user', ?, 'exam', 'e-1', ?, ?::text[], 'trace-1')",
        UUID::class.java, actor, action, result, fields,
    )!!

    private fun sqlState(block: () -> Unit): String? =
        runCatching(block).exceptionOrNull()?.let { generateSequence(it) { t -> t.cause }.filterIsInstance<SQLException>().firstOrNull()?.sqlState }

    @Test
    fun `the application records an event through the function and only admin reads it`() {
        val action = "probe.${UUID.randomUUID()}"
        val id = transactionContext.run(AccessContext.system()) { record(action = action) }

        val byAdmin = transactionContext.run(AccessContext.admin()) {
            jdbc.queryForList("select action from audit_event where id = ?", String::class.java, id)
        }
        assertThat(byAdmin).containsExactly(action)
        assertThat(transactionContext.run(AccessContext.forUser(UUID.randomUUID())) {
            jdbc.queryForList("select id from audit_event", UUID::class.java)
        }).isEmpty()
        assertThat(jdbc.queryForList("select id from audit_event", UUID::class.java)).isEmpty()
    }

    @Test
    fun `the application cannot insert directly, only through the function`() {
        assertThat(sqlState {
            transactionContext.run(AccessContext.system()) {
                jdbc.update("insert into audit_event (action, entity_type, result, fields) values ('x', 'y', 'SUCCESS', '{}')")
            }
        }).isEqualTo("42501")
    }

    @Test
    fun `update and delete are refused by the database for every role, even the owner and the superuser`() {
        val id = transactionContext.run(AccessContext.system()) { record() }
        val statements = listOf(
            "update platform.audit_event set result = 'DENIED' where id = '$id'",
            "delete from platform.audit_event where id = '$id'",
        )
        // The superuser skips RLS, so the trigger is the barrier.
        (statements + "truncate platform.audit_event").forEach { sql ->
            assertThatThrownBy { superuser.execute(sql) }.describedAs(sql).hasStackTraceContaining("append-only")
        }
        // The owner is under forced RLS and sees no row: nothing to change; truncate hits the trigger.
        statements.forEach { sql -> assertThat(owner.update(sql)).describedAs(sql).isZero() }
        assertThatThrownBy { owner.execute("truncate platform.audit_event") }.hasStackTraceContaining("append-only")
        // And the application role has no privilege at all.
        listOf("update audit_event set result = 'DENIED'", "delete from audit_event").forEach { sql ->
            assertThat(sqlState { transactionContext.run(AccessContext.admin()) { jdbc.update(sql) } }).describedAs(sql).isEqualTo("42501")
        }
        assertThat(superuser.queryForObject("select count(*) from platform.audit_event where id = ?", Int::class.java, id)).isEqualTo(1)
    }

    @Test
    fun `the database refuses field entries that are not field names`() {
        assertThat(sqlState { transactionContext.run(AccessContext.system()) { record(fields = "{joao@example.com}") } })
            .isEqualTo("23514")
        assertThat(sqlState { transactionContext.run(AccessContext.system()) { record(fields = "{\"valor 7,2 mg/dL\"}") } })
            .isEqualTo("23514")
        assertThat(transactionContext.run(AccessContext.system()) { record(fields = "{}") }).isNotNull()
    }

    @Test
    fun `an unknown result is refused and the time is set by the database`() {
        assertThat(sqlState { transactionContext.run(AccessContext.system()) { record(result = "MAYBE") } }).isEqualTo("23514")
        val id = transactionContext.run(AccessContext.system()) { record() }
        assertThat(superuser.queryForObject("select occurred_at is not null from platform.audit_event where id = ?", Boolean::class.java, id)).isTrue()
    }
}
