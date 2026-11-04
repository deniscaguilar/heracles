package com.heracles.platform.application.port.out

import java.time.Instant
import java.util.UUID

data class DataExport(val taskId: UUID, val userId: UUID, val storageKey: String, val createdAt: Instant, val expiresAt: Instant)

/** The references to export files (table `data_export`). Row-level security decides whose a user finds. */
interface DataExports {
    fun save(export: DataExport)
    fun find(taskId: UUID): DataExport?

    /** For the clean-up job, in the system scope. */
    fun expired(now: Instant): List<DataExport>
    fun delete(taskId: UUID)

    /** For the erasure, in the system scope. */
    fun ofUser(userId: UUID): List<DataExport>
}

/** What the platform itself holds about the person (the modules add theirs through `ExportContributor`). */
interface PersonalData {
    fun account(userId: UUID): Map<String, Any?>

    /** The audit lines that name the caller of the open transaction. */
    fun auditTrail(): List<Map<String, Any?>>
}
