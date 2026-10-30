package com.heracles.platform.application.port.out

import com.heracles.platform.domain.TaskView
import java.util.UUID

interface TaskReader {
    /** Row-level security decides what is visible: a user finds only their own tasks. */
    fun find(id: UUID): TaskView?
}
