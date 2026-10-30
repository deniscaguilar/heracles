package com.heracles.platform.config

import com.heracles.support.CapturedSpans
import com.heracles.support.OidcInitializer
import io.opentelemetry.sdk.trace.data.SpanData
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID

/**
 * E1-S4.5 (CA9): logs are JSON with trace, user and route; spans carry only an allowlist of attributes; sentinel
 * values (CPF, e-mail, health text) sent in every part of a request never show up in logs, spans or errors.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "management.tracing.export.otlp.enabled=false", // no network; CapturingSpanExporter stands in
        "management.opentelemetry.tracing.export.schedule-delay=50ms",
    ],
)
@ExtendWith(OutputCaptureExtension::class)
class ObservabilityIT(
    @Value("\${local.server.port}") private val port: Int,
    @Autowired private val captured: CapturedSpans,
) {
    private val spans get() = captured
    private val client = HttpClient.newHttpClient()
    private val json = ObjectMapper()

    private val cpf = "123.456.789-09"
    private val email = "sentinela.privada@example.com"
    private val health = "glicemia 7,2 mg/dL em jejum"
    private val sentinels = listOf(cpf, email, health, "7,2")

    private fun enc(value: String) = URLEncoder.encode(value, Charsets.UTF_8)

    private fun token(subject: String): String = OidcInitializer.server.issueToken(
        OidcInitializer.ISSUER_ID, "client",
        DefaultOAuth2TokenCallback(OidcInitializer.ISSUER_ID, subject, "JWT", listOf(OidcInitializer.AUDIENCE), emptyMap(), 3600),
    ).serialize()

    private fun send(method: String, path: String, subject: String?, body: String? = null, header: String? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://localhost:$port$path"))
            .method(method, body?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody())
        subject?.let { builder.header("Authorization", "Bearer ${token(it)}") }
        header?.let { builder.header("X-Note", it) }
        body?.let { builder.header("Content-Type", "application/json") }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun waitForSpans(count: Int) {
        await().atMost(Duration.ofSeconds(10)).until { spans.spans.size >= count }
    }

    @Test
    fun `a log line is JSON and carries the trace, the user and the route`(output: CapturedOutput) {
        val subject = "sub-${UUID.randomUUID()}"
        assertThat(send("GET", "/api/v1/_probe", subject).statusCode()).isEqualTo(200)

        val line = output.all.lines().first { it.contains("probe handled") }
        val log = json.readTree(line) // not JSON would throw
        val traceId = (log.get("trace.id") ?: log.get("traceId"))?.asString()
        assertThat(traceId).hasSize(32)
        assertThat(log.get("userId").asString()).matches("[0-9a-f-]{36}")
        assertThat(log.get("route").asString()).isEqualTo("GET /api/v1/_probe")
    }

    @Test
    fun `sentinel values in the query, path, body and headers never reach logs, spans or errors`(output: CapturedOutput) {
        val subject = "sub-${UUID.randomUUID()}"
        val before = spans.spans.size
        val responses = listOf(
            send("GET", "/api/v1/me?cpf=${enc(cpf)}&email=${enc(email)}", subject),
            send("GET", "/api/v1/admin/audit-events?cursor=${enc(health)}", subject),
            send("GET", "/api/v1/admin/audit-events?cursor=${enc(health)}", null),
            send("GET", "/api/v1/unknown/${enc(email)}", subject),
            send("GET", "/api/v1/_boom?note=${enc(health)}", subject),
            send("POST", "/api/v1/me", subject, body = """{"cpf":"$cpf","email":"$email","note":"$health"}"""),
            send("POST", "/api/v1/me", subject, body = """{"cpf":"$cpf", broken""", header = health),
            send("GET", "/api/v1/me", subject, header = "$cpf $email $health"),
        )
        assertThat(responses.map { it.statusCode() }).contains(200, 401, 403, 404, 500, 405)
        waitForSpans(before + 6)
        Thread.sleep(300) // let the batch finish

        val logs = output.all
        val exported = spans.spans.drop(before).joinToString("\n") { it.dump() }
        sentinels.forEach { sentinel ->
            assertThat(logs).describedAs("log has «$sentinel»").doesNotContain(sentinel)
            assertThat(exported).describedAs("spans have «$sentinel»").doesNotContain(sentinel)
            responses.forEach { assertThat(it.body()).describedAs("response has «$sentinel»").doesNotContain(sentinel) }
        }
    }

    @Test
    fun `spans carry only allowlisted attributes, no events and no error text`() {
        val before = spans.spans.size
        send("GET", "/api/v1/_probe", "sub-${UUID.randomUUID()}")
        send("GET", "/api/v1/_boom", "sub-${UUID.randomUUID()}")
        waitForSpans(before + 2)

        val exported = spans.spans.drop(before)
        assertThat(exported).isNotEmpty()
        exported.forEach { span ->
            span.attributes.forEach { key, _ ->
                assertThat(SpanAttributeAllowlist.KEYS).describedAs("attribute ${key.key} of span ${span.name}").contains(key.key)
            }
            assertThat(span.events).describedAs("events of ${span.name}").isEmpty()
            assertThat(span.status.description).describedAs("status text of ${span.name}").isEmpty()
        }
        // The user comes as a UUID.
        val withUser = exported.first { it.attributes.asMap().keys.any { k -> k.key == "user_id" } }
        assertThat(withUser.attributes.asMap().entries.first { it.key.key == "user_id" }.value.toString()).matches("[0-9a-f-]{36}")
    }

    private fun SpanData.dump(): String =
        "$name ${attributes.asMap()} ${events.map { it.name + it.attributes.asMap() }} ${status.statusCode} ${status.description}"
}
