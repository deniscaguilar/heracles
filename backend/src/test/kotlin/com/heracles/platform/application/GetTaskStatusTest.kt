package com.heracles.platform.application

import com.heracles.platform.application.port.out.TaskReader
import com.heracles.platform.application.port.out.TransactionContext
import com.heracles.platform.domain.AccessContext
import com.heracles.platform.domain.TaskNotFoundException
import com.heracles.platform.domain.TaskView
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

private class StatusTransactions : TransactionContext {
    val contexts = mutableListOf<AccessContext>()
    override fun <T> run(context: AccessContext, block: () -> T): T = block().also { contexts += context }
}

private class StatusReader(private val tasks: Map<UUID, TaskView>) : TaskReader {
    override fun find(id: UUID): TaskView? = tasks[id]
}

class GetTaskStatusTest {
    private val transactions = StatusTransactions()
    private val task = TaskView(UUID.randomUUID(), "DONE", "ref-1")
    private val getTaskStatus = GetTaskStatus(transactions, StatusReader(mapOf(task.id to task)))

    @Test
    fun `the status of a task is read in the scope of the asking user`() {
        val user = UUID.randomUUID()
        assertThat(getTaskStatus(user, task.id)).isEqualTo(task)
        assertThat(transactions.contexts).containsExactly(AccessContext.forUser(user))
    }

    @Test
    fun `a task that is not visible to that user is reported as not found`() {
        assertThatThrownBy { getTaskStatus(UUID.randomUUID(), UUID.randomUUID()) }.isInstanceOf(TaskNotFoundException::class.java)
    }
}
