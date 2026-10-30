package com.heracles.platform.adapter.`in`.web

import com.heracles.support.CapturedSpans
import com.heracles.support.OidcInitializer
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID

/** E1-S5.1 (CA10): every error is application/problem+json with a stable `code` and the `traceId` of its trace. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "management.tracing.export.otlp.enabled=false",
        "management.opentelemetry.tracing.export.schedule-delay=50ms",
    ],
)
class ProblemDetailsIT(
    @Value("\${local.server.port}") private val port: Int,
    @Autowired private val captured: CapturedSpans,
) {
    private val client = HttpClient.newHttpClient()
    private val json = ObjectMapper()
    private val sentinel = "valor-privado-7,2-mg"

    private fun token(subject: String) = OidcInitializer.server.issueToken(
        OidcInitializer.ISSUER_ID, "client",
        DefaultOAuth2TokenCallback(OidcInitializer.ISSUER_ID, subject, "JWT", listOf(OidcInitializer.AUDIENCE), emptyMap(), 3600),
    ).serialize()

    private fun send(method: String, path: String, body: String? = null, authenticated: Boolean = true): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://localhost:$port$path"))
            .method(method, body?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody())
        if (authenticated) builder.header("Authorization", "Bearer ${token("sub-${UUID.randomUUID()}")}")
        body?.let { builder.header("Content-Type", "application/json") }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun problem(response: HttpResponse<String>, status: Int, code: String): JsonNode {
        assertThat(response.statusCode()).isEqualTo(status)
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/problem+json")
        val body = json.readTree(response.body())
        assertThat(body.get("status").asInt()).isEqualTo(status)
        assertThat(body.get("code").asString()).isEqualTo(code)
        assertThat(body.get("traceId").asString()).matches("[0-9a-f]{32}")
        assertThat(response.body()).doesNotContain(sentinel).doesNotContain("boom")
        return body
    }

    @Test
    fun `a validation error has the code, the names of the fields and the traceId of its trace`() {
        val response = send("POST", "/api/v1/_validated", """{"name":"$sentinel"}""")
        val body = problem(response, 400, "validation_failed")

        val fields = body.get("fields")
        val names: List<String> = (0 until fields.size()).map { fields.get(it).asString() }
        assertThat(names).containsExactly("name")
        // The traceId is the one of the exported span of this very request.
        val traceId = body.get("traceId").asString()
        await().atMost(Duration.ofSeconds(10)).until { captured.spans.any { it.traceId == traceId } }
    }

    @Test
    fun `a parameter of the wrong type, a broken body and a missing body are 400 without echoing the value`() {
        problem(send("GET", "/api/v1/_number?n=$sentinel"), 400, "invalid_parameter")
        problem(send("POST", "/api/v1/_validated", """{"name":"$sentinel", broken"""), 400, "malformed_request")
        problem(send("POST", "/api/v1/_validated"), 400, "malformed_request")
    }

    @Test
    fun `unknown route, wrong method and an unexpected failure have their own stable codes`() {
        problem(send("GET", "/api/v1/nao-existe/$sentinel"), 404, "not_found")
        problem(send("DELETE", "/api/v1/me"), 405, "method_not_allowed")
        problem(send("GET", "/api/v1/_boom?x=$sentinel"), 500, "internal_error")
    }

    @Test
    fun `the 401 and 403 errors also carry code and traceId`() {
        problem(send("GET", "/api/v1/me", authenticated = false), 401, "unauthenticated")
        problem(send("GET", "/api/v1/admin/_probe"), 403, "forbidden")
    }
}
