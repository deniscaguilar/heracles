package com.heracles.platform.adapter.`in`.web

import com.heracles.support.OidcInitializer
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/** E1-S3.1 (CA4): the resource server validates signature, issuer, audience and expiry of the JWT. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthenticationIT(@Value("\${local.server.port}") private val port: Int) {
    private val client = HttpClient.newHttpClient()
    private val route = "/api/v1/_probe"

    private fun get(authorization: String?): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI("http://localhost:$port$route")).GET()
        authorization?.let { request.header("Authorization", it) }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun token(
        server: MockOAuth2Server = OidcInitializer.server,
        issuerId: String = OidcInitializer.ISSUER_ID,
        subject: String = "user-sub-1",
        audience: String = OidcInitializer.AUDIENCE,
        expirySeconds: Long = 3600,
    ): String = server.issueToken(
        issuerId, "client", DefaultOAuth2TokenCallback(issuerId, subject, "JWT", listOf(audience), emptyMap(), expirySeconds),
    ).serialize()

    private fun assertStandard401(response: HttpResponse<String>) {
        assertThat(response.statusCode()).isEqualTo(401)
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/problem+json")
        assertThat(response.headers().firstValue("WWW-Authenticate").orElse("")).startsWith("Bearer")
        assertThat(response.body()).contains("\"status\":401").contains("\"code\":\"unauthenticated\"")
        // The reason of the failure is never revealed to the caller.
        assertThat(response.body()).doesNotContain("expired", "signature", "issuer", "audience")
    }

    @Test
    fun `valid token reaches the route with the subject`() {
        val response = get("Bearer ${token(subject = "abc-123")}")
        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(response.body()).contains("\"subject\":\"abc-123\"")
    }

    @Test
    fun `no token is 401`() = assertStandard401(get(null))

    @Test
    fun `expired token is 401`() = assertStandard401(get("Bearer ${token(expirySeconds = -60)}"))

    @Test
    fun `token signed with another key is 401`() =
        assertStandard401(get("Bearer ${token(server = OidcInitializer.forger)}"))

    @Test
    fun `token from another issuer is 401`() = assertStandard401(get("Bearer ${token(issuerId = "other")}"))

    @Test
    fun `token for another audience is 401`() = assertStandard401(get("Bearer ${token(audience = "someone-else")}"))

    @Test
    fun `malformed token and other schemes are 401`() {
        assertStandard401(get("Bearer not-a-jwt"))
        assertStandard401(get("Basic dXNlcjpwYXNz"))
        assertStandard401(get("Bearer "))
    }

    @Test
    fun `health stays public`() {
        val response = client.send(
            HttpRequest.newBuilder(URI("http://localhost:$port/actuator/health/liveness")).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        assertThat(response.statusCode()).isEqualTo(200)
    }
}
