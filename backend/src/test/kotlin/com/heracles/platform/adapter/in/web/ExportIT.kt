package com.heracles.platform.adapter.`in`.web

import com.heracles.consent.api.ConsentType
import com.heracles.platform.api.Transactions
import com.heracles.platform.application.ConsumeTask
import com.heracles.platform.application.ExpireExports
import com.heracles.platform.application.port.out.Clock
import com.heracles.platform.application.port.out.FileStorage
import com.heracles.platform.application.port.out.TaskQueue
import com.heracles.platform.domain.QueueMessage
import com.heracles.platform.domain.ReceivedMessage
import com.heracles.support.ApiTestClient
import com.heracles.support.InMemoryFileStorage
import com.heracles.support.PostgresInitializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
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
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** E2-S8.3 (CA13): the export of one person has their data and no one else's; the link is short; the file goes away. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ExportIT.Storage::class)
class ExportIT(
    @Value("\${local.server.port}") port: Int,
    @Autowired private val transactions: Transactions,
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val consume: ConsumeTask,
    @Autowired private val storage: FileStorage,
    @Autowired private val expire: ExpireExports,
    @Autowired private val clock: Clock,
) {
    @TestConfiguration
    class Storage { @Bean @Primary fun inMemoryStorage(): FileStorage = InMemoryFileStorage() }

    @MockitoBean lateinit var queue: TaskQueue

    private val api = ApiTestClient(port)
    private val actor = UUID.randomUUID()
    private val json = JsonMapper()
    private val superuser = JdbcTemplate(
        DriverManagerDataSource(PostgresInitializer.postgres.jdbcUrl, PostgresInitializer.postgres.username, PostgresInitializer.postgres.password),
    )

    private fun person(allergyText: String): String = api.newSubject().also { me ->
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
            "items" to listOf(mapOf("kind" to "DISLIKED_FOOD", "otherText" to allergyText)),
        )
        assertThat(api.put(me, "/api/v1/me/anamnesis", body).status).isEqualTo(200)
    }

    /** The worker's job, done by hand: take the export message from the outbox and consume it. */
    private fun runTask(taskId: String) {
        val row = superuser.queryForMap("select id, type, payload::text as payload from platform.outbox_message where aggregate_id = ?", taskId)
        val ids = json.readTree(row["payload"] as String).properties().associate { it.key to it.value.asString() }
        consume(ReceivedMessage(QueueMessage(row["id"].toString(), row["type"] as String, ids), "receipt", 1))
        superuser.update("update platform.task set status = 'DONE' where id = ?::uuid", taskId)
    }

    @Test
    fun `an export has the person's data and nothing of anyone else (CA13)`() {
        val a = person("FOOD-OF-A")
        val b = person("FOOD-OF-B")

        val requested = api.post(a, "/api/v1/me/exports")
        assertThat(requested.status).isEqualTo(202)
        val taskId = requested.json.path("taskId").asString()
        assertThat(api.get(a, "/api/v1/me/exports/$taskId").code).isEqualTo("export_not_ready")

        runTask(taskId)
        val ready = api.get(a, "/api/v1/me/exports/$taskId")
        assertThat(ready.status).isEqualTo(200)
        val key = ready.json.path("url").asString().removePrefix("memory://").substringBefore("?")
        assertThat(ready.json.path("url").asString()).contains("valid=900")

        val file = String(storage.get(com.heracles.platform.domain.StorageKey(key))!!)
        assertThat(file).contains("FOOD-OF-A").doesNotContain("FOOD-OF-B")
        val doc = json.readTree(file)
        assertThat(doc.propertyNames().toList()).contains("account", "auditTrail", "anamnesis", "consents")
        assertThat(doc.path("consents").size()).isEqualTo(3)
        assertThat(doc.path("anamnesis").size()).isEqualTo(1)
        // the trail that names the person, with field names and no values
        val actions = doc.path("auditTrail").toList().map { it.path("action").asString() }
        assertThat(actions).contains("consent.accepted", "anamnesis.saved", "export.requested")
        assertThat(file).doesNotContain(b.removePrefix("sub-"))

        // B cannot get A's file, by its task id, and sees nothing about it
        assertThat(api.get(b, "/api/v1/me/exports/$taskId").status).isEqualTo(404)
        assertThat(api.get(null, "/api/v1/me/exports/$taskId").status).isEqualTo(401)
    }

    @Test
    fun `the same message twice leaves one file, and the file goes away after 24 hours`() {
        val a = person("FOOD-OF-A")
        val taskId = api.post(a, "/api/v1/me/exports").json.path("taskId").asString()
        runTask(taskId)
        runTask(taskId) // delivered again
        val files = (storage as InMemoryFileStorage).files
        assertThat(files.keys.filter { it.endsWith("$taskId.json") }).hasSize(1)
        assertThat(superuser.queryForObject("select count(*) from platform.data_export where task_id = ?::uuid", Int::class.java, taskId)).isEqualTo(1)

        // the clean-up honours the stored expiry: before it nothing goes; once the stored time has passed the file and reference go
        assertThat(expire()).isZero()
        superuser.update("update platform.data_export set expires_at = ? where task_id = ?::uuid", java.sql.Timestamp.from(clock.instant().minus(Duration.ofSeconds(1))), taskId)
        assertThat(expire()).isGreaterThanOrEqualTo(1)
        assertThat(files.keys.filter { it.endsWith("$taskId.json") }).isEmpty()
        assertThat(api.get(a, "/api/v1/me/exports/$taskId").code).isEqualTo("export_not_ready")
    }

    @Test
    fun `the export references are isolated by user, also for a query with no filter`() {
        val a = person("FOOD-OF-A")
        val b = person("FOOD-OF-B")
        val taskId = api.post(a, "/api/v1/me/exports").json.path("taskId").asString()
        runTask(taskId)
        val idOf = { s: String -> superuser.queryForObject("select id from platform.app_user where sub = ?", UUID::class.java, s)!! }
        val count = "select count(*) from platform.data_export"
        assertThat(transactions.asUser(idOf(a)) { jdbc.queryForObject(count, Int::class.java) }).isEqualTo(1)
        assertThat(transactions.asUser(idOf(b)) { jdbc.queryForObject(count, Int::class.java) }).isZero()
        assertThat(transactions.asAdmin { jdbc.queryForObject(count, Int::class.java) }).isZero()
    }

    @Test
    fun `an export does not need the health data consent, so the person can take their data out`() {
        val me = api.newSubject() // consented to nothing
        assertThat(api.post(me, "/api/v1/me/exports").status).isEqualTo(202)
    }
}
