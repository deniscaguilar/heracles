package com.heracles.platform.application

import com.heracles.platform.application.port.out.AuditTrail
import com.heracles.platform.application.port.out.DeadLetters
import com.heracles.platform.application.port.out.TaskQueue
import com.heracles.platform.application.port.out.TaskStatuses
import com.heracles.platform.domain.AppUser
import com.heracles.platform.domain.AuditEvent
import com.heracles.platform.domain.AuditResult
import com.heracles.platform.domain.DeadLetterNotFoundException
import com.heracles.platform.domain.QueueMessage
import com.heracles.platform.domain.ReceivedMessage
import com.heracles.platform.domain.UserRole
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.UUID

private class Box(val messages: MutableList<ReceivedMessage>) : DeadLetters {
    val removed = mutableListOf<String>()
    override fun send(message: QueueMessage) { messages += ReceivedMessage(message, "r-${message.id}") }
    override fun find(messageId: String): ReceivedMessage? = messages.firstOrNull { it.message.id == messageId }
    override fun remove(receipt: String) { removed += receipt; messages.removeIf { it.receipt == receipt } }
}

private class MainQueue : TaskQueue {
    val sent = mutableListOf<QueueMessage>()
    override fun send(message: QueueMessage) { sent += message }
    override fun receive(maxMessages: Int, waitSeconds: Int): List<ReceivedMessage> = emptyList()
    override fun acknowledge(receipt: String) = Unit
    override fun postpone(receipt: String, delay: Duration) = Unit
}

private class RequeueStatuses : TaskStatuses {
    val requeued = mutableListOf<UUID>()
    override fun markDone(taskId: UUID) = Unit
    override fun markRetrying(taskId: UUID, attempts: Int, errorCode: String) = Unit
    override fun markFailed(taskId: UUID, attempts: Int, errorCode: String) = Unit
    override fun markRequeued(taskId: UUID) { requeued += taskId }
}

private class AuditLog : AuditTrail {
    val events = mutableListOf<AuditEvent>()
    override fun append(event: AuditEvent): UUID = UUID.randomUUID().also { events += event }
}

class ReprocessDeadLetterTest {
    private val taskId = UUID.randomUUID()
    private val message = QueueMessage(UUID.randomUUID().toString(), "probe.note", mapOf("taskId" to taskId.toString()))
    private val box = Box(mutableListOf(ReceivedMessage(message, "r-dlq")))
    private val queue = MainQueue()
    private val statuses = RequeueStatuses()
    private val audit = AuditLog()
    private val admin = AppUser(UUID.randomUUID(), "sub-admin", UserRole.ADMIN)
    private val reprocess = ReprocessDeadLetter(box, queue, statuses, RecordAudit(audit))

    @Test
    fun `the message goes back to the work queue, leaves the dead letter queue and its task is requeued`() {
        reprocess(admin, message.id)

        assertThat(queue.sent).containsExactly(message)
        assertThat(box.messages).isEmpty()
        assertThat(statuses.requeued).containsExactly(taskId)
    }

    @Test
    fun `it is sent first and removed after, so a crash in between duplicates instead of losing`() {
        val order = mutableListOf<String>()
        val ordered = object : DeadLetters by box {
            override fun remove(receipt: String) { order += "remove"; box.remove(receipt) }
        }
        val sendingQueue = object : TaskQueue by queue {
            override fun send(message: QueueMessage) { order += "send"; queue.send(message) }
        }
        ReprocessDeadLetter(ordered, sendingQueue, statuses, RecordAudit(audit))(admin, message.id)
        assertThat(order).containsExactly("send", "remove")
    }

    @Test
    fun `the admin action is audited with the actor and the message id, never the content`() {
        reprocess(admin, message.id)
        val event = audit.events.single()
        assertThat(event.action).isEqualTo("dead_letter.reprocess")
        assertThat(event.result).isEqualTo(AuditResult.SUCCESS)
        assertThat(event.actorUserId).isEqualTo(admin.id)
        assertThat(event.entityType).isEqualTo("dead_letter")
        assertThat(event.entityId).isEqualTo(message.id)
        assertThat(event.fields).isEmpty()
    }

    @Test
    fun `an unknown message is not found and nothing happens`() {
        assertThatThrownBy { reprocess(admin, "no-such-message") }.isInstanceOf(DeadLetterNotFoundException::class.java)
        assertThat(queue.sent).isEmpty()
        assertThat(audit.events).isEmpty()
    }

    @Test
    fun `a message without a task is requeued without touching any task`() {
        val plain = QueueMessage(UUID.randomUUID().toString(), "probe.note", emptyMap())
        box.messages += ReceivedMessage(plain, "r-plain")
        reprocess(admin, plain.id)
        assertThat(queue.sent).containsExactly(plain)
        assertThat(statuses.requeued).isEmpty()
    }
}
