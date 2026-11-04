package com.heracles.consent.domain

import com.heracles.consent.api.ConsentType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

/** E2-S3.1: the state of a consent is derived from the last event and the current version. */
class ConsentRulesTest {
    private fun version(type: ConsentType) = PolicyVersion(UUID.randomUUID(), type, 1, "T", "text")

    @Test
    fun `three consents are required and four are optional`() {
        assertThat(ConsentType.entries.filter { it.required })
            .containsExactly(ConsentType.TERMS_OF_USE, ConsentType.PRIVACY_POLICY, ConsentType.HEALTH_DATA_CONSENT)
        assertThat(ConsentType.entries.filter { !it.required }).hasSize(4)
    }

    @Test
    fun `a required consent without any event is pending when a text is published`() {
        val current = version(ConsentType.TERMS_OF_USE)
        val status = ConsentRules.status(ConsentType.TERMS_OF_USE, current, null)
        assertThat(status.granted).isFalse()
        assertThat(status.satisfied).isFalse()
        assertThat(status.pending).isTrue()
    }

    @Test
    fun `a required consent granted in the current version is satisfied and not pending`() {
        val current = version(ConsentType.PRIVACY_POLICY)
        val status = ConsentRules.status(ConsentType.PRIVACY_POLICY, current, ConsentRecord(ConsentType.PRIVACY_POLICY, ConsentAction.GRANT, current.id))
        assertThat(status.satisfied).isTrue()
        assertThat(status.pending).isFalse()
        assertThat(status.acceptedVersionId).isEqualTo(current.id)
    }

    @Test
    fun `a required consent granted in an old version is pending again, but still shows what was accepted`() {
        val old = version(ConsentType.TERMS_OF_USE)
        val current = version(ConsentType.TERMS_OF_USE)
        val status = ConsentRules.status(ConsentType.TERMS_OF_USE, current, ConsentRecord(ConsentType.TERMS_OF_USE, ConsentAction.GRANT, old.id))
        assertThat(status.granted).isTrue()
        assertThat(status.satisfied).isFalse()
        assertThat(status.pending).isTrue()
        assertThat(status.acceptedVersionId).isEqualTo(old.id)
    }

    @Test
    fun `a required consent that was revoked is not satisfied`() {
        val current = version(ConsentType.HEALTH_DATA_CONSENT)
        val status = ConsentRules.status(ConsentType.HEALTH_DATA_CONSENT, current, ConsentRecord(ConsentType.HEALTH_DATA_CONSENT, ConsentAction.REVOKE, current.id))
        assertThat(status.satisfied).isFalse()
        assertThat(status.acceptedVersionId).isNull()
    }

    @Test
    fun `with no published text a required consent cannot be satisfied and nothing is pending`() {
        val status = ConsentRules.status(ConsentType.HEALTH_DATA_CONSENT, null, null)
        assertThat(status.satisfied).isFalse()
        assertThat(status.pending).isFalse()
    }

    @Test
    fun `an optional consent is off by default, on after a grant, and a new text does not switch it off`() {
        val old = version(ConsentType.AI_EXAMS_CONSENT)
        val current = version(ConsentType.AI_EXAMS_CONSENT)
        assertThat(ConsentRules.status(ConsentType.AI_EXAMS_CONSENT, current, null).satisfied).isFalse()
        val granted = ConsentRules.status(ConsentType.AI_EXAMS_CONSENT, current, ConsentRecord(ConsentType.AI_EXAMS_CONSENT, ConsentAction.GRANT, old.id))
        assertThat(granted.satisfied).isTrue()
        assertThat(granted.pending).isFalse()
        val revoked = ConsentRules.status(ConsentType.AI_EXAMS_CONSENT, current, ConsentRecord(ConsentType.AI_EXAMS_CONSENT, ConsentAction.REVOKE, current.id))
        assertThat(revoked.satisfied).isFalse()
    }
}
