package com.heracles.platform.application

import com.heracles.platform.application.port.out.AuditReader
import com.heracles.platform.application.port.out.TransactionContext
import com.heracles.platform.domain.AccessContext
import com.heracles.platform.domain.AuditCursor
import com.heracles.platform.domain.AuditRecord

data class AuditPage(val items: List<AuditRecord>, val nextCursor: String?)

/** Reads the audit trail for the backoffice (the caller was already authorized as ADMIN), page by page. */
class QueryAuditTrail(private val transactions: TransactionContext, private val reader: AuditReader) {
    operator fun invoke(cursor: String?, limit: Int): AuditPage {
        val after = cursor?.let(AuditCursor::decode)
        val size = limit.coerceIn(1, MAX_PAGE_SIZE)
        // One extra row tells whether there is a next page without a second query.
        val rows = transactions.run(AccessContext.admin()) { reader.find(after, size + 1) }
        val items = rows.take(size)
        val next = if (rows.size > size) items.last().let { AuditCursor(it.occurredAt, it.id).encode() } else null
        return AuditPage(items, next)
    }

    private companion object {
        const val MAX_PAGE_SIZE = 200
    }
}
