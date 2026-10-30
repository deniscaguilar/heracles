package com.heracles.platform.application

import com.heracles.platform.application.port.out.TaskReader
import com.heracles.platform.application.port.out.TransactionContext
import com.heracles.platform.domain.AccessContext
import com.heracles.platform.domain.TaskNotFoundException
import com.heracles.platform.domain.TaskView
import java.util.UUID

/** Status of a task, read as the asking user: another user's task, a system task and an unknown id are all "not found". */
class GetTaskStatus(private val transactions: TransactionContext, private val tasks: TaskReader) {
    operator fun invoke(userId: UUID, taskId: UUID): TaskView =
        transactions.run(AccessContext.forUser(userId)) { tasks.find(taskId) } ?: throw TaskNotFoundException(taskId)
}
