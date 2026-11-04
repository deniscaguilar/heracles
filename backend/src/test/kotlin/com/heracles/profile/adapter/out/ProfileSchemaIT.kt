package com.heracles.profile.adapter.out

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

/** E2-S6.1 (CA6): the anamnesis is health data, isolated by user, append-only; the catalogs are public seed data. */
@SpringBootTest
class ProfileSchemaIT(
    @Autowired private val transactions: Transactions,
    @Autowired private val app: JdbcTemplate,
) {
    private val superuser = JdbcTemplate(
        DriverManagerDataSource(
            PostgresInitializer.postgres.jdbcUrl, PostgresInitializer.postgres.username, PostgresInitializer.postgres.password,
        ),
    )

    private fun newUser(): UUID = UUID.randomUUID().also { superuser.update("insert into platform.app_user (id, sub) values (?, ?)", it, "sub-$it") }

    private fun saveRevision(user: UUID, revision: Int = 1, objective: String = "GENERAL_HEALTH"): UUID = transactions.asUser(user) {
        val id = UUID.randomUUID()
        app.update(
            "insert into profile.anamnesis_revision (id, user_id, revision, objective, training_level, birth_date) values (?, ?, ?, ?, 'BEGINNER', date '1990-05-01')",
            id, user, revision, objective,
        )
        id
    }

    private fun addItem(user: UUID, revision: UUID, kind: String, code: String?, other: String? = null, region: String? = null) =
        transactions.asUser(user) {
            app.update(
                "insert into profile.anamnesis_item (revision_id, user_id, kind, catalog_code, other_text, body_region) values (?, ?, ?, ?, ?, ?)",
                revision, user, kind, code, other, region,
            )
        }

    @Test
    fun `catalogs are seeded for every kind and readable by any role, but nobody in the application can change them`() {
        val kinds = app.queryForList("select distinct kind from profile.catalog_item", String::class.java)
        assertThat(kinds).containsExactlyInAnyOrder(
            "OBJECTIVE", "TRAINING_LEVEL", "ALLERGY", "INTOLERANCE", "RESTRICTION", "FOOD_PREFERENCE", "EQUIPMENT", "BODY_REGION",
        )
        val user = newUser()
        assertThat(transactions.asUser(user) { app.queryForObject("select count(*) from profile.catalog_item where kind = 'OBJECTIVE'", Int::class.java) }).isGreaterThan(2)
        assertThatThrownBy { transactions.asUser(user) { app.update("insert into profile.catalog_item (kind, code, sort_order) values ('ALLERGY', 'X', 99)") } }
            .rootCause().hasMessageContaining("permission denied")
        assertThatThrownBy { transactions.asUser(user) { app.update("delete from profile.catalog_item") } }
            .rootCause().hasMessageContaining("permission denied")
    }

    @Test
    fun `a user sees only their own revisions and items, even with a query that has no user filter (CA6)`() {
        val a = newUser()
        val b = newUser()
        val revA = saveRevision(a)
        val revB = saveRevision(b)
        addItem(a, revA, "ALLERGY", "PEANUT")
        addItem(b, revB, "ALLERGY", "SHELLFISH")

        assertThat(transactions.asUser(a) { app.queryForList("select user_id from profile.anamnesis_revision", UUID::class.java) }).containsOnly(a)
        assertThat(transactions.asUser(b) { app.queryForList("select catalog_code from profile.anamnesis_item", String::class.java) }).containsExactly("SHELLFISH")
        // B cannot write on A's behalf.
        assertThatThrownBy { saveRevisionAs(b, a) }.rootCause().hasMessageContaining("row-level security")
        assertThatThrownBy { addItem(b, revA, "ALLERGY", "PEANUT") }.isInstanceOf(Exception::class.java)
    }

    private fun saveRevisionAs(caller: UUID, owner: UUID) = transactions.asUser(caller) {
        app.update(
            "insert into profile.anamnesis_revision (user_id, revision, objective, training_level, birth_date) values (?, 5, 'GENERAL_HEALTH', 'BEGINNER', date '1990-05-01')",
            owner,
        )
    }

    @Test
    fun `without a user in the context, and in the admin and system scopes, nothing is readable`() {
        val user = newUser()
        val revision = saveRevision(user)
        addItem(user, revision, "EQUIPMENT", "DUMBBELLS")

        val tables = listOf("anamnesis_revision", "anamnesis_item")
        tables.forEach { table ->
            assertThat(transactions.asAdmin { app.queryForObject("select count(*) from profile.$table", Int::class.java) }).isZero()
            assertThat(transactions.asSystem { app.queryForObject("select count(*) from profile.$table", Int::class.java) }).isZero()
            assertThat(app.queryForObject("select count(*) from profile.$table", Int::class.java)).isZero()
        }
    }

    @Test
    fun `a revision number is unique per user and an item belongs to a revision of the same user`() {
        val a = newUser()
        val b = newUser()
        saveRevision(a, 1)
        saveRevision(b, 1)
        saveRevision(a, 2)
        assertThatThrownBy { saveRevision(a, 2) }.rootCause().hasMessageContaining("anamnesis_revision_user_revision")
    }

    @Test
    fun `revisions and items are append-only for every role`() {
        val user = newUser()
        val revision = saveRevision(user)
        addItem(user, revision, "ALLERGY", "PEANUT")

        assertThatThrownBy { transactions.asUser(user) { app.update("update profile.anamnesis_revision set objective = 'MUSCLE_GAIN' where user_id = ?", user) } }
            .rootCause().hasMessageContaining("permission denied")
        assertThatThrownBy { transactions.asUser(user) { app.update("delete from profile.anamnesis_item where user_id = ?", user) } }
            .rootCause().hasMessageContaining("permission denied")
        // Even the superuser, who skips grants and row-level security, is stopped by the trigger.
        assertThatThrownBy { superuser.update("update profile.anamnesis_revision set objective = 'MUSCLE_GAIN' where user_id = ?", user) }
            .rootCause().hasMessageContaining("append-only")
        assertThatThrownBy { superuser.update("update profile.anamnesis_item set note = 'x' where user_id = ?", user) }
            .rootCause().hasMessageContaining("append-only")
        assertThatThrownBy { superuser.update("delete from profile.anamnesis_item where user_id = ?", user) }
            .rootCause().hasMessageContaining("append-only")
        assertThatThrownBy { superuser.execute("truncate profile.anamnesis_revision cascade") }.rootCause().hasMessageContaining("append-only")
    }

    @Test
    fun `an item has exactly the fields its kind allows`() {
        val user = newUser()
        val revision = saveRevision(user)

        addItem(user, revision, "DISLIKED_FOOD", null, other = "beterraba")
        addItem(user, revision, "ALLERGY", null, other = "kiwi")
        addItem(user, revision, "INJURY", null, region = "KNEE")
        // both a catalog code and free text, or neither, or a region on a food item
        assertThatThrownBy { addItem(user, revision, "ALLERGY", "PEANUT", other = "x") }.isInstanceOf(Exception::class.java)
        assertThatThrownBy { addItem(user, revision, "ALLERGY", null) }.isInstanceOf(Exception::class.java)
        assertThatThrownBy { addItem(user, revision, "ALLERGY", "PEANUT", region = "KNEE") }.isInstanceOf(Exception::class.java)
        assertThatThrownBy { addItem(user, revision, "INJURY", null) }.isInstanceOf(Exception::class.java)
        assertThatThrownBy { addItem(user, revision, "DISLIKED_FOOD", "PEANUT") }.isInstanceOf(Exception::class.java)
        assertThatThrownBy { addItem(user, revision, "NOT_A_KIND", "X") }.isInstanceOf(Exception::class.java)
        assertThatThrownBy { addItem(user, revision, "ALLERGY", null, other = "x".repeat(81)) }.isInstanceOf(Exception::class.java)
    }
}
