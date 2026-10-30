package com.heracles.platform.adapter.out

import com.heracles.platform.application.port.out.TransactionContext
import com.heracles.platform.domain.AccessContext
import com.heracles.support.PostgresInitializer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.util.UUID

/** E1-S6.1: outbox, task and processed_message. The first and last are system-only; a task is readable by its owner. */
@SpringBootTest
class QueueTablesIT(
    @Autowired private val transactionContext: TransactionContext,
    @Autowired private val jdbc: JdbcTemplate,
) {
    private val superuser = JdbcTemplate(
        DriverManagerDataSource(
            PostgresInitializer.postgres.jdbcUrl, PostgresInitializer.postgres.username, PostgresInitializer.postgres.password,
        ),
    )
    private val system = AccessContext.system()
    private val userA = UUID.randomUUID()
    private val userB = UUID.randomUUID()

    private fun <T> asSystem(block: () -> T) = transactionContext.run(system, block)

    private fun insertTask(owner: UUID?, type: String = "probe.task"): UUID = asSystem {
        jdbc.queryForObject("insert into task (user_id, type) values (?, ?) returning id", UUID::class.java, owner, type)!!
    }

    private fun insertOutbox(): UUID = asSystem {
        jdbc.queryForObject(
            "insert into outbox_message (type, aggregate_type, aggregate_id, payload) values ('exam.imported', 'exam', 'e-1', '{\"examId\":\"e-1\"}'::jsonb) returning id",
            UUID::class.java,
        )!!
    }

    private fun contexts(): Map<String, AccessContext?> = mapOf(
        "user" to AccessContext.forUser(userA), "admin" to AccessContext.admin(), "no context" to null,
    )

    private fun <T> read(context: AccessContext?, block: () -> T): T =
        if (context == null) block() else transactionContext.run(context, block)

    @Test
    fun `the outbox and processed messages are for the system scope only`() {
        val id = insertOutbox()
        asSystem { jdbc.update("insert into processed_message (consumer, message_id) values ('probe', 'm-1') on conflict do nothing") }

        assertThat(asSystem { jdbc.queryForList("select id from outbox_message where id = ?", UUID::class.java, id) }).containsExactly(id)
        contexts().forEach { (name, context) ->
            assertThat(read(context) { jdbc.queryForList("select id from outbox_message", UUID::class.java) }).describedAs("outbox as $name").isEmpty()
            assertThat(read(context) { jdbc.queryForList("select consumer from processed_message", String::class.java) }).describedAs("processed as $name").isEmpty()
            assertThatThrownBy { read(context) { jdbc.update("insert into processed_message (consumer, message_id) values ('x', 'y')") } }
                .describedAs("write processed as $name").isInstanceOf(DataAccessException::class.java).rootCause().hasMessageContaining("row-level security")
        }
    }

    @Test
    fun `a task is read by its owner only, and only the system writes it`() {
        val mine = insertTask(userA)
        val theirs = insertTask(userB)
        val ofSystem = insertTask(null)
        val ids = { jdbc.queryForList("select id from task", UUID::class.java) }

        assertThat(transactionContext.run(AccessContext.forUser(userA), ids)).containsExactly(mine)
        assertThat(transactionContext.run(AccessContext.forUser(userB), ids)).containsExactly(theirs)
        assertThat(transactionContext.run(AccessContext.admin(), ids)).isEmpty()
        assertThat(ids()).isEmpty()
        assertThat(asSystem(ids)).contains(mine, theirs, ofSystem)

        // A user cannot create, change or delete a task, not even their own.
        assertThatThrownBy {
            transactionContext.run(AccessContext.forUser(userA)) { jdbc.update("insert into task (user_id, type) values (?, 'forged')", userA) }
        }.rootCause().hasMessageContaining("row-level security")
        val changed = transactionContext.run(AccessContext.forUser(userA)) {
            jdbc.update("update task set status = 'DONE'") + jdbc.update("delete from task")
        }
        assertThat(changed).isZero()
        assertThat(superuser.queryForObject("select status from platform.task where id = ?", String::class.java, mine)).isEqualTo("PENDING")
    }

    @Test
    fun `defaults and constraints keep the tables sane`() {
        val id = insertTask(userA)
        val row = superuser.queryForMap("select status, attempts, last_error_code, result_ref from platform.task where id = ?", id)
        assertThat(row["status"]).isEqualTo("PENDING")
        assertThat(row["attempts"]).isEqualTo(0)

        assertThatThrownBy { asSystem { jdbc.update("update task set status = 'WHATEVER' where id = ?", id) } }
            .rootCause().hasMessageContaining("check")
        // The payload of a message is an object of identifiers, not a free blob.
        assertThatThrownBy {
            asSystem { jdbc.update("insert into outbox_message (type, aggregate_type, aggregate_id, payload) values ('t', 'a', 'i', '[1,2]'::jsonb)") }
        }.rootCause().hasMessageContaining("check")
        val outbox = insertOutbox()
        assertThat(superuser.queryForObject("select published_at is null and attempts = 0 from platform.outbox_message where id = ?", Boolean::class.java, outbox)).isTrue()
    }

    @Test
    fun `the same message of a consumer cannot be recorded twice`() {
        val consumer = "probe-${UUID.randomUUID()}"
        val first = asSystem { jdbc.update("insert into processed_message (consumer, message_id) values (?, 'm-9') on conflict do nothing", consumer) }
        val second = asSystem { jdbc.update("insert into processed_message (consumer, message_id) values (?, 'm-9') on conflict do nothing", consumer) }
        assertThat(first).isEqualTo(1)
        assertThat(second).isZero()
    }
}
