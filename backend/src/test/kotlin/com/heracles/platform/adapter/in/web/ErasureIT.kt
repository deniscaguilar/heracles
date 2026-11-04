package com.heracles.platform.adapter.`in`.web

import com.heracles.consent.api.ConsentType
import com.heracles.platform.api.Transactions
import com.heracles.platform.application.ConsumeTask
import com.heracles.platform.application.QueueDueErasures
import com.heracles.platform.application.RunErasure
import com.heracles.platform.application.port.out.FileStorage
import com.heracles.platform.application.port.out.IdentityProvider
import com.heracles.platform.application.port.out.TaskQueue
import com.heracles.platform.api.TaskMessage
import com.heracles.platform.domain.QueueMessage
import com.heracles.platform.domain.ReceivedMessage
import com.heracles.support.ApiTestClient
import com.heracles.support.InMemoryFileStorage
import com.heracles.support.PostgresInitializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import tools.jackson.databind.json.JsonMapper
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/** E2-S8.4 (CA14): erasing a person end to end, twice, and what stays (without a link to them). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ErasureIT.Storage::class)
class ErasureIT(
    @Value("\${local.server.port}") port: Int,
    @Autowired private val transactions: Transactions,
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val consume: ConsumeTask,
    @Autowired private val queueDue: QueueDueErasures,
    @Autowired private val run: RunErasure,
    @Autowired private val storage: FileStorage,
) {
    @TestConfiguration
    class Storage { @Bean @Primary fun inMemoryStorage(): FileStorage = InMemoryFileStorage() }

    @MockitoBean lateinit var queue: TaskQueue
    @MockitoSpyBean lateinit var identity: IdentityProvider

    private val api = ApiTestClient(port)
    private val actor = UUID.randomUUID()
    private val json = JsonMapper()
    private val superuser = JdbcTemplate(
        DriverManagerDataSource(PostgresInitializer.postgres.jdbcUrl, PostgresInitializer.postgres.username, PostgresInitializer.postgres.password),
    )

    private fun person(): String = api.newSubject().also { me ->
        ConsentType.entries.filter { it.required }.forEach { type ->
            transactions.asAdmin {
                jdbc.queryForList("select id from consent.policy_version where type = ? and published_at is null", UUID::class.java, type.name)
                    .forEach { jdbc.queryForObject("select consent.discard_draft(?)", Boolean::class.javaObjectType, it) }
                val id = jdbc.queryForObject("select consent.create_draft(?, 'Title', 'Text.', ?)", UUID::class.java, type.name, actor)!!
                jdbc.queryForObject("select consent.publish_version(?, ?)", Int::class.javaObjectType, id, actor)
            }
        }
        val ids = ConsentType.entries.filter { it.required }.map { api.get(me, "/api/v1/policies/${it.name}/current").json.path("versionId").asString() }
        api.post(me, "/api/v1/me/required-consents", mapOf("versionIds" to ids, "client" to "WEB"))
        val body = mapOf(
            "objective" to "GENERAL_HEALTH", "trainingLevel" to "BEGINNER", "birthDate" to "1990-05-01",
            "screening" to mapOf("PREGNANT_OR_NURSING" to "NO", "CONDITION_REQUIRING_CARE" to "NO", "MEDICATION_AFFECTING_PLAN" to "NO", "EATING_DISORDER_HISTORY" to "NO"),
            "items" to listOf(mapOf("kind" to "DISLIKED_FOOD", "otherText" to "beterraba")),
        )
        assertThat(api.put(me, "/api/v1/me/anamnesis", body).status).isEqualTo(200)
    }

    private fun idOf(subject: String) = superuser.queryForObject("select id from platform.app_user where sub = ?", UUID::class.java, subject)!!
    private fun count(sql: String, id: UUID) = superuser.queryForObject(sql, Int::class.java, id)!!

    /** The 7 days are the clock's business (unit tests); here the stored time is moved back so the scheduler finds it due. */
    private fun makeDue(id: UUID) = superuser.update(
        "update platform.account_erasure set requested_at = ?, due_at = ? where user_id = ?",
        Timestamp.from(Instant.now().minusSeconds(8 * 86400)), Timestamp.from(Instant.now().minusSeconds(60)), id,
    )

    private fun runQueuedTask(id: UUID) {
        val row = superuser.queryForMap("select id, type, payload::text as payload from platform.outbox_message where type = 'erasure.run' and payload->>'subjectId' = ?", id.toString())
        val ids = json.readTree(row["payload"] as String).properties().associate { it.key to it.value.asString() }
        consume(ReceivedMessage(QueueMessage(row["id"].toString(), row["type"] as String, ids), "receipt", 1))
    }

    @Test
    fun `the request has 7 days, can be seen and canceled, and nothing is erased before they pass`() {
        val me = person()
        val id = idOf(me)
        val requested = api.post(me, "/api/v1/me/erasure-requests")
        assertThat(requested.status).isEqualTo(202)
        assertThat(requested.json.path("status").asString()).isEqualTo("PENDING")
        val days = java.time.Duration.between(Instant.parse(requested.json.path("requestedAt").asString()), Instant.parse(requested.json.path("dueAt").asString())).toDays()
        assertThat(days).isEqualTo(7)
        assertThat(api.get(me, "/api/v1/me/erasure-requests/current").json.path("dueAt").asString()).isEqualTo(requested.json.path("dueAt").asString())

        assertThat(queueDue()).isZero()
        assertThat(count("select count(*) from profile.anamnesis_revision where user_id = ?", id)).isEqualTo(1)
        assertThat(api.delete(me, "/api/v1/me/erasure-requests/current").status).isEqualTo(204)
        assertThat(api.get(me, "/api/v1/me/erasure-requests/current").code).isEqualTo("erasure_not_found")
        assertThat(api.delete(me, "/api/v1/me/erasure-requests/current").code).isEqualTo("erasure_not_found")
        assertThat(api.get(null, "/api/v1/me/erasure-requests/current").status).isEqualTo(401)
    }

    @Test
    fun `one person never sees or cancels another person's erasure request, and the database says the same`() {
        val a = person()
        val b = person()
        api.post(a, "/api/v1/me/erasure-requests")

        assertThat(api.get(b, "/api/v1/me/erasure-requests/current").code).isEqualTo("erasure_not_found")
        assertThat(api.delete(b, "/api/v1/me/erasure-requests/current").code).isEqualTo("erasure_not_found")
        assertThat(api.get(a, "/api/v1/me/erasure-requests/current").json.path("status").asString()).isEqualTo("PENDING")
        // a query with no filter by user, as B, finds nothing; as the admin scope, nothing either
        assertThat(transactions.asUser(idOf(b)) { jdbc.queryForObject("select count(*) from platform.account_erasure", Int::class.java) }).isZero()
        assertThat(transactions.asAdmin { jdbc.queryForObject("select count(*) from platform.account_erasure", Int::class.java) }).isZero()
        assertThat(transactions.asUser(idOf(a)) { jdbc.queryForObject("select count(*) from platform.account_erasure", Int::class.java) }).isEqualTo(1)
    }

    @Test
    fun `erasing removes the person, keeps the proof without a link to them, and the old subject starts a new empty account (CA14)`() {
        val me = person()
        val other = person()
        val id = idOf(me)
        val otherId = idOf(other)
        // an export file exists too
        val exportTask = api.post(me, "/api/v1/me/exports").json.path("taskId").asString()
        val exportRow = superuser.queryForMap("select id, type, payload::text as payload from platform.outbox_message where aggregate_id = ?", exportTask)
        consume(ReceivedMessage(QueueMessage(exportRow["id"].toString(), "export.run", json.readTree(exportRow["payload"] as String).properties().associate { it.key to it.value.asString() }), "r", 1))
        val files = (storage as InMemoryFileStorage).files
        assertThat(files.keys.any { it.startsWith("exports/$id/") }).isTrue()

        val consentTotal = superuser.queryForObject("select count(*) from consent.consent_event", Int::class.java)!!
        val consentOfMe = count("select count(*) from consent.consent_event where user_id = ?", id)
        assertThat(consentOfMe).isEqualTo(3)
        api.post(me, "/api/v1/me/erasure-requests")
        makeDue(id)

        assertThat(queueDue()).isEqualTo(1)
        runQueuedTask(id)

        // gone
        assertThat(count("select count(*) from profile.anamnesis_revision where user_id = ?", id)).isZero()
        assertThat(count("select count(*) from profile.anamnesis_item where user_id = ?", id)).isZero()
        assertThat(files.keys.any { it.startsWith("exports/$id/") }).isFalse()
        assertThat(count("select count(*) from platform.data_export where user_id = ?", id)).isZero()
        val hollow = superuser.queryForMap("select sub, role, deleted_at from platform.app_user where id = ?", id)
        assertThat(hollow["sub"]).isNull()
        assertThat(hollow["deleted_at"]).isNotNull()
        verify(identity).deleteUser(me)
        // the proof stays, but not linked to the person
        assertThat(count("select count(*) from consent.consent_event where user_id = ?", id)).isZero()
        assertThat(superuser.queryForObject("select count(*) from consent.consent_event", Int::class.java)).isEqualTo(consentTotal)
        assertThat(superuser.queryForObject("select count(*) from platform.audit_event where actor_user_id = ? or entity_id = ?", Int::class.java, id, id.toString())).isZero()
        assertThat(superuser.queryForObject("select count(*) from platform.audit_event where action = 'erasure.requested'", Int::class.java)).isGreaterThanOrEqualTo(1)
        // the other person is untouched
        assertThat(count("select count(*) from profile.anamnesis_revision where user_id = ?", otherId)).isEqualTo(1)
        assertThat(count("select count(*) from consent.consent_event where user_id = ?", otherId)).isEqualTo(3)

        // the old token opens a NEW, empty account
        val fresh = api.get(me, "/api/v1/me/consents")
        assertThat(fresh.status).isEqualTo(200)
        assertThat(fresh.json.path("items").toList().filter { it.path("required").asBoolean() }.all { it.path("pending").asBoolean() }).isTrue()
        assertThat(api.get(me, "/api/v1/me/anamnesis").code).isEqualTo("consent_required")
        val newId = superuser.queryForObject("select id from platform.app_user where sub = ?", UUID::class.java, me)!!
        assertThat(newId).isNotEqualTo(id)
    }

    @Test
    fun `the task run twice has the same result, even delivered again or handled directly`() {
        val me = person()
        val id = idOf(me)
        api.post(me, "/api/v1/me/erasure-requests")
        makeDue(id)
        queueDue()
        runQueuedTask(id)
        val totals = { superuser.queryForObject("select count(*) from consent.consent_event", Int::class.java) to superuser.queryForObject("select count(*) from platform.audit_event", Int::class.java) }
        val before = totals()

        runQueuedTask(id) // the same message again: the consumer recognizes it
        transactions.asSystem { run.handle(TaskMessage("again", "erasure.run", mapOf("subjectId" to id.toString()))) } // and the handler itself
        transactions.asSystem { run.handle(TaskMessage("again2", "erasure.run", mapOf("subjectId" to id.toString()))) }

        assertThat(totals().first).isEqualTo(before.first)
        assertThat(count("select count(*) from profile.anamnesis_revision where user_id = ?", id)).isZero()
        assertThat(superuser.queryForMap("select sub from platform.app_user where id = ?", id)["sub"]).isNull()
    }
}
