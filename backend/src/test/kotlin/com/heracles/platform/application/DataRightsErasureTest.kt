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
import com.heracles.platform.application.port.out.ErasureStatus
import com.heracles.platform.application.port.out.IdentityProvider
import com.heracles.platform.domain.Identity
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** E2-S8.4 (CA14): the 7 days by the port's clock, the order of the steps, and a task that can run twice. */
class DataRightsErasureTest {
    private class Tx : Transactions {
        override fun <T> asUser(userId: UUID, block: () -> T): T = block()
        override fun <T> asAdmin(block: () -> T): T = block()
        override fun <T> asSystem(block: () -> T): T = block()
    }

    private class Now(var at: Instant) : Clock { override fun instant() = at }

    private class Erasures : AccountErasures {
        val rows = mutableListOf<AccountErasure>()
        private fun replace(id: UUID, status: ErasureStatus) { rows.replaceAll { if (it.id == id) it.copy(status = status) else it } }
        override fun open(userId: UUID) = rows.firstOrNull { it.userId == userId && it.status in setOf(ErasureStatus.PENDING, ErasureStatus.RUNNING) }
        override fun create(userId: UUID, requestedAt: Instant, dueAt: Instant) = AccountErasure(UUID.randomUUID(), userId, requestedAt, dueAt, ErasureStatus.PENDING).also { rows += it }
        override fun cancel(userId: UUID): Boolean {
            val pending = rows.firstOrNull { it.userId == userId && it.status == ErasureStatus.PENDING } ?: return false
            replace(pending.id, ErasureStatus.CANCELED); return true
        }
        override fun due(now: Instant) = rows.filter { it.status == ErasureStatus.PENDING && !it.dueAt.isAfter(now) }
        override fun markRunning(id: UUID) = replace(id, ErasureStatus.RUNNING)
        override fun markDone(userId: UUID) { rows.filter { it.userId == userId && it.status != ErasureStatus.CANCELED }.forEach { replace(it.id, ErasureStatus.DONE) } }
    }

    private class Records(var subject: String?) : AccountRecords {
        val calls = mutableListOf<String>()
        override fun subjectOf(userId: UUID) = subject
        override fun empty(userId: UUID) { calls += "empty"; subject = null }
        override fun unlinkAuditTrail(userId: UUID) { calls += "unlink" }
    }

    private class Audit : AuditLog {
        val actions = mutableListOf<String>()
        override fun record(actorUserId: UUID?, actorScope: Scope, action: String, entityType: String, entityId: String?, outcome: Outcome, fields: List<String>) { actions += action }
    }

    private class Tasks : TaskGateway {
        val enqueued = mutableListOf<Pair<String, Map<String, String>>>()
        override fun enqueue(userId: UUID?, type: String, ids: Map<String, String>): UUID { enqueued += type to ids; return UUID.randomUUID() }
    }

    private val now = Now(Instant.parse("2026-11-04T12:00:00Z"))
    private val erasures = Erasures()
    private val audit = Audit()
    private val user = UUID.randomUUID()

    @Test
    fun `asking creates one request due in 7 days, and asking again returns the same`() {
        val request = RequestErasure(Tx(), erasures, audit, now)
        val first = request(user)
        assertThat(first.dueAt).isEqualTo(now.at.plus(Duration.ofDays(7)))
        now.at = now.at.plusSeconds(3600)
        assertThat(request(user)).isEqualTo(first)
        assertThat(erasures.rows).hasSize(1)
        assertThat(audit.actions).containsExactly("erasure.requested")
    }

    @Test
    fun `the person can cancel while pending, then ask again, and cancel with nothing open is not found`() {
        val request = RequestErasure(Tx(), erasures, audit, now)
        val cancel = CancelErasure(Tx(), erasures, audit)
        assertThatThrownBy { cancel(user) }.isInstanceOf(ErasureNotFoundException::class.java)
        request(user)
        cancel(user)
        assertThatThrownBy { GetErasure(Tx(), erasures)(user) }.isInstanceOf(ErasureNotFoundException::class.java)
        assertThat(request(user).status).isEqualTo(ErasureStatus.PENDING) // a new one
        assertThat(audit.actions).containsExactly("erasure.requested", "erasure.canceled", "erasure.requested")
    }

