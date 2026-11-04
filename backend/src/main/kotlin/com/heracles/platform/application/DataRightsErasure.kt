package com.heracles.platform.application

import com.heracles.platform.api.AuditLog
import com.heracles.platform.api.ErasureStep
import com.heracles.platform.api.Outcome
import com.heracles.platform.api.Scope
import com.heracles.platform.api.TaskGateway
import com.heracles.platform.api.TaskMessage
import com.heracles.platform.api.Transactions
import com.heracles.platform.application.port.out.AccountErasure
import com.heracles.platform.application.port.out.AccountErasures
import com.heracles.platform.application.port.out.AccountRecords
import com.heracles.platform.application.port.out.Clock
import com.heracles.platform.application.port.out.DataExports
import com.heracles.platform.application.port.out.FileStorage
import com.heracles.platform.application.port.out.IdentityProvider
import com.heracles.platform.domain.StorageKey
import java.time.Duration
import java.util.UUID

object ErasureRules {
    const val TASK_TYPE = "erasure.run"
    val GRACE_PERIOD: Duration = Duration.ofDays(7)
}

class ErasureNotFoundException : RuntimeException("erasure_not_found")

/** The person asks to be erased; they have 7 days (by the port's clock) to change their mind. Asking again returns the same request. */
class RequestErasure(
    private val transactions: Transactions,
    private val erasures: AccountErasures,
    private val audit: AuditLog,
    private val clock: Clock,
) {
    operator fun invoke(userId: UUID): AccountErasure = transactions.asUser(userId) {
        erasures.open(userId) ?: run {
            // The database keeps microseconds: what we answer must equal what is read later.
            val now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS)
            erasures.create(userId, now, now.plus(ErasureRules.GRACE_PERIOD)).also {
                audit.record(userId, Scope.USER, "erasure.requested", "account_erasure", it.id.toString(), Outcome.SUCCESS)
            }
        }
    }
}

class GetErasure(private val transactions: Transactions, private val erasures: AccountErasures) {
    operator fun invoke(userId: UUID): AccountErasure =
        transactions.asUser(userId) { erasures.open(userId) } ?: throw ErasureNotFoundException()
}

class CancelErasure(private val transactions: Transactions, private val erasures: AccountErasures, private val audit: AuditLog) {
    operator fun invoke(userId: UUID) = transactions.asUser(userId) {
        if (!erasures.cancel(userId)) throw ErasureNotFoundException()
        audit.record(userId, Scope.USER, "erasure.canceled", "account_erasure", null, Outcome.SUCCESS)
    }
}

/** Every tick: the requests whose 7 days passed become a task each, once. */
class QueueDueErasures(
    private val transactions: Transactions,
    private val erasures: AccountErasures,
    private val tasks: TaskGateway,
    private val clock: Clock,
) {
    operator fun invoke(): Int = transactions.asSystem {
        val due = erasures.due(clock.instant())
        due.forEach {
            // A system task: it runs in the system scope, because the person's own scope would stop seeing the rows halfway.
            tasks.enqueue(null, ErasureRules.TASK_TYPE, mapOf("subjectId" to it.userId.toString()))
            erasures.markRunning(it.id)
        }
        due.size
    }
}

/**
 * Erases one person (CA14), in the system scope: each module's steps in order, then the identity provider, then the account
 * itself. Safe to run twice: every step is, and once the account is empty the identity step is skipped. The subject is read
 * before anything is emptied, and the account is emptied last, so a failure in the middle can be retried with the subject still known.
 */
class RunErasure(
    private val steps: List<ErasureStep>,
    private val records: AccountRecords,
    private val identity: IdentityProvider,
    private val erasures: AccountErasures,
    private val audit: AuditLog,
) {
    init {
        require(steps.map { it.order }.let { it.size == it.toSet().size }) { "two erasure steps share an order" }
    }

    fun handle(message: TaskMessage) {
        val userId = UUID.fromString(message.ids.getValue("subjectId"))
        val subject = records.subjectOf(userId)

        steps.sortedBy { it.order }.forEach { it.erase(userId) }
        subject?.let(identity::deleteUser)
        records.unlinkAuditTrail(userId)
        records.empty(userId)
        erasures.markDone(userId)
        audit.record(null, Scope.SYSTEM, "erasure.completed", "task", message.ids["taskId"], Outcome.SUCCESS)
    }
}

/** The export files hold the person's data too: they go with the rest. */
class ExportFilesErasure(private val exports: DataExports, private val storage: FileStorage) : ErasureStep {
    override val order = 30

    override fun erase(userId: UUID) {
        exports.ofUser(userId).forEach {
            storage.delete(StorageKey(it.storageKey))
            exports.delete(it.taskId)
        }
    }
}
