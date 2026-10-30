package com.heracles.platform.adapter.out

import com.heracles.platform.application.port.out.TransactionContext
import com.heracles.platform.domain.AccessContext
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID

/**
 * E1-S2.2: the user and the scope reach the database per transaction and die with it. The pool has a single
 * connection, so consecutive calls are guaranteed to reuse it (what would leak context if it were session-wide).
 */
@SpringBootTest(properties = ["spring.datasource.hikari.maximum-pool-size=1"])
class TransactionContextIT(
    @Autowired private val transactionContext: TransactionContext,
    @Autowired private val jdbc: JdbcTemplate,
) {
    private fun currentUser(): UUID? = jdbc.queryForObject("select platform.current_app_user_id()", UUID::class.java)
    private fun currentScope(): String? = jdbc.queryForObject("select platform.current_app_scope()", String::class.java)
    private fun backendPid(): Int = jdbc.queryForObject("select pg_backend_pid()", Int::class.java)!!

    @Test
    fun `without context there is no user and the scope is user`() {
        assertThat(currentUser()).isNull()
        assertThat(currentScope()).isEqualTo("user")
    }

    @Test
    fun `user context is visible inside the transaction`() {
        val id = UUID.randomUUID()
        val seen = transactionContext.run(AccessContext.forUser(id)) { currentUser() to currentScope() }
        assertThat(seen).isEqualTo(id to "user")
    }

    @Test
    fun `admin and system contexts set the scope and no user`() {
        assertThat(transactionContext.run(AccessContext.admin()) { currentUser() to currentScope() })
            .isEqualTo(null to "admin")
        assertThat(transactionContext.run(AccessContext.system()) { currentUser() to currentScope() })
            .isEqualTo(null to "system")
    }

    @Test
    fun `two requests in a row on the same connection do not leak context`() {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        var pidInside = 0
        transactionContext.run(AccessContext.forUser(first)) { pidInside = backendPid() }

        // Same connection, no context: nothing left over from the previous transaction.
        assertThat(backendPid()).isEqualTo(pidInside)
        assertThat(currentUser()).isNull()
        assertThat(currentScope()).isEqualTo("user")

        // The next transaction sees only its own user.
        assertThat(transactionContext.run(AccessContext.forUser(second)) { currentUser() }).isEqualTo(second)
        assertThat(currentUser()).isNull()
    }

    @Test
    fun `context is cleared when the transaction fails`() {
        assertThatThrownBy {
            transactionContext.run(AccessContext.forUser(UUID.randomUUID())) { error("boom") }
        }.hasMessage("boom")
        assertThat(currentUser()).isNull()
    }

    @Test
    fun `without context a policy based on the user finds zero rows`() {
        val rowsVisible = "select count(*) from generate_series(1, 3) as g where platform.current_app_user_id() is not null"
        assertThat(jdbc.queryForObject(rowsVisible, Int::class.java)).isZero()
        assertThat(transactionContext.run(AccessContext.forUser(UUID.randomUUID())) {
            jdbc.queryForObject(rowsVisible, Int::class.java)
        }).isEqualTo(3)
    }

    @Test
    fun `nesting contexts is refused instead of silently replacing the outer one`() {
        val outer = UUID.randomUUID()
        assertThatThrownBy {
            transactionContext.run(AccessContext.forUser(outer)) {
                transactionContext.run(AccessContext.forUser(UUID.randomUUID())) { }
            }
        }.isInstanceOf(IllegalStateException::class.java)
    }
}
