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

/** E1-S2.3 (CA6): the database isolates users even when the query is wrong. */
@SpringBootTest
class RowLevelSecurityIT(
    @Autowired private val transactionContext: TransactionContext,
    @Autowired private val jdbc: JdbcTemplate,
) {
    private val userA = UUID.randomUUID()
    private val userB = UUID.randomUUID()

    private fun insertNote(user: UUID, body: String): UUID = transactionContext.run(AccessContext.forUser(user)) {
        jdbc.queryForObject(
            "insert into test_user_note (user_id, body) values (?, ?) returning id", UUID::class.java, user, body,
        )!!
    }

    private fun insertSample(user: UUID, value: String) = transactionContext.run(AccessContext.forUser(user)) {
        jdbc.update("insert into test_health_sample (user_id, value) values (?, ?)", user, value)
    }

    // Deliberately wrong query: no user filter at all.
    private fun allNoteBodies(context: AccessContext?): List<String> {
        val query = { jdbc.queryForList("select body from test_user_note", String::class.java).filterNotNull() }
        return if (context == null) query() else transactionContext.run(context, query)
    }

    private fun allSampleValues(context: AccessContext?): List<String> {
        val query = { jdbc.queryForList("select value from test_health_sample", String::class.java).filterNotNull() }
        return if (context == null) query() else transactionContext.run(context, query)
    }

    @Test
    fun `each user sees only their own rows even with a query that has no user filter`() {
        insertNote(userA, "a-1")
        insertNote(userA, "a-2")
        insertNote(userB, "b-1")

        assertThat(allNoteBodies(AccessContext.forUser(userA))).containsExactlyInAnyOrder("a-1", "a-2")
        assertThat(allNoteBodies(AccessContext.forUser(userB))).containsExactly("b-1")
    }

    @Test
    fun `without a user nothing comes back`() {
        insertNote(userA, "a-hidden")
        insertSample(userA, "sample-hidden")

        assertThat(allNoteBodies(null)).isEmpty()
        assertThat(allSampleValues(null)).isEmpty()
    }

    @Test
    fun `admin and system scopes cannot read health data or user data`() {
        insertNote(userA, "a-note")
        insertSample(userA, "a-sample")
        // The rows exist: the admin connection of the test container (superuser) sees them.
        assertThat(adminJdbc().queryForObject("select count(*) from platform.test_health_sample", Int::class.java))
            .isGreaterThan(0)

        listOf(AccessContext.admin(), AccessContext.system()).forEach { context ->
            assertThat(allSampleValues(context)).describedAs("health as ${context.scope}").isEmpty()
            assertThat(allNoteBodies(context)).describedAs("notes as ${context.scope}").isEmpty()
        }
    }

    @Test
    fun `a user cannot write a row for someone else`() {
        assertThatThrownBy { transactionContext.run(AccessContext.forUser(userA)) {
            jdbc.update("insert into test_user_note (user_id, body) values (?, 'forged')", userB)
        } }.isInstanceOf(DataAccessException::class.java).rootCause().hasMessageContaining("row-level security")

        assertThatThrownBy { insertNoteWithoutContext(userA) }
            .isInstanceOf(DataAccessException::class.java).rootCause().hasMessageContaining("row-level security")
    }

    private fun insertNoteWithoutContext(user: UUID) =
        jdbc.update("insert into test_user_note (user_id, body) values (?, 'no-context')", user)

    @Test
    fun `a user cannot change or delete rows of someone else`() {
        insertNote(userB, "b-protected")

        val changed = transactionContext.run(AccessContext.forUser(userA)) {
            jdbc.update("update test_user_note set body = 'tampered' where body = 'b-protected'") +
                jdbc.update("delete from test_user_note where body = 'b-protected'")
        }
        assertThat(changed).isZero()
        assertThat(allNoteBodies(AccessContext.forUser(userB))).contains("b-protected")
    }

    @Test
    fun `RLS is forced, so not even the table owner skips it`() {
        insertNote(userA, "a-owner-test")
        val ownerJdbc = JdbcTemplate(
            DriverManagerDataSource(
                PostgresInitializer.postgres.jdbcUrl, PostgresInitializer.OWNER_ROLE, PostgresInitializer.OWNER_PASSWORD,
            ),
        )
        assertThat(ownerJdbc.queryForObject("select current_user", String::class.java)).isEqualTo("heracles_owner")
        assertThat(ownerJdbc.queryForObject("select count(*) from platform.test_user_note", Int::class.java)).isZero()
    }

    private fun adminJdbc() = JdbcTemplate(
        DriverManagerDataSource(
            PostgresInitializer.postgres.jdbcUrl, PostgresInitializer.postgres.username, PostgresInitializer.postgres.password,
        ),
    )
}
