package com.heracles.consent.adapter.out

import com.heracles.platform.api.ErasureStep
import com.heracles.platform.api.ExportContributor
import com.heracles.platform.api.Transactions
import com.heracles.support.PostgresInitializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.util.UUID

/** E2-S8.2: the history exported for one person, and the erasure that unlinks the proof without destroying it. */
@SpringBootTest
class ConsentDataRightsIT(
    @Autowired private val transactions: Transactions,
    @Autowired private val app: JdbcTemplate,
    @Autowired private val exporters: List<ExportContributor>,
    @Autowired private val steps: List<ErasureStep>,
) {
    private val superuser = JdbcTemplate(
        DriverManagerDataSource(PostgresInitializer.postgres.jdbcUrl, PostgresInitializer.postgres.username, PostgresInitializer.postgres.password),
    )
    private val actor = UUID.randomUUID()
    private val export get() = exporters.single { it.section == "consents" }
    private val erasure get() = steps.single { it is JdbcConsentErasure }

    private fun newUser(): UUID = UUID.randomUUID().also { superuser.update("insert into platform.app_user (id, sub) values (?, ?)", it, "sub-$it") }

    private fun version(): UUID = transactions.asAdmin {
        val draft = app.queryForObject("select consent.create_draft('NOTIFICATIONS_CONSENT', 'T', 'x', ?)", UUID::class.java, actor)!!
        app.queryForObject("select consent.publish_version(?, ?)", Int::class.javaObjectType, draft, actor)
        draft
    }

    private fun accept(user: UUID, version: UUID) = transactions.asUser(user) {
        app.update("insert into consent.consent_event (user_id, type, action, policy_version_id, client) values (?, 'NOTIFICATIONS_CONSENT', 'GRANT', ?, 'WEB')", user, version)
    }

    private fun events(user: UUID) = superuser.queryForObject("select count(*) from consent.consent_event where user_id = ?", Int::class.java, user)!!

    @Test
    fun `the export has the history of the person and nothing of anyone else`() {
        val a = newUser()
        val b = newUser()
        val v = version()
        accept(a, v); accept(a, v); accept(b, v)

        assertThat(transactions.asUser(a) { export.export(a) } as List<*>).hasSize(2)
        assertThat(transactions.asUser(a) { export.export(b) } as List<*>).isEmpty()
    }

    @Test
    fun `erasing unlinks the proof of acceptance from the person, keeps it, and a second run changes nothing`() {
        val a = newUser()
        val b = newUser()
        val v = version()
        accept(a, v); accept(b, v)
        val total = superuser.queryForObject("select count(*) from consent.consent_event", Int::class.java)!!

        transactions.asSystem { erasure.erase(a) }
        assertThat(events(a)).isZero()
        assertThat(events(b)).isEqualTo(1)
        assertThat(superuser.queryForObject("select count(*) from consent.consent_event", Int::class.java)).isEqualTo(total) // the proof stays
        transactions.asSystem { erasure.erase(a) }
        assertThat(superuser.queryForObject("select count(*) from consent.consent_event", Int::class.java)).isEqualTo(total)
    }
}
