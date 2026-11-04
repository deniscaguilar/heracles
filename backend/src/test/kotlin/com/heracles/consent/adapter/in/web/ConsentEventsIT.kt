package com.heracles.consent.adapter.`in`.web

import com.heracles.consent.api.ConsentMessages
import com.heracles.consent.api.ConsentType
import com.heracles.platform.adapter.out.fake.RecordingNotificationChannel
import com.heracles.platform.api.EventPublisher
import com.heracles.platform.api.Notifier
import com.heracles.platform.api.Transactions
import com.heracles.platform.application.ConsumeOutcome
import com.heracles.platform.application.ConsumeTask
import com.heracles.platform.application.port.out.TaskQueue
import com.heracles.platform.domain.QueueMessage
import com.heracles.platform.domain.ReceivedMessage
import com.heracles.support.ApiTestClient
import com.heracles.support.PostgresInitializer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doCallRealMethod
import org.mockito.Mockito.doThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import java.util.UUID

/**
 * E2-S5.1 to E2-S5.3 (CA8, CA10): the events leave through the outbox in the same transaction, carry identifiers only,
 * are audited without values, and the confirmation is sent once even when the message is delivered twice.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = ["heracles.adapters.fake.enabled=true"])
class ConsentEventsIT(
    @Value("\${local.server.port}") port: Int,
    @Autowired private val transactions: Transactions,
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val consume: ConsumeTask,
    @Autowired private val channel: RecordingNotificationChannel,
) {
    @MockitoBean lateinit var queue: TaskQueue
    @MockitoSpyBean lateinit var publisher: EventPublisher
    @MockitoSpyBean lateinit var notifier: Notifier

    private val api = ApiTestClient(port)
    private val actor = UUID.randomUUID()
    private val superuser = JdbcTemplate(
        DriverManagerDataSource(
            PostgresInitializer.postgres.jdbcUrl, PostgresInitializer.postgres.username, PostgresInitializer.postgres.password,
        ),
    )

    private fun publish(type: ConsentType): UUID = transactions.asAdmin {
        // Another test class may have left a draft of this type open; there is one draft per type.
        jdbc.queryForList("select id from consent.policy_version where type = ? and published_at is null", UUID::class.java, type.name)
            .forEach { jdbc.queryForObject("select consent.discard_draft(?)", Boolean::class.javaObjectType, it) }
        val id = jdbc.queryForObject("select consent.create_draft(?, 'Title', 'Text', ?)", UUID::class.java, type.name, actor)!!
        jdbc.queryForObject("select consent.publish_version(?, ?)", Int::class.javaObjectType, id, actor)
        id
    }

    private fun userId(subject: String) = superuser.queryForObject("select id from platform.app_user where sub = ?", UUID::class.java, subject)!!

    private fun acceptAll(subject: String): ApiTestClient.Response {
        val ids = ConsentType.entries.filter { it.required }.map { publish(it) }
        return api.post(subject, "/api/v1/me/required-consents", mapOf("versionIds" to ids, "client" to "WEB"))
    }

    @Test
    fun `accepting leaves one outbox message and one audit line per consent, with identifiers only`() {
        val me = api.newSubject()
        assertThat(acceptAll(me).status).isEqualTo(204)
        val id = userId(me)

        val messages = superuser.queryForList("select type, payload::text as payload from platform.outbox_message where payload->>'userId' = ?", id.toString())
        assertThat(messages).hasSize(3)
        assertThat(messages.map { it["type"] }.distinct()).containsExactly(ConsentMessages.ACCEPTED)
        messages.forEach {
            val payload = it["payload"] as String
            assertThat(Regex("\"(\\w+)\"\\s*:").findAll(payload).map { m -> m.groupValues[1] }.toSet())
                .containsExactlyInAnyOrder("userId", "eventId", "consentType", "policyVersionId")
        }

        val audit = superuser.queryForList("select action, entity_type, fields from platform.audit_event where actor_user_id = ? and action like 'consent.%'", id)
        assertThat(audit).hasSize(3)
        assertThat(audit.map { it["action"] }.distinct()).containsExactly("consent.accepted")
        assertThat(audit.all { (it["fields"] as java.sql.Array).array.let { a -> (a as Array<*>).toList() } == listOf("type", "policyVersionId") }).isTrue()
    }

    @Test
    fun `a revocation leaves its own message and audit line`() {
        val me = api.newSubject()
        val ai = publish(ConsentType.AI_EXAMS_CONSENT)
        api.put(me, "/api/v1/me/consents/AI_EXAMS_CONSENT", mapOf("granted" to true, "versionId" to ai, "client" to "WEB"))
        api.put(me, "/api/v1/me/consents/AI_EXAMS_CONSENT", mapOf("granted" to false, "versionId" to ai, "client" to "WEB"))
        val id = userId(me)

        assertThat(superuser.queryForList("select type from platform.outbox_message where payload->>'userId' = ? order by created_at", String::class.java, id.toString()))
            .containsExactly(ConsentMessages.ACCEPTED, ConsentMessages.REVOKED)
        assertThat(superuser.queryForList("select action from platform.audit_event where actor_user_id = ? and action like 'consent.%' order by occurred_at", String::class.java, id))
            .containsExactly("consent.accepted", "consent.revoked")
    }

    @Test
    fun `when the message cannot be stored, the consents, the audit and the messages all roll back together`() {
        val me = api.newSubject()
        api.get(me, "/api/v1/me")
        val id = userId(me)
        doCallRealMethod().doThrow(IllegalStateException("outbox down")).`when`(publisher).publish(anyString(), anyString(), anyString(), anyMap())

        val response = acceptAll(me)

        assertThat(response.status).isEqualTo(500)
        assertThat(superuser.queryForObject("select count(*) from consent.consent_event where user_id = ?", Int::class.java, id)).isZero()
        assertThat(superuser.queryForObject("select count(*) from platform.outbox_message where payload->>'userId' = ?", Int::class.java, id.toString())).isZero()
        assertThat(superuser.queryForObject("select count(*) from platform.audit_event where actor_user_id = ? and action like 'consent.%'", Int::class.java, id)).isZero()
        org.mockito.Mockito.reset(publisher)
    }

    private fun delivery(userId: UUID, type: String = ConsentMessages.ACCEPTED, id: UUID = UUID.randomUUID()) = ReceivedMessage(
        QueueMessage(
            id.toString(), type,
            mapOf("userId" to userId.toString(), "eventId" to UUID.randomUUID().toString(), "consentType" to "HEALTH_DATA_CONSENT", "policyVersionId" to UUID.randomUUID().toString()),
        ),
        "receipt-$id",
    )

    @Test
    fun `the same message delivered twice sends one confirmation (CA10)`() {
        val user = UUID.randomUUID()
        val message = delivery(user)

        assertThat(consume(message)).isEqualTo(ConsumeOutcome.PROCESSED)
        assertThat(consume(message)).isEqualTo(ConsumeOutcome.DUPLICATE)

        val sent = channel.sent.filter { it.userId == user }
        assertThat(sent).hasSize(1)
        assertThat(sent.single().template).isEqualTo("consent_accepted")
        assertThat(sent.single().params).containsEntry("consentType", "HEALTH_DATA_CONSENT")
    }

    @Test
    fun `a revocation message sends the revocation template`() {
        val user = UUID.randomUUID()
        consume(delivery(user, ConsentMessages.REVOKED))
        assertThat(channel.sent.single { it.userId == user }.template).isEqualTo("consent_revoked")
    }

    @Test
    fun `a failed delivery loses nothing, and the retry sends the confirmation once (CA10)`() {
        val user = UUID.randomUUID()
        val message = delivery(user)
        doThrow(IllegalStateException("channel down")).doCallRealMethod().`when`(notifier).send(org.mockito.ArgumentMatchers.any(UUID::class.java) ?: user, anyString(), anyMap())

        assertThatThrownBy { consume(message) }.isInstanceOf(IllegalStateException::class.java)
        assertThat(channel.sent.filter { it.userId == user }).isEmpty()

        assertThat(consume(message)).isEqualTo(ConsumeOutcome.PROCESSED)
        assertThat(channel.sent.filter { it.userId == user }).hasSize(1)
        org.mockito.Mockito.reset(notifier)
    }
}
