package com.heracles.platform.adapter.out

import com.heracles.platform.application.port.out.Outbox
import com.heracles.platform.application.port.out.TransactionContext
import com.heracles.platform.domain.AccessContext
import com.heracles.platform.domain.QueueMessage
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

/** E1-S6.3 (CA12): the message goes to the outbox in the same transaction as the state change, from any scope. */
@SpringBootTest
class OutboxIT(
    @Autowired private val outbox: Outbox,
    @Autowired private val transactionContext: TransactionContext,
    @Autowired private val jdbc: JdbcTemplate,
) {
    private val superuser = JdbcTemplate(
        DriverManagerDataSource(
            PostgresInitializer.postgres.jdbcUrl, PostgresInitializer.postgres.username, PostgresInitializer.postgres.password,
        ),
    )
    private val user = UUID.randomUUID()

    private fun message() = QueueMessage(UUID.randomUUID().toString(), "exam.imported", mapOf("examId" to "e-1", "userId" to user.toString()))

    private fun notesWith(body: String) = superuser.queryForObject("select count(*) from platform.test_user_note where body = ?", Int::class.java, body)
    private fun outboxRows(id: String) = superuser.queryForObject("select count(*) from platform.outbox_message where id = ?::uuid", Int::class.java, id)

    @Test
    fun `a committed transaction leaves the state change and the message`() {
        val message = message()
        val body = "committed-${UUID.randomUUID()}"
        transactionContext.run(AccessContext.forUser(user)) {
            jdbc.update("insert into test_user_note (user_id, body) values (?, ?)", user, body)
            outbox.append("exam", "e-1", message)
        }
        assertThat(notesWith(body)).isEqualTo(1)
        assertThat(outboxRows(message.id)).isEqualTo(1)
        val row = superuser.queryForMap("select type, aggregate_type, aggregate_id, payload::text as payload, published_at from platform.outbox_message where id = ?::uuid", message.id)
        assertThat(row["type"]).isEqualTo("exam.imported")
        assertThat(row["aggregate_type"]).isEqualTo("exam")
        assertThat(row["aggregate_id"]).isEqualTo("e-1")
        assertThat(row["published_at"]).isNull()
        assertThat(row["payload"].toString()).contains("\"examId\": \"e-1\"").contains(user.toString())
    }

    @Test
    fun `a transaction that is rolled back leaves neither the state change nor the message`() {
        val message = message()
        val body = "rolled-back-${UUID.randomUUID()}"
        assertThatThrownBy {
            transactionContext.run(AccessContext.forUser(user)) {
                jdbc.update("insert into test_user_note (user_id, body) values (?, ?)", user, body)
                outbox.append("exam", "e-1", message)
                error("the business rule failed after the message was appended")
            }
        }.hasMessageContaining("business rule")
        assertThat(notesWith(body)).isZero()
        assertThat(outboxRows(message.id)).isZero()
    }

    @Test
    fun `any scope can append, but only the system reads the outbox`() {
        listOf(AccessContext.forUser(user), AccessContext.admin(), AccessContext.system()).forEach { context ->
            val message = message()
            transactionContext.run(context) { outbox.append("exam", "e-1", message) }
            assertThat(outboxRows(message.id)).describedAs("appended as ${context.scope}").isEqualTo(1)
            assertThat(transactionContext.run(context) { jdbc.queryForList("select id from outbox_message where id = ?::uuid", UUID::class.java, message.id) })
                .describedAs("read back as ${context.scope}").hasSize(if (context == AccessContext.system()) 1 else 0)
        }
    }

    @Test
    fun `a user cannot insert straight into the table`() {
        assertThat(sqlState {
            transactionContext.run(AccessContext.forUser(user)) {
                jdbc.update("insert into outbox_message (type, aggregate_type, aggregate_id, payload) values ('t', 'a', 'i', '{}'::jsonb)")
            }
        }).isEqualTo("42501")
    }

    @Test
    fun `the database itself refuses a payload that is not made of identifiers`() {
        listOf("""{"note":"glicemia 7,2 mg/dL"}""", """{"email":"joao@example.com"}""", """{"n":7}""", """{"nested":{"a":"b"}}""").forEach { payload ->
            assertThat(sqlState {
                superuser.update("insert into platform.outbox_message (type, aggregate_type, aggregate_id, payload) values ('t', 'a', 'i', ?::jsonb)", payload)
            }).describedAs(payload).isEqualTo("23514")
        }
    }

    @Test
    fun `the message id must be a UUID, since it is the key of the outbox row`() {
        val notUuid = QueueMessage("not-a-uuid", "exam.imported", emptyMap())
        assertThatThrownBy { transactionContext.run(AccessContext.system()) { outbox.append("exam", "e-1", notUuid) } }
            .isInstanceOf(IllegalArgumentException::class.java).message().doesNotContain("not-a-uuid")
    }

    private fun sqlState(block: () -> Unit): String? =
        runCatching(block).exceptionOrNull()?.let { generateSequence(it) { t -> t.cause }.filterIsInstance<SQLException>().firstOrNull()?.sqlState }
}
