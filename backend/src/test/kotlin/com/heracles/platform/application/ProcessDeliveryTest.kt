package com.heracles.platform.application

import com.heracles.platform.application.port.out.DeadLetters
import com.heracles.platform.application.port.out.ProcessedMessages
import com.heracles.platform.application.port.out.TaskHandler
import com.heracles.platform.application.port.out.TaskQueue
import com.heracles.platform.application.port.out.TaskStatuses
import com.heracles.platform.application.port.out.TransactionContext
import com.heracles.platform.domain.AccessContext
import com.heracles.platform.domain.Backoff
import com.heracles.platform.domain.QueueMessage
import com.heracles.platform.domain.ReceivedMessage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.UUID

private class DeliveryTransactions : TransactionContext {
    override fun <T> run(context: AccessContext, block: () -> T): T = block()
}

private class DeliveryMarkers : ProcessedMessages {
    val seen = mutableSetOf<String>()
    override fun markProcessed(consumer: String, messageId: String) = seen.add(messageId)
}

private class DeliveryQueue : TaskQueue {
    val acknowledged = mutableListOf<String>()
    val postponed = mutableListOf<Pair<String, Duration>>()
    override fun send(message: QueueMessage) = Unit
    override fun receive(maxMessages: Int, waitSeconds: Int): List<ReceivedMessage> = emptyList()
    override fun acknowledge(receipt: String) { acknowledged += receipt }
    override fun postpone(receipt: String, delay: Duration) { postponed += receipt to delay }
}

private class DeadLetterBox : DeadLetters {
    val received = mutableListOf<QueueMessage>()
    override fun send(message: QueueMessage) { received += message }
    override fun find(messageId: String): ReceivedMessage? = null
    override fun remove(receipt: String) = Unit
}

private class RecordedStatuses : TaskStatuses {
    val calls = mutableListOf<String>()
    override fun markDone(taskId: UUID) { calls += "done:$taskId" }
    override fun markRetrying(taskId: UUID, attempts: Int, errorCode: String) { calls += "retrying:$taskId:$attempts:$errorCode" }
    override fun markFailed(taskId: UUID, attempts: Int, errorCode: String) { calls += "failed:$taskId:$attempts:$errorCode" }
    override fun markRequeued(taskId: UUID) { calls += "requeued:$taskId" }
}

class ProcessDeliveryTest {
    private val queue = DeliveryQueue()
    private val deadLetters = DeadLetterBox()
    private val statuses = RecordedStatuses()
    private val taskId = UUID.randomUUID()
    private var handled = 0
    private var failWith: Exception? = null
    private val handler = TaskHandler { handled++; failWith?.let { throw it } }

    private fun processor(maxAttempts: Int = 3) = ProcessDelivery(
        ConsumeTask(DeliveryTransactions(), DeliveryMarkers(), queue, mapOf("probe.note" to handler)),
        queue, deadLetters, statuses, maxAttempts,
    )

    private fun delivery(receiveCount: Int, withTask: Boolean = true, type: String = "probe.note", receipt: String = "r$receiveCount") =
        ReceivedMessage(
            QueueMessage(UUID.randomUUID().toString(), type, if (withTask) mapOf("taskId" to taskId.toString()) else emptyMap()),
            receipt, receiveCount,
        )

    @Test
    fun `a success acknowledges and marks the task DONE`() {
        assertThat(processor()(delivery(1))).isEqualTo(DeliveryOutcome.PROCESSED)
        assertThat(statuses.calls).containsExactly("done:$taskId")
        assertThat(queue.acknowledged).containsExactly("r1")
    }

    @Test
    fun `a failure before the last attempt postpones the message with a growing wait, without acknowledging`() {
        failWith = IllegalStateException("secret detail that must not be stored")
        assertThat(processor()(delivery(2))).isEqualTo(DeliveryOutcome.RETRY_SCHEDULED)

        assertThat(queue.postponed).containsExactly("r2" to Backoff.delay(2))
        assertThat(queue.acknowledged).isEmpty()
        assertThat(deadLetters.received).isEmpty()
        // Only the kind of failure is stored, never its message.
        assertThat(statuses.calls).containsExactly("retrying:$taskId:2:IllegalStateException")
    }

    @Test
    fun `the wait grows from one failure to the next`() {
        failWith = IllegalStateException()
        val processor = processor(maxAttempts = 10)
        (1..4).forEach { processor(delivery(it)) }
        assertThat(queue.postponed.map { it.second }).containsExactly(*(1..4).map { Backoff.delay(it) }.toTypedArray())
    }

    @Test
    fun `at the last attempt the message goes to the dead letter queue, the task is FAILED and the original is acknowledged`() {
        failWith = IllegalStateException("boom")
        val delivery = delivery(3)
        assertThat(processor(maxAttempts = 3)(delivery)).isEqualTo(DeliveryOutcome.DEAD_LETTERED)

        assertThat(deadLetters.received).containsExactly(delivery.message)
        assertThat(queue.acknowledged).containsExactly("r3")
        assertThat(queue.postponed).isEmpty()
        assertThat(statuses.calls).containsExactly("failed:$taskId:3:IllegalStateException")
    }

    @Test
    fun `a message of unknown type fails the same way`() {
        assertThat(processor(maxAttempts = 1)(delivery(1, type = "nobody.handles.this"))).isEqualTo(DeliveryOutcome.DEAD_LETTERED)
        assertThat(statuses.calls).containsExactly("failed:$taskId:1:UnknownTaskTypeException")
    }

    @Test
    fun `a message without a task id touches no task`() {
        failWith = IllegalStateException()
        processor(maxAttempts = 1)(delivery(1, withTask = false))
        assertThat(statuses.calls).isEmpty()
        assertThat(deadLetters.received).hasSize(1)
    }

    @Test
    fun `a duplicate delivery acknowledges and still marks the task DONE`() {
        val processor = processor()
        val first = delivery(1)
        processor(first)
        assertThat(processor(ReceivedMessage(first.message, "r-again", 2))).isEqualTo(DeliveryOutcome.DUPLICATE)
        assertThat(handled).isEqualTo(1)
        assertThat(statuses.calls).containsExactly("done:$taskId", "done:$taskId")
    }
}
