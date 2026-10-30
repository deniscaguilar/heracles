package com.heracles.platform.application.port.out

import com.heracles.platform.domain.OutboxEntry
import java.time.Instant
import java.util.UUID

/** The publisher's side of the outbox. All calls join the transaction opened by the caller (system scope). */
interface OutboxStore {
    /**
     * Locks and returns up to [limit] unpublished messages that are due at [now]. Rows locked by another publisher are
     * skipped, so two publishers never take the same message. The lock lasts until the transaction ends.
     */
    fun claimDue(limit: Int, now: Instant): List<OutboxEntry>

    fun markPublished(id: UUID, at: Instant)

    fun markFailed(id: UUID, attempts: Int, nextAttemptAt: Instant)
}
