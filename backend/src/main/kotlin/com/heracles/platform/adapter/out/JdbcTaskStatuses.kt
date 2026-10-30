package com.heracles.platform.adapter.out

import com.heracles.platform.application.port.out.TaskStatuses
import com.heracles.platform.application.port.out.TransactionContext
import com.heracles.platform.domain.AccessContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.util.UUID

/** Each call is its own system-scope transaction, apart from the one that ran the handler (which may be in a user scope). */
@Component
class JdbcTaskStatuses(private val transactions: TransactionContext, private val jdbc: JdbcTemplate) : TaskStatuses {
    override fun markDone(taskId: UUID) = update(taskId, "DONE", null, null)
    override fun markRetrying(taskId: UUID, attempts: Int, errorCode: String) = update(taskId, "PROCESSING", attempts, errorCode)
    override fun markFailed(taskId: UUID, attempts: Int, errorCode: String) = update(taskId, "FAILED", attempts, errorCode)

    private fun update(taskId: UUID, status: String, attempts: Int?, errorCode: String?) {
        transactions.run(AccessContext.system()) {
            jdbc.update(
                "update task set status = ?, attempts = coalesce(?, attempts), last_error_code = coalesce(?, last_error_code), updated_at = now() where id = ?",
                status, attempts, errorCode, taskId,
            )
        }
    }
}
