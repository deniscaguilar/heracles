package com.heracles.platform.domain

import java.util.UUID

/** What a screen needs to know about an asynchronous task: its id, status and, when done, a reference to the result. */
data class TaskView(val id: UUID, val status: String, val resultRef: String?)

class TaskNotFoundException(id: UUID) : RuntimeException("task not found: $id")
