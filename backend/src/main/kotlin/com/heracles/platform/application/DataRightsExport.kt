package com.heracles.platform.application

import com.heracles.platform.api.AuditLog
import com.heracles.platform.api.ExportContributor
import com.heracles.platform.api.Outcome
import com.heracles.platform.api.Scope
import com.heracles.platform.api.TaskGateway
import com.heracles.platform.api.TaskMessage
import com.heracles.platform.api.Transactions
import com.heracles.platform.application.port.out.Clock
import com.heracles.platform.application.port.out.DataExports
import com.heracles.platform.application.port.out.FileStorage
import com.heracles.platform.application.port.out.PersonalData
import com.heracles.platform.application.port.out.TaskReader
import com.heracles.platform.domain.StorageKey
import com.heracles.platform.domain.TaskNotFoundException
import java.time.Duration
import java.time.Instant
import java.util.UUID

object ExportRules {
    const val TASK_TYPE = "export.run"
    val FILE_LIFETIME: Duration = Duration.ofHours(24)
    val LINK_LIFETIME: Duration = Duration.ofMinutes(15)
}

class ExportNotReadyException : RuntimeException("export_not_ready")
class ExportExpiredException : RuntimeException("export_expired")

/** The person asks for their data. Only the request is synchronous: a task does the rest. */
class RequestExport(private val transactions: Transactions, private val tasks: TaskGateway, private val audit: AuditLog) {
    operator fun invoke(userId: UUID): UUID = transactions.asUser(userId) {
        val taskId = tasks.enqueue(userId, ExportRules.TASK_TYPE)
        audit.record(userId, Scope.USER, "export.requested", "task", taskId.toString(), Outcome.SUCCESS)
        taskId
    }
}

/**
 * The handler of the export task. It runs in the person's own transaction, so row-level security alone decides what goes
 * in the file (CA13). A repeated message finds the reference already saved and does nothing.
 */
class RunExport(
    private val contributors: List<ExportContributor>,
    private val personal: PersonalData,
    private val storage: FileStorage,
    private val exports: DataExports,
    private val clock: Clock,
    private val audit: AuditLog,
    private val toJson: (Any) -> ByteArray,
) {
    init {
        val names = contributors.map { it.section } + RESERVED
        require(names.size == names.toSet().size) { "two export sections share a name" }
    }

    fun handle(message: TaskMessage) {
        val userId = UUID.fromString(message.ids.getValue("userId"))
        val taskId = UUID.fromString(message.ids.getValue("taskId"))
        if (exports.find(taskId) != null) return

        val now = clock.instant()
        val document = linkedMapOf<String, Any?>(
            "generatedAt" to now.toString(),
            "account" to personal.account(userId),
            "auditTrail" to personal.auditTrail(),
        )
        contributors.sortedBy { it.section }.forEach { document[it.section] = it.export(userId) }

        val key = StorageKey("exports/$userId/$taskId.json")
        storage.put(key, toJson(document), "application/json")
        exports.save(com.heracles.platform.application.port.out.DataExport(taskId, userId, key.value, now, now.plus(ExportRules.FILE_LIFETIME)))
        audit.record(userId, Scope.USER, "export.completed", "task", taskId.toString(), Outcome.SUCCESS)
    }

    private companion object {
        val RESERVED = listOf("generatedAt", "account", "auditTrail")
    }
}

data class ExportLink(val url: String, val expiresAt: Instant)

/** The link to the file, valid for 15 minutes, only for the person who asked and only while the file exists. */
class GetExport(
    private val transactions: Transactions,
    private val tasks: TaskReader,
    private val exports: DataExports,
    private val storage: FileStorage,
    private val clock: Clock,
) {
    operator fun invoke(userId: UUID, taskId: UUID): ExportLink = transactions.asUser(userId) {
        tasks.find(taskId) ?: throw TaskNotFoundException(taskId)
        val export = exports.find(taskId) ?: throw ExportNotReadyException()
        val now = clock.instant()
        if (!now.isBefore(export.expiresAt)) throw ExportExpiredException()
        ExportLink(storage.downloadUrl(StorageKey(export.storageKey), ExportRules.LINK_LIFETIME), export.expiresAt)
    }
}

/** Deletes the files that outlived their 24 hours, then the references. Safe to run again. */
class ExpireExports(private val transactions: Transactions, private val exports: DataExports, private val storage: FileStorage, private val clock: Clock) {
    operator fun invoke(): Int = transactions.asSystem {
        val expired = exports.expired(clock.instant())
        expired.forEach {
            storage.delete(StorageKey(it.storageKey))
            exports.delete(it.taskId)
        }
        expired.size
    }
}
