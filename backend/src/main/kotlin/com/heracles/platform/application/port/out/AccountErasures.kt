package com.heracles.platform.application.port.out

import java.time.Instant
import java.util.UUID

enum class ErasureStatus { PENDING, CANCELED, RUNNING, DONE }

data class AccountErasure(val id: UUID, val userId: UUID, val requestedAt: Instant, val dueAt: Instant, val status: ErasureStatus)

interface AccountErasures {
    fun open(userId: UUID): AccountErasure?
    fun create(userId: UUID, requestedAt: Instant, dueAt: Instant): AccountErasure

    /** True when a pending request was canceled. */
    fun cancel(userId: UUID): Boolean

    /** System scope: the pending requests whose time has come. */
    fun due(now: Instant): List<AccountErasure>
    fun markRunning(id: UUID)
    fun markDone(userId: UUID)
}

/** What the platform does to the account itself, in the system scope. Every call is safe to repeat. */
interface AccountRecords {
    /** The identity subject, or null when the account was already emptied. */
    fun subjectOf(userId: UUID): String?

    /** The row stays, with no subject and no role, so the old subject can open a new, empty account. */
    fun empty(userId: UUID)

    /** Swaps the person's identifier in the audit trail for a random one that is kept nowhere. */
    fun unlinkAuditTrail(userId: UUID)
}
