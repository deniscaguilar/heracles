package com.heracles.platform.application.port.out

import com.heracles.platform.domain.AuditCursor
import com.heracles.platform.domain.AuditRecord

interface AuditReader {
    /** Newest first. Only the admin scope sees rows (row-level security). */
    fun find(after: AuditCursor?, limit: Int): List<AuditRecord>
}
