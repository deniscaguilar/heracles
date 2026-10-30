package com.heracles.platform.adapter.`in`.web

import com.heracles.support.OidcInitializer
import com.heracles.support.PostgresInitializer
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID

/** E1-S6.7 (CA15): the owner sees the status of their task and nothing else; anyone else gets 404. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TaskStatusEndpointIT(@Value("\${local.server.port}") private val port: Int) {
    private val client = HttpClient.newHttpClient()
    private val json = ObjectMapper()
    private val superuser = JdbcTemplate(
        DriverManagerDataSource(
            PostgresInitializer.postgres.jdbcUrl, PostgresInitializer.postgres.username, PostgresInitializer.postgres.password,
        ),
    )

    private fun token(subject: String) = OidcInitializer.server.issueToken(
        OidcInitializer.ISSUER_ID, "client",
        DefaultOAuth2TokenCallback(OidcInitializer.ISSUER_ID, subject, "JWT", listOf(OidcInitializer.AUDIENCE), emptyMap(), 3600),
    ).serialize()

    private fun get(path: String, subject: String?): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI("http://localhost:$port$path")).GET()
        subject?.let { request.header("Authorization", "Bearer ${token(it)}") }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString())
    }

    /** Provisions the user through /me and returns their platform id. */
    private fun userId(subject: String): UUID = UUID.fromString(json.readTree(get("/api/v1/me", subject).body()).get("id").asString())

    private fun task(owner: UUID?, status: String = "PENDING", resultRef: String? = null): UUID = superuser.queryForObject(
        "insert into platform.task (user_id, type, status, result_ref, last_error_code) values (?, 'probe.task', ?, ?, 'IllegalStateException') returning id",
        UUID::class.java, owner, status, resultRef,
    )!!

    @Test
    fun `the owner sees the status and only the status`() {
        val subject = "sub-${UUID.randomUUID()}"
        val id = task(userId(subject), status = "DONE", resultRef = "ref-1")

        val response = get("/api/v1/tasks/$id", subject)

        assertThat(response.statusCode()).isEqualTo(200)
        val body = json.readTree(response.body())
        assertThat(body.propertyNames()).containsExactlyInAnyOrder("id", "status", "resultRef")
        assertThat(body.get("id").asString()).isEqualTo(id.toString())
        assertThat(body.get("status").asString()).isEqualTo("DONE")
        assertThat(body.get("resultRef").asString()).isEqualTo("ref-1")
        assertThat(response.body()).doesNotContain("IllegalStateException").doesNotContain("probe.task")
    }

    @Test
    fun `a task still waiting shows PENDING and no result`() {
        val subject = "sub-${UUID.randomUUID()}"
        val body = json.readTree(get("/api/v1/tasks/${task(userId(subject))}", subject).body())
        assertThat(body.get("status").asString()).isEqualTo("PENDING")
        assertThat(body.get("resultRef").isNull).isTrue()
    }

    @Test
    fun `another user, a system task and an unknown id all look the same, a 404`() {
        val owner = "sub-${UUID.randomUUID()}"
        val other = "sub-${UUID.randomUUID()}"
        val ownerId = userId(owner)
        userId(other)
        val mine = task(ownerId)
        val ofSystem = task(null)

        listOf(mine to other, ofSystem to owner, UUID.randomUUID() to owner).forEach { (id, subject) ->
            val response = get("/api/v1/tasks/$id", subject)
            assertThat(response.statusCode()).describedAs("task $id as $subject").isEqualTo(404)
            assertThat(response.body()).contains("\"code\":\"not_found\"")
        }
    }

    @Test
    fun `without a token it is 401 and a malformed id is 400`() {
        assertThat(get("/api/v1/tasks/${UUID.randomUUID()}", null).statusCode()).isEqualTo(401)
        val response = get("/api/v1/tasks/not-a-uuid", "sub-${UUID.randomUUID()}")
        assertThat(response.statusCode()).isEqualTo(400)
        assertThat(response.body()).contains("\"code\":\"invalid_parameter\"").doesNotContain("not-a-uuid")
    }
}
