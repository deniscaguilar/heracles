package com.heracles.platform.adapter.`in`.web

import com.heracles.platform.application.port.out.DeadLetters
import com.heracles.platform.application.port.out.TaskQueue
import com.heracles.platform.domain.QueueMessage
import com.heracles.platform.domain.ReceivedMessage
import com.heracles.support.OidcInitializer
import com.heracles.support.PostgresInitializer
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID

/** E1-S6.8: the administrative endpoint is ADMIN only and speaks the standard error format. The queues are doubles here. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReprocessDeadLetterEndpointIT(@Value("\${local.server.port}") private val port: Int) {
    @MockitoBean
    private lateinit var deadLetters: DeadLetters

    @MockitoBean
    private lateinit var queue: TaskQueue

    private val client = HttpClient.newHttpClient()
    private val superuser = JdbcTemplate(
        DriverManagerDataSource(
            PostgresInitializer.postgres.jdbcUrl, PostgresInitializer.postgres.username, PostgresInitializer.postgres.password,
        ),
    )

    private fun post(path: String, subject: String): HttpResponse<String> {
        val token = OidcInitializer.server.issueToken(
            OidcInitializer.ISSUER_ID, "client",
            DefaultOAuth2TokenCallback(OidcInitializer.ISSUER_ID, subject, "JWT", listOf(OidcInitializer.AUDIENCE), emptyMap(), 3600),
        ).serialize()
        val request = HttpRequest.newBuilder(URI("http://localhost:$port$path")).POST(HttpRequest.BodyPublishers.noBody())
            .header("Authorization", "Bearer $token").build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun userWithRole(role: String): String {
        val subject = "sub-${UUID.randomUUID()}"
        val token = OidcInitializer.server.issueToken(
            OidcInitializer.ISSUER_ID, "client",
            DefaultOAuth2TokenCallback(OidcInitializer.ISSUER_ID, subject, "JWT", listOf(OidcInitializer.AUDIENCE), emptyMap(), 3600),
        ).serialize()
        client.send(HttpRequest.newBuilder(URI("http://localhost:$port/api/v1/me")).header("Authorization", "Bearer $token").GET().build(), HttpResponse.BodyHandlers.ofString())
        superuser.update("update platform.app_user set role = ? where sub = ?", role, subject)
        return subject
    }

    @Test
    fun `an admin requeues a dead message and gets 202`() {
        val message = QueueMessage(UUID.randomUUID().toString(), "probe.note", emptyMap())
        given(deadLetters.find(message.id)).willReturn(ReceivedMessage(message, "r-1"))

        val response = post("/api/v1/admin/dead-letters/${message.id}/reprocess", userWithRole("ADMIN"))

        assertThat(response.statusCode()).isEqualTo(202)
        assertThat(response.body()).contains("\"id\":\"${message.id}\"").contains("\"status\":\"QUEUED\"")
        verify(queue).send(message)
        verify(deadLetters).remove("r-1")
    }

    @Test
    fun `an unknown message is 404 with the standard code`() {
        given(deadLetters.find("missing-one")).willReturn(null)
        val response = post("/api/v1/admin/dead-letters/missing-one/reprocess", userWithRole("ADMIN"))
        assertThat(response.statusCode()).isEqualTo(404)
        assertThat(response.body()).contains("\"code\":\"not_found\"")
    }

    @Test
    fun `a user who is not an admin gets 403 and nothing is touched`() {
        val response = post("/api/v1/admin/dead-letters/anything/reprocess", userWithRole("USER"))
        assertThat(response.statusCode()).isEqualTo(403)
        verify(deadLetters, never()).remove("r-1")
    }

    @Test
    fun `an id that is not an identifier is refused`() {
        val response = post("/api/v1/admin/dead-letters/${"a b".replace(" ", "%20")}/reprocess", userWithRole("ADMIN"))
        assertThat(response.statusCode()).isEqualTo(400)
        assertThat(response.body()).contains("\"code\":\"validation_failed\"")
    }
}
