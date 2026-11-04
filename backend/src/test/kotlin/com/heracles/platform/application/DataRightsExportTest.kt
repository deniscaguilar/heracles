package com.heracles.platform.application

import com.heracles.platform.api.AuditLog
import com.heracles.platform.api.ExportContributor
import com.heracles.platform.api.Outcome
import com.heracles.platform.api.Scope
import com.heracles.platform.api.TaskGateway
import com.heracles.platform.api.TaskMessage
import com.heracles.platform.api.Transactions
import com.heracles.platform.application.port.out.Clock
import com.heracles.platform.application.port.out.DataExport
import com.heracles.platform.application.port.out.DataExports
import com.heracles.platform.application.port.out.PersonalData
import com.heracles.platform.application.port.out.TaskReader
import com.heracles.platform.domain.StorageKey
import com.heracles.platform.domain.TaskNotFoundException
import com.heracles.platform.domain.TaskView
import com.heracles.support.InMemoryFileStorage
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

class DataRightsExportTest {
    private class Tx : Transactions {
        override fun <T> asUser(userId: UUID, block: () -> T): T = block()
        override fun <T> asAdmin(block: () -> T): T = block()
        override fun <T> asSystem(block: () -> T): T = block()
    }

    private class Now(var at: Instant) : Clock { override fun instant() = at }

    private class Exports : DataExports {
        val rows = mutableMapOf<UUID, DataExport>()
        override fun save(export: DataExport) { rows.putIfAbsent(export.taskId, export) }
        override fun find(taskId: UUID) = rows[taskId]
        override fun expired(now: Instant) = rows.values.filter { !it.expiresAt.isAfter(now) }
        override fun delete(taskId: UUID) { rows.remove(taskId) }
        override fun ofUser(userId: UUID) = rows.values.filter { it.userId == userId }
    }

    private class Audit : AuditLog {
        val actions = mutableListOf<String>()
        override fun record(actorUserId: UUID?, actorScope: Scope, action: String, entityType: String, entityId: String?, outcome: Outcome, fields: List<String>) { actions += action }
    }

    private class Tasks(val known: MutableSet<UUID> = mutableSetOf()) : TaskReader, TaskGateway {
        override fun find(id: UUID) = if (id in known) TaskView(id, "DONE", null) else null
        override fun enqueue(userId: UUID?, type: String, ids: Map<String, String>) = UUID.randomUUID().also { known += it }
    }

    private fun contributor(name: String, data: Any?) = object : ExportContributor {
        override val section = name
        override fun export(userId: UUID) = data
    }

    private val now = Now(Instant.parse("2026-11-04T12:00:00Z"))
    private val storage = InMemoryFileStorage()
    private val exports = Exports()
    private val audit = Audit()
    private val tasks = Tasks()
    private val user = UUID.randomUUID()
    private val personal = object : PersonalData {
        override fun account(userId: UUID) = mapOf<String, Any?>("id" to userId.toString())
        override fun auditTrail() = listOf(mapOf<String, Any?>("action" to "x"))
    }
    private val json = tools.jackson.databind.json.JsonMapper()
    private fun run(contributors: List<ExportContributor> = listOf(contributor("anamnesis", listOf("a")), contributor("consents", listOf("c")))) =
        RunExport(contributors, personal, storage, exports, now, audit) { json.writeValueAsBytes(it) }

    private fun message(task: UUID) = TaskMessage(task.toString(), ExportRules.TASK_TYPE, mapOf("userId" to user.toString(), "taskId" to task.toString()))

    @Test
    fun `the file has the platform sections and every module section, under a key of the person's own`() {
        val task = UUID.randomUUID()
        run().handle(message(task))

        val saved = exports.rows.getValue(task)
        assertThat(saved.storageKey).isEqualTo("exports/$user/$task.json")
        assertThat(saved.expiresAt).isEqualTo(now.at.plus(Duration.ofHours(24)))
        val doc = json.readTree(storage.files.getValue(saved.storageKey))
        assertThat(doc.propertyNames().toList()).containsExactly("generatedAt", "account", "auditTrail", "anamnesis", "consents")
        assertThat(doc.path("account").path("id").asString()).isEqualTo(user.toString())
        assertThat(audit.actions).containsExactly("export.completed")
    }

    @Test
    fun `the same message twice writes one file and one reference`() {
        val task = UUID.randomUUID()
        val handler = run()
        handler.handle(message(task))
        val first = storage.files.getValue("exports/$user/$task.json")
        now.at = now.at.plusSeconds(60)
        handler.handle(message(task))

        assertThat(exports.rows).hasSize(1)
        assertThat(storage.files).hasSize(1)
        assertThat(storage.files.getValue("exports/$user/$task.json")).isEqualTo(first)
        assertThat(audit.actions).hasSize(1)
    }

    @Test
    fun `two modules cannot claim the same section`() {
        assertThatThrownBy { run(listOf(contributor("consents", 1), contributor("consents", 2))) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { run(listOf(contributor("account", 1))) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `requesting creates a task and an audit line`() {
        val id = RequestExport(Tx(), tasks, audit)(user)
        assertThat(tasks.known).contains(id)
        assertThat(audit.actions).containsExactly("export.requested")
    }

    @Test
    fun `the link lasts 15 minutes, is refused before the file exists and after its 24 hours`() {
        val task = tasks.enqueue(user, ExportRules.TASK_TYPE)
        val get = GetExport(Tx(), tasks, exports, storage, now)
        assertThatThrownBy { get(user, task) }.isInstanceOf(ExportNotReadyException::class.java)
        assertThatThrownBy { get(user, UUID.randomUUID()) }.isInstanceOf(TaskNotFoundException::class.java)

        run().handle(message(task))
        val link = get(user, task)
        assertThat(link.url).contains("valid=900")
        assertThat(storage.urlValidity[link.url]).isEqualTo(Duration.ofMinutes(15))

        now.at = now.at.plus(Duration.ofHours(24))
        assertThatThrownBy { get(user, task) }.isInstanceOf(ExportExpiredException::class.java)
    }

    @Test
    fun `the clean-up deletes the file and the reference after 24 hours, and only then, and is safe to repeat`() {
        val task = UUID.randomUUID()
        run().handle(message(task))
        val expire = ExpireExports(Tx(), exports, storage, now)

        now.at = now.at.plus(Duration.ofHours(23))
        assertThat(expire()).isZero()
        assertThat(storage.files).hasSize(1)

        now.at = now.at.plus(Duration.ofHours(1))
        assertThat(expire()).isEqualTo(1)
        assertThat(storage.files).isEmpty()
        assertThat(exports.rows).isEmpty()
        assertThat(expire()).isZero()
        assertThat(storage.get(StorageKey("exports/$user/$task.json"))).isNull()
    }
}
