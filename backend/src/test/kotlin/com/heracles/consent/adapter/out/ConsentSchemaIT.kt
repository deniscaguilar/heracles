package com.heracles.consent.adapter.out

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

/**
 * E2-S2.1 to E2-S2.3 (CA2, CA5, CA6): immutable published versions, append-only consent events, isolation between users,
 * and aggregate counts that carry no identity.
 */
@SpringBootTest
class ConsentSchemaIT(
    @Autowired private val transactions: Transactions,
    @Autowired private val app: JdbcTemplate,
) {
    private val superuser = JdbcTemplate(
        DriverManagerDataSource(
            PostgresInitializer.postgres.jdbcUrl, PostgresInitializer.postgres.username, PostgresInitializer.postgres.password,
        ),
    )
    private val actor = UUID.randomUUID()

    private fun newUser(): UUID = UUID.randomUUID().also { superuser.update("insert into platform.app_user (id, sub) values (?, ?)", it, "sub-$it") }

    private fun draft(type: String, text: String = "text"): UUID =
        transactions.asAdmin { app.queryForObject("select consent.create_draft(?, ?, ?, ?)", UUID::class.java, type, "Title", text, actor)!! }

    private fun publish(id: UUID): Int? =
        transactions.asAdmin { app.queryForObject("select consent.publish_version(?, ?)", Int::class.javaObjectType, id, actor) }

    private fun publishedVersion(type: String): UUID = publish(draft(type)).let {
        superuser.queryForObject("select id from consent.policy_version where type = ? order by version desc limit 1", UUID::class.java, type)!!
    }

    private fun accept(user: UUID, type: String, version: UUID, action: String = "GRANT") = transactions.asUser(user) {
        app.update(
            "insert into consent.consent_event (user_id, type, action, policy_version_id, client) values (?, ?, ?, ?, 'WEB')",
            user, type, action, version,
        )
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

    // ---- E2-S2.1: policy versions

    @Test
    fun `versions are numbered per type, and a published one is immutable for the application, the owner and the superuser`() {
        // Other test classes publish these types too (one shared database), so check the sequence, not the absolute number.
        val first = draft("TERMS_OF_USE")
        val firstNumber = publish(first)!!
        val second = draft("TERMS_OF_USE", "new text")
        assertThat(publish(second)).isEqualTo(firstNumber + 1)
        val privacy = publish(draft("PRIVACY_POLICY"))!!
        assertThat(publish(draft("PRIVACY_POLICY"))).isEqualTo(privacy + 1)

        assertThatThrownBy { transactions.asAdmin { app.update("update consent.policy_version set markdown = 'x' where id = ?", first) } }
            .isInstanceOf(Exception::class.java)
        assertThatThrownBy { asOwner("update consent.policy_version set markdown = 'x' where id = ?", first) }
            .rootCause().hasMessageContaining("immutable")
        assertThatThrownBy { superuser.update("update consent.policy_version set markdown = 'x' where id = ?", first) }
            .rootCause().hasMessageContaining("immutable")
        assertThatThrownBy { superuser.update("delete from consent.policy_version where id = ?", first) }
            .rootCause().hasMessageContaining("immutable")
        assertThat(superuser.queryForObject("select markdown from consent.policy_version where id = ?", String::class.java, first)).isEqualTo("text")
    }

    @Test
    fun `a draft can be edited and discarded, there is one draft per type, and a missing draft publishes nothing`() {
        val id = draft("COMMUNITY_CONSENT")
        assertThatThrownBy { draft("COMMUNITY_CONSENT") }.rootCause().hasMessageContaining("policy_one_draft")
        assertThat(transactions.asAdmin { app.queryForObject("select consent.update_draft(?, 'T2', 'edited')", Boolean::class.javaObjectType, id) }).isTrue()
        assertThat(superuser.queryForObject("select markdown from consent.policy_version where id = ?", String::class.java, id)).isEqualTo("edited")
        assertThat(transactions.asAdmin { app.queryForObject("select consent.discard_draft(?)", Boolean::class.javaObjectType, id) }).isTrue()
        assertThat(publish(id)).isNull()
        assertThat(publish(draft("COMMUNITY_CONSENT"))).isEqualTo(1)
    }

    @Test
    fun `only the admin scope manages policies, and users never see a draft`() {
        val user = newUser()
        assertThatThrownBy { transactions.asUser(user) { app.queryForObject("select consent.create_draft('TERMS_OF_USE','T','x',?)", UUID::class.java, user) } }
            .rootCause().hasMessageContaining("only the admin scope")
        assertThatThrownBy { transactions.asSystem { app.queryForObject("select consent.create_draft('TERMS_OF_USE','T','x',?)", UUID::class.java, user) } }
            .rootCause().hasMessageContaining("only the admin scope")
        assertThatThrownBy { transactions.asUser(user) { app.update("insert into consent.policy_version (type, title, markdown, created_by) values ('TERMS_OF_USE','T','x',?)", user) } }
            .rootCause().hasMessageContaining("permission denied")

        val id = draft("NOTIFICATIONS_CONSENT", "secret draft")
        assertThat(transactions.asUser(user) { app.queryForObject("select count(*) from consent.policy_version where id = ?", Int::class.java, id) }).isZero()
        assertThat(transactions.asAdmin { app.queryForObject("select count(*) from consent.policy_version where id = ?", Int::class.java, id) }).isEqualTo(1)
        publish(id)
        assertThat(transactions.asUser(user) { app.queryForObject("select count(*) from consent.policy_version where id = ?", Int::class.java, id) }).isEqualTo(1)
    }

    // ---- E2-S2.2: consent events

    @Test
    fun `a consent event is append-only for every role (CA5)`() {
        val user = newUser()
        val version = publishedVersion("HEALTH_DATA_CONSENT")
        accept(user, "HEALTH_DATA_CONSENT", version)
        val update = "update consent.consent_event set client = 'MOBILE' where user_id = ?"

        // The application has no UPDATE or DELETE grant at all; the trigger stops everyone who does.
        assertThatThrownBy { transactions.asUser(user) { app.update(update, user) } }.rootCause().hasMessageContaining("permission denied")
        assertThatThrownBy { transactions.asUser(user) { app.update("delete from consent.consent_event where user_id = ?", user) } }
            .rootCause().hasMessageContaining("permission denied")
        // The owner may only swap the user id (E2-S8.1, PseudonymizationIT); any other column is refused, and it has no DELETE policy.
        assertThatThrownBy { asOwner(update, user) }.rootCause().hasMessageContaining("append-only")
        asOwner("delete from consent.consent_event where user_id = ?", user)
        assertThat(superuser.queryForObject("select client from consent.consent_event where user_id = ?", String::class.java, user)).isEqualTo("WEB")
        assertThatThrownBy { superuser.update(update, user) }.rootCause().hasMessageContaining("append-only")
        assertThatThrownBy { superuser.update("delete from consent.consent_event where user_id = ?", user) }.rootCause().hasMessageContaining("append-only")
        assertThatThrownBy { superuser.execute("truncate consent.consent_event") }.rootCause().hasMessageContaining("append-only")
    }

    @Test
    fun `an event needs a published version of the same type, and the database sets the time`() {
        val user = newUser()
        val terms = publishedVersion("TERMS_OF_USE")
        val draftId = draft("PROMOTIONAL_CONSENT")

        assertThatThrownBy { accept(user, "PRIVACY_POLICY", terms) }.rootCause().hasMessageContaining("published one of the same type")
        assertThatThrownBy { accept(user, "PROMOTIONAL_CONSENT", draftId) }.rootCause().hasMessageContaining("published one of the same type")

        transactions.asUser(user) {
            app.update(
                "insert into consent.consent_event (user_id, type, action, policy_version_id, client, occurred_at) " +
                    "values (?, 'TERMS_OF_USE', 'GRANT', ?, 'WEB', '2001-01-01')",
                user, terms,
            )
        }
        val at = superuser.queryForObject("select occurred_at from consent.consent_event where user_id = ?", java.sql.Timestamp::class.java, user)!!
        assertThat(at.toInstant().isAfter(java.time.Instant.parse("2020-01-01T00:00:00Z"))).isTrue()
    }

    @Test
    fun `each user sees only their own events, even without a filter, and admin and system see nothing (CA6)`() {
        val a = newUser()
        val b = newUser()
        val version = publishedVersion("AI_EXAMS_CONSENT")
        accept(a, "AI_EXAMS_CONSENT", version)
        accept(b, "AI_EXAMS_CONSENT", version)
        accept(b, "AI_EXAMS_CONSENT", version, "REVOKE")

        val count = "select count(*) from consent.consent_event"
        assertThat(transactions.asUser(a) { app.queryForObject(count, Int::class.java) }).isEqualTo(1)
        assertThat(transactions.asUser(b) { app.queryForObject(count, Int::class.java) }).isEqualTo(2)
        assertThat(transactions.asAdmin { app.queryForObject(count, Int::class.java) }).isZero()
        assertThat(transactions.asSystem { app.queryForObject(count, Int::class.java) }).isZero()
        assertThatThrownBy { transactions.asUser(a) { app.update("insert into consent.consent_event (user_id, type, action, policy_version_id, client) values (?, 'AI_EXAMS_CONSENT', 'GRANT', ?, 'WEB')", b, version) } }
            .rootCause().hasMessageContaining("row-level security")
    }

    @Test
    fun `the current state of a consent is its last event`() {
        val user = newUser()
        val version = publishedVersion("COMMUNITY_CONSENT")
        accept(user, "COMMUNITY_CONSENT", version)
        accept(user, "COMMUNITY_CONSENT", version, "REVOKE")
        accept(user, "COMMUNITY_CONSENT", version)
        accept(user, "COMMUNITY_CONSENT", version, "REVOKE")

        val state = transactions.asUser(user) { app.queryForList("select type, action from consent.current_consent") }
        assertThat(state).containsExactly(mapOf("type" to "COMMUNITY_CONSENT", "action" to "REVOKE"))
        assertThat(superuser.queryForObject("select count(*) from consent.consent_event where user_id = ?", Int::class.java, user)).isEqualTo(4)
    }

    // ---- E2-S2.3: aggregate counts

    @Test
    fun `the counts say how many people hold each version and carry no identity`() {
        val x = newUser()
        val y = newUser()
        val v1 = publishedVersion("NOTIFICATIONS_CONSENT")
        accept(x, "NOTIFICATIONS_CONSENT", v1)
        accept(y, "NOTIFICATIONS_CONSENT", v1)
        accept(y, "NOTIFICATIONS_CONSENT", v1, "REVOKE")
        val v2 = publishedVersion("NOTIFICATIONS_CONSENT")
        accept(y, "NOTIFICATIONS_CONSENT", v2)

        val rows = transactions.asAdmin { app.queryForList("select * from consent.version_counts() where type = 'NOTIFICATIONS_CONSENT'") }
        assertThat(rows.map { it.keys }.distinct().single()).containsExactlyInAnyOrder("type", "version", "accepted")
        fun numberOf(id: UUID) = superuser.queryForObject("select version from consent.policy_version where id = ?", Int::class.java, id)
        val byVersion = rows.associate { (it["version"] as Int) to (it["accepted"] as Long) }
        assertThat(byVersion[numberOf(v1)]).isEqualTo(1L)
        assertThat(byVersion[numberOf(v2)]).isEqualTo(1L)
        assertThatThrownBy { transactions.asUser(x) { app.queryForList("select * from consent.version_counts()") } }
            .rootCause().hasMessageContaining("only the admin scope")
    }
}
