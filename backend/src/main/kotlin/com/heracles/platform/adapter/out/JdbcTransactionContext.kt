package com.heracles.platform.adapter.out

import com.heracles.platform.application.port.out.TransactionContext
import com.heracles.platform.domain.AccessContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate

/** `set_config(..., true)` is `SET LOCAL`: the values die with the transaction, so pooled connections never leak them. */
@Component
class JdbcTransactionContext(
    transactionManager: PlatformTransactionManager,
    private val jdbc: JdbcTemplate,
) : TransactionContext {
    private val transaction = TransactionTemplate(transactionManager)

    override fun <T> run(context: AccessContext, block: () -> T): T {
        check(!TransactionSynchronizationManager.isActualTransactionActive()) {
            "a transaction context cannot be opened inside another transaction"
        }
        return transaction.execute {
            jdbc.queryForRowSet(
                "select set_config('app.user_id', ?, true), set_config('app.scope', ?, true)",
                context.userId?.toString().orEmpty(),
                context.scope.databaseValue,
            )
            block()
        } as T
    }
}
