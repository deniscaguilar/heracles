package com.heracles.profile.adapter.`in`.web

import com.heracles.consent.api.ConsentType
import com.heracles.platform.api.Transactions
import com.heracles.profile.api.EligibilityQuery
import com.heracles.profile.api.EligibilityStatus
import com.heracles.support.ApiTestClient
import com.heracles.support.PostgresInitializer
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID

/** E2-S6.3 (CA1, CA6, CA11): the anamnesis API behind the consent barrier. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AnamnesisApiIT(
    @Value("\${local.server.port}") port: Int,
    @Autowired private val transactions: Transactions,
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val eligibility: EligibilityQuery,
) {
    private val api = ApiTestClient(port)
    private val actor = UUID.randomUUID()
    private val superuser = JdbcTemplate(
        DriverManagerDataSource(PostgresInitializer.postgres.jdbcUrl, PostgresInitializer.postgres.username, PostgresInitializer.postgres.password),
    )

    @BeforeEach
    fun publishHealthTexts() {
        ConsentType.entries.filter { it.required }.forEach { type ->
            transactions.asAdmin {
                jdbc.queryForList("select id from consent.policy_version where type = ? and published_at is null", UUID::class.java, type.name)
                    .forEach { jdbc.queryForObject("select consent.discard_draft(?)", Boolean::class.javaObjectType, it) }
                val id = jdbc.queryForObject("select consent.create_draft(?, 'Title', 'Text.', ?)", UUID::class.java, type.name, actor)!!
                jdbc.queryForObject("select consent.publish_version(?, ?)", Int::class.javaObjectType, id, actor)
            }
        }
    }

    private fun consenting(): String = api.newSubject().also { me ->
        val ids = ConsentType.entries.filter { it.required }.map {
            api.get(me, "/api/v1/policies/${it.name}/current").json.path("versionId").asString()
        }
        assertThat(api.post(me, "/api/v1/me/required-consents", mapOf("versionIds" to ids, "client" to "WEB")).status).isEqualTo(204)
    }

    private val screening = mapOf(
        "PREGNANT_OR_NURSING" to "NO", "CONDITION_REQUIRING_CARE" to "NO", "MEDICATION_AFFECTING_PLAN" to "NO", "EATING_DISORDER_HISTORY" to "NO",
    )

    private fun body(allergy: String = "PEANUT", birthDate: String = "1990-05-01", objective: String = "GENERAL_HEALTH") = mapOf(
        "objective" to objective, "trainingLevel" to "BEGINNER", "birthDate" to birthDate, "biologicalSex" to "FEMALE", "screening" to screening,
        "items" to listOf(
            mapOf("kind" to "ALLERGY", "catalogCode" to allergy),
            mapOf("kind" to "DISLIKED_FOOD", "otherText" to "beterraba"),
            mapOf("kind" to "INJURY", "bodyRegion" to "KNEE", "note" to "dor ao agachar"),
        ),
    )

    @Test
    fun `without a token every route is 401, and the catalogs need only a token`() {
        assertThat(api.get(null, "/api/v1/me/anamnesis").status).isEqualTo(401)
        assertThat(api.put(null, "/api/v1/me/anamnesis", body()).status).isEqualTo(401)
        assertThat(api.get(null, "/api/v1/catalogs/ALLERGY").status).isEqualTo(401)
        val catalog = api.get(api.newSubject(), "/api/v1/catalogs/ALLERGY")
        assertThat(catalog.status).isEqualTo(200)
        assertThat(catalog.json.path("codes").toList().map { it.asString() }).contains("PEANUT", "MILK")
    }

    @Test
    fun `without the health data consent every anamnesis route is 403 consent_required (CA1)`() {
        val me = api.newSubject() // signed up, consented to nothing
        listOf(
            api.get(me, "/api/v1/me/anamnesis"), api.put(me, "/api/v1/me/anamnesis", body()),
            api.get(me, "/api/v1/me/anamnesis/revisions"), api.get(me, "/api/v1/me/anamnesis/revisions/1"),
        ).forEach {
            assertThat(it.status).isEqualTo(403)
            assertThat(it.code).isEqualTo("consent_required")
        }
    }

    @Test
    fun `saving twice creates two revisions, the current is the last and the first stays readable (CA11)`() {
        val me = consenting()
        assertThat(api.get(me, "/api/v1/me/anamnesis").status).isEqualTo(404)

        val first = api.put(me, "/api/v1/me/anamnesis", body("PEANUT"))
        assertThat(first.status).isEqualTo(200)
        assertThat(first.json.path("revision").asInt()).isEqualTo(1)
        assertThat(first.json.path("eligible").asBoolean()).isTrue()
        assertThat(first.json.path("items").size()).isEqualTo(3)

        assertThat(api.put(me, "/api/v1/me/anamnesis", body("MILK")).json.path("revision").asInt()).isEqualTo(2)
        val current = api.get(me, "/api/v1/me/anamnesis").json
        assertThat(current.path("revision").asInt()).isEqualTo(2)
        assertThat(current.path("items").any { it.path("catalogCode").asString(null) == "MILK" }).isTrue()
        val old = api.get(me, "/api/v1/me/anamnesis/revisions/1").json
        assertThat(old.path("items").any { it.path("catalogCode").asString(null) == "PEANUT" }).isTrue()
        assertThat(api.get(me, "/api/v1/me/anamnesis/revisions").json.path("items").toList().map { it.path("revision").asInt() }).containsExactly(1, 2)
        assertThat(api.get(me, "/api/v1/me/anamnesis/revisions/3").code).isEqualTo("anamnesis_not_found")
    }

    @Test
    fun `invalid values are 400 with stable code, field names and trace id, and never repeat the value (CA11)`() {
        val me = consenting()
        val outside = api.put(me, "/api/v1/me/anamnesis", body(allergy = "SENTINEL_NOT_IN_CATALOG"))
        assertThat(outside.status).isEqualTo(400)
        assertThat(outside.code).isEqualTo("anamnesis_invalid")
        assertThat(outside.json.path("fields").toList().map { it.asString() }).containsExactly("items[0].catalogCode")
        assertThat(outside.json.path("traceId").asString("")).isNotEmpty()
        assertThat(outside.body).doesNotContain("SENTINEL_NOT_IN_CATALOG")

        val future = api.put(me, "/api/v1/me/anamnesis", body(birthDate = "2999-01-01"))
        assertThat(future.code).isEqualTo("anamnesis_invalid")
        assertThat(future.json.path("fields").toList().map { it.asString() }).containsExactly("birthDate")
        assertThat(future.body).doesNotContain("2999")

        val tooLong = api.put(me, "/api/v1/me/anamnesis", body() + ("items" to listOf(mapOf("kind" to "DISLIKED_FOOD", "otherText" to "x".repeat(81)))))
        assertThat(tooLong.json.path("fields").toList().map { it.asString() }).containsExactly("items[0].otherText")
        // nothing was saved by any of them
        assertThat(api.get(me, "/api/v1/me/anamnesis").status).isEqualTo(404)
    }

    @Test
    fun `a minor is out of the automatic plan, the mark is audited and redoing the anamnesis removes it (CA12)`() {
        val me = consenting()
        val minor = api.put(me, "/api/v1/me/anamnesis", body(birthDate = java.time.LocalDate.now().minusYears(15).toString()))
        assertThat(minor.status).isEqualTo(200)
        assertThat(minor.json.path("eligible").asBoolean()).isFalse()
        assertThat(minor.json.path("ineligibilityReasons").toList().map { it.asString() }).containsExactly("UNDER_MINIMUM_AGE")
        assertThat(statusOf(me)).isEqualTo(EligibilityStatus.NOT_ELIGIBLE_FOR_AUTOMATIC_PLAN)
        assertThat(auditCount(me)).isEqualTo(1)

        val adult = api.put(me, "/api/v1/me/anamnesis", body())
        assertThat(adult.json.path("eligible").asBoolean()).isTrue()
        assertThat(statusOf(me)).isEqualTo(EligibilityStatus.ELIGIBLE)
        assertThat(auditCount(me)).isEqualTo(2)
        // the old revision keeps what it was
        assertThat(api.get(me, "/api/v1/me/anamnesis/revisions/1").json.path("eligible").asBoolean()).isFalse()
    }

    @Test
    fun `a screening answer of yes or prefer-not-to-say is out of the plan, and a missing screening is 400`() {
        val me = consenting()
        val yes = api.put(me, "/api/v1/me/anamnesis", body() + ("screening" to (screening + ("PREGNANT_OR_NURSING" to "YES"))))
        assertThat(yes.json.path("ineligibilityReasons").toList().map { it.asString() }).containsExactly("PREGNANT_OR_NURSING")
        val partial = api.put(me, "/api/v1/me/anamnesis", body() + ("screening" to (screening - "PREGNANT_OR_NURSING")))
        assertThat(partial.code).isEqualTo("anamnesis_invalid")
        assertThat(partial.json.path("fields").toList().map { it.asString() }).containsExactly("screening")
        assertThat(api.put(me, "/api/v1/me/anamnesis", body() - "screening").status).isEqualTo(400)
    }

    @Test
    fun `a person with no anamnesis has an unknown eligibility`() {
        assertThat(statusOf(consenting())).isEqualTo(EligibilityStatus.UNKNOWN)
    }

    private fun statusOf(subject: String): EligibilityStatus {
        val id = superuser.queryForObject("select id from platform.app_user where sub = ?", UUID::class.java, subject)!!
        return eligibility.statusOf(id)
    }

    private fun auditCount(subject: String): Int =
        superuser.queryForObject(
            "select count(*) from platform.audit_event where action = 'eligibility.changed' and actor_user_id = (select id from platform.app_user where sub = ?)",
            Int::class.java, subject,
        )!!

    @Test
    fun `a person never reads another person's anamnesis (CA6)`() {
        val a = consenting()
        val b = consenting()
        api.put(a, "/api/v1/me/anamnesis", body("PEANUT"))

        assertThat(api.get(b, "/api/v1/me/anamnesis").status).isEqualTo(404)
        assertThat(api.get(b, "/api/v1/me/anamnesis/revisions").json.path("items").size()).isZero()
        assertThat(api.get(b, "/api/v1/me/anamnesis/revisions/1").status).isEqualTo(404)
    }

    @Test
    fun `revoking the health data consent closes the routes at the next call`() {
        val me = consenting()
        assertThat(api.put(me, "/api/v1/me/anamnesis", body()).status).isEqualTo(200)
        val version = api.get(me, "/api/v1/policies/HEALTH_DATA_CONSENT/current").json.path("versionId").asString()
        // health data consent is required, so it cannot be revoked by the optional route: a new version makes it pending instead
        assertThat(api.put(me, "/api/v1/me/consents/HEALTH_DATA_CONSENT", mapOf("granted" to false, "versionId" to version, "client" to "WEB")).code)
            .isEqualTo("consent_not_optional")
        transactions.asAdmin {
            val id = jdbc.queryForObject("select consent.create_draft('HEALTH_DATA_CONSENT', 'T', 'New.', ?)", UUID::class.java, actor)!!
            jdbc.queryForObject("select consent.publish_version(?, ?)", Int::class.javaObjectType, id, actor)
        }
        assertThat(api.get(me, "/api/v1/me/anamnesis").code).isEqualTo("consent_required")
    }
}
