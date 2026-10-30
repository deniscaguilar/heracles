package com.heracles.platform.adapter.`in`.web

import com.heracles.platform.application.ReprocessDeadLetter
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Pattern
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class ReprocessResponse(
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val id: String,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = ["QUEUED"]) val status: String,
)

/** Admin-only (the prefix is guarded by AdminAuthorizationManager). */
@RestController
class DeadLetterController(private val reprocessDeadLetter: ReprocessDeadLetter) {

    @Operation(operationId = "reprocessDeadLetter", summary = "Send a dead message back to the work queue (ADMIN only)")
    @PostMapping("/api/v1/admin/dead-letters/{id}/reprocess", produces = ["application/json"])
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun reprocess(
        @PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]{1,128}$") id: String,
        authentication: Authentication,
    ): ReprocessResponse {
        reprocessDeadLetter((authentication as IdentityAuthentication).user, id)
        return ReprocessResponse(id, "QUEUED")
    }
}
