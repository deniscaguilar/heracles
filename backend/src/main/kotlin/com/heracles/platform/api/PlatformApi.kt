package com.heracles.platform.api

import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** The authenticated caller: the platform user id and role name, read from the database (never from the token). */
data class CurrentUser(val id: UUID, val role: String) {
    val isAdmin: Boolean get() = role == "ADMIN"
}

/** The caller of the request being handled, or null when there is none (an anonymous request). */
interface CurrentUserProvider {
    fun current(): CurrentUser?
}

/** For whom a database transaction runs (ADR-0003). A user sees only their own rows; admin and system see no health data. */
enum class Scope { USER, ADMIN, SYSTEM }

/** Runs a block in one transaction that knows its scope, so row-level security applies. Nesting is refused. */
interface Transactions {
    fun <T> asUser(userId: UUID, block: () -> T): T
    fun <T> asAdmin(block: () -> T): T
    fun <T> asSystem(block: () -> T): T
}

enum class Outcome { SUCCESS, DENIED, FAILURE }

/** Append-only audit trail. Names of fields and identifiers only, never a value (CLAUDE.md, section 4). Joins the open transaction. */
interface AuditLog {
    fun record(
        actorUserId: UUID?,
        actorScope: Scope,
        action: String,
        entityType: String,
        entityId: String?,
        outcome: Outcome,
        fields: List<String> = emptyList(),
    )
}

/** The time, always with a zone (America/Sao_Paulo by default). */
interface ClockProvider {
    fun now(): Instant
    val zone: ZoneId
}

/** What a task handler receives: an id, a type and identifiers. Never health or personal data. */
data class TaskMessage(val id: String, val type: String, val ids: Map<String, String>)

/** A module registers one bean of this kind per message type it handles. Runs in the consumer's transaction, in the user's scope. */
class TaskHandlerRegistration(val type: String, val handler: (TaskMessage) -> Unit)

/** Creates the status row of a task and puts its message in the outbox, in the open transaction. */
interface TaskGateway {
    /** Returns the task id. [userId] null is a system task. The message carries `taskId` (and `userId`) among [ids]. */
    fun enqueue(userId: UUID?, type: String, ids: Map<String, String> = emptyMap()): UUID
}

/** Puts an event in the outbox, in the open transaction: it leaves for the queue only if the transaction commits. Identifiers only. */
interface EventPublisher {
    fun publish(aggregateType: String, aggregateId: String, type: String, ids: Map<String, String>)
}

/** Sends a template to a person through the notification channel (a local fake in this stage). Identifiers only. */
interface Notifier {
    fun send(userId: UUID, template: String, params: Map<String, String> = emptyMap())
}

/** A module says what it exports for the data subject (LGPD). Runs in the user's scope, so row-level security decides what is theirs. */
interface ExportContributor {
    /** Name of the section in the export file. */
    val section: String

    /** Plain maps, lists, strings and numbers (it becomes JSON). Called inside a transaction in the person's scope. */
    fun export(userId: UUID): Any?
}

/** A module says how it erases the data of one person. Must be safe to run again. */
interface ErasureStep {
    /** Lower runs first. */
    val order: Int

    /** Called inside one transaction in the system scope, because the data stops being reachable by its owner. */
    fun erase(userId: UUID)
}

/** Thrown by a module's web adapter to answer with a status and a stable error code (Problem Details, docs/api-conventions.md). */
class ApiProblemException(val status: Int, val code: String, val fields: List<String> = emptyList()) :
    RuntimeException("$status $code")
