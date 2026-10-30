package com.heracles.platform.application

import com.heracles.platform.application.port.out.OutboxStore
import com.heracles.platform.application.port.out.TaskQueue
import com.heracles.platform.application.port.out.TransactionContext
import com.heracles.platform.domain.AccessContext
import com.heracles.platform.domain.AccessScope
import com.heracles.platform.domain.Backoff
import com.heracles.platform.domain.OutboxEntry
import com.heracles.platform.domain.QueueMessage
import com.heracles.platform.domain.ReceivedMessage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.Duration
import java.util.UUID

private class PublishTransactions : TransactionContext {
    val contexts = mutableListOf<AccessContext>()
    override fun <T> run(context: AccessContext, block: () -> T): T = block().also { contexts += context }
}

private class PublishStore(val entries: MutableList<OutboxEntry>) : OutboxStore {
    val published = mutableMapOf<UUID, Instant>()
    val failed = mutableMapOf<UUID, Pair<Int, Instant>>()
    var lastLimit = 0
    override fun claimDue(limit: Int, now: Instant): List<OutboxEntry> = entries.take(limit).also { lastLimit = limit }
    override fun markPublished(id: UUID, at: Instant) { published[id] = at }
    override fun markFailed(id: UUID, attempts: Int, nextAttemptAt: Instant) { failed[id] = attempts to nextAttemptAt }
}

private class PublishQueue(private val failOn: Set<String> = emptySet()) : TaskQueue {
    val sent = mutableListOf<QueueMessage>()
    override fun send(message: QueueMessage) {
        if (message.id in failOn) error("queue is down")
        sent += message
    }
    override fun receive(maxMessages: Int, waitSeconds: Int): List<ReceivedMessage> = emptyList()
    override fun acknowledge(receipt: String) = Unit
    override fun postpone(receipt: String, delay: Duration) = Unit
}

class PublishOutboxTest {
    private val now = Instant.parse("2026-10-30T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private fun entry(attempts: Int = 0) = OutboxEntry(UUID.randomUUID(), "exam.imported", mapOf("examId" to "e-1"), attempts)

    @Test
    fun `due messages are sent to the queue and marked as published`() {
        val a = entry(); val b = entry()
        val store = PublishStore(mutableListOf(a, b)); val queue = PublishQueue(); val transactions = PublishTransactions()
        val result = PublishOutbox(transactions, store, queue, clock, batchSize = 10)()

        assertThat(queue.sent.map { it.id }).containsExactly(a.id.toString(), b.id.toString())
        assertThat(queue.sent.first()).isEqualTo(QueueMessage(a.id.toString(), "exam.imported", mapOf("examId" to "e-1")))
        assertThat(store.published.keys).containsExactlyInAnyOrder(a.id, b.id)
        assertThat(result).isEqualTo(PublishResult(published = 2, failed = 0))
        assertThat(transactions.contexts).containsExactly(AccessContext.system())
    }

    @Test
    fun `a failure schedules the retry with a growing wait and does not stop the others`() {
        val broken = entry(attempts = 2); val fine = entry()
        val store = PublishStore(mutableListOf(broken, fine)); val queue = PublishQueue(failOn = setOf(broken.id.toString()))
        val result = PublishOutbox(PublishTransactions(), store, queue, clock, batchSize = 10)()

        assertThat(result).isEqualTo(PublishResult(published = 1, failed = 1))
        assertThat(store.failed[broken.id]).isEqualTo(3 to now.plus(Backoff.delay(3)))
        assertThat(store.published).containsOnlyKeys(fine.id)
        assertThat(queue.sent.map { it.id }).containsExactly(fine.id.toString())
    }

    @Test
    fun `the batch size limits what is claimed`() {
        val store = PublishStore(MutableList(5) { entry() })
        val result = PublishOutbox(PublishTransactions(), store, PublishQueue(), clock, batchSize = 3)()
        assertThat(store.lastLimit).isEqualTo(3)
        assertThat(result.published).isEqualTo(3)
    }

    @Test
    fun `an entry that cannot become a queue message counts as a failure instead of breaking the batch`() {
        val poison = OutboxEntry(UUID.randomUUID(), "Not A Type", emptyMap(), 0)
        val store = PublishStore(mutableListOf(poison, entry()))
        val result = PublishOutbox(PublishTransactions(), store, PublishQueue(), clock, batchSize = 10)()
        assertThat(result).isEqualTo(PublishResult(published = 1, failed = 1))
        assertThat(store.failed).containsKey(poison.id)
    }
}
