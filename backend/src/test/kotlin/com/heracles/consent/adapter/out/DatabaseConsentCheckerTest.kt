package com.heracles.consent.adapter.out

import com.heracles.consent.api.ConsentType
import com.heracles.consent.application.CheckConsent
import com.heracles.consent.application.FakeConsentEvents
import com.heracles.consent.application.FakePolicies
import com.heracles.consent.application.FakeTransactions
import com.heracles.consent.application.port.out.PolicyRepository
import com.heracles.consent.domain.Client
import com.heracles.consent.domain.ConsentAction
import com.heracles.consent.domain.PolicyVersion
import com.heracles.platform.api.Transactions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

/** E2-S3.4: what other modules ask must fail closed. Any failure while answering is "no", never "yes" (mutation M06). */
class DatabaseConsentCheckerTest {
    private val user = UUID.randomUUID()
    private val type = ConsentType.HEALTH_DATA_CONSENT

    private fun checker(
        policies: PolicyRepository = FakePolicies().also { it.publish(type) },
        events: FakeConsentEvents = FakeConsentEvents(),
        transactions: Transactions = FakeTransactions(),
    ) = DatabaseConsentChecker(CheckConsent(policies, events), transactions)

    @Test
    fun `a failure while reading the policies answers no`() {
        val broken = object : PolicyRepository {
            override fun currentVersions(): Map<ConsentType, PolicyVersion> = throw IllegalStateException("database down")
        }
        assertThat(checker(policies = broken).hasConsented(user, type)).isFalse()
    }

    @Test
    fun `a failure opening the transaction of the person answers no`() {
        val broken = object : Transactions {
            override fun <T> asUser(userId: UUID, block: () -> T): T = throw IllegalStateException("no connection")
            override fun <T> asAdmin(block: () -> T): T = block()
            override fun <T> asSystem(block: () -> T): T = block()
        }
        assertThat(checker(transactions = broken).hasConsented(user, type)).isFalse()
    }

    @Test
    fun `with the consent in the current version the answer is yes, so the no above is the failure and not a constant`() {
        val policies = FakePolicies()
        val version = policies.publish(type)
        val events = FakeConsentEvents()
        assertThat(checker(policies, events).hasConsented(user, type)).isFalse() // nothing accepted yet
        events.append(user, type, ConsentAction.GRANT, version.id, Client.WEB)
        assertThat(checker(policies, events).hasConsented(user, type)).isTrue()
    }
}
