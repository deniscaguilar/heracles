package com.heracles.platform.adapter.out

import com.heracles.platform.api.TaskGateway
import com.heracles.platform.api.Transactions
import com.heracles.platform.application.port.out.TransactionContext
import com.heracles.support.PostgresInitializer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.util.UUID

/** E2-S1.2 and E2-S1.3: the task gateway (task row + outbox in one transaction) and the module schemas. */
@SpringBootTest
class PlatformApiIT(
    @Autowired private val transactions: Transactions,
    @Autowired private val gateway: TaskGateway,
) {
    private val admin = JdbcTemplate(
        DriverManagerDataSource(
            PostgresInitializer.postgres.jdbcUrl, PostgresInitializer.postgres.username, PostgresInitializer.postgres.password,
        ),
    )

    private fun newUser(): UUID {
        val id = UUID.randomUUID()
        admin.update("insert into platform.app_user (id, sub) values (?, ?)", id, "sub-$id")
        return id
    }

    @Test
    fun `a committed task leaves the status row and the message with the identifiers`() {
        val user = newUser()
        val taskId = transactions.asUser(user) { gateway.enqueue(user, "demo.job", mapOf("subjectId" to "abc")) }

        assertThat(admin.queryForObject("select status from platform.task where id = ?", String::class.java, taskId)).isEqualTo("PENDING")
        val payload = admin.queryForObject("select payload::text from platform.outbox_message where id = ?", String::class.java, taskId)!!
        assertThat(payload).contains("\"taskId\"").contains("\"userId\"").contains("\"subjectId\"")
    }

    @Test
    fun `a rolled back transaction leaves neither the task nor the message`() {
        val user = newUser()
        assertThatThrownBy {
            transactions.asUser(user) {
                gateway.enqueue(user, "demo.job")
                throw IllegalStateException("rollback")
            }
        }.isInstanceOf(IllegalStateException::class.java)

        assertThat(admin.queryForObject("select count(*) from platform.task where user_id = ?", Int::class.java, user)).isZero()
    }

    @Test
    fun `a user cannot create a task for someone else, but the system can`() {
        val a = newUser()
        val b = newUser()
        assertThatThrownBy { transactions.asUser(a) { gateway.enqueue(b, "demo.job") } }.rootCause().hasMessageContaining("only for the caller")
        val system = transactions.asSystem { gateway.enqueue(b, "demo.job") }
        assertThat(admin.queryForObject("select user_id from platform.task where id = ?", UUID::class.java, system)).isEqualTo(b)
    }

    @Test
    fun `the module schemas belong to the owner, with their own history and no DDL for the application`() {
        for (module in listOf("platform", "consent", "profile")) {
            assertThat(admin.queryForObject("select pg_get_userbyid(nspowner) from pg_namespace where nspname = ?", String::class.java, module))
                .describedAs("owner of $module").isEqualTo("heracles_owner")
            assertThat(admin.queryForObject("select count(*) from $module.flyway_schema_history where success", Int::class.java))
                .describedAs("history of $module").isGreaterThan(0)
            assertThat(admin.queryForObject("select has_schema_privilege('heracles_app', ?, 'USAGE')", Boolean::class.java, module)).isTrue()
            assertThat(admin.queryForObject("select has_schema_privilege('heracles_app', ?, 'CREATE')", Boolean::class.java, module))
                .describedAs("CREATE on $module").isFalse()
        }
    }
}
