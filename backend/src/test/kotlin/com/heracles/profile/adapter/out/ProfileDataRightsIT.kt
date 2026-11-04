package com.heracles.profile.adapter.out

import com.heracles.platform.api.ErasureStep
import com.heracles.platform.api.ExportContributor
import com.heracles.platform.api.Transactions
import com.heracles.support.PostgresInitializer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.util.UUID

/** E2-S8.2: what `profile` exports and how it erases, for one person only, and safe to run twice. */
@SpringBootTest
class ProfileDataRightsIT(
    @Autowired private val transactions: Transactions,
    @Autowired private val app: JdbcTemplate,
    @Autowired private val exporters: List<ExportContributor>,
    @Autowired private val steps: List<ErasureStep>,
) {
    private val superuser = JdbcTemplate(
        DriverManagerDataSource(PostgresInitializer.postgres.jdbcUrl, PostgresInitializer.postgres.username, PostgresInitializer.postgres.password),
    )
    private val export get() = exporters.single { it.section == "anamnesis" }
    private val erasure get() = steps.single { it is JdbcProfileErasure }

    private fun newUser(): UUID = UUID.randomUUID().also { superuser.update("insert into platform.app_user (id, sub) values (?, ?)", it, "sub-$it") }

    private fun saveAnamnesis(user: UUID, revisions: Int = 1) = transactions.asUser(user) {
        repeat(revisions) { n ->
            val id = UUID.randomUUID()
            app.update(
                "insert into profile.anamnesis_revision (id, user_id, revision, objective, training_level, birth_date) values (?, ?, ?, 'GENERAL_HEALTH', 'BEGINNER', date '1990-05-01')",
                id, user, n + 1,
            )
            app.update("insert into profile.anamnesis_item (revision_id, user_id, kind, catalog_code) values (?, ?, 'ALLERGY', 'PEANUT')", id, user)
        }
    }

    private fun rows(user: UUID) = superuser.queryForObject("select count(*) from profile.anamnesis_revision where user_id = ?", Int::class.java, user)!! +
        superuser.queryForObject("select count(*) from profile.anamnesis_item where user_id = ?", Int::class.java, user)!!

    @Test
    fun `the export has every revision of the person and nothing of anyone else`() {
        val a = newUser()
        val b = newUser()
        saveAnamnesis(a, revisions = 2)
        saveAnamnesis(b)

        @Suppress("UNCHECKED_CAST")
        val ofA = transactions.asUser(a) { export.export(a) } as List<Map<String, Any?>>
        assertThat(ofA.map { it["revision"] }).containsExactly(1, 2)
        assertThat((ofA.first()["items"] as List<*>)).hasSize(1)
        // even asking for B's id inside A's transaction, row-level security gives nothing
        assertThat(transactions.asUser(a) { export.export(b) } as List<*>).isEmpty()
    }

    @Test
    fun `erasing removes the person's anamnesis and only theirs, and a second run changes nothing`() {
        val a = newUser()
        val b = newUser()
        saveAnamnesis(a, revisions = 2)
        saveAnamnesis(b)

        transactions.asSystem { erasure.erase(a) }
        assertThat(rows(a)).isZero()
        assertThat(rows(b)).isEqualTo(2)
        transactions.asSystem { erasure.erase(a) } // again: same result, no error
        assertThat(rows(a)).isZero()
        assertThat(rows(b)).isEqualTo(2)
    }

    @Test
    fun `only the system scope erases, and the anamnesis is still not deletable any other way`() {
        val a = newUser()
        saveAnamnesis(a)
        assertThatThrownBy { transactions.asUser(a) { erasure.erase(a) } }.rootCause().hasMessageContaining("only the system scope")
        assertThatThrownBy { transactions.asAdmin { erasure.erase(a) } }.rootCause().hasMessageContaining("only the system scope")
        assertThatThrownBy { superuser.update("delete from profile.anamnesis_item where user_id = ?", a) }.rootCause().hasMessageContaining("append-only")
        assertThat(rows(a)).isEqualTo(2)
    }

    @Test
    fun `the erasure steps run in the declared order`() {
        assertThat(steps.sortedBy { it.order }.map { it.order }).isSorted()
        assertThat(steps.map { it.order }).doesNotHaveDuplicates()
    }
}
