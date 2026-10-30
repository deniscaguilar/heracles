package com.heracles.platform.adapter.`in`.web

import com.heracles.platform.application.GetTaskStatus
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class TaskStatusResponse(
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val id: String,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = ["PENDING", "PROCESSING", "DONE", "FAILED"]) val status: String,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) val resultRef: String?,
)

@RestController
class TaskController(private val getTaskStatus: GetTaskStatus) {

    @Operation(operationId = "getTaskStatus", summary = "Status of one of the caller's asynchronous tasks")
    @GetMapping("/api/v1/tasks/{id}", produces = ["application/json"])
    fun status(@PathVariable id: UUID, authentication: Authentication): TaskStatusResponse {
        val task = getTaskStatus((authentication as IdentityAuthentication).user.id, id)
        return TaskStatusResponse(task.id.toString(), task.status, task.resultRef)
    }
}
