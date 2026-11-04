package com.heracles.consent.application

import com.heracles.consent.api.ConsentMessages
import com.heracles.consent.api.ConsentType
import com.heracles.consent.application.port.out.ConsentEvents
import com.heracles.consent.application.port.out.PolicyRepository
import com.heracles.consent.domain.Client
import com.heracles.consent.domain.ConsentAction
import com.heracles.consent.domain.ConsentRules
import com.heracles.consent.domain.ConsentStatus
import com.heracles.consent.domain.NotOptionalConsentException
import com.heracles.consent.domain.PolicyNotFoundException
import com.heracles.consent.domain.PolicyVersion
import com.heracles.consent.domain.PolicyVersionOutdatedException
import com.heracles.platform.api.AuditLog
import com.heracles.platform.api.EventPublisher
import com.heracles.platform.api.Outcome
import com.heracles.platform.api.Scope
import com.heracles.platform.api.Transactions
import java.util.UUID

/** The seven consents of the person, each with where it stands (spec RF2, RF3). */
class ListMyConsents(
    private val transactions: Transactions,
    private val policies: PolicyRepository,
    private val events: ConsentEvents,
) {
    operator fun invoke(userId: UUID): List<ConsentStatus> = transactions.asUser(userId) {
        val current = policies.currentVersions()
        val last = events.lastRecords(userId)
        ConsentType.entries.map { ConsentRules.status(it, current[it], last[it]) }
    }
}

/** The text a person must read before accepting. */
class GetCurrentPolicy(private val policies: PolicyRepository) {
    operator fun invoke(type: ConsentType): PolicyVersion = policies.currentVersions()[type] ?: throw PolicyNotFoundException(type)
}

/**
 * The three required consents are accepted together, each in the version the person saw. If any of those versions is no
 * longer the current one, nothing is recorded (`policy_version_outdated`): the person must read the new text.
 */
class AcceptRequiredConsents(
    private val transactions: Transactions,
    private val policies: PolicyRepository,
    private val events: ConsentEvents,
    private val audit: AuditLog,
    private val publisher: EventPublisher,
) {
    operator fun invoke(userId: UUID, acceptedVersionIds: Set<UUID>, client: Client) = transactions.asUser(userId) {
        val current = policies.currentVersions()
        val required = ConsentType.entries.filter { it.required }
        val versions = required.map { current[it] ?: throw PolicyVersionOutdatedException() }
        if (acceptedVersionIds != versions.map { it.id }.toSet()) throw PolicyVersionOutdatedException()

        val last = events.lastRecords(userId)
        versions.forEach { version ->
            val already = ConsentRules.status(version.type, version, last[version.type]).satisfied
            if (!already) record(userId, version, ConsentAction.GRANT, client)
        }
    }

    private fun record(userId: UUID, version: PolicyVersion, action: ConsentAction, client: Client) =
        recordConsentEvent(events, audit, publisher, userId, version, action, client)
}

/** An optional consent is turned on or off, always in the current version. Repeating the same state records nothing. */
class SetOptionalConsent(
    private val transactions: Transactions,
    private val policies: PolicyRepository,
    private val events: ConsentEvents,
    private val audit: AuditLog,
    private val publisher: EventPublisher,
) {
    operator fun invoke(userId: UUID, type: ConsentType, granted: Boolean, versionId: UUID, client: Client) {
        if (type.required) throw NotOptionalConsentException(type)
        transactions.asUser(userId) {
            val version = policies.currentVersions()[type] ?: throw PolicyNotFoundException(type)
            if (version.id != versionId) throw PolicyVersionOutdatedException()
            val now = events.lastRecords(userId)[type]?.action == ConsentAction.GRANT
            if (now != granted) {
                recordConsentEvent(events, audit, publisher, userId, version, if (granted) ConsentAction.GRANT else ConsentAction.REVOKE, client)
            }
        }
    }
}

/** Writes the event, its audit line and its outbox message in the open transaction, so the three stay or go together. */
internal fun recordConsentEvent(
    events: ConsentEvents, audit: AuditLog, publisher: EventPublisher,
    userId: UUID, version: PolicyVersion, action: ConsentAction, client: Client,
) {
    val eventId = events.append(userId, version.type, action, version.id, client)
    val granted = action == ConsentAction.GRANT
    audit.record(
        actorUserId = userId, actorScope = Scope.USER, action = if (granted) "consent.accepted" else "consent.revoked",
        entityType = "consent", entityId = eventId.toString(), outcome = Outcome.SUCCESS, fields = listOf("type", "policyVersionId"),
    )
    publisher.publish(
        "consent", eventId.toString(), if (granted) ConsentMessages.ACCEPTED else ConsentMessages.REVOKED,
        mapOf("userId" to userId.toString(), "eventId" to eventId.toString(), "consentType" to version.type.name, "policyVersionId" to version.id.toString()),
    )
}

/** The question other modules ask. Opens its own transaction only when none is open (the adapter decides). */
class CheckConsent(private val policies: PolicyRepository, private val events: ConsentEvents) {
    operator fun invoke(userId: UUID, type: ConsentType): Boolean {
        val status = ConsentRules.status(type, policies.currentVersions()[type], events.lastRecords(userId)[type])
        return status.satisfied
    }
}
