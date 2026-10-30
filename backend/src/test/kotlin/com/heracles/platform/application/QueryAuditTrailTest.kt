package com.heracles.platform.application

import com.heracles.platform.application.port.out.AuditReader
import com.heracles.platform.application.port.out.TransactionContext
import com.heracles.platform.domain.AccessContext
import com.heracles.platform.domain.AccessScope
import com.heracles.platform.domain.AuditCursor
import com.heracles.platform.domain.AuditRecord
import com.heracles.platform.domain.AuditResult
import com.heracles.platform.domain.InvalidCursorException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

private class FakeTransactions : TransactionContext {
    val contexts = mutableListOf<AccessContext>()
    override fun <T> run(context: AccessContext, block: () -> T): T {
        contexts += context
        return block()
    }
}

/** Newest first, like the real reader; honours the cursor and the limit it is given. */
private class FakeReader(val all: List<AuditRecord>) : AuditReader {
    var lastLimit = 0
    override fun find(after: AuditCursor?, limit: Int): List<AuditRecord> {
        lastLimit = limit
        val sorted = all.sortedWith(compareByDescending<AuditRecord> { it.occurredAt }.thenByDescending { it.id })
        val rest = if (after == null) sorted else sorted.filter {
            it.occurredAt < after.occurredAt || (it.occurredAt == after.occurredAt && it.id < after.id)
        }
        return rest.take(limit)
    }
}

class QueryAuditTrailTest {
    private val base = Instant.parse("2026-10-30T12:00:00Z")
    private fun record(n: Int) = AuditRecord(
        id = UUID(0, n.toLong()), occurredAt = base.plusSeconds(n.toLong()), actorUserId = null, actorScope = AccessScope.SYSTEM,
        action = "probe.$n", entityType = "route", entityId = null, result = AuditResult.SUCCESS, fields = emptyList(), traceId = null,
    )

    private val transactions = FakeTransactions()
    private fun query(count: Int) = FakeReader((1..count).map(::record)).let { reader -> reader to QueryAuditTrail(transactions, reader) }

    @Test
    fun `reads in the admin scope, newest first, and tells where the next page starts`() {
        val (_, queryAuditTrail) = query(5)
        val page = queryAuditTrail(null, 2)

        assertThat(transactions.contexts).containsExactly(AccessContext.admin())
        assertThat(page.items.map { it.action }).containsExactly("probe.5", "probe.4")
        assertThat(page.nextCursor).isNotNull()
    }

    @Test
    fun `following the cursors visits every event once and ends with no cursor`() {
        val (_, queryAuditTrail) = query(5)
        val seen = mutableListOf<String>()
        var cursor: String? = null
        do {
            val page = queryAuditTrail(cursor, 2)
            seen += page.items.map { it.action }
            cursor = page.nextCursor
        } while (cursor != null)

        assertThat(seen).containsExactly("probe.5", "probe.4", "probe.3", "probe.2", "probe.1")
    }

    @Test
    fun `the last full page has no next cursor`() {
        val (_, queryAuditTrail) = query(4)
        val second = queryAuditTrail(queryAuditTrail(null, 2).nextCursor, 2)
        assertThat(second.items).hasSize(2)
        assertThat(second.nextCursor).isNull()
    }

    @Test
    fun `the limit is kept between 1 and 200`() {
        val (reader, queryAuditTrail) = query(3)
        queryAuditTrail(null, 0)
        assertThat(reader.lastLimit).isEqualTo(2) // 1 + the extra row used to detect a next page
        queryAuditTrail(null, 100_000)
        assertThat(reader.lastLimit).isEqualTo(201)
    }

    @Test
    fun `a cursor that is not ours is refused`() {
        val (_, queryAuditTrail) = query(1)
        listOf("not-a-cursor", "", "@@@", AuditCursor(base, UUID(0, 1)).encode().dropLast(3)).forEach { bad ->
            assertThatThrownBy { queryAuditTrail(bad, 10) }.describedAs(bad).isInstanceOf(InvalidCursorException::class.java)
        }
    }

    @Test
    fun `a cursor survives a round trip`() {
        val cursor = AuditCursor(Instant.parse("2026-10-30T12:00:00.123456Z"), UUID.randomUUID())
        assertThat(AuditCursor.decode(cursor.encode())).isEqualTo(cursor)
    }
}
