package com.heracles.consent.api

import java.util.UUID

/** The seven consents of the product. The first three are required before any health data (spec RF2). */
enum class ConsentType(val required: Boolean) {
    TERMS_OF_USE(true),
    PRIVACY_POLICY(true),
    HEALTH_DATA_CONSENT(true),
    AI_EXAMS_CONSENT(false),
    COMMUNITY_CONSENT(false),
    NOTIFICATIONS_CONSENT(false),
    PROMOTIONAL_CONSENT(false),
}

/**
 * The question other modules ask: "did this person consent to X, in the current version?". Fails closed: when it cannot
 * answer, the answer is no. A revocation counts from the very next call. Works inside or outside a user transaction.
 */
interface ConsentChecker {
    fun hasConsented(userId: UUID, type: ConsentType): Boolean
}

/** Marks a route (or a whole controller) that reads or writes health data: it answers 403 `consent_required` without the consent. */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequiresConsent(val type: ConsentType = ConsentType.HEALTH_DATA_CONSENT)

/** Message types the module publishes through the outbox. They carry identifiers only. */
object ConsentMessages {
    const val ACCEPTED = "consent.accepted"
    const val REVOKED = "consent.revoked"
}
