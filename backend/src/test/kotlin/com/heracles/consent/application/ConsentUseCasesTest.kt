package com.heracles.consent.application

import com.heracles.consent.api.ConsentMessages
import com.heracles.consent.api.ConsentType
import com.heracles.consent.domain.Client
import com.heracles.consent.domain.NotOptionalConsentException
import com.heracles.consent.domain.PolicyNotFoundException
import com.heracles.consent.domain.PolicyVersionOutdatedException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

/** E2-S3.2 and the audit/event side of E2-S5: the use cases with fakes of every port (no Spring, no database). */
class ConsentUseCasesTest {
    private val transactions = FakeTransactions()
    private val policies = FakePolicies()
    private val events = FakeConsentEvents()
    private val audit = RecordingAuditLog()
    private val publisher = RecordingPublisher()
    private val user = UUID.randomUUID()

    private val list = ListMyConsents(transactions, policies, events)
    private val accept = AcceptRequiredConsents(transactions, policies, events, audit, publisher)
    private val setOptional = SetOptionalConsent(transactions, policies, events, audit, publisher)
    private val check = CheckConsent(policies, events)

    private fun publishRequired() = ConsentType.entries.filter { it.required }.map { policies.publish(it) }

    @Test
    fun `the list has the seven consents, the required ones pending and the optional ones off`() {
        publishRequired()
        policies.publish(ConsentType.AI_EXAMS_CONSENT)
        val items = list(user)
        assertThat(items).hasSize(7)
        assertThat(items.filter { it.pending }.map { it.type }).containsExactlyInAnyOrder(
            ConsentType.TERMS_OF_USE, ConsentType.PRIVACY_POLICY, ConsentType.HEALTH_DATA_CONSENT,
        )
        assertThat(items.filter { !it.required }.none { it.granted }).isTrue()
    }

    @Test
    fun `accepting the three required consents records three events in the user scope`() {
        val versions = publishRequired()
        accept(user, versions.map { it.id }.toSet(), Client.WEB)

        assertThat(events.events.map { it.record.type }).containsExactlyInAnyOrder(
            ConsentType.TERMS_OF_USE, ConsentType.PRIVACY_POLICY, ConsentType.HEALTH_DATA_CONSENT,
        )
        assertThat(events.events.all { it.client == Client.WEB }).isTrue()
        assertThat(check(user, ConsentType.HEALTH_DATA_CONSENT)).isTrue()
        assertThat(list(user).none { it.pending }).isTrue()
    }

    @Test
    fun `each acceptance leaves an audit line and an outbox message with identifiers only`() {
        val versions = publishRequired()
        accept(user, versions.map { it.id }.toSet(), Client.MOBILE)

        assertThat(audit.calls).hasSize(3)
        assertThat(audit.calls.map { it.action }.distinct()).containsExactly("consent.accepted")
        assertThat(audit.calls.all { it.fields == listOf("type", "policyVersionId") }).isTrue()
        assertThat(publisher.published).hasSize(3)
        assertThat(publisher.published.map { it.type }.distinct()).containsExactly(ConsentMessages.ACCEPTED)
        publisher.published.forEach { message ->
            assertThat(message.ids.keys).containsExactlyInAnyOrder("userId", "eventId", "consentType", "policyVersionId")
            assertThat(message.ids.values).allMatch { it.matches(Regex("^[A-Za-z0-9_-]{1,128}$")) }
        }
    }

    @Test
    fun `a version that is no longer the current one records nothing`() {
        val seen = publishRequired()
        policies.publish(ConsentType.TERMS_OF_USE)
        assertThatThrownBy { accept(user, seen.map { it.id }.toSet(), Client.WEB) }.isInstanceOf(PolicyVersionOutdatedException::class.java)
        assertThat(events.events).isEmpty()
        assertThat(publisher.published).isEmpty()
    }

    @Test
    fun `accepting needs exactly the three current versions`() {
        val versions = publishRequired()
        assertThatThrownBy { accept(user, versions.take(2).map { it.id }.toSet(), Client.WEB) }.isInstanceOf(PolicyVersionOutdatedException::class.java)
        assertThatThrownBy { accept(user, versions.map { it.id }.toSet() + UUID.randomUUID(), Client.WEB) }.isInstanceOf(PolicyVersionOutdatedException::class.java)
    }

