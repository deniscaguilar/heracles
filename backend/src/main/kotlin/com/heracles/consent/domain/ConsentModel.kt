package com.heracles.consent.domain

import com.heracles.consent.api.ConsentType
import java.util.UUID

/** A published text of one policy. [markdown] is what the administrator wrote; clients receive [PolicyBlocks.parse] of it. */
data class PolicyVersion(val id: UUID, val type: ConsentType, val version: Int, val title: String, val markdown: String)

enum class ConsentAction { GRANT, REVOKE }
enum class Client { WEB, MOBILE }

/** The last event of one consent of one person (consent is an event, never a state). */
data class ConsentRecord(val type: ConsentType, val action: ConsentAction, val policyVersionId: UUID)

/**
 * Where one consent stands for one person. [satisfied] is what the barrier asks: for a required consent, the person
 * granted the CURRENT version; for an optional one, the person granted it (a new version of an optional text does not
 * switch it off). [pending] is what a screen asks: the person must accept a published text now.
 */
data class ConsentStatus(
    val type: ConsentType,
    val required: Boolean,
    val currentVersion: PolicyVersion?,
    val acceptedVersionId: UUID?,
    val granted: Boolean,
    val satisfied: Boolean,
    val pending: Boolean,
)

object ConsentRules {
    fun status(type: ConsentType, current: PolicyVersion?, last: ConsentRecord?): ConsentStatus {
        val granted = last?.action == ConsentAction.GRANT
        val satisfied = if (type.required) granted && current != null && last?.policyVersionId == current.id else granted
        val pending = type.required && current != null && !satisfied
        return ConsentStatus(type, type.required, current, if (granted) last?.policyVersionId else null, granted, satisfied, pending)
    }
}

class PolicyNotFoundException(val type: ConsentType) : RuntimeException("no published policy of type $type")

/** The version the person saw is no longer the current one (or is not one at all). */
class PolicyVersionOutdatedException : RuntimeException("policy version is outdated")

class NotOptionalConsentException(val type: ConsentType) : RuntimeException("consent $type is required, not optional")

class DraftAlreadyExistsException(val type: ConsentType) : RuntimeException("a draft of $type already exists")

/** There is no draft with this id (it was published, discarded or never existed). */
class DraftNotFoundException : RuntimeException("draft not found")

/** A policy version as the backoffice sees it: a draft has no number and no publication date. */
data class PolicyVersionSummary(
    val id: UUID, val type: ConsentType, val version: Int?, val title: String,
    val createdAt: java.time.Instant, val publishedAt: java.time.Instant?, val accepted: Long,
)

data class PolicyDocument(val summary: PolicyVersionSummary, val markdown: String)
