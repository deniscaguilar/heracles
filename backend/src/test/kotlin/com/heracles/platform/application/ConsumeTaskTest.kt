package com.heracles.platform.application

import com.heracles.platform.application.port.out.ProcessedMessages
import com.heracles.platform.application.port.out.TaskHandler
import com.heracles.platform.application.port.out.TaskQueue
import com.heracles.platform.application.port.out.TransactionContext
import com.heracles.platform.domain.AccessContext
import com.heracles.platform.domain.QueueMessage
import com.heracles.platform.domain.ReceivedMessage
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.UUID

private class ConsumeTransactions : TransactionContext {
    val contexts = mutableListOf<AccessContext>()
    override fun <T> run(context: AccessContext, block: () -> T): T = block().also { contexts += context }
}

private class SeenMessages : ProcessedMessages {
    val seen = mutableSetOf<Pair<String, String>>()
    override fun markProcessed(consumer: String, messageId: String): Boolean = seen.add(consumer to messageId)
}

private class AckQueue : TaskQueue {
    val acknowledged = mutableListOf<String>()
    override fun send(message: QueueMessage) = Unit
    override fun receive(maxMessages: Int, waitSeconds: Int): List<ReceivedMessage> = emptyList()
    override fun acknowledge(receipt: String) { acknowledged += receipt }
    override fun postpone(receipt: String, delay: Duration) = Unit
}

class ConsumeTaskTest {
    private val transactions = ConsumeTransactions()
    private val seen = SeenMessages()
    private val queue = AckQueue()
    private val handled = mutableListOf<QueueMessage>()
    private val handler = TaskHandler { handled += it }
    private val consume = ConsumeTask(transactions, seen, queue, mapOf("probe.note" to handler))

    private fun delivery(id: String = UUID.randomUUID().toString(), ids: Map<String, String> = emptyMap(), receipt: String = "r-1") =
        ReceivedMessage(QueueMessage(id, "probe.note", ids), receipt)

    @Test
    fun `a new message is handled once and acknowledged`() {
        val delivery = delivery()
        assertThat(consume(delivery)).isEqualTo(ConsumeOutcome.PROCESSED)
        assertThat(handled).containsExactly(delivery.message)
        assertThat(queue.acknowledged).containsExactly("r-1")
    }

    @Test
    fun `the same message delivered twice has one effect and both deliveries are acknowledged`() {
        val id = UUID.randomUUID().toString()
        assertThat(consume(delivery(id, receipt = "r-1"))).isEqualTo(ConsumeOutcome.PROCESSED)
        assertThat(consume(delivery(id, receipt = "r-2"))).isEqualTo(ConsumeOutcome.DUPLICATE)
        assertThat(handled).hasSize(1)
        assertThat(queue.acknowledged).containsExactly("r-1", "r-2")
    }

    @Test
    fun `a failing handler leaves the message unacknowledged, so the queue delivers it again`() {
        val failing = ConsumeTask(transactions, seen, queue, mapOf("probe.note" to TaskHandler { error("boom") }))
        assertThatThrownBy { failing(delivery()) }.hasMessage("boom")
        assertThat(queue.acknowledged).isEmpty()
    }

    @Test
    fun `a message of an unknown type is refused without being acknowledged`() {
        val unknown = ReceivedMessage(QueueMessage(UUID.randomUUID().toString(), "nobody.handles.this", emptyMap()), "r-9")
        assertThatThrownBy { consume(unknown) }.isInstanceOf(UnknownTaskTypeException::class.java)
        assertThat(queue.acknowledged).isEmpty()
        assertThat(seen.seen).isEmpty()
    }

    @Test
    fun `the work runs in the scope of the user named in the message, or in the system scope without one`() {
        val user = UUID.randomUUID()
        consume(delivery(ids = mapOf("userId" to user.toString())))
        consume(delivery(ids = mapOf("examId" to "e-1")))
        assertThat(transactions.contexts).containsExactly(AccessContext.forUser(user), AccessContext.system())
    }
}
