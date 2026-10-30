package com.heracles.platform.application.port.out

import java.util.UUID

/** Status of the asynchronous task behind a message (table `task`, written in the system scope). Codes, never messages. */
interface TaskStatuses {
    fun markDone(taskId: UUID)

    /** The attempt failed and another will come: status PROCESSING. */
    fun markRetrying(taskId: UUID, attempts: Int, errorCode: String)

    /** Every attempt failed: status FAILED. */
    fun markFailed(taskId: UUID, attempts: Int, errorCode: String)
}
