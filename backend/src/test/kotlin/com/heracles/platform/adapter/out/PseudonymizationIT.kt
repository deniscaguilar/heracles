package com.heracles.platform.adapter.out

import com.heracles.platform.api.AuditLog
import com.heracles.platform.api.Outcome
import com.heracles.platform.api.Scope
import com.heracles.platform.api.Transactions
import com.heracles.support.PostgresInitializer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.ConnectionCallback
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.util.UUID

/** E2-S8.1 (plan section 11, point 1): the one narrow exception to "append-only", for audit_event and consent_event. */
@SpringBootTest
class PseudonymizationIT(
    @Autowired private val transactions: Transactions,
    @Autowired private val app: JdbcTemplate,
    @Autowired private val audit: AuditLog,
) {
    private val superuser = JdbcTemplate(
        DriverManagerDataSource(PostgresInitializer.postgres.jdbcUrl, PostgresInitializer.postgres.username, PostgresInitializer.postgres.password),
    )
    private val actor = UUID.randomUUID()

    private fun newUser(): UUID = UUID.randomUUID().also { superuser.update("insert into platform.app_user (id, sub) values (?, ?)", it, "sub-$it") }

    private fun auditLine(user: UUID, action: String = "x.done") = transactions.asUser(user) {
        audit.record(user, Scope.USER, action, "user", user.toString(), Outcome.SUCCESS, listOf("field"))
    }

    private fun asOwner(sql: String, vararg args: Any) = superuser.execute(
        ConnectionCallback { c ->
            c.createStatement().use { it.execute("set role heracles_owner") }
            try {
                c.prepareStatement(sql).use { ps -> args.forEachIndexed { i, a -> ps.setObject(i + 1, a) }; ps.execute() }
            } finally {
                c.createStatement().use { it.execute("reset role") }
            }
        },
    )

    private fun pseudonymize(function: String, old: UUID, new: UUID): Int? =
        transactions.asSystem { app.queryForObject("select $function(?, ?)", Int::class.javaObjectType, old, new) }

    @Test
    fun `the audit swap changes only the identifiers, touches only that person, and a second run is a no-op`() {
        val a = newUser()
        val b = newUser()
        auditLine(a, "one"); auditLine(a, "two"); auditLine(b, "other")
        val others = superuser.queryForList("select to_jsonb(e)::text from platform.audit_event e where actor_user_id = ?", String::class.java, b)
        val before = superuser.queryForList("select id, action, result, fields::text, occurred_at from platform.audit_event where actor_user_id = ? order by action", a)
        val replacement = UUID.randomUUID()

        assertThat(pseudonymize("platform.pseudonymize_user", a, replacement)).isEqualTo(4) // 2 as actor + 2 as entity
        assertThat(superuser.queryForObject("select count(*) from platform.audit_event where actor_user_id = ? or entity_id = ?", Int::class.java, a, a.toString())).isZero()
        val after = superuser.queryForList("select id, action, result, fields::text, occurred_at from platform.audit_event where actor_user_id = ? order by action", replacement)
        assertThat(after).isEqualTo(before) // nothing but the identifier moved
        assertThat(superuser.queryForList("select to_jsonb(e)::text from platform.audit_event e where actor_user_id = ?", String::class.java, b)).isEqualTo(others)

        assertThat(pseudonymize("platform.pseudonymize_user", a, UUID.randomUUID())).isZero()
    }

    @Test
    fun `everything else about audit_event is still refused, for the application, the owner and the superuser`() {
        val user = newUser()
        auditLine(user)
        val update = "update platform.audit_event set actor_user_id = ? where actor_user_id = ?"

        assertThatThrownBy { transactions.asSystem { app.update(update, UUID.randomUUID(), user) } }.rootCause().hasMessageContaining("permission denied")
        assertThatThrownBy { superuser.update(update, UUID.randomUUID(), user) }.rootCause().hasMessageContaining("append-only")
        assertThatThrownBy { superuser.update("delete from platform.audit_event where actor_user_id = ?", user) }.rootCause().hasMessageContaining("append-only")
        assertThatThrownBy { superuser.execute("truncate platform.audit_event") }.rootCause().hasMessageContaining("append-only")
        // the owner may swap the identifier but no other column, and may not delete
        assertThatThrownBy { asOwner("update platform.audit_event set action = 'forged' where actor_user_id = ?", user) }.rootCause().hasMessageContaining("append-only")
        assertThatThrownBy { asOwner("update platform.audit_event set result = 'DENIED', actor_user_id = ? where actor_user_id = ?", UUID.randomUUID(), user) }
            .rootCause().hasMessageContaining("append-only")
        // (it has no DELETE policy, so a delete finds no row: nothing is removed)
        asOwner("delete from platform.audit_event where actor_user_id = ?", user)
        assertThat(superuser.queryForObject("select count(*) from platform.audit_event where actor_user_id = ? and action = 'x.done'", Int::class.java, user)).isEqualTo(1)
    }

    @Test
    fun `only the system scope can call the functions`() {
        val user = newUser()
        listOf("platform.pseudonymize_user", "consent.pseudonymize_user").forEach { function ->
            assertThatThrownBy { transactions.asUser(user) { app.queryForObject("select $function(?, ?)", Int::class.java, user, UUID.randomUUID()) } }
                .rootCause().hasMessageContaining("only the system scope")
            assertThatThrownBy { transactions.asAdmin { app.queryForObject("select $function(?, ?)", Int::class.java, user, UUID.randomUUID()) } }
                .rootCause().hasMessageContaining("only the system scope")
        }
    }

    @Test
    fun `the consent swap changes only user_id and keeps the version it proves`() {
        val user = newUser()
        val other = newUser()
        val version = transactions.asAdmin {
            val draft = app.queryForObject("select consent.create_draft('AI_EXAMS_CONSENT', 'T', 'x', ?)", UUID::class.java, actor)!!
            app.queryForObject("select consent.publish_version(?, ?)", Int::class.javaObjectType, draft, actor)
            draft
        }
        listOf(user, other).forEach { u ->
            transactions.asUser(u) {
                app.update("insert into consent.consent_event (user_id, type, action, policy_version_id, client) values (?, 'AI_EXAMS_CONSENT', 'GRANT', ?, 'WEB')", u, version)
            }
        }
        val before = superuser.queryForMap("select id, type, action, policy_version_id, client, occurred_at from consent.consent_event where user_id = ?", user)
        val replacement = UUID.randomUUID()

        assertThat(pseudonymize("consent.pseudonymize_user", user, replacement)).isEqualTo(1)
        assertThat(superuser.queryForMap("select id, type, action, policy_version_id, client, occurred_at from consent.consent_event where user_id = ?", replacement)).isEqualTo(before)
        assertThat(superuser.queryForObject("select count(*) from consent.consent_event where user_id = ?", Int::class.java, user)).isZero()
        assertThat(superuser.queryForObject("select count(*) from consent.consent_event where user_id = ?", Int::class.java, other)).isEqualTo(1)
        assertThat(pseudonymize("consent.pseudonymize_user", user, UUID.randomUUID())).isZero()

        // still refused: any other column, a delete, for the owner and the superuser
        assertThatThrownBy { asOwner("update consent.consent_event set action = 'REVOKE' where user_id = ?", replacement) }.rootCause().hasMessageContaining("append-only")
        asOwner("delete from consent.consent_event where user_id = ?", replacement) // no DELETE policy: no row to remove
        assertThat(superuser.queryForObject("select count(*) from consent.consent_event where user_id = ?", Int::class.java, replacement)).isEqualTo(1)
        assertThatThrownBy { superuser.update("update consent.consent_event set user_id = ? where user_id = ?", UUID.randomUUID(), replacement) }
            .rootCause().hasMessageContaining("append-only")
    }

    @Test
    fun `an erased user is a row with no sub, and a row cannot be both active and without a sub`() {
        val user = newUser()
        superuser.update("update platform.app_user set sub = null, deleted_at = now() where id = ?", user)
        assertThatThrownBy { superuser.update("update platform.app_user set sub = null where id = ?", newUser()) }.isInstanceOf(Exception::class.java)
        assertThatThrownBy { superuser.update("update platform.app_user set deleted_at = now() where id = ?", newUser()) }.isInstanceOf(Exception::class.java)
        // the freed sub can open a new account
        superuser.update("insert into platform.app_user (sub) values (?)", "sub-$user")
    }
}
