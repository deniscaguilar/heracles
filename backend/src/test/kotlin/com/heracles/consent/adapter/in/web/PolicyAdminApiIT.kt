package com.heracles.consent.adapter.`in`.web

import com.heracles.consent.api.ConsentType
import com.heracles.support.ApiTestClient
import com.heracles.support.PostgresInitializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.util.UUID

/** E2-S4.2 and E2-S4.3 (CA7, CA8): the backoffice of the policies, ADMIN only, and what it leaves in the audit trail. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PolicyAdminApiIT(@Value("\${local.server.port}") port: Int) {
    private val api = ApiTestClient(port)
    private val superuser = JdbcTemplate(
        DriverManagerDataSource(
            PostgresInitializer.postgres.jdbcUrl, PostgresInitializer.postgres.username, PostgresInitializer.postgres.password,
        ),
    )

    private fun person(role: String): String {
        val subject = api.newSubject()
        api.get(subject, "/api/v1/me")
        superuser.update("update platform.app_user set role = ? where sub = ?", role, subject)
        return subject
    }

    private fun draft(admin: String, type: ConsentType, markdown: String = "Some **text**.", title: String = "Title") =
        api.post(admin, "/api/v1/admin/policies/${type.name}/draft", mapOf("title" to title, "markdown" to markdown))

    private fun idOf(response: ApiTestClient.Response) = UUID.fromString(response.json.path("id").asString())

    private fun discardAny(admin: String, type: ConsentType) {
        api.get(admin, "/api/v1/admin/policies").json.path("items")
            .filter { it.path("type").asString() == type.name && it.path("status").asString() == "DRAFT" }
            .forEach { api.delete(admin, "/api/v1/admin/policy-drafts/${it.path("id").asString()}") }
    }

    @Test
    fun `only an ADMIN reaches the backoffice of the policies (CA7)`() {
        val user = person("USER")
        val moderator = person("MODERATOR")
        val id = UUID.randomUUID()
        val calls = listOf<(String?) -> ApiTestClient.Response>(
            { api.get(it, "/api/v1/admin/policies") },
            { api.post(it, "/api/v1/admin/policies/TERMS_OF_USE/draft", mapOf("title" to "T", "markdown" to "x")) },
            { api.put(it, "/api/v1/admin/policy-drafts/$id", mapOf("title" to "T", "markdown" to "x")) },
            { api.delete(it, "/api/v1/admin/policy-drafts/$id") },
            { api.post(it, "/api/v1/admin/policy-drafts/$id/publish") },
            { api.get(it, "/api/v1/admin/policy-versions/$id/preview") },
        )
        calls.forEach { call ->
            assertThat(call(null).status).isEqualTo(401)
            assertThat(call(user).status).isEqualTo(403)
            assertThat(call(moderator).status).isEqualTo(403)
        }
    }

    @Test
    fun `an admin writes a draft, previews it, publishes it, and it becomes the current text (CA7)`() {
        val admin = person("ADMIN")
        discardAny(admin, ConsentType.COMMUNITY_CONSENT)
        val created = draft(admin, ConsentType.COMMUNITY_CONSENT, "Share **with care**.\n\n- one\n- two", "Community")
        assertThat(created.status).isEqualTo(201)
        val id = idOf(created)

        val preview = api.get(admin, "/api/v1/admin/policy-versions/$id/preview")
        assertThat(preview.status).isEqualTo(200)
        assertThat(preview.json.path("summary").path("status").asString()).isEqualTo("DRAFT")
        assertThat(preview.json.path("markdown").asString()).startsWith("Share")
        assertThat(preview.json.path("blocks").size()).isEqualTo(2)

        assertThat(api.put(admin, "/api/v1/admin/policy-drafts/$id", mapOf("title" to "Community 2", "markdown" to "Edited.")).status).isEqualTo(204)
        val published = api.post(admin, "/api/v1/admin/policy-drafts/$id/publish")
        assertThat(published.status).isEqualTo(200)
        val number = published.json.path("version").asInt()
        assertThat(number).isGreaterThan(0)

        val current = api.get(api.newSubject(), "/api/v1/policies/COMMUNITY_CONSENT/current")
        assertThat(current.json.path("versionId").asString()).isEqualTo(id.toString())
        assertThat(current.json.path("version").asInt()).isEqualTo(number)
        assertThat(current.json.path("title").asString()).isEqualTo("Community 2")

        // Published is final: it cannot be edited, discarded or published again.
        assertThat(api.put(admin, "/api/v1/admin/policy-drafts/$id", mapOf("title" to "x", "markdown" to "y")).status).isEqualTo(404)
        assertThat(api.delete(admin, "/api/v1/admin/policy-drafts/$id").status).isEqualTo(404)
        assertThat(api.post(admin, "/api/v1/admin/policy-drafts/$id/publish").status).isEqualTo(404)
        assertThat(api.get(admin, "/api/v1/admin/policy-versions/$id/preview").json.path("summary").path("status").asString()).isEqualTo("PUBLISHED")
    }

    @Test
    fun `a second draft of the same type is 409, and a discarded draft frees the slot`() {
        val admin = person("ADMIN")
        discardAny(admin, ConsentType.NOTIFICATIONS_CONSENT)
        val first = draft(admin, ConsentType.NOTIFICATIONS_CONSENT)
        assertThat(first.status).isEqualTo(201)
        val second = draft(admin, ConsentType.NOTIFICATIONS_CONSENT)
        assertThat(second.status).isEqualTo(409)
        assertThat(second.code).isEqualTo("draft_already_exists")

        assertThat(api.delete(admin, "/api/v1/admin/policy-drafts/${idOf(first)}").status).isEqualTo(204)
        assertThat(draft(admin, ConsentType.NOTIFICATIONS_CONSENT).status).isEqualTo(201)
        discardAny(admin, ConsentType.NOTIFICATIONS_CONSENT)
    }

    @Test
    fun `text outside the restricted format is refused with the field name and no echo (CA7)`() {
        val admin = person("ADMIN")
        discardAny(admin, ConsentType.PROMOTIONAL_CONSENT)
        listOf(
            "<script>alert('leak-me')</script>",
            "Click [here](javascript:alert('leak-me'))",
            "![leak-me](https://example.org/x.png)",
            "<b>leak-me</b>",
        ).forEach { markdown ->
            val response = draft(admin, ConsentType.PROMOTIONAL_CONSENT, markdown)
            assertThat(response.status).describedAs(markdown).isEqualTo(400)
            assertThat(response.code).isEqualTo("policy_text_invalid")
            assertThat(response.body).doesNotContain("leak-me")
            assertThat(response.json.path("fields").get(0).asString()).isEqualTo("markdown")
        }
        assertThat(api.get(admin, "/api/v1/admin/policies").json.path("items").none { it.path("type").asString() == "PROMOTIONAL_CONSENT" && it.path("status").asString() == "DRAFT" }).isTrue()
    }

    @Test
    fun `a blank title or text is a validation error`() {
        val admin = person("ADMIN")
        discardAny(admin, ConsentType.AI_EXAMS_CONSENT)
        val response = api.post(admin, "/api/v1/admin/policies/AI_EXAMS_CONSENT/draft", mapOf("title" to "", "markdown" to " "))
        assertThat(response.status).isEqualTo(400)
        assertThat(response.code).isEqualTo("validation_failed")
    }

    @Test
    fun `the list shows how many people hold each version, with no identity (CA7)`() {
        val admin = person("ADMIN")
        val required = ConsentType.entries.filter { it.required }.associateWith { type ->
            discardAny(admin, type)
            val id = idOf(draft(admin, type))
            api.post(admin, "/api/v1/admin/policy-drafts/$id/publish")
            id
        }
        repeat(3) {
            val me = api.newSubject()
            api.post(me, "/api/v1/me/required-consents", mapOf("versionIds" to required.values, "client" to "WEB"))
        }
        val items = api.get(admin, "/api/v1/admin/policies").json.path("items")
        val terms = items.first { it.path("id").asString() == required.getValue(ConsentType.TERMS_OF_USE).toString() }
        assertThat(terms.path("accepted").asLong()).isEqualTo(3L)
        assertThat(terms.path("status").asString()).isEqualTo("PUBLISHED")
        assertThat(terms.propertyNames().toList()).containsExactlyInAnyOrder("id", "type", "version", "title", "status", "createdAt", "publishedAt", "accepted")
    }

    @Test
    fun `publishing and drafting leave an audit line that never carries the text (CA8)`() {
        val admin = person("ADMIN")
        discardAny(admin, ConsentType.COMMUNITY_CONSENT)
        val secretText = "audit-must-not-carry-${UUID.randomUUID()}"
        val id = idOf(draft(admin, ConsentType.COMMUNITY_CONSENT, secretText, "Title $secretText".take(60)))
        api.post(admin, "/api/v1/admin/policy-drafts/$id/publish")

        val audit = api.get(admin, "/api/v1/admin/audit-events?limit=100")
        val mine = audit.json.path("items").filter { it.path("entityId").asString() == id.toString() }
        assertThat(mine.map { it.path("action").asString() }).contains("policy.draft_created", "policy.published")
        assertThat(mine.all { it.path("result").asString() == "SUCCESS" }).isTrue()
        assertThat(audit.body).doesNotContain(secretText)
    }
}