    @Test
    fun `accepting while a required text is not published fails`() {
        policies.publish(ConsentType.TERMS_OF_USE)
        assertThatThrownBy { accept(user, setOf(UUID.randomUUID()), Client.WEB) }.isInstanceOf(PolicyVersionOutdatedException::class.java)
    }

    @Test
    fun `accepting twice does not repeat what is already accepted`() {
        val versions = publishRequired()
        accept(user, versions.map { it.id }.toSet(), Client.WEB)
        accept(user, versions.map { it.id }.toSet(), Client.WEB)
        assertThat(events.events).hasSize(3)
    }

    @Test
    fun `a new required version makes the person pending again and accepting it records only that one`() {
        val versions = publishRequired()
        accept(user, versions.map { it.id }.toSet(), Client.WEB)
        val newTerms = policies.publish(ConsentType.TERMS_OF_USE)

        assertThat(list(user).filter { it.pending }.map { it.type }).containsExactly(ConsentType.TERMS_OF_USE)
        assertThat(check(user, ConsentType.HEALTH_DATA_CONSENT)).isTrue()
        assertThat(check(user, ConsentType.TERMS_OF_USE)).isFalse()
        accept(user, setOf(newTerms.id, versions[1].id, versions[2].id), Client.WEB)
        assertThat(events.events).hasSize(4)
        assertThat(list(user).none { it.pending }).isTrue()
    }

    @Test
    fun `an optional consent is turned on and off with effect on the very next question`() {
        val ai = policies.publish(ConsentType.AI_EXAMS_CONSENT)
        assertThat(check(user, ConsentType.AI_EXAMS_CONSENT)).isFalse()
        setOptional(user, ConsentType.AI_EXAMS_CONSENT, true, ai.id, Client.WEB)
        assertThat(check(user, ConsentType.AI_EXAMS_CONSENT)).isTrue()
        setOptional(user, ConsentType.AI_EXAMS_CONSENT, false, ai.id, Client.WEB)
        assertThat(check(user, ConsentType.AI_EXAMS_CONSENT)).isFalse()

        assertThat(events.events.map { it.record.action.name }).containsExactly("GRANT", "REVOKE")
        assertThat(audit.calls.map { it.action }).containsExactly("consent.accepted", "consent.revoked")
        assertThat(publisher.published.map { it.type }).containsExactly(ConsentMessages.ACCEPTED, ConsentMessages.REVOKED)
    }

    @Test
    fun `repeating the same optional state records nothing`() {
        val ai = policies.publish(ConsentType.COMMUNITY_CONSENT)
        setOptional(user, ConsentType.COMMUNITY_CONSENT, false, ai.id, Client.WEB)
        setOptional(user, ConsentType.COMMUNITY_CONSENT, true, ai.id, Client.WEB)
        setOptional(user, ConsentType.COMMUNITY_CONSENT, true, ai.id, Client.WEB)
        assertThat(events.events).hasSize(1)
    }

    @Test
    fun `an optional consent refuses an old version, a missing text and a required type`() {
        val old = policies.publish(ConsentType.PROMOTIONAL_CONSENT)
        policies.publish(ConsentType.PROMOTIONAL_CONSENT)
        assertThatThrownBy { setOptional(user, ConsentType.PROMOTIONAL_CONSENT, true, old.id, Client.WEB) }
            .isInstanceOf(PolicyVersionOutdatedException::class.java)
        assertThatThrownBy { setOptional(user, ConsentType.NOTIFICATIONS_CONSENT, true, UUID.randomUUID(), Client.WEB) }
            .isInstanceOf(PolicyNotFoundException::class.java)
        assertThatThrownBy { setOptional(user, ConsentType.TERMS_OF_USE, true, UUID.randomUUID(), Client.WEB) }
            .isInstanceOf(NotOptionalConsentException::class.java)
    }

    @Test
    fun `the use cases run inside one user transaction and it is closed afterwards`() {
        val versions = publishRequired()
        accept(user, versions.map { it.id }.toSet(), Client.WEB)
        assertThat(transactions.depth).isZero()
    }

    @Test
    fun `current policy returns the latest published text or says there is none`() {
        val get = GetCurrentPolicy(policies)
        assertThatThrownBy { get(ConsentType.TERMS_OF_USE) }.isInstanceOf(PolicyNotFoundException::class.java)
        policies.publish(ConsentType.TERMS_OF_USE)
        val second = policies.publish(ConsentType.TERMS_OF_USE)
        assertThat(get(ConsentType.TERMS_OF_USE)).isEqualTo(second)
    }
}
