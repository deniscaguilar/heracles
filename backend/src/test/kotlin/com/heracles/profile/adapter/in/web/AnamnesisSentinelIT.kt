package com.heracles.profile.adapter.`in`.web

import com.heracles.consent.api.ConsentType
import com.heracles.platform.api.Transactions
import com.heracles.support.ApiTestClient
import com.heracles.support.CapturedSpans
import com.heracles.support.PostgresInitializer
import io.micrometer.core.instrument.MeterRegistry
import io.opentelemetry.sdk.trace.data.SpanData
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.time.Duration
import java.util.UUID

/**
 * E2-S6.4 (CA9): health text typed in the anamnesis (an allergy, an injury note, a disliked food) never shows up in a
 * log, a span, a metric, an outbox or task message, an audit line, or an error body, on success or on error.
 * The last test proves the check itself fails when a value does leak.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "management.tracing.export.otlp.enabled=false", // no network; CapturingSpanExporter stands in
        "management.opentelemetry.tracing.export.schedule-delay=50ms",
    ],
)
@ExtendWith(OutputCaptureExtension::class)
class AnamnesisSentinelIT(
    @Value("\${local.server.port}") port: Int,
    @Autowired private val transactions: Transactions,
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val captured: CapturedSpans,
    @Autowired private val meters: MeterRegistry,
) {
    private val api = ApiTestClient(port)
    private val actor = UUID.randomUUID()
    private val superuser = JdbcTemplate(
        DriverManagerDataSource(PostgresInitializer.postgres.jdbcUrl, PostgresInitializer.postgres.username, PostgresInitializer.postgres.password),
    )

    private val allergy = "SENTINELA-ALERGIA-kiwi"
    private val injury = "SENTINELA-LESAO-joelho-direito"
    private val food = "SENTINELA-ALIMENTO-beterraba"
    private val sentinels = listOf(allergy, injury, food, "SENTINELA")

    private fun consenting(): String = api.newSubject().also { me ->
        ConsentType.entries.filter { it.required }.forEach { type ->
            transactions.asAdmin {
                jdbc.queryForList("select id from consent.policy_version where type = ? and published_at is null", UUID::class.java, type.name)
                    .forEach { jdbc.queryForObject("select consent.discard_draft(?)", Boolean::class.javaObjectType, it) }
                val id = jdbc.queryForObject("select consent.create_draft(?, 'Title', 'Text.', ?)", UUID::class.java, type.name, actor)!!
                jdbc.queryForObject("select consent.publish_version(?, ?)", Int::class.javaObjectType, id, actor)
            }
        }
        val ids = ConsentType.entries.filter { it.required }.map { api.get(me, "/api/v1/policies/${it.name}/current").json.path("versionId").asString() }
        api.post(me, "/api/v1/me/required-consents", mapOf("versionIds" to ids, "client" to "WEB"))
    }

    private fun body(birthDate: String = "1990-05-01", catalogCode: String? = "PEANUT", other: String? = allergy) = mapOf(
        "objective" to "GENERAL_HEALTH", "trainingLevel" to "BEGINNER", "birthDate" to birthDate,
        "screening" to mapOf("PREGNANT_OR_NURSING" to "YES", "CONDITION_REQUIRING_CARE" to "NO", "MEDICATION_AFFECTING_PLAN" to "NO", "EATING_DISORDER_HISTORY" to "NO"),
        "items" to listOf(
            mapOf("kind" to "ALLERGY", "catalogCode" to catalogCode, "otherText" to other),
            mapOf("kind" to "DISLIKED_FOOD", "otherText" to food),
            mapOf("kind" to "INJURY", "bodyRegion" to "KNEE", "note" to injury),
        ),
    )

    private fun SpanData.dump() = "$name ${attributes.asMap()} ${events.map { it.name + it.attributes.asMap() }} ${status.description}"

    /** Everything an operator, a collector or a queue consumer could see, as text. */
    private fun surfaces(output: CapturedOutput, responses: List<ApiTestClient.Response>, spansBefore: Int): Map<String, String> {
        await().atMost(Duration.ofSeconds(10)).until { captured.spans.size > spansBefore }
        Thread.sleep(300)
        return mapOf(
            "log" to output.all,
            "spans" to captured.spans.drop(spansBefore).joinToString("\n") { it.dump() },
            "metrics" to meters.meters.joinToString("\n") { m -> "${m.id.name} ${m.id.tags}" },
            "outbox" to superuser.queryForList("select type || ' ' || aggregate_id || ' ' || payload::text from platform.outbox_message", String::class.java).joinToString("\n"),
            "tasks" to superuser.queryForList("select type || ' ' || coalesce(last_error_code, '') from platform.task", String::class.java).joinToString("\n"),
            "audit" to superuser.queryForList("select action || ' ' || coalesce(entity_id, '') || ' ' || fields::text from platform.audit_event", String::class.java).joinToString("\n"),
            "errors" to responses.filter { it.status >= 400 }.joinToString("\n") { it.body },
        )
    }

    private fun assertNoLeak(surfaces: Map<String, String>) = sentinels.forEach { sentinel ->
        surfaces.forEach { (name, text) -> assertThat(text).describedAs("$name has «$sentinel»").doesNotContain(sentinel) }
    }

    @Test
    fun `typed health text stays out of logs, spans, metrics, queue messages, audit and errors`(output: CapturedOutput) {
        val before = captured.spans.size
        val me = consenting()
        val responses = listOf(
            api.put(me, "/api/v1/me/anamnesis", body(other = null)),                             // success
            api.get(me, "/api/v1/me/anamnesis"),                                                  // read back
            api.put(me, "/api/v1/me/anamnesis", body(catalogCode = "PEANUT", other = allergy)),   // 400: code and text together
            api.put(me, "/api/v1/me/anamnesis", body(catalogCode = injury, other = null)),        // 400: outside the catalog
            api.put(me, "/api/v1/me/anamnesis", body(birthDate = "2999-01-01", other = null)),    // 400: future date
            api.put(me, "/api/v1/me/anamnesis", body(other = null) + ("note" to injury)),         // unknown extra field
            api.put(me, "/api/v1/me/anamnesis", null),                                            // missing body
            api.get(api.newSubject(), "/api/v1/me/anamnesis"),                                    // 403, no consent
        )
        assertThat(responses.map { it.status }).contains(200, 400, 403)
        assertThat(responses[0].status).isEqualTo(200)
        // the person's own data does come back to the person; the check below is about everything else
        assertThat(responses[1].body).contains(food, injury)

        val s = surfaces(output, responses.filterIndexed { i, _ -> i != 1 }, before)
        assertNoLeak(s)
    }

    @Test
    fun `a malformed body carrying the values is a 400 that does not echo them`(output: CapturedOutput) {
        val before = captured.spans.size
        val me = consenting()
        val broken = api.put(me, "/api/v1/me/anamnesis", """{"objective":"$food","items":[{"kind":"ALLERGY","otherText":"$allergy"""")
        assertThat(broken.status).isEqualTo(400)
        assertNoLeak(surfaces(output, listOf(broken), before))
    }

    @Test
    fun `the check fails when a value does leak, so a pass means something`(output: CapturedOutput) {
        LoggerFactory.getLogger("leak.probe").info("note: {}", injury)
        val before = captured.spans.size
        val me = consenting()
        val responses = listOf(api.put(me, "/api/v1/me/anamnesis", body(other = null)))
        val s = surfaces(output, responses, before)
        assertThatThrownBy { assertNoLeak(s) }.hasMessageContaining("log has")
        // and a leak through an error body is caught the same way
        assertThatThrownBy { assertNoLeak(s + ("errors" to "invalid: $food")) }.isInstanceOf(AssertionError::class.java)
    }
}
