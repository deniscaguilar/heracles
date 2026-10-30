package com.heracles.platform.application.port.out

import com.heracles.platform.domain.AccessContext

/**
 * Runs [block] in one database transaction that knows who it runs for, so row-level security can isolate users.
 * The context lives only as long as the transaction. Nesting is refused.
 */
interface TransactionContext {
    fun <T> run(context: AccessContext, block: () -> T): T
}