    @Test
    fun `a request becomes a task only when its 7 days are over, once, and then it cannot be canceled`() {
        RequestErasure(Tx(), erasures, audit, now)(user)
        val tasks = Tasks()
        val queue = QueueDueErasures(Tx(), erasures, tasks, now)

        now.at = now.at.plus(Duration.ofDays(7)).minusSeconds(1)
        assertThat(queue()).isZero()
        now.at = now.at.plusSeconds(1)
        assertThat(queue()).isEqualTo(1)
        assertThat(queue()).isZero() // already running: not queued twice
        assertThat(tasks.enqueued).containsExactly(ErasureRules.TASK_TYPE to mapOf("subjectId" to user.toString()))
        assertThatThrownBy { CancelErasure(Tx(), erasures, audit)(user) }.isInstanceOf(ErasureNotFoundException::class.java)
    }

    private class Step(override val order: Int, val log: MutableList<String>, val failOnce: Boolean = false) : ErasureStep {
        var failed = false
        override fun erase(userId: UUID) {
            if (failOnce && !failed) { failed = true; throw IllegalStateException("boom") }
            log += "step$order"
        }
    }

    private val identity = object : IdentityProvider {
        val deleted = mutableListOf<String>()
        override fun verify(token: String): Identity = error("not used")
        override fun deleteUser(subject: String) { deleted += subject }
    }

    private fun message() = TaskMessage("t", ErasureRules.TASK_TYPE, mapOf("subjectId" to user.toString(), "taskId" to "t"))

    @Test
    fun `the steps run in order, then the identity, then the audit unlink, then the account is emptied`() {
        val log = mutableListOf<String>()
        val records = Records("sub-1")
        RequestErasure(Tx(), erasures, audit, now)(user)
        RunErasure(listOf(Step(30, log), Step(10, log), Step(20, log)), records, identity, erasures, audit).handle(message())

        assertThat(log).containsExactly("step10", "step20", "step30")
        assertThat(identity.deleted).containsExactly("sub-1")
        assertThat(records.calls).containsExactly("unlink", "empty")
        assertThat(erasures.rows.single().status).isEqualTo(ErasureStatus.DONE)
        assertThat(audit.actions).contains("erasure.completed")
    }

    @Test
    fun `running the task twice gives the same result and calls the identity provider once`() {
        val log = mutableListOf<String>()
        val records = Records("sub-1")
        RequestErasure(Tx(), erasures, audit, now)(user)
        val run = RunErasure(listOf(Step(10, log)), records, identity, erasures, audit)
        run.handle(message())
        run.handle(message())

        assertThat(identity.deleted).containsExactly("sub-1")
        assertThat(records.subject).isNull()
        assertThat(erasures.rows.single().status).isEqualTo(ErasureStatus.DONE)
    }

    @Test
    fun `a failure in the middle leaves the account and its subject untouched, and the retry finishes the job`() {
        val log = mutableListOf<String>()
        val records = Records("sub-1")
        RequestErasure(Tx(), erasures, audit, now)(user)
        val run = RunErasure(listOf(Step(10, log), Step(20, log, failOnce = true)), records, identity, erasures, audit)

        assertThatThrownBy { run.handle(message()) }.isInstanceOf(IllegalStateException::class.java)
        assertThat(records.subject).isEqualTo("sub-1")
        assertThat(identity.deleted).isEmpty()
        run.handle(message())
        assertThat(identity.deleted).containsExactly("sub-1")
        assertThat(records.subject).isNull()
    }

    @Test
    fun `two steps cannot share an order`() {
        assertThatThrownBy { RunErasure(listOf(Step(10, mutableListOf()), Step(10, mutableListOf())), Records("s"), identity, erasures, audit) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
