package com.heracles.platform.adapter.`in`.web

import com.heracles.platform.application.RecordAudit
import com.heracles.platform.application.port.out.TransactionContext
import com.heracles.platform.domain.AccessContext
import com.heracles.platform.domain.AccessScope
import com.heracles.platform.domain.AuditEvent
import com.heracles.platform.domain.AuditResult
import com.heracles.support.OidcInitializer
import com.heracles.support.PostgresInitializer
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID

/** E1-S4.4: only ADMIN reads the trail, newest first, with a cursor; the answer has names and identifiers only. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuditEventsEndpointIT(
    @Value("\${local.server.port}") private val port: Int,
    @Autowired private val recordAudit: RecordAudit,
    @Autowired private val transactionContext: TransactionContext,
) {
    private val client = HttpClient.newHttpClient()
    private val json = ObjectMapper()
    private val superuser = JdbcTemplate(
        DriverManagerDataSource(
            PostgresInitializer.postgres.jdbcUrl, PostgresInitializer.postgres.username, PostgresInitializer.postgres.password,
        ),
    )

    private fun get(subject: String, query: String = ""): HttpResponse<String> {
        val token = OidcInitializer.server.issueToken(
            OidcInitializer.ISSUER_ID, "client",
            DefaultOAuth2TokenCallback(OidcInitializer.ISSUER_ID, subject, "JWT", listOf(OidcInitializer.AUDIENCE), emptyMap(), 3600),
        ).serialize()
        val request = HttpRequest.newBuilder(URI("http://localhost:$port/api/v1/admin/audit-events$query"))
            .header("Authorization", "Bearer $token").GET().build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun userWithRole(role: String): String {
        val subject = "sub-${UUID.randomUUID()}"
        val token = OidcInitializer.server.issueToken(
            OidcInitializer.ISSUER_ID, "client",
            DefaultOAuth2TokenCallback(OidcInitializer.ISSUER_ID, subject, "JWT", listOf(OidcInitializer.AUDIENCE), emptyMap(), 3600),
        ).serialize()
        client.send(
            HttpRequest.newBuilder(URI("http://localhost:$port/api/v1/me")).header("Authorization", "Bearer $token").GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        superuser.update("update platform.app_user set role = ? where sub = ?", role, subject)
        return subject
    }

    private fun record(action: String) = transactionContext.run(AccessContext.system()) {
        recordAudit(
            AuditEvent(null, AccessScope.SYSTEM, action, "route", "r-1", AuditResult.SUCCESS, listOf("name", "unit"), "t-1"),
        )
    }

    @Test
    fun `an admin pages through the trail newest first with a cursor`() {
        val tag = UUID.randomUUID().toString().replace('-', '_')
        (1..5).forEach { record("page.${tag}_$it") }
        val admin = userWithRole("ADMIN")

        val seen = mutableListOf<JsonNode>()
        var cursor: String? = null
        var pages = 0
        do {
            val response = get(admin, "?limit=2" + (cursor?.let { "&cursor=$it" } ?: ""))
            assertThat(response.statusCode()).isEqualTo(200)
            val body = json.readTree(response.body())
            assertThat(body.get("items").size()).isLessThanOrEqualTo(2)
            seen.addAll(body.get("items"))
            cursor = body.get("nextCursor").takeUnless { it.isNull }?.asString()
            pages++
        } while (cursor != null && pages < 200)

        val ours = seen.filter { it.get("action").asString().startsWith("page.$tag") }
        assertThat(ours.map { it.get("action").asString() }).containsExactly(
            "page.${tag}_5", "page.${tag}_4", "page.${tag}_3", "page.${tag}_2", "page.${tag}_1",
        )
        assertThat(seen.map { it.get("id").asString() }).doesNotHaveDuplicates()
    }

    @Test
    fun `the answer carries only names and identifiers`() {
        record("shape.${UUID.randomUUID().toString().replace('-', '_')}")
        val body = json.readTree(get(userWithRole("ADMIN"), "?limit=1").body())
        val item = body.get("items").get(0)

        assertThat(item.propertyNames()).containsExactlyInAnyOrder(
            "id", "occurredAt", "actorUserId", "actorScope", "action", "entityType", "entityId", "result", "fields", "traceId",
        )
        val fields = item.get("fields")
        val fieldNames: List<String> = (0 until fields.size()).map { fields.get(it).asString() }
        assertThat(fieldNames).containsExactly("name", "unit")
    }

    @Test
    fun `a non admin gets 403 and a bad cursor gets 400`() {
        assertThat(get(userWithRole("USER")).statusCode()).isEqualTo(403)
        val response = get(userWithRole("ADMIN"), "?cursor=not-a-cursor")
        assertThat(response.statusCode()).isEqualTo(400)
        assertThat(response.body()).contains("\"code\":\"invalid_cursor\"").doesNotContain("not-a-cursor")
    }
}
