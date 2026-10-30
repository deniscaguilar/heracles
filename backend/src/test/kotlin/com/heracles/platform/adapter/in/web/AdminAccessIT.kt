package com.heracles.platform.adapter.`in`.web

import com.heracles.platform.application.port.out.TransactionContext
import com.heracles.platform.domain.AccessContext
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
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID

/** E1-S4.3: a USER on an ADMIN route gets 403 and leaves a DENIED event. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminAccessIT(
    @Value("\${local.server.port}") private val port: Int,
    @Autowired private val transactionContext: TransactionContext,
    @Autowired private val jdbc: JdbcTemplate,
) {
    private val client = HttpClient.newHttpClient()
    private val superuser = JdbcTemplate(
        DriverManagerDataSource(
            PostgresInitializer.postgres.jdbcUrl, PostgresInitializer.postgres.username, PostgresInitializer.postgres.password,
        ),
    )

    private fun call(subject: String?, path: String = "/api/v1/admin/_probe"): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI("http://localhost:$port$path")).GET()
        subject?.let {
            val token = OidcInitializer.server.issueToken(
                OidcInitializer.ISSUER_ID, "client",
                DefaultOAuth2TokenCallback(OidcInitializer.ISSUER_ID, it, "JWT", listOf(OidcInitializer.AUDIENCE), emptyMap(), 3600),
            ).serialize()
            request.header("Authorization", "Bearer $token")
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun userWithRole(role: String): String {
        val subject = "sub-${UUID.randomUUID()}"
        call(subject, "/api/v1/me") // provisions the user as USER
        superuser.update("update platform.app_user set role = ? where sub = ?", role, subject)
        return subject
    }

    private fun idOf(subject: String): UUID =
        superuser.queryForObject("select id from platform.app_user where sub = ?", UUID::class.java, subject)!!

    private fun deniedEventsOf(userId: UUID): List<Map<String, Any?>> = transactionContext.run(AccessContext.admin()) {
        jdbc.queryForList("select action, result, actor_scope, fields::text as fields from audit_event where actor_user_id = ?", userId)
    }

    @Test
    fun `a USER on an admin route gets 403 in the standard format and a DENIED event`() {
        val subject = userWithRole("USER")
        val response = call(subject)

        assertThat(response.statusCode()).isEqualTo(403)
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/problem+json")
        assertThat(response.body()).contains("\"status\":403").contains("\"code\":\"forbidden\"")
        assertThat(deniedEventsOf(idOf(subject))).containsExactly(
            mapOf("action" to "admin.access", "result" to "DENIED", "actor_scope" to "user", "fields" to "{}"),
        )
    }

    @Test
    fun `a MODERATOR is also refused on an admin only route`() {
        val subject = userWithRole("MODERATOR")
        assertThat(call(subject).statusCode()).isEqualTo(403)
        assertThat(deniedEventsOf(idOf(subject))).hasSize(1)
    }

    @Test
    fun `an ADMIN reaches the route and no denial is recorded`() {
        val subject = userWithRole("ADMIN")
        assertThat(call(subject).statusCode()).isEqualTo(200)
        assertThat(deniedEventsOf(idOf(subject))).isEmpty()
    }

    @Test
    fun `without a token it is 401, not 403`() {
        assertThat(call(null).statusCode()).isEqualTo(401)
    }
}
