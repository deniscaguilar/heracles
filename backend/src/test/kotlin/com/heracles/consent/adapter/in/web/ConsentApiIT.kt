package com.heracles.consent.adapter.`in`.web

import com.heracles.consent.api.ConsentType
import com.heracles.platform.api.Transactions
import com.heracles.support.ApiTestClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID

/** E2-S3.3 and E2-S3.4 (CA1, CA3, CA4): the client API of consent and the barrier before health data. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConsentApiIT(
    @Value("\${local.server.port}") port: Int,
    @Autowired private val transactions: Transactions,
    @Autowired private val jdbc: JdbcTemplate,
) {
    private val api = ApiTestClient(port)
    private val actor = UUID.randomUUID()

    private fun publish(type: ConsentType, markdown: String = "Plain **text**."): UUID = transactions.asAdmin {
        // Another test class may have left a draft of this type open; there is one draft per type.
        jdbc.queryForList("select id from consent.policy_version where type = ? and published_at is null", UUID::class.java, type.name)
            .forEach { jdbc.queryForObject("select consent.discard_draft(?)", Boolean::class.javaObjectType, it) }
        val id = jdbc.queryForObject("select consent.create_draft(?, 'Title', ?, ?)", UUID::class.java, type.name, markdown, actor)!!
        jdbc.queryForObject("select consent.publish_version(?, ?)", Int::class.javaObjectType, id, actor)
        id
    }

    private lateinit var required: Map<ConsentType, UUID>

    @BeforeEach
    fun publishRequiredTexts() {
        required = ConsentType.entries.filter { it.required }.associateWith { publish(it) }
    }

    private fun acceptAll(subject: String, ids: Collection<UUID> = required.values) =
        api.post(subject, "/api/v1/me/required-consents", mapOf("versionIds" to ids, "client" to "WEB"))

    @Test
    fun `without a token every consent route is 401`() {
        assertThat(api.get(null, "/api/v1/me/consents").status).isEqualTo(401)
        assertThat(api.get(null, "/api/v1/policies/TERMS_OF_USE/current").status).isEqualTo(401)
        assertThat(api.post(null, "/api/v1/me/required-consents", mapOf("versionIds" to listOf(UUID.randomUUID()), "client" to "WEB")).status).isEqualTo(401)
    }

    @Test
    fun `a new person has the three required consents pending and the four optional ones off (CA4)`() {
        val me = api.newSubject()
        val response = api.get(me, "/api/v1/me/consents")
        assertThat(response.status).isEqualTo(200)
        val items = response.json.path("items")
        assertThat(items.size()).isEqualTo(7)
        val byType = items.associateBy { it.path("type").asString() }
        ConsentType.entries.forEach { type ->
            assertThat(byType.getValue(type.name).path("required").asBoolean()).isEqualTo(type.required)
            assertThat(byType.getValue(type.name).path("granted").asBoolean()).isFalse()
            assertThat(byType.getValue(type.name).path("pending").asBoolean()).isEqualTo(type.required)
        }
        assertThat(byType.getValue("TERMS_OF_USE").path("currentVersionId").asString()).isEqualTo(required.getValue(ConsentType.TERMS_OF_USE).toString())
    }

    @Test
    fun `the current policy comes as blocks to draw, not as markup`() {
        val me = api.newSubject()
        val response = api.get(me, "/api/v1/policies/PRIVACY_POLICY/current")
        assertThat(response.status).isEqualTo(200)
        assertThat(response.json.path("versionId").asString()).isEqualTo(required.getValue(ConsentType.PRIVACY_POLICY).toString())
        val paragraph = response.json.path("blocks").get(0)
        assertThat(paragraph.path("type").asString()).isEqualTo("paragraph")
        assertThat(paragraph.path("children").get(1).path("type").asString()).isEqualTo("strong")
        assertThat(response.body).doesNotContain("<").doesNotContain("markdown")
        assertThat(api.get(me, "/api/v1/policies/NOPE/current").status).isEqualTo(400)
    }

    @Test
    fun `accepting the required consents with the versions seen works, and an old version is 409 (CA3)`() {
        val me = api.newSubject()
        val old = required.toMap()
        publish(ConsentType.TERMS_OF_USE)

        val stale = acceptAll(me, old.values)
        assertThat(stale.status).isEqualTo(409)
        assertThat(stale.code).isEqualTo("policy_version_outdated")
        assertThat(api.get(me, "/api/v1/me/consents").json.path("items").count { it.path("granted").asBoolean() }).isZero()

        val current = ConsentType.entries.filter { it.required }.associateWith { type ->
            api.get(me, "/api/v1/policies/${type.name}/current").json.path("versionId").asString().let(UUID::fromString)
        }
        assertThat(acceptAll(me, current.values).status).isEqualTo(204)
        val after = api.get(me, "/api/v1/me/consents").json.path("items")
        assertThat(after.filter { it.path("required").asBoolean() }.all { it.path("granted").asBoolean() && !it.path("pending").asBoolean() }).isTrue()
    }

    @Test
    fun `a new required version makes the person pending again and the old acceptance stays linked to the old version (CA3)`() {
        val me = api.newSubject()
        assertThat(acceptAll(me).status).isEqualTo(204)
        val newTerms = publish(ConsentType.TERMS_OF_USE, "Changed text.")

        val items = api.get(me, "/api/v1/me/consents").json.path("items").associateBy { it.path("type").asString() }
        val terms = items.getValue("TERMS_OF_USE")
        assertThat(terms.path("pending").asBoolean()).isTrue()
        assertThat(terms.path("acceptedVersionId").asString()).isEqualTo(required.getValue(ConsentType.TERMS_OF_USE).toString())
        assertThat(terms.path("currentVersionId").asString()).isEqualTo(newTerms.toString())
        assertThat(items.getValue("PRIVACY_POLICY").path("pending").asBoolean()).isFalse()
    }

    @Test
    fun `optional consents turn on and off with effect on the next call (CA4)`() {
        val me = api.newSubject()
        val ai = publish(ConsentType.AI_EXAMS_CONSENT)
        fun granted() = api.get(me, "/api/v1/me/consents").json.path("items").first { it.path("type").asString() == "AI_EXAMS_CONSENT" }.path("granted").asBoolean()

        assertThat(granted()).isFalse()
        assertThat(api.put(me, "/api/v1/me/consents/AI_EXAMS_CONSENT", mapOf("granted" to true, "versionId" to ai, "client" to "MOBILE")).status).isEqualTo(204)
        assertThat(granted()).isTrue()
        assertThat(api.put(me, "/api/v1/me/consents/AI_EXAMS_CONSENT", mapOf("granted" to false, "versionId" to ai, "client" to "MOBILE")).status).isEqualTo(204)
        assertThat(granted()).isFalse()
    }

    @Test
    fun `optional consent routes refuse a required type, an old version and a bad body`() {
        val me = api.newSubject()
        val promo = publish(ConsentType.PROMOTIONAL_CONSENT)
        val required = api.put(me, "/api/v1/me/consents/TERMS_OF_USE", mapOf("granted" to true, "versionId" to promo, "client" to "WEB"))
        assertThat(required.status).isEqualTo(400)
        assertThat(required.code).isEqualTo("consent_not_optional")

        publish(ConsentType.PROMOTIONAL_CONSENT)
        val stale = api.put(me, "/api/v1/me/consents/PROMOTIONAL_CONSENT", mapOf("granted" to true, "versionId" to promo, "client" to "WEB"))
        assertThat(stale.status).isEqualTo(409)
        assertThat(stale.code).isEqualTo("policy_version_outdated")

        val bad = api.put(me, "/api/v1/me/consents/PROMOTIONAL_CONSENT", mapOf("granted" to true))
        assertThat(bad.status).isEqualTo(400)
        assertThat(bad.code).isEqualTo("validation_failed")
    }

    @Test
    fun `the barrier answers 403 consent_required before the route runs, and opens after the acceptance (CA1)`() {
        val me = api.newSubject()
        val before = api.get(me, "/api/v1/_health_probe")
        assertThat(before.status).isEqualTo(403)
        assertThat(before.code).isEqualTo("consent_required")
        assertThat(before.body).doesNotContain("reached")

        assertThat(acceptAll(me).status).isEqualTo(204)
        val after = api.get(me, "/api/v1/_health_probe")
        assertThat(after.status).isEqualTo(200)
        assertThat(after.body).contains("reached")
    }

    @Test
    fun `a new health data text closes the barrier until the person accepts it again (CA1, CA3)`() {
        val me = api.newSubject()
        assertThat(acceptAll(me).status).isEqualTo(204)
        assertThat(api.get(me, "/api/v1/_health_probe").status).isEqualTo(200)

        val newHealth = publish(ConsentType.HEALTH_DATA_CONSENT, "Changed.")
        assertThat(api.get(me, "/api/v1/_health_probe").status).isEqualTo(403)

        val ids = listOf(required.getValue(ConsentType.TERMS_OF_USE), required.getValue(ConsentType.PRIVACY_POLICY), newHealth)
        assertThat(acceptAll(me, ids).status).isEqualTo(204)
        assertThat(api.get(me, "/api/v1/_health_probe").status).isEqualTo(200)
    }

    @Test
    fun `an anonymous call to a health route is 401, never reaching the barrier`() {
        assertThat(api.get(null, "/api/v1/_health_probe").status).isEqualTo(401)
    }
}
