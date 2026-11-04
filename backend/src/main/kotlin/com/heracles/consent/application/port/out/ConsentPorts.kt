package com.heracles.consent.application.port.out

import com.heracles.consent.api.ConsentType
import com.heracles.consent.domain.Client
import com.heracles.consent.domain.ConsentAction
import com.heracles.consent.domain.ConsentRecord
import com.heracles.consent.domain.PolicyVersion
import java.util.UUID

interface PolicyRepository {
    /** The latest published version of each type that has one. */
    fun currentVersions(): Map<ConsentType, PolicyVersion>
}

interface ConsentEvents {
    /** The last event of each consent of this person (the row-level security of the open transaction decides whose). */
    fun lastRecords(userId: UUID): Map<ConsentType, ConsentRecord>

    /** Every event of this person, oldest first (for the export). */
    fun history(userId: UUID): List<ConsentHistoryEntry>

    /** Returns the id of the new event. */
    fun append(userId: UUID, type: ConsentType, action: ConsentAction, policyVersionId: UUID, client: Client): UUID
}

/** One line of the history of consents, as the person did it. */
data class ConsentHistoryEntry(val type: ConsentType, val action: ConsentAction, val policyVersionId: UUID, val client: Client, val occurredAt: java.time.Instant)
